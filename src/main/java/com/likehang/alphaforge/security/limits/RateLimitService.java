package com.likehang.alphaforge.security.limits;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.likehang.alphaforge.exception.RateLimitException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class RateLimitService {

    public enum Rule { REQUEST_IP, LOGIN_IP, LOGIN_EMAIL, REGISTER_IP, GUEST_CREATION_IP, CSRF_IP, UPLOAD_USER }

    private final RateLimitProperties properties;
    private final TimeMeter timeMeter;
    private final Cache<String, Bucket> buckets;

    public RateLimitService(RateLimitProperties properties, TimeMeter timeMeter) {
        this.properties = properties;
        this.timeMeter = timeMeter;
        this.buckets = Caffeine.newBuilder().maximumSize(properties.maxKeys())
                .expireAfterAccess(properties.idleTimeout()).ticker(timeMeter::currentTimeNanos).build();
    }

    public void check(Rule rule, String identity) {
        if (!properties.enabled()) return;
        String key = rule.name() + ":" + digest(identity);
        Bucket bucket = bucket(key, limit(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitException((long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
        }
    }

    private Bucket bucket(String key, RateLimitProperties.Limit limit) {
        // Refuse new identities at capacity instead of evicting a blocked user's bucket.
        synchronized (buckets) {
            Bucket existing = buckets.getIfPresent(key);
            if (existing != null) return existing;
            buckets.cleanUp();
            if (buckets.estimatedSize() >= properties.maxKeys()) throw new RateLimitException(60);
            Bucket created = Bucket.builder().withCustomTimePrecision(timeMeter)
                    .addLimit(builder -> builder.capacity(limit.capacity())
                            .refillIntervally(limit.capacity(), limit.period()))
                    .build();
            buckets.put(key, created);
            return created;
        }
    }

    private RateLimitProperties.Limit limit(Rule rule) {
        return switch (rule) {
            case REQUEST_IP -> properties.requestsPerIp();
            case LOGIN_IP -> properties.loginPerIp();
            case LOGIN_EMAIL -> properties.loginPerEmail();
            case REGISTER_IP -> properties.registerPerIp();
            case GUEST_CREATION_IP -> properties.guestCreationPerIp();
            case CSRF_IP -> properties.csrfPerIp();
            case UPLOAD_USER -> properties.uploadPerUser();
        };
    }

    private String digest(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
