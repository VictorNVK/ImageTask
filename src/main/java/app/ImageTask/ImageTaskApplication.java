package app.ImageTask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ImageTaskApplication {

	public static void main(String[] args) {
		SpringApplication.run(ImageTaskApplication.class, args);
	}
}
