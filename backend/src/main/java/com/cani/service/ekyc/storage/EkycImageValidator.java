package com.cani.service.ekyc.storage;

import com.cani.exception.BadRequestException;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Xác thực bảo mật tệp ảnh CCCD và ảnh Selfie tải lên:
 * - Kiểm tra không rỗng và giới hạn dung lượng (mặc định 5MB/ảnh).
 * - Kiểm tra Magic Bytes thực tế (chỉ chấp nhận JPEG, PNG, WEBP — chặn PDF/HTML/SVG/EXE giả mạo).
 * - Kiểm tra nhất quán giữa MIME type khai báo, phần mở rộng tên tệp và Magic Bytes thực tế.
 */
@Component
public class EkycImageValidator {

    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L; // 5MB

    private static final Map<String, Set<String>> MIME_TO_EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp")
    );

    private static final Map<String, String> MIME_TO_CANONICAL_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final long maxFileSizeBytes;

    @Autowired
    public EkycImageValidator(
            @Value("${cani.storage.ekyc-max-file-size-bytes:5242880}") long maxFileSizeBytes
    ) {
        this.maxFileSizeBytes = maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    @lombok.Value
    @Builder
    public static class ValidatedEkycImage {
        byte[] bytes;
        String detectedMimeType;
        String safeExtension;
        String sha256Hash;
        long sizeBytes;
    }

    public ValidatedEkycImage validate(MultipartFile file, String fieldLabel) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(fieldLabel + " không được để trống.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            long maxMb = Math.max(1L, maxFileSizeBytes / (1024L * 1024L));
            throw new BadRequestException(fieldLabel + " vượt quá dung lượng tối đa " + maxMb + "MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BadRequestException("Không thể đọc dữ liệu " + fieldLabel + ".");
        }

        if (bytes.length == 0) {
            throw new BadRequestException(fieldLabel + " rỗng.");
        }
        if (bytes.length > maxFileSizeBytes) {
            long maxMb = Math.max(1L, maxFileSizeBytes / (1024L * 1024L));
            throw new BadRequestException(fieldLabel + " vượt quá dung lượng tối đa " + maxMb + "MB.");
        }

        String detectedMime = detectImageMimeByMagicBytes(bytes);
        if (detectedMime == null) {
            throw new BadRequestException(
                    fieldLabel + " có định dạng thực tế không hợp lệ hoặc bị hỏng. Chỉ chấp nhận ảnh JPEG, PNG hoặc WEBP chuẩn."
            );
        }

        // Kiểm tra Content-Type khai báo (nếu khác octet-stream thì phải thuộc nhóm ảnh hỗ trợ)
        String declaredMime = file.getContentType();
        if (declaredMime != null && !declaredMime.isBlank()) {
            String normalizedDeclared = declaredMime.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (!"application/octet-stream".equals(normalizedDeclared)
                    && !detectedMime.equals(normalizedDeclared)) {
                throw new BadRequestException(fieldLabel + " có MIME type không khớp định dạng ảnh thực tế.");
            }
        }

        validateExtensionConsistency(file.getOriginalFilename(), detectedMime, fieldLabel);

        return ValidatedEkycImage.builder()
                .bytes(bytes)
                .detectedMimeType(detectedMime)
                .safeExtension(MIME_TO_CANONICAL_EXT.get(detectedMime))
                .sha256Hash(computeSha256(bytes))
                .sizeBytes(bytes.length)
                .build();
    }

    public String detectImageMimeByMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "image/png";
        }
        // WEBP: RIFF....WEBP
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private void validateExtensionConsistency(String rawFilename, String detectedMime, String fieldLabel) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return;
        }
        String base = rawFilename.replace('\\', '/');
        int slashIdx = base.lastIndexOf('/');
        if (slashIdx >= 0) {
            base = base.substring(slashIdx + 1);
        }
        int dotIdx = base.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == base.length() - 1) {
            return;
        }
        String ext = base.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        Set<String> allowed = MIME_TO_EXTENSIONS.get(detectedMime);
        if (allowed == null || !allowed.contains(ext)) {
            throw new BadRequestException(
                    fieldLabel + " có phần mở rộng (." + ext + ") không khớp với định dạng thực tế (" + detectedMime + ")."
            );
        }
    }

    private static String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
