package app.ImageTask.controller;

import app.ImageTask.domain.dto.SizeDto;
import app.ImageTask.domain.dto.VideoDto;
import app.ImageTask.service.VideoService;
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
        return null;
    }

}
