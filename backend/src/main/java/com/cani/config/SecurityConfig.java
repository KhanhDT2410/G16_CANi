package com.cani.config;

import com.cani.security.RestAccessDeniedHandler;
import com.cani.security.RestAuthenticationEntryPoint;
import com.cani.security.UserAuthorityFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserAuthorityFilter userAuthorityFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/health",
                    "/actuator/health",
                    "/api/auth/**",
                    "/api/v1/workers/skills/taxonomy",
                    "/api/v1/workers/skills/tiers/config",
                    "/api/v1/workers/ekyc/**",
                    "/api/v1/eo/ekyc/**"
                ).permitAll()
                // Thẩm định chứng chỉ kỹ năng (UC-47): Customer Care cùng quyền kiểm duyệt với Admin
                .requestMatchers("/api/v1/workers/skills/admin/**").hasAnyRole("ADMIN", "CUSTOMER_CARE")
                // Module tranh chấp (UC-99/100/102/109): Customer Care cùng quyền với Admin
                // (BR-59: CC có thẩm quyền quyết định Level-1; Admin quyết định cuối).
                .requestMatchers("/api/v1/admin/disputes/**").hasAnyRole("ADMIN", "CUSTOMER_CARE")
                .requestMatchers("/api/v1/disputes/**").authenticated()
                // Module phiếu hỗ trợ (UC-105/106, SCR-25/26): Customer Care & Admin quản lý queue & detail
                .requestMatchers("/api/v1/admin/support/**").hasAnyRole("ADMIN", "CUSTOMER_CARE")
                .requestMatchers("/api/v1/support/**").authenticated()
                // Module FAQ (UC-110): Customer Care cùng quyền với Admin
                // Đặt TRƯỚC wildcard /api/v1/admin/** — nếu không CC sẽ bị 403.
                .requestMatchers("/api/v1/admin/faqs/**").hasAnyRole("ADMIN", "CUSTOMER_CARE")
                .requestMatchers("/api/v1/faqs/**").permitAll()
                // Thẩm định hồ sơ eKYC Worker và Doanh nghiệp: Customer Care cùng quyền kiểm duyệt với Admin
                .requestMatchers("/api/v1/admin/ekyc/**", "/api/v1/admin/eo-ekyc/**").hasAnyRole("ADMIN", "CUSTOMER_CARE")
                // Module Kháng cáo điểm uy tín Worker (UC-94, SRS FE-10): Customer Care cùng quyền xử lý với Admin
                // ĐẶT TRƯỚC wildcard /api/v1/admin/** để Customer Care không bị 403 Forbidden.
                .requestMatchers("/api/v1/admin/appeals/**").hasAnyRole("ADMIN", "CUSTOMER_CARE")
                .requestMatchers("/api/v1/workers/appeals/**").hasRole("WORKER")
                // Module Doanh thu (UC-108, SRS FE-12): Admin / Finance quản lý bảng doanh thu
                // Ghi chú: Hệ thống hiện tại chưa cấu hình role FINANCE riêng biệt, phân quyền sử dụng ADMIN theo quy ước
                .requestMatchers("/api/v1/admin/revenue/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/workers/skills/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .addFilterAfter(userAuthorityFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
