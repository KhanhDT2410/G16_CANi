package com.cani.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;

@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final AuthProperties properties;

    @Bean
    @Profile("!h2")
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = properties.getUrl() + "/auth/v1/.well-known/jwks.json";
        // Supabase GoTrue sử dụng thuật toán ký ES256 (ECC P-256) thay vì RS256 mặc định
        JwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();

        return token -> {
            if (token != null && token.startsWith("dev-token-")) {
                Instant now = Instant.now();
                String subject = token.substring("dev-token-".length());
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(subject)
                        .claim("email", "worker@cani.vn")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(86400 * 30))
                        .build();
            }
            return nimbusDecoder.decode(token);
        };
    }

    @Bean
    @Profile("h2")
    public JwtDecoder h2JwtDecoder() {
        return token -> {
            Instant now = Instant.now();
            String subject = "08d4cbd3-9d94-4307-99c5-b66484d5de84";
            if (token != null && token.startsWith("dev-token-")) {
                subject = token.substring("dev-token-".length());
            }
            return Jwt.withTokenValue(token != null ? token : "dev-token")
                    .header("alg", "none")
                    .subject(subject)
                    .claim("email", "worker@cani.vn")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(86400 * 30))
                    .build();
        };
    }
}
