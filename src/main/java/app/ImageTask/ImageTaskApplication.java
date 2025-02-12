package app.ImageTask;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ImageTaskApplication {

	public static void main(String[] args) {
		SpringApplication.run(ImageTaskApplication.class, args);
	}

	@Bean
	public Dotenv dotenv(){
		return Dotenv.load();
	}
}
