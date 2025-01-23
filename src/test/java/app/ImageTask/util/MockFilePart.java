package app.ImageTask.util;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;

public class MockFilePart implements FilePart {

    private final String filename;
    private final HttpHeaders headers;
    private final Flux<DataBuffer> content;

    public MockFilePart(String filename, MediaType mediaType, byte[] fileContent) {
        this.filename = filename;
        this.headers = new HttpHeaders();
        this.headers.setContentType(mediaType);
        DataBufferFactory factory = new DefaultDataBufferFactory();
        this.content = Flux.just(factory.wrap(fileContent));
    }

    @Override
    public String filename() {
        return filename;
    }

    @Override
    public Mono<Void> transferTo(Path dest) {
        return Mono.fromRunnable(() -> {
            try {
                Files.write(dest, (content.blockFirst()).asByteBuffer().array());
            } catch (Exception e) {
                throw new RuntimeException("Failed to transfer file", e);
            }
        });
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public Flux<DataBuffer> content() {
        return content;
    }

}
