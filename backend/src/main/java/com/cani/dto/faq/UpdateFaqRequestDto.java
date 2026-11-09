package com.cani.dto.faq;

import com.cani.entity.enums.FaqCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFaqRequestDto {

    @NotNull(message = "Danh mục câu hỏi không được để trống")
    private FaqCategory category;

    @NotBlank(message = "Câu hỏi không được để trống")
    @Size(min = 10, max = 500, message = "Câu hỏi phải có từ 10 đến 500 ký tự")
    private String question;

    @NotBlank(message = "Câu trả lời không được để trống")
    @Size(min = 20, message = "Câu trả lời phải có ít nhất 20 ký tự")
    private String answer;
}

