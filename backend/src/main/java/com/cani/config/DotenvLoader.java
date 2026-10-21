package com.cani.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility to automatically discover, parse, and load .env variables
 * into System properties and Spring environment before context startup.
 */
public final class DotenvLoader {

    private static final Logger log = LoggerFactory.getLogger(DotenvLoader.class);
    private static boolean loaded = false;
    private static final Map<String, Object> CACHED_PROPERTIES = new HashMap<>();

    private DotenvLoader() {}

    public static synchronized Map<String, Object> load() {
        if (loaded) {
            return Collections.unmodifiableMap(CACHED_PROPERTIES);
        }

        List<Path> candidates = List.of(
            Paths.get(".env"),
            Paths.get("backend/.env"),
            Paths.get("../.env"),
            Paths.get("../backend/.env")
        );

        Path envFile = null;
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                envFile = candidate.toAbsolutePath().normalize();
                break;
            }
        }

        if (envFile == null) {
            log.info("[DotenvLoader] No .env file found in search path. Relying on OS environment variables.");
            loaded = true;
            return Collections.emptyMap();
        }

        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();

                // Strip surrounding single or double quotes
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    if (value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                }

                CACHED_PROPERTIES.put(key, value);

                // Set in System properties only if not already present in OS environment or System properties
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                    count++;
                }
            }
            log.info("[DotenvLoader] Successfully loaded {} environment variables from {}", count, envFile);
        } catch (IOException e) {
            log.warn("[DotenvLoader] Failed to read .env file at {}: {}", envFile, e.getMessage());
        }

        loaded = true;
        return Collections.unmodifiableMap(CACHED_PROPERTIES);
    }
}
