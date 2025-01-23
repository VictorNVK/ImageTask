package app.ImageTask.domain.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "videos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Video {

    @Id
    private String id;
    private String filename;
    private String filePath;
    private Boolean processing;
    private Boolean processingSuccess;
}
