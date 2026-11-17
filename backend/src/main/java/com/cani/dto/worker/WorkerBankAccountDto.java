package com.cani.dto.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO chứa thông tin tài khoản ngân hàng thụ hưởng của Worker (Mục 11 & 12 SRS).
 * Dùng để sinh mã VietQR chuẩn Napas 247 cho Event Organizer (EO) quét chuyển khoản trực tiếp.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerBankAccountDto {

    @NotBlank(message = "Mã ngân hàng (Bank Code) không được để trống")
    @Size(max = 30, message = "Mã ngân hàng không vượt quá 30 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã ngân hàng không hợp lệ")
    private String bankCode;

    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    @Size(min = 5, max = 50, message = "Số tài khoản ngân hàng từ 5 đến 50 ký tự")
    @Pattern(regexp = "^[0-9A-Za-z]+$", message = "Số tài khoản ngân hàng chỉ chứa chữ và số")
    private String bankAccountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(min = 2, max = 100, message = "Tên chủ tài khoản từ 2 đến 100 ký tự")
    private String bankAccountName;

    /**
     * URL hình ảnh VietQR tự động sinh (Read-only từ hệ thống)
     */
    private String vietQrUrl;
}
