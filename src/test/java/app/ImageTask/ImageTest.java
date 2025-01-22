package app.ImageTask;

import app.ImageTask.domain.entity.Video;
import app.ImageTask.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

public class ImageTest extends AbstractMongoTest{

    @Autowired
    private VideoService videoService;

    @Test
    void correctUploadVideo(){
    }
}
