package com.cani.client.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bộ định tuyến nguồn tra cứu chứng chỉ chính thức.
 * Kiểm tra lần lượt các {@link IssuerCertificateLookupAdapter} đã tích hợp;
 * nếu chưa có đơn vị cấp nào khớp sẽ fallback trung thực về {@link UnsupportedCertificateLookupSource}.
 */
@Component
@Primary
@RequiredArgsConstructor
public class RoutingCertificateLookupSource implements CertificateLookupSource {

    private final List<IssuerCertificateLookupAdapter> adapters;
    private final UnsupportedCertificateLookupSource unsupportedFallback;

    @Override
    public CertificateLookupResult verifyCertificate(CertificateLookupRequest request) {
        if (adapters != null && request != null) {
            for (IssuerCertificateLookupAdapter adapter : adapters) {
                if (adapter != null && adapter.supports(request)) {
                    return adapter.verifyCertificate(request);
                }
            }
        }
        return unsupportedFallback.verifyCertificate(request);
    }
}
