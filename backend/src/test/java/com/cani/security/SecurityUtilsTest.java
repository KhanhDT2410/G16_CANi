package com.cani.security;

import com.cani.entity.User;
import com.cani.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityUtilsTest {

    @Test
    @DisplayName("Normal: getCurrentUserId trích xuất thành công khi Principal là thực thể User")
    void getCurrentUserId_whenPrincipalIsUser_returnsUserId() {
        // Arrange
        UUID expectedUserId = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setUserId(expectedUserId);
        Authentication auth = new UsernamePasswordAuthenticationToken(mockUser, "token", List.of());

        // Act
        UUID result = SecurityUtils.getCurrentUserId(auth);

        // Assert
        assertThat(result).isEqualTo(expectedUserId);
    }

    @Test
    @DisplayName("Normal: getCurrentUserId trích xuất thành công khi name là UUID string hợp lệ")
    void getCurrentUserId_whenPrincipalNotUser_fallbackToNameAsUuid() {
        // Arrange
        UUID expectedUserId = UUID.randomUUID();
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("arbitrary-string");
        when(auth.getName()).thenReturn(expectedUserId.toString());

        // Act
        UUID result = SecurityUtils.getCurrentUserId(auth);

        // Assert
        assertThat(result).isEqualTo(expectedUserId);
    }

    @Test
    @DisplayName("Abnormal: getCurrentUserId ném BadRequestException khi Authentication là null")
    void getCurrentUserId_whenAuthenticationIsNull_throwsBadRequestException() {
        // Arrange, Act & Assert
        assertThatThrownBy(() -> SecurityUtils.getCurrentUserId(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Phiên đăng nhập không hợp lệ");
    }

    @Test
    @DisplayName("Abnormal: getCurrentUserId ném BadRequestException khi name không phải định dạng UUID")
    void getCurrentUserId_whenNameIsNotUuid_throwsBadRequestException() {
        // Arrange
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("plain-user");
        when(auth.getName()).thenReturn("invalid-uuid-string");

        // Act & Assert
        assertThatThrownBy(() -> SecurityUtils.getCurrentUserId(auth))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Phiên đăng nhập không hợp lệ");
    }

    @Test
    @DisplayName("Normal: isAdmin trả về true khi có ROLE_ADMIN hoặc ADMIN")
    void isAdmin_whenAdminRole_returnsTrue() {
        // Arrange
        Authentication authRoleAdmin = new UsernamePasswordAuthenticationToken(
                "admin", "cred", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication authAdmin = new UsernamePasswordAuthenticationToken(
                "admin", "cred", List.of(new SimpleGrantedAuthority("ADMIN")));

        // Act & Assert
        assertThat(SecurityUtils.isAdmin(authRoleAdmin)).isTrue();
        assertThat(SecurityUtils.isAdmin(authAdmin)).isTrue();
    }

    @Test
    @DisplayName("Normal: isAdmin trả về false khi là WORKER hoặc không có quyền")
    void isAdmin_whenNotAdminRole_returnsFalse() {
        // Arrange
        Authentication authWorker = new UsernamePasswordAuthenticationToken(
                "worker", "cred", List.of(new SimpleGrantedAuthority("ROLE_WORKER")));

        // Act & Assert
        assertThat(SecurityUtils.isAdmin(authWorker)).isFalse();
        assertThat(SecurityUtils.isAdmin(null)).isFalse();
    }
}

