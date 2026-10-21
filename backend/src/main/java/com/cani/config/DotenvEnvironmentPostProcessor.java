package com.cani.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

/**
 * Spring Boot EnvironmentPostProcessor to ensure .env variables are registered
 * in the Spring Environment before any configuration files and beans are initialized.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = DotenvLoader.load();
        if (!properties.isEmpty()) {
            MapPropertySource source = new MapPropertySource("dotenvProperties", properties);
            if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    source
                );
            } else {
                environment.getPropertySources().addFirst(source);
            }
        }
    }

    @Override
    public int getOrder() {
        // Run before ConfigDataEnvironmentPostProcessor (HIGHEST_PRECEDENCE + 10)
        return Ordered.HIGHEST_PRECEDENCE + 9;
    }
}
