package app.ImageTask.service;

import app.ImageTask.domain.entity.Video;
import app.ImageTask.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.google.common.io.Files.getFileExtension;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    public Mono<ResponseEntity<Map<String, String>>> saveVideo(FilePart file) {
        return Mono.just(file)
                .flatMap(f -> {
                    if (!isMp4File(f)) {
                        return Mono.error(new IllegalArgumentException("Uploaded file is not an MP4 file!!"));
                    }
                    String filename = f.filename();
                    String filePath = null;

                    Video video = Video.builder()
                            .id(UUID.randomUUID().toString())
                            .filename(filename)
                            .filePath(filePath)
                            .processing(false)
                            .processingSuccess(null)
                            .build();

                    return videoRepository.save(video);
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
