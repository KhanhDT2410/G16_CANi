package com.cani.exception;

import com.cani.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleGenericException() không rò rỉ thông báo kỹ thuật/tiếng Anh ra response 500")
    void handleGenericException_HidesRawEnglishMessage() {
        ResponseEntity<ApiResponse<Object>> res = handler.handleGenericException(
                new IllegalStateException("SHA-256 unavailable: java.security.NoSuchAlgorithmException")
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).isEqualTo("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
        assertThat(res.getBody().getMessage()).doesNotContain("SHA-256");
    }

    @Test
    @DisplayName("handleDataIntegrityViolation() không rò rỉ câu SQL tiếng Anh thô")
    void handleDataIntegrityViolation_HidesRawSqlConstraintDetails() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_custom_key\"")
        );

        ResponseEntity<ApiResponse<Object>> res = handler.handleDataIntegrityViolation(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).doesNotContain("duplicate key");
        assertThat(res.getBody().getMessage()).doesNotContain("ERROR:");
    }

    @Test
    @DisplayName("ResourceNotFoundException dịch các tên entity/field tiếng Anh phổ biến sang tiếng Việt")
    void resourceNotFoundException_TranslatesCommonNames() {
        ResourceNotFoundException ex = new ResourceNotFoundException("WorkerGroup", "inviteCode", "ABC123");

        assertThat(ex.getMessage()).isEqualTo("Nhóm nhân sự không tồn tại với mã mời: 'ABC123'");
    }
}
