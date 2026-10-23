package com.cani.client.ekyc;

import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Điều phối viên eKYC trung tâm (Orchestrator / Strategy Pattern).
 * Tự động chọn Provider dựa trên cấu hình:
 * - cani.ekyc.provider = vnpt (Mặc định)
 * - cani.ekyc.provider = mock
 *
 * Nếu cấu hình là 'vnpt' nhưng chưa bật (enabled=false) hoặc chưa điền Access Token,
 * hệ thống sẽ tự động chuyển sang Mock Provider để bảo vệ hệ thống không bị lỗi 401/timeout khi dev.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class PrimaryEkycProvider implements EkycProvider {

    private final VnptEkycClient vnptClient;
    private final MockEkycProvider mockClient;
    private final VnptEkycProperties vnptProperties;

    @Value("${cani.ekyc.provider:vnpt}")
    private String configuredProvider;

    @Override
    public String getProviderName() {
        return getDelegate().getProviderName();
    }

    @Override
    public EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        EkycProvider delegate = getDelegate();
        log.info("eKYC Router: Đang điều phối yêu cầu xác thực sang Provider: [{}]", delegate.getProviderName());
        return delegate.verifyIdentity(frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);
    }

    /**
     * Lựa chọn Delegate Provider phù hợp với cấu hình môi trường
     */
    public EkycProvider getDelegate() {
        if ("mock".equalsIgnoreCase(configuredProvider)) {
            return mockClient;
        }

        if ("vnpt".equalsIgnoreCase(configuredProvider)) {
            if (vnptProperties.isEnabled() && vnptProperties.getAccessToken() != null && !vnptProperties.getAccessToken().isBlank()) {
                return vnptClient;
            } else {
                log.info("Cấu hình eKYC là VNPT nhưng vnpt.enabled=false hoặc chưa có Access Token. Tự động chuyển tiếp sang Mock Provider để bảo toàn luồng chạy.");
                return mockClient;
            }
        }

        log.info("Mặc định sử dụng Mock Provider cho eKYC kiểm thử.");
        return mockClient;
    }
}

