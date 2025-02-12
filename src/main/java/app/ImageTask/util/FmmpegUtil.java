package app.ImageTask.util;

import lombok.SneakyThrows;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

import static com.google.common.io.Files.getFileExtension;

@Component
public class FmmpegUtil {

    public Mono<String> getFileFormat(String fileName) {
        return Mono.fromCallable(() -> {
                    int lastIndex = fileName.lastIndexOf('.');
                    if (lastIndex == -1) {
                        return "Unknown"; // Если расширение отсутствует
                    }
                    return fileName.substring(lastIndex + 1).toLowerCase();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> getNameWithoutExtension(String fileName) {
        return Mono.fromCallable(() -> {
            int lastDotIndex = fileName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                return fileName.substring(0, lastDotIndex);
            }
            return fileName; // Если расширение отсутствует, возвращаем имя как есть
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> convertVideoToGif(String filePath, FFmpegExecutor executor) {
        return Mono.fromCallable(() -> {
                    Path tempOutputPath = Paths.get(filePath.replace(".mp4", ".gif"));
                    FFmpegBuilder builder = new FFmpegBuilder()
                            .setInput(filePath)
                            .addOutput(tempOutputPath.toString())
                            .setFormat("gif")
                            .done();

                    FFmpegJob job = executor.createJob(builder);
                    job.run();

                    return null;
                }).subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Boolean> isMp4File(FilePart file) {
        return Mono.fromCallable(() -> {
            String contentType = file.headers().getContentType().toString();
            String fileExtension = getFileExtension(file.filename());
            return "video/mp4".equalsIgnoreCase(contentType) && "mp4".equalsIgnoreCase(fileExtension);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> convertVideo(String filePath, int width, int height, FFmpegExecutor executor) {
        return Mono.fromCallable(() -> {
                    Path tempOutputPath = Paths.get(filePath.replace(".mp4", "_temp.mp4"));

                    FFmpegBuilder builder = new FFmpegBuilder()
                            .setInput(filePath)
                            .addOutput(tempOutputPath.toString())
                            .setVideoResolution(width, height)
                            .done();

                    FFmpegJob job = executor.createJob(builder);
                    job.run();

                    try {
                        Files.move(tempOutputPath, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to move the converted video file", e);
                    }

                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
