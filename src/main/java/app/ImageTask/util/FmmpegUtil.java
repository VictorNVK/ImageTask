package app.ImageTask.util;

import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.io.Files.getFileExtension;

@Slf4j
@Component
public class FmmpegUtil {

    public Mono<String> getFileFormat(String fileName) {
        return Mono.fromCallable(() -> {
                    int lastIndex = fileName.lastIndexOf('.');
                    if (lastIndex == -1) {
                        return "Unknown";
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
            return fileName;
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


    public Mono<Void> cutVideoByTime(String filePath, String start, String end, FFmpegExecutor executor) {
        return Mono.fromRunnable(() -> {
                    Path outputPath = Paths.get(filePath.replace(".mp4", "_cut.mp4"));
                    long startMillis = parseTimeToMillis(start);
                    long endMillis = parseTimeToMillis(end);

                    FFmpegBuilder builder = new FFmpegBuilder()
                            .setInput(filePath)
                            .addOutput(outputPath.toString())
                            .setStartOffset(startMillis, TimeUnit.MILLISECONDS)
                            .setDuration(endMillis - startMillis, TimeUnit.MILLISECONDS)
                            .done();

                    FFmpegJob job = executor.createJob(builder);
                    job.run();
                    try {
                        Files.move(outputPath, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to move the converted video file", e);
                    }

                }).subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Void> convertVideoToHLS(String filePath, String outputDir, FFmpegExecutor executor) {
        return Mono.fromRunnable(() -> {
            try {
                Files.createDirectories(Paths.get(outputDir));
                FFmpegBuilder builder = new FFmpegBuilder()
                        .setInput(filePath)
                        .addOutput(outputDir + "/index.m3u8")
                        .addExtraArgs("-codec:v", "libx264", "-codec:a", "aac", "-start_number", "0", "-hls_time", "10", "-hls_list_size", "0", "-f", "hls")
                        .done();
                FFmpegJob job = executor.createJob(builder);
                job.run();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directories for HLS output", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }


    public Mono<Void> transcodeVideoWithCodec(String inputFilePath, String outputCodec) {
        return Mono.fromCallable(() -> {

            Path outputFilePath = Paths.get(inputFilePath.replace(".mp4", "_transcoded.mp4"));

            if (!Files.exists(Paths.get(inputFilePath))) {
                throw new RuntimeException("Input file not found: " + Paths.get(inputFilePath));
            }

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg",
                    "-i", inputFilePath,
                    "-c:v", outputCodec,
                    outputFilePath.toString()
            );
            processBuilder.redirectErrorStream(true);

            try {
                Process process = processBuilder.start();
                boolean completed = process.waitFor(30, TimeUnit.MINUTES);
                if (completed) {
                    int exitCode = process.exitValue();
                    if (exitCode == 0) {
                        log.info("Video transcoded successfully to codec: {}", outputCodec);
                        Files.move(outputFilePath, Paths.get(inputFilePath), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        throw new RuntimeException("FFmpeg process exited with error code: " + exitCode);
                    }
                } else {
                    process.destroyForcibly();
                    throw new RuntimeException("FFmpeg process timed out");
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Error running FFmpeg process", e);
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> convertVideoToHLSWithMultiBitrate(String filePath, String outputDir, FFmpegExecutor executor) {
        return Mono.fromRunnable(() -> {
            try {
                String[] bitrates = {"800k", "1200k", "2400k", "4800k", "7200k"};
                String[] resolutions = {"640:360", "842:480", "1280:720", "1920:1080", "2560:1440"};


                for (int i = 0; i < bitrates.length; i++) {
                    Files.createDirectories(Paths.get(outputDir, "stream_" + i));

                    FFmpegBuilder builder = new FFmpegBuilder()
                            .setInput(filePath)
                            .addOutput(outputDir + "/stream_" + i + "/index.m3u8")
                            .addExtraArgs("-codec:v", "libx264", "-codec:a", "aac", "-b:v", bitrates[i], "-vf", "scale=" + resolutions[i],
                                    "-start_number", "0", "-hls_time", "10", "-hls_list_size", "0", "-f", "hls")
                            .done();

                    FFmpegJob job = executor.createJob(builder);
                    job.run();
                }

                FFmpegBuilder masterBuilder = new FFmpegBuilder()
                        .setInput(filePath)
                        .addOutput(outputDir + "/master.m3u8")
                        .addExtraArgs("-codec", "copy", "-start_number", "0", "-hls_time", "10", "-hls_list_size", "0", "-f", "hls",
                                "-master_pl_name", "master.m3u8",
                                "-var_stream_map", "v:0,a:0")
                        .done();

                FFmpegJob masterJob = executor.createJob(masterBuilder);
                masterJob.run();

            } catch (IOException e) {
                throw new RuntimeException("Failed to create directories for HLS output", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }


    private long parseTimeToMillis(String time) {
        String[] parts = time.split(":");
        long millis = 0;

        if (parts.length == 3) {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            millis = TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes) + TimeUnit.SECONDS.toMillis(seconds);
        } else if (parts.length == 1) {
            long seconds = Long.parseLong(parts[0]);
            millis = TimeUnit.SECONDS.toMillis(seconds);
        } else {
            throw new IllegalArgumentException("Invalid time format: " + time);
        }

        return millis;
    }

    public List<String> extractTsFilesFromPlaylist(String playlistContent) {
        List<String> tsFiles = new ArrayList<>();
        Pattern pattern = Pattern.compile("(.*\\.ts)");
        Matcher matcher = pattern.matcher(playlistContent);
        while (matcher.find()) {
            tsFiles.add(matcher.group(1));
        }
        return tsFiles;
    }

    public byte[] createZipArchive(Path parentDir, List<String> tsFiles) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(baos)) {
            zipOut.putNextEntry(new ZipEntry("index.m3u8"));
            Files.copy(parentDir.resolve("index.m3u8"), zipOut);
            zipOut.closeEntry();
            for (String tsFile : tsFiles) {
                zipOut.putNextEntry(new ZipEntry(tsFile));
                Files.copy(parentDir.resolve(tsFile), zipOut);
                zipOut.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}

