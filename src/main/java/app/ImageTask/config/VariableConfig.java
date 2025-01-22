package app.ImageTask.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VariableConfig {

    @Value("${ffmpeg.path}")
    public String FFMPEG_PATH;

    @Value("${ffprobe.path}")
    public String FFPROBE_PATH;
}
