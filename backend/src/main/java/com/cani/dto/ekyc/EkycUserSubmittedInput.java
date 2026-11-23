package com.cani.dto.ekyc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Dữ liệu người dùng nhập tay hoặc quét QR từ client.
 * CHỈ ĐƯỢC DÙNG ĐỂ ĐỐI CHIẾU CHÉO với kết quả OCR từ VNPT eKYC — tuyệt đối không ghi đè dữ liệu xác minh.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EkycUserSubmittedInput {

    private String cccdNumber;
    private String fullName;
    private String dob;
    private String gender;
    private String originAddress;
    private String residenceAddress;
    private String issueDate;
    private String expiryDate;

    public boolean hasAnyData() {
        return isPresent(cccdNumber)
                || isPresent(fullName)
                || isPresent(dob)
                || isPresent(gender)
                || isPresent(originAddress)
                || isPresent(residenceAddress)
                || isPresent(issueDate)
                || isPresent(expiryDate);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
