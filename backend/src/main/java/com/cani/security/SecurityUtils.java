package com.cani.security;

import com.cani.entity.User;
import com.cani.exception.BadRequestException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility class hỗ trợ trích xuất thông tin an toàn từ Authentication context.
 * Tuân thủ nguyên lý Single Source of Truth và DRY, ngăn chặn hoàn toàn các lỗ hổng BOLA / IDOR.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Prevent instantiation
    }

    /**
     * Trích xuất userId (UUID) từ Authentication Principal an toàn.
     *
     * @param authentication thông tin xác thực từ Spring Security
     * @return UUID của người dùng hiện tại
     * @throws BadRequestException nếu không tìm thấy phiên hợp lệ hoặc danh tính không xác định
     */
    public static UUID getCurrentUserId(Authentication authentication) {
        Authentication auth = authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User u) {
            return u.getUserId();
        } else if (auth != null && auth.getName() != null) {
            try {
                return UUID.fromString(auth.getName());
            } catch (Exception ignored) {
                // Name không phải UUID
            }
        }
        throw new BadRequestException("Phiên đăng nhập không hợp lệ hoặc người dùng không tồn tại");
    }

    /**
     * Kiểm tra người dùng hiện tại có vai trò ADMIN hay không.
     *
     * @param authentication thông tin xác thực từ Spring Security
     * @return true nếu có quyền ADMIN, ngược lại false
     */
    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
    }
}
