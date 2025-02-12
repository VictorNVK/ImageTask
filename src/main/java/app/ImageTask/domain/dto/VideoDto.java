package app.ImageTask.domain.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VideoDto {

    private String id;
    private String filename;
    private String format;
    private Boolean processing;
    private Boolean processingSuccess;

}
