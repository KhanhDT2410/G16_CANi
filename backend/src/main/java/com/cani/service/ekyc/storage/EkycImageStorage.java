package com.cani.service.ekyc.storage;

import lombok.Builder;
import lombok.Value;
import org.springframework.core.io.Resource;

import java.util.UUID;

/**
 * Abstraction cho Private Object Storage lưu trữ ảnh CCCD và ảnh chân dung Selfie.
 * Cho phép thay thế triển khai (Local private disk, S3, GCS, MinIO, Supabase Storage private bucket)
 * mà không ảnh hưởng tới nghiệp vụ eKYC.
 *
 * Quy tắc bảo mật:
 * - Chỉ trả về objectKey nội bộ (KHÔNG trả về public URL, KHÔNG tạo URL giả).
 * - Fail-fast nếu không thể lưu ảnh.
 */
public interface EkycImageStorage {

    @Value
    @Builder
    class StoredObject {
        String objectKey;
        String sha256Hash;
        long sizeBytes;
        String contentType;
        String storageProvider;
    }

    /**
     * Lưu ảnh eKYC vào vùng lưu trữ riêng tư.
     *
     * @param ownerScope "worker" hoặc "eo"
     * @param ownerId    workerId hoặc eoId
     * @param slot       "front", "back", hoặc "selfie"
     * @param bytes      nội dung nhị phân ảnh đã qua kiểm tra magic bytes
     * @param extension  phần mở rộng chuẩn hóa (jpg, png, webp)
     * @param mimeType   MIME type đã phát hiện từ magic bytes
     * @return metadata objectKey nội bộ (tuyệt đối không phải public URL)
     */
    StoredObject store(
            String ownerScope,
            UUID ownerId,
            String slot,
            byte[] bytes,
            String extension,
            String mimeType
    );

    Resource loadAsResource(String objectKey);

    boolean exists(String objectKey);

    boolean deleteIfPresent(String objectKey);

    default void deleteQuietly(String objectKey) {
        deleteIfPresent(objectKey);
    }

    /**
     * Kiểm tra xem chuỗi lưu trong DB có phải là objectKey nội bộ hợp lệ (không phải public/fake URL).
     */
    boolean isManagedObjectKey(String storedValue);

    String getProviderName();
}
