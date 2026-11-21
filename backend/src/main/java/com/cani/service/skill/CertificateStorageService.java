package com.cani.service.skill;

import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu trữ riêng tư tài liệu chứng chỉ ngoài webroot với tên tệp UUID ngẫu nhiên,
 * chống Path Traversal và fail-fast khi không thể ghi tệp.
 */
@Service
@Slf4j
public class CertificateStorageService {

    private static final Set<String> SAFE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");

    private final Path baseDirectory;

    public CertificateStorageService(
            @Value("${cani.storage.certificate-dir:uploads/certificates}") String storageDir
    ) {
        this.baseDirectory = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    /**
     * Lưu tệp bằng tên UUID ngẫu nhiên (tuyệt đối không dùng filename của người dùng làm đường dẫn).
     * Trả về đường dẫn lưu trữ chuẩn (chưa gắn chữ ký tạm thời).
     */
    public String storeDocument(byte[] fileBytes, String safeExtension) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BadRequestException("Nội dung tệp chứng chỉ trống.");
        }
        String ext = (safeExtension != null && SAFE_EXTENSIONS.contains(safeExtension.toLowerCase()))
                ? safeExtension.toLowerCase()
                : "jpg";

        String generatedFilename = "cert_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            if (!Files.exists(baseDirectory)) {
                Files.createDirectories(baseDirectory);
            }
            Path targetPath = resolveSafePath(generatedFilename);
            Files.write(targetPath, fileBytes);
            return DocumentAccessTokenService.DOCUMENT_BASE_PATH + generatedFilename;
        } catch (IOException e) {
            log.error("Lỗi nghiêm trọng khi lưu tệp chứng chỉ {}: {}", generatedFilename, e.getMessage());
            throw new AppException("Không thể lưu trữ tài liệu chứng chỉ lên hệ thống. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Resource loadAsResource(String filename) {
        Path filePath = resolveSafePath(filename);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException("Không tìm thấy tài liệu chứng chỉ yêu cầu.");
        } catch (MalformedURLException e) {
            throw new BadRequestException("Đường dẫn tài liệu không hợp lệ.");
        }
    }

    public byte[] readBytes(String filename) {
        Path filePath = resolveSafePath(filename);
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new ResourceNotFoundException("Không tìm thấy tệp chứng chỉ để thực hiện thử lại.");
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new AppException("Không thể đọc lại tệp chứng chỉ đã lưu.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteQuietly(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Path filePath = resolveSafePath(filename);
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("Không thể xóa tệp chứng chỉ {}: {}", filename, e.getMessage());
        }
    }

    Path resolveSafePath(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("..")
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains("\0")) {
            throw new BadRequestException("Tên tệp tài liệu không hợp lệ.");
        }
        Path resolved = baseDirectory.resolve(filename).normalize();
        if (!resolved.startsWith(baseDirectory)) {
            throw new BadRequestException("Đường dẫn tài liệu không hợp lệ (Path Traversal bị chặn).");
        }
        return resolved;
    }
}
