package com.bank.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates production secrets at startup.
 *
 * <p>Only active when {@code spring.profiles.active=prod}. Checks that required secrets
 * are present and not equal to their development placeholders, failing fast with a clear
 * error message so operators know exactly what to fix.
 */
@Component
@Profile("prod")
public class ProductionSecretValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretValidator.class);

    /** The dev-only placeholder from application.yml — must never appear in production. */
    private static final String DEV_JWT_SECRET =
            "Y2hhbmdlLW1lLXRoaXMtaXMtYS1kZXYtb25seS1zZWNyZXQta2V5LTEyMzQ1Ng==";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = new ArrayList<>();

        // JWT secret — must be supplied and must not be the dev placeholder
        if (jwtSecret == null || jwtSecret.isBlank()) {
            errors.add("JWT_SECRET must be provided. Generate: openssl rand -base64 64");
        } else if (DEV_JWT_SECRET.equals(jwtSecret)) {
            errors.add("JWT_SECRET is set to the development placeholder. " +
                       "Generate a secure key: openssl rand -base64 64");
        }

        // Database — must not default to localhost dev values
        if (dbUrl == null || dbUrl.isBlank()) {
            errors.add("DB_URL must be provided.");
        }
        if (dbUsername == null || dbUsername.isBlank()) {
            errors.add("DB_USERNAME must be provided.");
        }
        if ("banking".equals(dbPassword) || (dbPassword != null && dbPassword.isBlank())) {
            errors.add("DB_PASSWORD must be set to a strong production password, not the dev default.");
        }

        // App base URL — must point to a real domain, not localhost
        if (appBaseUrl == null || appBaseUrl.contains("localhost") || appBaseUrl.contains("127.0.0.1")) {
            errors.add("APP_BASE_URL must be set to the production domain (e.g. https://bank.example.com), " +
                       "not localhost.");
        }

        if (!errors.isEmpty()) {
            String message = "Production startup validation FAILED — insecure or missing configuration:\n  - "
                    + String.join("\n  - ", errors);
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Production configuration validation passed.");
    }
}
