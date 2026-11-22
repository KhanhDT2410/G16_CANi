package com.cani.client.ekyc;

import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.entity.enums.EkycRoutingDecision;
import com.cani.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Điều phối viên eKYC production — sử dụng VNPT eKYC là nhà cung cấp xác minh danh tính DUY NHẤT.
 *
 * Nguyên tắc bảo mật & kiến trúc:
 * - Không sử dụng Gemini hay bất kỳ LLM nào trong luồng eKYC.
 * - Không fallback sang AI/Mock khi VNPT lỗi, thiếu cấu hình hoặc không trả đủ dữ liệu.
 * - Không ghi CCCD đầy đủ, họ tên, ảnh hay raw response chứa PII vào log.
 * - Lỗi kỹ thuật trả về cờ technicalFailure=true (cho phép retry), không đánh đồng gian lận.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class PrimaryEkycProvider implements EkycProvider {

    private final VnptEkycClient vnptClient;

    @Override
    public String getProviderName() {
        return vnptClient.getProviderName();
    }

    @Override
    public EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        try {
            EkycIdentityResultDto result = vnptClient.verifyIdentity(
                    frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);
            if (result == null) {
                log.error("eKYC Provider [{}] trả về kết quả null không mong đợi.", getProviderName());
                return EkycIdentityResultDto.builder()
                        .providerName(getProviderName())
                        .technicalFailure(true)
                        .technicalErrorCode("VNPT_EMPTY_RESULT")
                        .routingDecision(EkycRoutingDecision.TECHNICAL_FAILURE.name())
                        .routingReason("Dịch vụ VNPT eKYC không trả về dữ liệu phản hồi. Vui lòng thử lại sau.")
                        .rawAnalysis(Map.of("provider", getProviderName(), "errorCode", "VNPT_EMPTY_RESULT"))
                        .build();
            }
            return result;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi kỹ thuật không mong đợi khi gọi VNPT eKYC: {}", e.getClass().getSimpleName());
            return EkycIdentityResultDto.builder()
                    .providerName(getProviderName())
                    .technicalFailure(true)
                    .technicalErrorCode("VNPT_UNEXPECTED_ERROR")
                    .routingDecision(EkycRoutingDecision.TECHNICAL_FAILURE.name())
                    .routingReason("Hệ thống xác thực VNPT eKYC đang gặp sự cố kỹ thuật tạm thời. Vui lòng thử lại sau.")
                    .rawAnalysis(Map.of("provider", getProviderName(), "errorCode", "VNPT_UNEXPECTED_ERROR"))
                    .build();
        }
    }
}
