package app.ImageTask.domain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VideoDto {

    private String id;
    private String filename;
    private boolean processing;
    private Boolean processingSuccess;

}
