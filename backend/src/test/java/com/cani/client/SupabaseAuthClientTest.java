package com.cani.client;

import com.cani.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupabaseAuthClientTest {

    private final SupabaseAuthClient client = createClient();

    private static SupabaseAuthClient createClient() {
        AuthProperties props = new AuthProperties();
        props.setUrl("https://example.supabase.co");
        props.setAnonKey("anon");
        props.setServiceRoleKey("service");
        return new SupabaseAuthClient(props);
    }

    @Test
    @DisplayName("Phân loại lỗi 422 email_exists từ GoTrue Admin API thành thông báo rõ ràng")
    void resolveCreateUserErrorMessage_EmailExists_ReturnsClearMessage() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                HttpHeaders.EMPTY,
                "{\"code\":\"email_exists\",\"msg\":\"A user with this email address has already been registered\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        assertEquals(
                "Email này đã tồn tại trên hệ thống xác thực. Vui lòng đăng nhập hoặc sử dụng email khác.",
                client.resolveCreateUserErrorMessage(ex)
        );
    }

    @Test
    @DisplayName("Phân loại lỗi 422 weak_password từ GoTrue Admin API")
    void resolveCreateUserErrorMessage_WeakPassword_ReturnsClearMessage() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                HttpHeaders.EMPTY,
                "{\"code\":\"weak_password\",\"msg\":\"Password should be at least 6 characters.\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        assertEquals(
                "Mật khẩu chưa đáp ứng yêu cầu bảo mật của hệ thống xác thực.",
                client.resolveCreateUserErrorMessage(ex)
        );
    }

    @Test
    @DisplayName("Phân loại lỗi 401/403 sai service role key từ GoTrue Admin API")
    void resolveCreateUserErrorMessage_Unauthorized_ReturnsConfigMessage() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                "{\"msg\":\"Invalid API key\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        assertEquals(
                "Cấu hình xác thực hệ thống không hợp lệ. Vui lòng liên hệ quản trị viên.",
                client.resolveCreateUserErrorMessage(ex)
        );
    }
}
