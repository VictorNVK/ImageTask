package app.ImageTask.service;

import app.ImageTask.config.VariableConfig;
import app.ImageTask.domain.dto.SizeDto;
import app.ImageTask.domain.dto.VideoDto;
import app.ImageTask.domain.entity.Video;
import app.ImageTask.repository.VideoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.google.common.io.Files.getFileExtension;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {

    private final VideoRepository videoRepository;
    private final VariableConfig variableConfig;
    private FFmpegExecutor executor;

    @SneakyThrows
    @PostConstruct
    public void initFFmpeg() {
        String ffmpegPath = variableConfig.FFMPEG_PATH;
        String ffprobePath = variableConfig.FFPROBE_PATH;

        if (ffmpegPath == null || ffprobePath == null) {
            throw new IllegalStateException("FFMPEG_PATH and FFPROBE_PATH environment variables must be set");
        }

        FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
        FFprobe ffprobe = new FFprobe(ffprobePath);
        executor = new FFmpegExecutor(ffmpeg, ffprobe);

        log.info("ffmpeg was initialized");
    }

    public Mono<ResponseEntity<Map<String, String>>> saveVideo(FilePart file) {
        return Mono.just(file)
                .flatMap(f -> {
                    if (!isMp4File(f)) {
                        log.error("Uploaded file, is not a MP4 format", f.filename());
                        return Mono.error(new IllegalArgumentException("Uploaded file is not an MP4 file!!"));
                    }
                    String filename = f.filename();
                    String id = UUID.randomUUID().toString();
                    Path filePath = Paths.get("videos", id + ".mp4");

                    return f.transferTo(filePath)
                            .then(Mono.just(filePath))
                            .flatMap(path -> {
                                Video video = Video.builder()
                                        .id(id)
                                        .filename(filename)
                                        .filePath(path.toString())
                                        .processing(false)
                                        .processingSuccess(null)
                                        .build();

                                return videoRepository.save(video);
                            });
                })
                .map(video -> {
                    log.info("Video was saved, ID : {}", video.getId());
                    Map<String, String> responseMap = new HashMap<>();
                    responseMap.put("id", video.getId());
                    return ResponseEntity.ok(responseMap);
                })
                .onErrorResume(e -> {
                    log.error("Error in saving video : {}", e.getMessage());
                    Map<String, String> errorMap = new HashMap<>();
                    errorMap.put("error", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMap));
                });
    }

    public Mono<ResponseEntity<Map<String, Boolean>>> changeVideoSize(SizeDto sizeDto, String id) {
        return videoRepository.findById(id)
                .flatMap(video -> {
                    if (sizeDto.getWidth() % 2 != 0 || sizeDto.getHeight() % 2 != 0) {
                        log.error("Invalid size for video, ID : {}", id);
                        return Mono.error(new IllegalArgumentException("Width and height must be even numbers greater than 20"));
                    }
                    video.setProcessing(true);
                    video.setProcessingSuccess(null);

                    return videoRepository.save(video)
                            .then(Mono.fromFuture(CompletableFuture.runAsync(() -> {
                                try {
                                    convertVideo(video.getFilePath(), sizeDto.getWidth(), sizeDto.getHeight());
                                    video.setProcessing(false);
                                    video.setProcessingSuccess(true);
                                    log.info("Video was converted successful, ID : {}", id);
                                } catch (IOException e) {
                                    video.setProcessing(false);
                                    video.setProcessingSuccess(false);
                                    log.error("Error converting video ID: {}", id, e);
                                } finally {
                                    videoRepository.save(video).subscribe();
                                }
                            })))
                            .then(Mono.just(ResponseEntity.ok(Map.of("success", true))));
                })
                .onErrorResume(e -> {
                    log.error("Error in changing video size {}", e.getMessage());
                    Map<String, Boolean> errorMap = new HashMap<>();
                    errorMap.put("error", Boolean.FALSE);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMap));
                });
    }

    public Mono<ResponseEntity<VideoDto>> getVideo(String id) {
        return videoRepository.findById(id)
                .map(video ->
                        VideoDto.builder()
                                .id(video.getId())
                                .filename(video.getFilename())
                                .processing(video.getProcessing())
                                .processingSuccess(video.getProcessingSuccess())
                                .build())
                .map(videoDto -> ResponseEntity.ok(videoDto))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    public Mono<ResponseEntity<Map<String, Boolean>>> deleteVideo(String id) {
        return videoRepository.findById(id)
                .flatMap(video -> {
                    try {
                        Files.deleteIfExists(Paths.get(video.getFilePath()));
                        log.info("Video file deleted successfully with ID: {}", id);
                        return videoRepository.delete(video)
                                .then(Mono.just(ResponseEntity.ok(Map.of("success", true))));
                    } catch (IOException e) {
                        log.error("Error deleting video file with ID: {}", id, e);
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false)));
                    }
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false))));
    }

    private boolean isMp4File(FilePart file) {
        String contentType = file.headers().getContentType().toString();
        String fileExtension = getFileExtension(file.filename());
        return "video/mp4".equalsIgnoreCase(contentType) && "mp4".equalsIgnoreCase(fileExtension);
    }

    @SneakyThrows
    private void convertVideo(String filePath, int width, int height) throws IOException {
        Path tempOutputPath = Paths.get(filePath.replace(".mp4", "_temp.mp4"));

        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(filePath)
                .addOutput(tempOutputPath.toString())
                .setVideoResolution(width, height)
                .done();

        FFmpegJob job = executor.createJob(builder);
        job.run();
        Files.move(tempOutputPath, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
    }
}
