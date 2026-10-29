package com.cani.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${cani.firebase.enabled:true}")
    private boolean firebaseEnabled;

    @Value("${cani.firebase.service-account-path:firebase-service-account.json}")
    private String serviceAccountPath;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!firebaseEnabled) {
            log.info("[FirebaseConfig] Firebase is disabled via configuration (cani.firebase.enabled=false)");
            return null;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try {
            InputStream serviceAccountStream = null;

            // 1. Thử tìm file theo đường dẫn trực tiếp trong thư mục hệ thống
            File file = new File(serviceAccountPath);
            if (file.exists() && file.isFile()) {
                log.info("[FirebaseConfig] Found Firebase service account file at: {}", file.getAbsolutePath());
                serviceAccountStream = new FileInputStream(file);
            } else {
                // 2. Thử tìm trong ClassPath
                ClassPathResource resource = new ClassPathResource(serviceAccountPath);
                if (resource.exists()) {
                    log.info("[FirebaseConfig] Found Firebase service account file in classpath: {}", serviceAccountPath);
                    serviceAccountStream = resource.getInputStream();
                }
            }

            if (serviceAccountStream == null) {
                log.warn("[FirebaseConfig] Firebase service account file not found at '{}'. Push notification will be disabled gracefully.", serviceAccountPath);
                return null;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("[FirebaseConfig] FirebaseApp successfully initialized for project: {}", options.getProjectId());
            return app;
        } catch (Exception e) {
            log.error("[FirebaseConfig] Failed to initialize FirebaseApp: {}. Push notifications will be inactive.", e.getMessage());
            return null;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(@Nullable FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            log.info("[FirebaseConfig] FirebaseMessaging Bean is null because FirebaseApp is not initialized.");
            return null;
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
