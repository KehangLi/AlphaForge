package com.likehang.alphaforge.security.limits;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.stream.Stream;

@Validated
@ConfigurationProperties("alphaforge.rate-limits")
public record RateLimitProperties(
        boolean enabled,
        @Min(1) int maxKeys,
        @NotNull @Valid Limit requestsPerIp,
        @NotNull @Valid Limit loginPerIp,
        @NotNull @Valid Limit loginPerEmail,
        @NotNull @Valid Limit registerPerIp,
        @NotNull @Valid Limit guestCreationPerIp,
        @NotNull @Valid Limit csrfPerIp,
        @NotNull @Valid Limit uploadPerUser
) {
    public record Limit(@Min(1) long capacity, @NotNull Duration period) {
        @AssertTrue(message = "rate limit periods must be positive and no longer than one day")
        public boolean isPeriodValid() {
            return period != null && !period.isNegative() && !period.isZero()
                    && period.compareTo(Duration.ofDays(1)) <= 0;
        }
    }

    public Duration idleTimeout() {
        return Stream.of(requestsPerIp, loginPerIp, loginPerEmail, registerPerIp,
                        guestCreationPerIp, csrfPerIp, uploadPerUser)
                .map(Limit::period).max(Duration::compareTo).orElseThrow().plusMinutes(1);
    }
}

/*
login-per-ip: {capacity: 30, period: 1m}

properties.loginPerIp().capacity();
properties.loginPerIp().period();
*/
