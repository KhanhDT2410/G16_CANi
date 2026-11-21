package com.cani.client.skill;

import lombok.Getter;

/**
 * Ngoại lệ kỹ thuật khi giao tiếp hoặc phân tích phản hồi từ nhà cung cấp Vision AI.
 * Được dùng để kích hoạt cơ chế Fail-Closed (chuyển VERIFICATION_FAILED, 0 SP, cho phép retry có kiểm soát)
 * thay vì tự động phê duyệt hoặc bịa dữ liệu giả.
 */
@Getter
public class AiProviderException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;
    private final String userSafeReason;

    public AiProviderException(String errorCode, boolean retryable, String userSafeReason) {
        super(userSafeReason);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.userSafeReason = userSafeReason;
    }

    public AiProviderException(String errorCode, boolean retryable, String userSafeReason, Throwable cause) {
        super(userSafeReason, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.userSafeReason = userSafeReason;
    }
}
