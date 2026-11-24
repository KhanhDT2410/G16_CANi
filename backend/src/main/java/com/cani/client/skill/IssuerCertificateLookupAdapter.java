package com.cani.client.skill;

/**
 * Adapter xác thực chứng chỉ cho từng đơn vị cấp cụ thể (Issuer API, QR verify portal, hoặc chữ ký số).
 */
public interface IssuerCertificateLookupAdapter extends CertificateLookupSource {

    /**
     * Trả về true nếu adapter này nhận diện được chứng chỉ thuộc đơn vị cấp mà nó hỗ trợ.
     */
    boolean supports(CertificateLookupRequest request);
}
