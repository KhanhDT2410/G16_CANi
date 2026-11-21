package com.cani.service.skill;

import com.cani.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileSecurityValidator — Magic Bytes, Size, Extension Spoofing & SHA-256 Tests")
class FileSecurityValidatorTest {

    private final FileSecurityValidator validator = new FileSecurityValidator();

    private static byte[] validJpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
    }

    private static byte[] validPngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    }

    private static byte[] validWebpBytes() {
        return "RIFF\u0024\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] validPdfBytes() {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("Chấp nhận JPEG, PNG, WEBP, PDF chuẩn magic bytes và tính đúng SHA-256")
    void validate_ValidFiles_SucceedAndComputeSha256() {
        MockMultipartFile jpg = new MockMultipartFile("file", "cert.jpg", "image/jpeg", validJpegBytes());
        FileSecurityValidator.ValidatedDocument jpgDoc = validator.validate(jpg);
        assertEquals("image/jpeg", jpgDoc.getDetectedMimeType());
        assertEquals("jpg", jpgDoc.getSafeExtension());
        assertEquals(64, jpgDoc.getSha256Hash().length());

        MockMultipartFile png = new MockMultipartFile("file", "cert.png", "image/png", validPngBytes());
        assertEquals("image/png", validator.validate(png).getDetectedMimeType());

        MockMultipartFile webp = new MockMultipartFile("file", "cert.webp", "image/webp", validWebpBytes());
        assertEquals("image/webp", validator.validate(webp).getDetectedMimeType());

        MockMultipartFile pdf = new MockMultipartFile("file", "cert.pdf", "application/pdf", validPdfBytes());
        assertEquals("application/pdf", validator.validate(pdf).getDetectedMimeType());
    }

    @Test
    @DisplayName("Chặn file vượt quá giới hạn 5MB")
    void validate_OversizedFile_ThrowsBadRequest() {
        byte[] huge = new byte[(int) FileSecurityValidator.MAX_FILE_SIZE_BYTES + 10];
        huge[0] = (byte) 0xFF;
        huge[1] = (byte) 0xD8;
        huge[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("file", "huge.jpg", "image/jpeg", huge);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(file));
        assertTrue(ex.getMessage().contains("5MB"));
    }

    @Test
    @DisplayName("Chặn file sai MIME type / plain text đổi đuôi giả thành .jpg (extension spoofing)")
    void validate_FakeExtensionSpoofing_ThrowsBadRequest() {
        MockMultipartFile spoofedTxt = new MockMultipartFile(
                "file", "fake_cert.jpg", "image/jpeg", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)
        );
        assertThrows(BadRequestException.class, () -> validator.validate(spoofedTxt));

        // PDF bytes nhưng đặt tên .jpg -> bị chặn mismatch extension
        MockMultipartFile pdfNamedJpg = new MockMultipartFile(
                "file", "cert.jpg", "image/jpeg", validPdfBytes()
        );
        assertThrows(BadRequestException.class, () -> validator.validate(pdfNamedJpg));

        // text/plain không còn được chấp nhận
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "cert.txt", "text/plain", "Certificate text".getBytes(StandardCharsets.UTF_8)
        );
        assertThrows(BadRequestException.class, () -> validator.validate(txtFile));
    }

    @Test
    @DisplayName("Chặn file PDF bị hỏng (thiếu marker %%EOF hoặc cắt cụt)")
    void validate_CorruptedPdf_ThrowsBadRequest() {
        MockMultipartFile brokenPdf = new MockMultipartFile(
                "file", "broken.pdf", "application/pdf", "%PDF-1.4\ntruncated-content-without-eof".getBytes(StandardCharsets.ISO_8859_1)
        );
        assertThrows(BadRequestException.class, () -> validator.validate(brokenPdf));
    }

    @Test
    @DisplayName("Sanitize tên tệp chứa Path Traversal (../../etc/passwd.jpg)")
    void validate_PathTraversalOriginalFilename_IsSanitized() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.jpg", "image/jpeg", validJpegBytes()
        );
        FileSecurityValidator.ValidatedDocument doc = validator.validate(file);
        assertEquals("passwd.jpg", doc.getSanitizedOriginalFilename());
        assertFalse(doc.getSanitizedOriginalFilename().contains(".."));
        assertFalse(doc.getSanitizedOriginalFilename().contains("/"));
    }
}
