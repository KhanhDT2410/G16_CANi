package com.cani.config;

import com.cani.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<UserRepository> userRepositoryProvider;

    @Bean
    @Profile("!h2")
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = properties.getUrl() + "/auth/v1/.well-known/jwks.json";
        // Supabase GoTrue sử dụng thuật toán ký ES256 (ECC P-256) thay vì RS256 mặc định
        JwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();

        // ponytail: nhánh dev-token- cũ (auth bypass mọi profile != h2) đã bị xóa.
        // Token dev-token-* chỉ còn được JwtDecoder của profile "h2" chấp nhận.
        return nimbusDecoder;
    }

    @Bean
    @Profile("h2")
    public JwtDecoder h2JwtDecoder() {
        return token -> {
            Instant now = Instant.now();
            String safeToken = token != null ? token : "";

            // 1. Trường hợp token thật (JWT chứa header.payload.signature từ Supabase Google OAuth)
            if (token != null && token.contains(".")) {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    try {
                        byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
                        JsonNode payload = objectMapper.readTree(decodedBytes);

                        if (!payload.hasNonNull("sub")) {
                            throw new BadJwtException("Token JWT thiếu trường 'sub'");
                        }
                        String subject = payload.get("sub").asText();

                        String email = null;
                        if (payload.hasNonNull("email")) {
                            email = payload.get("email").asText();
                        } else if (payload.path("user_metadata").hasNonNull("email")) {
                            email = payload.path("user_metadata").get("email").asText();
                        } else {
                            email = "user@cani.vn";
                        }

                        Jwt.Builder builder = Jwt.withTokenValue(safeToken)
                                .header("alg", "none")
                                .subject(subject)
                                .claim("email", email)
                                .issuedAt(now)
                                .expiresAt(now.plusSeconds(86400 * 30));

                        if (payload.hasNonNull("user_metadata")) {
                            Map<String, Object> metadata = objectMapper.convertValue(
                                    payload.get("user_metadata"),
                                    new TypeReference<Map<String, Object>>() {}
                            );
                            builder.claim("user_metadata", metadata);
                        }

                        log.info("[H2 Profile] Giải mã JWT OAuth: subject={}, email={}", subject, email);
                        return builder.build();
                    } catch (BadJwtException e) {
                        throw e;
                    } catch (Exception e) {
                        log.warn("[H2 Profile] Lỗi parse payload của token: {}", e.getMessage());
                        throw new BadJwtException("Token JWT không hợp lệ: " + e.getMessage(), e);
                    }
                }
            }

            // 2. Trường hợp dev-token-<userId> (đăng nhập cục bộ offline H2)
            if (token != null && token.startsWith("dev-token-")) {
                String subject = token.substring("dev-token-".length());
                try {
                    UUID uid = UUID.fromString(subject);
                    UserRepository repo = userRepositoryProvider.getIfAvailable();
                    if (repo != null) {
                        var userOpt = repo.findById(uid);
                        if (userOpt.isPresent()) {
                            String email = userOpt.get().getEmail();
                            return Jwt.withTokenValue(safeToken)
                                    .header("alg", "none")
                                    .subject(subject)
                                    .claim("email", email)
                                    .issuedAt(now)
                                    .expiresAt(now.plusSeconds(86400 * 30))
                                    .build();
                        }
                    }
                } catch (IllegalArgumentException e) {
                    throw new BadJwtException("Định dạng dev-token không hợp lệ");
                }
            }

            // Chặn tuyệt đối token rác, không fallback tự động về worker@cani.vn
            throw new BadJwtException("Mã truy cập không hợp lệ hoặc người dùng không tồn tại");
        };
    }
}
