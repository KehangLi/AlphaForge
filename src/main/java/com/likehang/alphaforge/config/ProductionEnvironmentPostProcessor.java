package com.likehang.alphaforge.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Checks production settings before any database connection can be opened. */
public class ProductionEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new IllegalStateException("The prod profile cannot be combined with local or test.");
        }

        List<String> missing = new ArrayList<>();
        String url = requiredProperty(environment, "spring.datasource.url", "DB_URL", missing);
        requiredProperty(environment, "spring.datasource.username", "DB_USERNAME", missing);
        requiredProperty(environment, "spring.datasource.password", "DB_PASSWORD", missing);
        String proxySecret = requiredProperty(environment, "alphaforge.client-ip.proxy-secret", "TRUSTED_PROXY_SECRET", missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing production configuration: " + String.join(", ", missing)
                    + ". Supply nonblank values through runtime environment variables or secret references.");
        }

        validateDatabaseUrl(url);
        if (proxySecret.length() < 32 || proxySecret.length() > 256) {
            throw new IllegalStateException("TRUSTED_PROXY_SECRET must contain 32-256 characters.");
        }
    }

    private String requiredProperty(ConfigurableEnvironment environment, String property, String variable,
                                    List<String> missing) {
        String value = null;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException ignored) {
            // An unresolved placeholder is a missing value. Never include its contents or cause in the error.
        }
        if (value == null || value.isBlank()) {
            missing.add(variable);
        }
        return value;
    }

    private void validateDatabaseUrl(String url) {
        if (url.startsWith("jdbc:postgresql://")) {
            try {
                URI address = URI.create(url.substring("jdbc:".length()));
                if (address.getHost() != null && address.getRawUserInfo() == null
                        && address.getRawQuery() == null && address.getRawFragment() == null
                        && address.getPath() != null && address.getPath().length() > 1) {
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // URI parser exceptions can contain the full connection string.
            }
        }
        throw new IllegalStateException("DB_URL must use jdbc:postgresql://host[:port]/database without "
                + "credentials, query parameters or fragments. Supply credentials separately; "
                + "production TLS settings are configured in application-prod.yaml.");
    }

    @Override
    public int getOrder() {
        // Run after Spring Boot has loaded configuration files and resolved the active profiles.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
