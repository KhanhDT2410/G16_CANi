package com.cani.client;

import com.cani.client.dto.SupabaseSession;
import com.cani.client.dto.SupabaseUser;
import com.cani.config.AuthProperties;
import com.cani.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class SupabaseAuthClient {

    private final RestClient restClient;
    private final AuthProperties properties;

    public SupabaseAuthClient(AuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Tạo user qua GoTrue Admin API (sử dụng service_role key).
     */
    public SupabaseUser createUser(String email, String password, String fullName, String phone) {
        return createUser(email, password, fullName, phone, null, null);
    }

    /**
     * Tạo user qua GoTrue Admin API kèm metadata role và companyName (hỗ trợ DB Trigger ACID).
     */
    public SupabaseUser createUser(String email, String password, String fullName, String phone, String role, String companyName) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("email", email);
            body.put("password", password);
            body.put("email_confirm", true); // Tự động confirm cho MVP nếu chưa bắt buộc email OTP

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("full_name", fullName);
            if (phone != null && !phone.isBlank()) {
                metadata.put("phone", phone);
            }
            if (role != null && !role.isBlank()) {
                metadata.put("role", role);
            }
            if (companyName != null && !companyName.isBlank()) {
                metadata.put("company_name", companyName);
            }
            body.put("user_metadata", metadata);

            return restClient.post()
                    .uri("/auth/v1/admin/users")
                    .header("apikey", properties.getServiceRoleKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceRoleKey())
                    .body(body)
                    .retrieve()
                    .body(SupabaseUser.class);
        } catch (HttpClientErrorException e) {
            log.error("Lỗi khi gọi GoTrue Admin API createUser: {}", e.getResponseBodyAsString());
            throw new BadRequestException("Không thể tạo tài khoản trên hệ thống xác thực: " + e.getResponseBodyAsString());
        }
    }

    /**
     * Đăng nhập mật khẩu bằng GoTrue password grant (sử dụng anon key).
     */
    public SupabaseSession signInWithPassword(String email, String password) {
        try {
            Map<String, String> body = Map.of(
                    "email", email,
                    "password", password
            );

            return restClient.post()
                    .uri("/auth/v1/token?grant_type=password")
                    .header("apikey", properties.getAnonKey())
                    .body(body)
                    .retrieve()
                    .body(SupabaseSession.class);
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Đăng nhập thất bại cho email {}: {}", email, e.getResponseBodyAsString());
            throw new BadRequestException("Email hoặc mật khẩu không chính xác");
        } catch (HttpClientErrorException e) {
            log.error("Lỗi xác thực GoTrue signInWithPassword: {}", e.getResponseBodyAsString());
            throw new BadRequestException("Lỗi xác thực: " + e.getMessage());
        }
    }

    /**
     * Xác thực OTP email qua GoTrue verify endpoint.
     */
    public void verifyOtp(String email, String token) {
        try {
            Map<String, String> body = Map.of(
                    "type", "email",
                    "email", email,
                    "token", token
            );

            restClient.post()
                    .uri("/auth/v1/verify")
                    .header("apikey", properties.getAnonKey())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            log.error("Xác thực OTP thất bại cho email {}: {}", email, e.getResponseBodyAsString());
            throw new BadRequestException("Mã OTP không hợp lệ hoặc đã hết hạn");
        }
    }

    /**
     * Gửi email khôi phục mật khẩu qua GoTrue recover endpoint.
     */
    public void resetPassword(String email) {
        try {
            Map<String, String> body = Map.of("email", email);

            restClient.post()
                    .uri("/auth/v1/recover")
                    .header("apikey", properties.getAnonKey())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            log.error("Gửi email reset password thất bại cho {}: {}", email, e.getResponseBodyAsString());
            throw new BadRequestException("Không thể gửi email đặt lại mật khẩu");
        }
    }

    /**
     * Làm mới token qua GoTrue refresh_token grant.
     */
    public SupabaseSession refreshToken(String refreshToken) {
        try {
            Map<String, String> body = Map.of("refresh_token", refreshToken);

            return restClient.post()
                    .uri("/auth/v1/token?grant_type=refresh_token")
                    .header("apikey", properties.getAnonKey())
                    .body(body)
                    .retrieve()
                    .body(SupabaseSession.class);
        } catch (HttpClientErrorException e) {
            log.error("Làm mới token thất bại: {}", e.getResponseBodyAsString());
            throw new BadRequestException("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        }
    }

    /**
     * Cập nhật mật khẩu user qua GoTrue Admin API (sử dụng service_role key).
     */
    public void updateUserPassword(UUID userId, String newPassword) {
        try {
            Map<String, Object> body = Map.of(
                    "password", newPassword
            );

            restClient.put()
                    .uri("/auth/v1/admin/users/{userId}", userId)
                    .header("apikey", properties.getServiceRoleKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceRoleKey())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Đã cập nhật mật khẩu mới thành công qua GoTrue Admin cho user: {}", userId);
        } catch (HttpClientErrorException e) {
            log.error("Lỗi khi cập nhật mật khẩu qua GoTrue Admin API: {}", e.getResponseBodyAsString());
            throw new BadRequestException("Không thể cập nhật mật khẩu mới: " + e.getResponseBodyAsString());
        }
    }
}
