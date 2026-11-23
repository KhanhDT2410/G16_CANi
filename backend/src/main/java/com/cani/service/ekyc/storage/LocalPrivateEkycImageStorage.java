package com.cani.service.ekyc.storage;

import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Triển khai Private Storage trên ổ đĩa riêng tư ngoài webroot cho hạ tầng hiện tại.
 * Dễ dàng thay thế bằng S3/GCS/MinIO private bucket qua interface {@link EkycImageStorage}.
 *
 * Bảo mật:
 * - Đường dẫn lưu ngoài webroot (không mở public static handler).
 * - Object key dùng UUID ngẫu nhiên, tuyệt đối không dùng tên file do client gửi.
 * - Chặn Path Traversal nghiêm ngặt.
 * - Fail-fast khi không ghi được file (tuyệt đối không trả URL giả).
 */
@Slf4j
@Component
public class LocalPrivateEkycImageStorage implements EkycImageStorage {

    public static final String PROVIDER_NAME = "LOCAL_PRIVATE_STORAGE";
    private static final Set<String> SAFE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_SCOPES = Set.of("worker", "eo");
    private static final Set<String> ALLOWED_SLOTS = Set.of("front", "back", "selfie");

    private static final Pattern GENERATED_KEY = Pattern.compile(
            "(worker|eo)/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}/[0-9a-f]{32}_(front|back|selfie)[.](jpg|jpeg|png|webp)");

    private final Path baseDirectory;

    @Autowired
    public LocalPrivateEkycImageStorage(
            @Value("${cani.storage.ekyc-dir:uploads/ekyc-private}") String storageDir
    ) {
        this.baseDirectory = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(
            String ownerScope,
            UUID ownerId,
            String slot,
            byte[] bytes,
            String extension,
            String mimeType
    ) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Nội dung ảnh eKYC không được trống.");
        }
        String scope = ownerScope != null ? ownerScope.trim().toLowerCase(Locale.ROOT) : "";
        String normalizedSlot = slot != null ? slot.trim().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_SCOPES.contains(scope) || !ALLOWED_SLOTS.contains(normalizedSlot) || ownerId == null) {
            throw new BadRequestException("Tham số phân vùng lưu trữ ảnh eKYC không hợp lệ.");
        }

        String ext = (extension != null && SAFE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT)))
                ? extension.toLowerCase(Locale.ROOT)
                : "jpg";

        String objectKey = scope + "/" + ownerId + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + normalizedSlot + "." + ext;

        try {
            Path targetPath = resolveSafePath(objectKey);
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            try (var output = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                output.write(bytes);
            } catch (IOException writeFailure) {
                if (!(writeFailure instanceof java.nio.file.FileAlreadyExistsException)) {
                    deleteIfPresent(objectKey);
                }
                throw writeFailure;
            }

            return StoredObject.builder()
                    .objectKey(objectKey)
                    .sha256Hash(computeSha256(bytes))
                    .sizeBytes(bytes.length)
                    .contentType(mimeType != null ? mimeType : "image/jpeg")
                    .storageProvider(PROVIDER_NAME)
                    .build();
        } catch (IOException e) {
            log.error("Lỗi nghiêm trọng khi lưu ảnh eKYC vào private storage [scope={}, slot={}]: {}", scope, normalizedSlot, e.getClass().getSimpleName());
            throw new AppException("Không thể lưu trữ ảnh xác minh danh tính vào kho bảo mật. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Resource loadAsResource(String objectKey) {
        if (!isManagedObjectKey(objectKey)) {
            throw new ResourceNotFoundException("Không tìm thấy tệp ảnh eKYC trong kho lưu trữ bảo mật.");
        }
        Path filePath = resolveSafePath(objectKey);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException("Ảnh xác minh eKYC không tồn tại hoặc đã bị xóa theo chính sách lưu giữ.");
        } catch (MalformedURLException e) {
            throw new BadRequestException("Đường dẫn ảnh eKYC không hợp lệ.");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        if (!isManagedObjectKey(objectKey)) {
            return false;
        }
        try {
            Path filePath = resolveSafePath(objectKey);
            return Files.exists(filePath) && Files.isRegularFile(filePath);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isReadable(String objectKey) {
        if (!isManagedObjectKey(objectKey)) {
            return false;
        }
        try {
            Path filePath = resolveSafePath(objectKey);
            return Files.exists(filePath)
                    && Files.isRegularFile(filePath)
                    && Files.isReadable(filePath)
                    && Files.size(filePath) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deleteIfPresent(String objectKey) {
        if (!isManagedObjectKey(objectKey)) {
            return true;
        }
        try {
            Path filePath = resolveSafePath(objectKey);
            Files.deleteIfExists(filePath);
            return true;
        } catch (Exception e) {
            log.warn("Private eKYC image removal failed [{}]", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean isManagedObjectKey(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return false;
        }
        String trimmed = storedValue.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/")) {
            return false;
        }
        if (trimmed.contains("..") || trimmed.contains("\\") || trimmed.contains("\0")) {
            return false;
        }
        String[] parts = trimmed.split("/");
        return parts.length == 3 && ALLOWED_SCOPES.contains(parts[0]);
    }

    void visitAgedObjects(Instant before, Consumer<String> visitor) throws IOException {
        if (!Files.isDirectory(baseDirectory)) return;
        try (var candidates = Files.find(baseDirectory, 3, (path, attrs) ->
                attrs.isRegularFile() && attrs.lastModifiedTime().toInstant().isBefore(before))) {
            candidates.map(path -> baseDirectory.relativize(path).toString().replace((char) 92, (char) 47))
                    .filter(key -> GENERATED_KEY.matcher(key).matches())
                    .forEach(visitor);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    Path resolveSafePath(String objectKey) {
        if (!isManagedObjectKey(objectKey)) {
            throw new BadRequestException("Object key ảnh eKYC không hợp lệ.");
        }
        Path resolved = baseDirectory.resolve(objectKey).normalize();
        if (!resolved.startsWith(baseDirectory)) {
            throw new BadRequestException("Đường dẫn ảnh eKYC không hợp lệ (Path Traversal bị chặn).");
        }
        for (Path path = resolved; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) {
                throw new BadRequestException("Đường dẫn ảnh eKYC không hợp lệ.");
            }
        }
        return resolved;
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
