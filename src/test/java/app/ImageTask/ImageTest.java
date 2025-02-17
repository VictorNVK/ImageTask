package app.ImageTask;

import app.ImageTask.domain.dto.SizeDto;
import app.ImageTask.service.VideoService;
import app.ImageTask.util.MockFilePart;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ImageTest extends AbstractMongoTest {

    @Autowired
    private VideoService videoService;

    @Autowired
    private WebTestClient webTestClient;

    private static String uuid;

    @Test
    @Order(0)
    void correctUploadVideo() throws Exception {
        Path filePath = Paths.get("src/test/java/app/ImageTask/resources/test-video.mp4");
        byte[] fileContent = Files.readAllBytes(filePath);

        MockFilePart filePart = new MockFilePart(filePath.getFileName().toString(), MediaType.parseMediaType("video/mp4"), fileContent);

        Mono<ResponseEntity<Map<String, String>>> responseMono = videoService.saveVideo(filePart);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().containsKey("id"));
                    uuid = response.getBody().get("id");
                })
                .verifyComplete();
    }

    @Test
    @Order(1)
    void correctChangeSize() {
        String id = uuid;
        SizeDto sizeDto = new SizeDto(200, 400);

        Mono<ResponseEntity<Map<String, Boolean>>> responseMono = videoService.changeVideoSize(sizeDto, id);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().containsKey("success"));
                    assertThat(response.getBody().get("success")).isEqualTo(true);
                })
                .verifyComplete();
    }

    @Test
    @Order(2)
    void correctConvertToGif(){
        String id = uuid;

        Mono<ResponseEntity<Map<String, Boolean>>> responseMono = videoService.toGif(id);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().containsKey("success"));
                    assertThat(response.getBody().get("success")).isEqualTo(true);
                })
                .verifyComplete();
    }

    @Test
    @Order(3)
    void correctToHls(){
        String id = uuid;
        Mono<ResponseEntity<Map<String, Boolean>>> responseMono = videoService.toHLS(id);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().containsKey("success"));
                    assertThat(response.getBody().get("success")).isEqualTo(true);
                }).verifyComplete();
    }



    @Test
    @Order(4)
    void correctDownloadVideo(){
        String id = uuid;
        Mono<ResponseEntity<?>> responseMono = videoService.downloadVideo(id);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                }).verifyComplete();
    }

    @Test
    @Order(5)
    void correctHlsDownload(){
        String id = uuid;
        Mono<ResponseEntity<?>> responseMono = videoService.getHlsPlaylist(id);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                }).verifyComplete();
    }

    @Test
    @Order(6)
    void correctDeleteVideo() {
        String id = uuid;

        Mono<ResponseEntity<Map<String, Boolean>>> responseMono = videoService.deleteVideo(id);

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(Objects.requireNonNull(response.getBody()).containsKey("success"));
                    assertThat(response.getBody().get("success")).isEqualTo(true);
                })
                .verifyComplete();
    }

}

