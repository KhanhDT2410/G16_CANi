package com.cani.client.ekyc;

import com.cani.dto.ekyc.EkycIdentityResultDto;

/**
 * Interface trừu tượng hóa dịch vụ xác thực danh tính điện tử eKYC (Adapter Pattern).
 * Cho phép linh hoạt chuyển đổi giữa VNPT eKYC hoặc Mock Provider.
 */
public interface EkycProvider {

    /**
     * Xác thực danh tính qua 3 ảnh: Mặt trước CCCD, Mặt sau CCCD và Ảnh Selfie chân dung
     *
     * @param frontBytes  Mã nhị phân ảnh mặt trước CCCD
     * @param frontMime   MIME type ảnh trước (image/jpeg, image/png...)
     * @param backBytes   Mã nhị phân ảnh mặt sau CCCD
     * @param backMime    MIME type ảnh sau
     * @param selfieBytes Mã nhị phân ảnh chụp selfie khuôn mặt
     * @param selfieMime  MIME type ảnh selfie
     * @return Kết quả chuẩn hóa OCR, so khớp khuôn mặt và quyết định duyệt
     */
    EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime);

    /**
     * Tên nhà cung cấp (VNPT_EKYC, MOCK_EKYC...)
     */
    String getProviderName();
}

