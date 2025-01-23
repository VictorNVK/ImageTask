package app.ImageTask.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class SizeDto {

    @NotNull
    @Min(20)
    private int width;

    @NotNull
    @Min(20)
    private int height;


}
