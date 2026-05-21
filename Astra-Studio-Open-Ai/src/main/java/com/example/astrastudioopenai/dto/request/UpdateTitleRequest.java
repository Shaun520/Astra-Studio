package com.example.astrastudioopenai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTitleRequest {

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 200, message = "Title length cannot exceed 200 characters")
    private String title;
}
