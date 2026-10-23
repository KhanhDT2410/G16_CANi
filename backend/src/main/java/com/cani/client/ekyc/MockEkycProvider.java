package com.cani.client.ekyc;

import com.cani.dto.ekyc.EkycIdentityResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Provider giả lập eKYC dùng cho môi trường Local / Dev / Unit Test.
 * Giúp phát triển giao diện và kiểm thử tính năng hoàn toàn miễn phí mà không làm hao hụt 100 requests dùng thử của VNPT.
 */
@Slf4j
@Component
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
