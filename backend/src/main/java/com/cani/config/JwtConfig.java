package com.cani.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final AuthProperties properties;

    @Bean
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = properties.getUrl() + "/auth/v1/.well-known/jwks.json";
        // Supabase GoTrue sử dụng thuật toán ký ES256 (ECC P-256) thay vì RS256 mặc định
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
    }
}
