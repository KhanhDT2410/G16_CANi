package com.cani.client.ekyc;

import com.cani.dto.ekyc.EkycIdentityResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * DEPRECATED: Đã loại bỏ hoàn toàn chế độ Mock theo yêu cầu nghiệp vụ thực tế.
 * Hệ thống chỉ chấp nhận xác thực thật qua VNPT eKYC hoặc Gemini Vision AI OCR.
 */
@Slf4j
public class MockEkycProvider implements EkycProvider {

    @Override
    public String getProviderName() {
        return "MOCK_EKYC";
    }

    @Override
    public EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        log.info("Executing MockEkycProvider: Trả về kết quả xác thực giả lập hợp lệ (tiết kiệm quota VNPT).");

        Map<String, Object> raw = new HashMap<>();
        raw.put("mock", true);
        raw.put("note", "Chế độ Mock giả lập eKYC để tiết kiệm quota VNPT.");

        return EkycIdentityResultDto.builder()
                .idNumber("001202012345")
                .fullName("NGUYỄN VĂN AN")
                .dob("15/08/2002")
                .gender("Nam")
                .nationality("Việt Nam")
                .originAddress("Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .residenceAddress("Khu Công Nghệ Cao, TP. Thủ Đức, TP. Hồ Chí Minh")
                .expiryDate("15/08/2042")
                .issueDate("10/05/2021")
                .identificationFeatures("Nốt ruồi cách 1cm dưới đuôi mắt phải")
                .ocrConfidence(0.98)
                .faceMatchScore(96.8)
                .isCutCorner(false)
                .isScreenCaptured(false)
                .isPhotoshoped(false)
                .isExpired(false)
                .routingDecision("AUTO_APPROVED")
                .routingReason("Xác thực thành công qua Mock Provider (Local/Dev Profile).")
                .rawAnalysis(raw)
                .build();
    }
}
