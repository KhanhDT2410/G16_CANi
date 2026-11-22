package com.cani.exception;

import lombok.Getter;

/**
 * Exception ném ra khi tài khoản đang bị khóa (LOCKED / SUSPENDED) cố gắng đăng nhập.
 * Kế thừa BadRequestException để tương thích ngược với luồng xác thực và test suite.
 * Chứa thông tin bổ trợ giúp Frontend chuyển tiếp trực tiếp sang trang giải trình (/account-locked).
 */
@Getter
public class AccountLockedException extends BadRequestException {

    private final String email;
    private final String role;
    private final String statusName;
    private final String lockReason;
    private final String appealToken;

    public AccountLockedException(String message, String email, String role, String statusName, String lockReason, String appealToken) {
        super(message);
        this.email = email;
        this.role = role;
        this.statusName = statusName;
        this.lockReason = lockReason;
        this.appealToken = appealToken;
    }
}
