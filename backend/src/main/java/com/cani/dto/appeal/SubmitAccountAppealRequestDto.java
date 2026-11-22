package com.cani.dto.appeal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitAccountAppealRequestDto {

    @NotBlank(message = "Email tài khoản không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Danh mục lý do giải trình không được để trống")
    private String reasonCategory;

    @NotBlank(message = "Nội dung giải trình và cam kết không được để trống")
    @Size(min = 10, max = 3000, message = "Nội dung giải trình phải từ 10 đến 3000 ký tự")
    private String appealContent;

    /**
     * Danh sách URL hình ảnh / tài liệu chứng minh khắc phục đính kèm
     */
    private String attachmentUrls;

    /**
     * Token tạm thời nhận được từ phản hồi lỗi đăng nhập (nếu có)
     */
    private String appealToken;
}
