package com.likehang.alphaforge.security.limits;

import com.likehang.alphaforge.exception.RateLimitException;
import com.likehang.alphaforge.support.MutableTimeMeter;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.likehang.alphaforge.security.limits.RateLimitService.Rule.LOGIN_EMAIL;
import static com.likehang.alphaforge.security.limits.RateLimitService.Rule.LOGIN_IP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {

    @Test
    void rejectsUntilTheRefillAndReportsRemainingWait() {
        MutableTimeMeter time = new MutableTimeMeter();
        RateLimitService service = service(100, 2, time);
        service.check(LOGIN_EMAIL, "user@example.com");
        service.check(LOGIN_EMAIL, "user@example.com");
        time.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> service.check(LOGIN_EMAIL, "user@example.com"))
                .isInstanceOfSatisfying(RateLimitException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(30));
        time.advance(Duration.ofSeconds(30));
        assertThatCode(() -> service.check(LOGIN_EMAIL, "user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void identitiesAndRulesHaveIndependentBuckets() {
        RateLimitService service = service(100, 1, new MutableTimeMeter());
        service.check(LOGIN_EMAIL, "first@example.com");
        assertThatThrownBy(() -> service.check(LOGIN_EMAIL, "first@example.com")).isInstanceOf(RateLimitException.class);
        assertThatCode(() -> service.check(LOGIN_EMAIL, "second@example.com")).doesNotThrowAnyException();
        assertThatCode(() -> service.check(LOGIN_IP, "first@example.com")).doesNotThrowAnyException();
    }

    @Test
    void aFullCacheDoesNotEvictBlockedIdentitiesAndExpiredEntriesFreeCapacity() {
        MutableTimeMeter time = new MutableTimeMeter();
        RateLimitService service = service(1, 1, time);
        service.check(LOGIN_EMAIL, "first@example.com");
        assertThatThrownBy(() -> service.check(LOGIN_EMAIL, "second@example.com")).isInstanceOf(RateLimitException.class);
        assertThatThrownBy(() -> service.check(LOGIN_EMAIL, "first@example.com")).isInstanceOf(RateLimitException.class);
        time.advance(Duration.ofMinutes(3));
        assertThatCode(() -> service.check(LOGIN_EMAIL, "second@example.com")).doesNotThrowAnyException();
    }

    @Test
    void concurrentRequestsCannotConsumeMoreThanTheCapacity() throws Exception {
        RateLimitService service = service(100, 3, new MutableTimeMeter());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 30; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.check(LOGIN_EMAIL, "user@example.com");
                        return true;
                    } catch (RateLimitException exception) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(3);
        }
    }

    private RateLimitService service(int keys, int capacity, TimeMeter time) {
        var rule = new RateLimitProperties.Limit(capacity, Duration.ofMinutes(1));
        return new RateLimitService(new RateLimitProperties(true, keys, rule, rule, rule, rule, rule, rule, rule), time);
    }

}
