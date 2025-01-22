package app.ImageTask.service;

import app.ImageTask.config.VariableConfig;
import app.ImageTask.domain.entity.Video;
import app.ImageTask.repository.VideoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import org.springframework.data.convert.ValueConverter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.google.common.io.Files.getFileExtension;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final VariableConfig variableConfig;
    private FFmpegExecutor executor;
    private FFmpeg ffmpeg;
    private FFprobe ffprobe;


    @SneakyThrows
    @PostConstruct
    public void initFFmpeg() {
        // Use environment variables to set the path to ffmpeg and ffprobe

        String ffmpegPath = variableConfig.FFMPEG_PATH;
        String ffprobePath = variableConfig.FFPROBE_PATH;

        if (ffmpegPath == null || ffprobePath == null) {
            throw new IllegalStateException("FFMPEG_PATH and FFPROBE_PATH environment variables must be set");
        }

        ffmpeg = new FFmpeg(ffmpegPath);
        ffprobe = new FFprobe(ffprobePath);
        executor = new FFmpegExecutor(ffmpeg, ffprobe);
    }



    public Mono<ResponseEntity<Map<String, String>>> saveVideo(FilePart file) {
        return Mono.just(file)
                .flatMap(f -> {
                    if (!isMp4File(f)) {
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
                    Map<String, String> responseMap = new HashMap<>();
                    responseMap.put("id", video.getId());
                    return ResponseEntity.ok(responseMap);
                })
                .onErrorResume(e -> {
                    Map<String, String> errorMap = new HashMap<>();
                    errorMap.put("error", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMap));
                });
    }

    private boolean isMp4File(FilePart file) {
        String contentType = file.headers().getContentType().toString();
        String fileExtension = getFileExtension(file.filename());
        return "video/mp4".equalsIgnoreCase(contentType) && "mp4".equalsIgnoreCase(fileExtension);
    }
}
