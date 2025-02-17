package app.ImageTask.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.Map;

public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    public static <T> Mono<ResponseEntity<T>> handleError(Throwable error, HttpStatus status, T errorResponse) {
        log.error("Error occurred: {}", error.getMessage(), error);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    public static Mono<ResponseEntity<Map<String, String>>> handleInternalServerError(Throwable error) {
        return handleError(error, HttpStatus.INTERNAL_SERVER_ERROR, Map.of("error", "Internal Server Error"));
    }

    public static Mono<ResponseEntity<Map<String, String>>> handleNotFoundError(Throwable error) {
        return handleError(error, HttpStatus.NOT_FOUND, Map.of("error", "Resource not found"));
    }
}
