package com.cani.exception;

import com.cani.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Dữ liệu đầu vào không hợp lệ")
                        .data(errors)
                        .build());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Dung lượng tệp tải lên vượt quá giới hạn cho phép (tối đa 5MB)."));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON or invalid parameter format: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Dữ liệu gửi lên không đúng định dạng (ví dụ: ID phải là chuỗi UUID chuẩn)."));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch on request parameter: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Tham số yêu cầu không đúng kiểu dữ liệu (UUID)."));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Bạn không có quyền truy cập tài nguyên này"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Database constraint violation: {}", ex.getMessage());
        String rawMsg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        Throwable rootCause = ex.getRootCause();
        if (rootCause != null && rootCause.getMessage() != null) {
            rawMsg += " " + rootCause.getMessage().toLowerCase();
        }

        String friendlyMessage;
        if (rawMsg.contains("users_phone_key") || rawMsg.contains("phone")) {
            friendlyMessage = "Số điện thoại này đã được sử dụng bởi một tài khoản khác trong hệ thống. Vui lòng sử dụng số điện thoại khác.";
        } else if (rawMsg.contains("users_email_key") || rawMsg.contains("email")) {
            friendlyMessage = "Địa chỉ email này đã được sử dụng bởi một tài khoản khác trong hệ thống.";
        } else if (rawMsg.contains("users_username_key") || rawMsg.contains("username")) {
            friendlyMessage = "Tên tài khoản này đã tồn tại trong hệ thống. Vui lòng chọn tên khác.";
        } else {
            String detail = rootCause != null && rootCause.getMessage() != null
                    ? rootCause.getMessage()
                    : (ex.getMessage() != null ? ex.getMessage() : "");
            if (detail.length() > 180) {
                detail = detail.substring(0, 180) + "...";
            }
            friendlyMessage = detail.isBlank()
                    ? "Dữ liệu gửi lên vi phạm ràng buộc toàn vẹn của hệ thống (dữ liệu có thể đã tồn tại). Vui lòng kiểm tra lại."
                    : "Dữ liệu gửi lên không hợp lệ hoặc vi phạm ràng buộc hệ thống: " + detail;
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(friendlyMessage));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception occurred: ", ex);
        String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? "Lỗi hệ thống: " + ex.getMessage()
                : "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.";
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(msg));
    }
}
