package com.cani.client.skill;

import org.springframework.stereotype.Component;

/**
 * Triển khai mặc định trung thực khi CANi chưa tích hợp trình giải mã QR/PDF,
 * bộ kiểm tra chữ ký số hoặc API tra cứu chính thức của đơn vị cấp chứng chỉ.
 * Tuyệt đối không giả lập kết quả thành công hay tự duyệt theo tên/file.
 */
@Component
public class UnsupportedCertificateLookupSource implements CertificateLookupSource {

    public static final String SOURCE_CODE = "UNSUPPORTED_SOURCE";

    @Override
    public CertificateLookupResult verifyCertificate(CertificateLookupRequest request) {
        return CertificateLookupResult.builder()
                .outcome(CertificateLookupOutcome.UNSUPPORTED_SOURCE)
                .verificationSource(SOURCE_CODE)
                .failureCode("LOOKUP_SOURCE_NOT_INTEGRATED")
                .reason("Chưa thể xác minh tự động: Chứng chỉ không có nguồn tra cứu chính thức được CANi hỗ trợ "
                        + "(chưa tích hợp API đơn vị cấp, giải mã QR tra cứu chính thức hoặc kiểm tra chữ ký số). "
                        + "Vui lòng nộp lại chứng chỉ có nguồn tra cứu hợp lệ khi hệ thống hỗ trợ.")
                .build();
    }
}
