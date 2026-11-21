package com.cani.service.skill;

import com.cani.exception.BadRequestException;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Kiểm tra bảo mật tệp chứng chỉ tải lên:
 * - Giới hạn dung lượng tối đa 5MB.
 * - Kiểm tra Magic Bytes thực tế (không tin tưởng Content-Type hay phần mở rộng từ client).
 * - Chặn giả mạo extension (extension spoofing) và chặn tệp văn bản thô (.txt) trên production.
 * - Tính SHA-256 hash phục vụ chống nộp trùng (idempotency) và kiểm toán.
 */
@Component
public class FileSecurityValidator {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L; // 5MB

    private static final Map<String, Set<String>> MIME_TO_EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp"),
            "application/pdf", Set.of("pdf")
    );

    private static final Map<String, String> MIME_TO_CANONICAL_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf"
    );

    @Value
    @Builder
    public static class ValidatedDocument {
        byte[] bytes;
        String detectedMimeType;
        String safeExtension;
        String sha256Hash;
        String sanitizedOriginalFilename;
    }

    public ValidatedDocument validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng tải lên tệp ảnh chứng chỉ hoặc bằng cấp hợp lệ (JPG, PNG, WEBP, PDF).");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Dung lượng tệp tải lên vượt quá giới hạn 5MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BadRequestException("Không thể đọc nội dung tệp tải lên.");
        }

        if (bytes.length == 0) {
            throw new BadRequestException("Tệp tải lên rỗng.");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Dung lượng tệp tải lên vượt quá giới hạn 5MB.");
        }

        String detectedMime = detectMimeByMagicBytes(bytes);
        if (detectedMime == null) {
            throw new BadRequestException(
                    "Định dạng tệp thực tế không hợp lệ hoặc bị hỏng. Hệ thống chỉ chấp nhận ảnh JPG, PNG, WEBP hoặc PDF chuẩn."
            );
        }

        // Nếu PDF: kiểm tra cơ bản tính toàn vẹn phần kết thúc hoặc cấu trúc tối thiểu
        if ("application/pdf".equals(detectedMime) && !isMinimalValidPdf(bytes)) {
            throw new BadRequestException("Tệp PDF bị hỏng hoặc không đúng cấu trúc tiêu chuẩn.");
        }

        String originalName = sanitizeFilename(file.getOriginalFilename());
        validateExtensionConsistency(originalName, detectedMime);

        // Nếu client gửi Content-Type nhưng hoàn toàn không nằm trong nhóm được phép -> từ chối sớm
        String declaredContentType = file.getContentType();
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String normDeclared = declaredContentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (!"application/octet-stream".equals(normDeclared)
                    && !MIME_TO_EXTENSIONS.containsKey(normDeclared)) {
                throw new BadRequestException("Loại nội dung (MIME type) không được hỗ trợ: " + normDeclared);
            }
        }

        String safeExt = MIME_TO_CANONICAL_EXT.get(detectedMime);
        String sha256 = computeSha256(bytes);

        return ValidatedDocument.builder()
                .bytes(bytes)
                .detectedMimeType(detectedMime)
                .safeExtension(safeExt)
                .sha256Hash(sha256)
                .sanitizedOriginalFilename(originalName)
                .build();
    }

    /**
     * Phát hiện MIME type thực tế từ Magic Bytes ở đầu tệp.
     */
    public String detectMimeByMagicBytes(byte[] bytes) {
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

        // PDF: %PDF-
        if (bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            return "application/pdf";
        }

        return null;
    }

    private boolean isMinimalValidPdf(byte[] bytes) {
        // Kiểm tra tối thiểu tệp PDF có độ dài hợp lý và có thẻ %%EOF ở phần cuối tệp
        if (bytes.length < 20) {
            return false;
        }
        int tailLen = Math.min(bytes.length, 1024);
        String tail = new String(bytes, bytes.length - tailLen, tailLen, StandardCharsets.ISO_8859_1);
        return tail.contains("%%EOF");
    }

    private void validateExtensionConsistency(String originalName, String detectedMime) {
        if (originalName == null || !originalName.contains(".")) {
            return;
        }
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx == originalName.length() - 1) {
            return;
        }
        String ext = originalName.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        Set<String> allowedExts = MIME_TO_EXTENSIONS.get(detectedMime);
        if (allowedExts == null || !allowedExts.contains(ext)) {
            throw new BadRequestException(
                    "Phần mở rộng tệp (." + ext + ") không khớp với định dạng thực tế của tệp (" + detectedMime + ")."
            );
        }
    }

    public String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "certificate";
        }
        // Loại bỏ đường dẫn nếu client gửi kèm path (chống path traversal trên tên gốc)
        String base = rawFilename.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        String cleaned = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return "certificate";
        }
        return cleaned.length() > 150 ? cleaned.substring(cleaned.length() - 150) : cleaned;
    }

    public String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
