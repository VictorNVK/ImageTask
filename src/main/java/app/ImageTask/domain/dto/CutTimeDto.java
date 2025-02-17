package app.ImageTask.domain.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CutTimeDto {

    @Pattern(regexp = "\\d{2}:\\d{2}:\\d{2}", message = "Invalid time format. Use HH:MM:SS.")
    private String start;

    @Pattern(regexp = "\\d{2}:\\d{2}:\\d{2}", message = "Invalid time format. Use HH:MM:SS.")
    private String end;
}
