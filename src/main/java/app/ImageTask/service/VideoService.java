package app.ImageTask.service;

import app.ImageTask.config.VariableConfig;
import app.ImageTask.domain.dto.SizeDto;
import app.ImageTask.domain.dto.VideoDto;
import app.ImageTask.domain.entity.Video;
import app.ImageTask.repository.VideoRepository;
import app.ImageTask.util.FmmpegUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {

    private final VideoRepository videoRepository;
    private final VariableConfig variableConfig;
    private final FmmpegUtil ffmpegUtil;
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
        return Mono.zip(ffmpegUtil.isMp4File(file),
                        ffmpegUtil.getNameWithoutExtension(file.filename()),
                        ffmpegUtil.getFileFormat(file.filename()))
                .flatMap(tuple -> {
                    boolean isMp4 = tuple.getT1();
                    String filename = tuple.getT2();
                    String format = tuple.getT3();

                    if (!isMp4) {
                        return Mono.error(new IllegalArgumentException("Uploaded file is not an MP4 file!!"));
                    }

                    String id = UUID.randomUUID().toString();
                    Path filePath = Paths.get("videos", id + ".mp4");

                    return Mono.fromCallable(() -> {
                                Files.createDirectories(filePath.getParent());
                                return filePath;
                            }).subscribeOn(Schedulers.boundedElastic())
                            .flatMap(path -> file.transferTo(path)
                                    .then(Mono.fromCallable(() -> Video.builder()
                                            .id(id)
                                            .filename(filename)
                                            .format(format)
                                            .filePath(path.toString())
                                            .processing(false)
                                            .processingSuccess(null)
                                            .build()))
                            );
                })
                .flatMap(videoRepository::save)
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


    public Mono<ResponseEntity<VideoDto>> getVideo(String id) {
        return videoRepository.findById(id)
                .map(video ->
                        VideoDto.builder()
                                .id(video.getId())
                                .filename(video.getFilename())
                                .processing(video.getProcessing())
                                .processingSuccess(video.getProcessingSuccess())
                                .build())
                .map(ResponseEntity::ok)
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

    public Mono<ResponseEntity<?>> downloadVideo(String id) {
        return videoRepository.findById(id)
                .flatMap(video -> {
                    Path filePath = Paths.get(video.getFilePath());
                    if (Files.exists(filePath)) {
                        DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
                        try {
                            DataBuffer dataBuffer = dataBufferFactory.wrap(Files.readAllBytes(filePath));
                            return Mono.just(ResponseEntity.ok()
                                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + video.getFilename() + "." + video.getFormat() + "\"")
                                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                    .body(dataBuffer));
                        } catch (IOException e) {
                            log.error("Error reading file: {}", e.getMessage());
                            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                        }
                    } else {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
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
                            .then(ffmpegUtil.convertVideo(video.getFilePath(), sizeDto.getWidth(), sizeDto.getHeight(), executor))
                            .then(Mono.fromRunnable(() -> {
                                video.setProcessing(false);
                                video.setProcessingSuccess(true);
                                video.setFilePath(Paths.get("videos", id + ".mp4").toString());
                                videoRepository.save(video).subscribe();
                            }))
                            .thenReturn(ResponseEntity.ok(Map.of("success", true)))
                            .onErrorResume(e -> {
                                log.error("Conversion failed, ID: {}", id, e);
                                return handleConversionError(id);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Global error in conversion: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", false)));
                });
    }

    public Mono<ResponseEntity<Map<String, Boolean>>> toGif(String id) {
        return videoRepository.findById(id)
                .flatMap(video -> {
                    video.setProcessing(true);
                    video.setProcessingSuccess(null);

                    return videoRepository.save(video)
                            .then(ffmpegUtil.convertVideoToGif(video.getFilePath(), executor))
                            .then(Mono.fromRunnable(() -> {
                                video.setProcessing(false);
                                video.setProcessingSuccess(true);
                                video.setFormat("gif");
                                video.setFilePath(Paths.get("videos", id + ".gif").toString());
                                log.info("Video converted successfully, ID: {}", id);
                                videoRepository.save(video).subscribe();
                            }))

                            .thenReturn(ResponseEntity.ok(Map.of("success", true)))
                            .onErrorResume(e -> {
                                log.error("Conversion failed, ID: {}", id, e);
                                return handleConversionError(id);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Global error in conversion: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", false)));
                });
    }

    private Mono<ResponseEntity<Map<String, Boolean>>> handleConversionError(String id) {
        return videoRepository.findById(id)
                .flatMap(errorVideo -> {
                    errorVideo.setProcessing(false);
                    errorVideo.setProcessingSuccess(false);
                    return videoRepository.save(errorVideo);
                })
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", false)));
    }
}
