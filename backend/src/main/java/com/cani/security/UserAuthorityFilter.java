package com.cani.security;

import com.cani.entity.User;
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
import java.util.Optional;
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
            String subStr = jwt.getToken().getSubject();
            String email = jwt.getToken().getClaimAsString("email");

            UUID userId = null;
            try {
                if (subStr != null && !subStr.isBlank()) {
                    userId = UUID.fromString(subStr);
                }
            } catch (IllegalArgumentException e) {
                log.debug("JWT subject không phải UUID chuẩn: {}", subStr);
            }

            Optional<User> userOpt = Optional.empty();
            if (userId != null) {
                userOpt = userRepository.findById(userId);
            }
            if (userOpt.isEmpty() && email != null && !email.isBlank()) {
                userOpt = userRepository.findByEmail(email);
            }

            userOpt.ifPresentOrElse(user -> {
                if (user.getStatus() == UserStatus.ACTIVE) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    jwt.getCredentials(),
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                            )
                    );
                } else {
                    log.warn("User {} ({}) có trạng thái {}, từ chối xác thực", user.getUserId(), user.getEmail(), user.getStatus());
                    SecurityContextHolder.clearContext();
                }
            }, () -> {
                log.warn("Không tìm thấy user trong database với subject: {} hoặc email: {}", subStr, email);
                SecurityContextHolder.clearContext();
            });
        }

        filterChain.doFilter(request, response);
    }
}
