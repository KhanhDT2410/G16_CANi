package com.cani.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải có tối thiểu 8 ký tự")
    @jakarta.validation.constraints.Pattern(
        regexp = "^(?=.*[A-Z]).+$", 
        message = "Mật khẩu phải chứa ít nhất 1 chữ cái in hoa (A-Z)"
    )
    private String password;

    @NotNull(message = "Loại tài khoản bắt buộc phải chọn")
    private AccountType accountType;

    // Tên công ty/tổ chức (nếu đăng ký EVENT_ORGANIZER từ Web)
    private String companyName;

    @NotBlank(message = "Mã xác thực OTP không được để trống")
    @Size(min = 6, max = 6, message = "Mã OTP phải gồm đúng 6 chữ số")
    private String otpCode;
}
