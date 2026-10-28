package com.likehang.alphaforge.security.limits;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("alphaforge.client-ip")
public record ClientIpProperties(@DefaultValue("") String proxySecret) {

    @AssertTrue(message = "proxy secret must be empty or at least 32 characters")
    public boolean isSecretValid() {
        return proxySecret != null && (proxySecret.isEmpty()
                || (proxySecret.length() >= 32 && proxySecret.length() <= 256));
    }

    @Override
    public String toString() {
        return "ClientIpProperties[proxySecret=REDACTED]";
    }
}
