package app.ImageTask.controller;

import  app.ImageTask.domain.dto.CutTimeDto;
import app.ImageTask.domain.dto.SizeDto;
import app.ImageTask.domain.dto.VideoDto;
import app.ImageTask.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, String>>> uploadVideo(@RequestPart("file") FilePart file) {
        return videoService.saveVideo(file);
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Boolean>>> changeVideSize(@Valid @RequestBody SizeDto sizeDto,
                                                                     @PathVariable String id) {
        return videoService.changeVideoSize(sizeDto, id);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<VideoDto>> getVideo(@PathVariable String id) {
        return videoService.getVideo(id);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Boolean>>> deleteVideo(@PathVariable String id) {
        return videoService.deleteVideo(id);
    }

    @GetMapping("/download/{id}")
    public Mono<ResponseEntity<?>> downloadVideo(@PathVariable String id) {
        return videoService.downloadVideo(id);
    }

    @PatchMapping("/toGif/{id}")
    public Mono<ResponseEntity<Map<String, Boolean>>> toGif(@PathVariable String id) {
        return videoService.toGif(id);
    }

    @PatchMapping("/cut/{id}")
    public Mono<ResponseEntity<Map<String, Boolean>>> cutByTime(@PathVariable String id, @RequestBody @Valid CutTimeDto cutTimeDto) {
        return videoService.cutByTime(id, cutTimeDto);
    }

    @PatchMapping("/toHLS/{id}")
    public Mono<ResponseEntity<Map<String, Boolean>>> toHLS(@PathVariable String id) {
        return videoService.toHLS(id);
    }

    @Operation(summary = "Доступные варианты битрейта - 800k 1200k 2400k 4800k 7200k")
    @GetMapping("/getHLS/{id}/{bitrate}")
    public Mono<ResponseEntity<?>> getHlsPlayList(@PathVariable String id, @PathVariable String bitrate){
        return videoService.getHlsPlaylist(id, bitrate);
    }

    @Operation(summary = "Изменение кодека видео, доступные варианты - libx264 libx265 libvpx-vp9 libxvid")
    @PatchMapping("/change_codec/{id}/{codec}")
    public Mono<ResponseEntity<Map<String, Boolean>>> changeCodec(@PathVariable String id, @PathVariable String codec){
        return videoService.transcodeVideo(id, codec);
    }
}
