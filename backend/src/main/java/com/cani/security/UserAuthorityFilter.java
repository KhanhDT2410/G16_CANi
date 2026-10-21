package com.cani.security;

import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthorityFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                UUID userId = UUID.fromString(jwt.getToken().getSubject());
                userRepository.findById(userId).ifPresentOrElse(user -> {
                    if (user.getStatus() == UserStatus.ACTIVE) {
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        user,
                                        jwt.getCredentials(),
                                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                                )
                        );
                    } else {
                        log.warn("User {} có trạng thái {}, từ chối xác thực", userId, user.getStatus());
                        SecurityContextHolder.clearContext();
                    }
                }, () -> {
                    log.warn("Không tìm thấy user trong database với subject UUID: {}", userId);
                    SecurityContextHolder.clearContext();
                });
            } catch (IllegalArgumentException e) {
                log.error("JWT subject không phải định dạng UUID hợp lệ: {}", jwt.getToken().getSubject());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
