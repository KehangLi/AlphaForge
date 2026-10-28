package com.likehang.alphaforge.rest.controller;

import com.jayway.jsonpath.JsonPath;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.security.limits.ClientIpResolver;
import com.likehang.alphaforge.security.limits.RateLimitService;
import com.likehang.alphaforge.support.MutableTimeMeter;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.likehang.alphaforge.security.limits.RateLimitService.Rule.*;
import static com.likehang.alphaforge.support.SecurityTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "alphaforge.rate-limits.enabled=true",
        "alphaforge.rate-limits.login-per-ip.capacity=3",
        "alphaforge.rate-limits.login-per-email.capacity=2",
        "alphaforge.rate-limits.register-per-ip.capacity=2",
        "alphaforge.rate-limits.guest-creation-per-ip.capacity=2",
        "alphaforge.client-ip.proxy-secret=test-proxy-secret-with-at-least-32-characters"
})
@Import(AbuseProtectionIntegrationTest.TimeConfiguration.class)
class AbuseProtectionIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String SECRET = "test-proxy-secret-with-at-least-32-characters";
    @Value("${local.server.port}") int port;
    @Autowired RateLimitService limits;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableTimeMeter time;

    @BeforeEach
    void expireOldBuckets() { time.advance(Duration.ofDays(2)); }

    @Test
    void csrfLimitRunsBeforeAnonymousSessionCreationAndReturnsCorsAndRetryHeaders() throws Exception {
        for (int i = 0; i < 60; i++) limits.check(CSRF_IP, "192.0.2.10");
        try (HttpClient client = client()) {
            var blocked = client.send(builder("/api/auth/csrf", "192.0.2.10").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertRateLimited(blocked);
            assertThat(blocked.headers().firstValue("Set-Cookie")).isEmpty();
            assertThat(blocked.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:3000");
            assertThat(sessionCount()).isZero();
            assertThat(client.send(builder("/api/auth/csrf", "192.0.2.11").GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void ipLoginLimitCannotBeBypassedByChangingEmailsOrForwardedHeaders() throws Exception {
        try (HttpClient client = client()) {
            for (int i = 0; i < 3; i++) {
                assertThat(post(client, "/api/auth/login", credentials("unknown" + i + "@example.com"), "192.0.2.20")
                        .statusCode()).isEqualTo(401);
            }
            var blocked = post(client, "/api/auth/login", credentials("another@example.com"), "192.0.2.20");
            assertRateLimited(blocked);
            assertThat(users.count()).isZero();
            assertThat(blocked.headers().firstValue("Set-Cookie")).isEmpty();
        }
    }

    @Test
    void emailLoginLimitIsNormalizedAndIndependentOfIpAndExpires() throws Exception {
        try (HttpClient client = client()) {
            assertThat(post(client, "/api/auth/login", credentials("Same@Example.com"), "192.0.2.30").statusCode()).isEqualTo(401);
            assertThat(post(client, "/api/auth/login", credentials(" same@example.com "), "192.0.2.31").statusCode()).isEqualTo(401);
            assertRateLimited(post(client, "/api/auth/login", credentials("same@example.com"), "192.0.2.32"));
            assertThat(post(client, "/api/auth/login", credentials("different@example.com"), "192.0.2.32").statusCode()).isEqualTo(401);
            time.advance(Duration.ofMinutes(10));
            assertThat(post(client, "/api/auth/login", credentials("same@example.com"), "192.0.2.32").statusCode()).isEqualTo(401);
        }
    }

    @Test
    void registeringTooFrequentlyDoesNotCreateAnotherUserOrReplaceTheSession() throws Exception {
        try (HttpClient client = client()) {
            assertThat(post(client, "/api/auth/register", registration("first@example.com"), "192.0.2.40").statusCode()).isEqualTo(201);
            assertThat(post(client, "/api/auth/register", registration("second@example.com"), "192.0.2.40").statusCode()).isEqualTo(201);
            String before = currentUser(client);
            assertRateLimited(post(client, "/api/auth/register", registration("third@example.com"), "192.0.2.40"));
            assertThat(users.count()).isEqualTo(2);
            assertThat(currentUser(client)).isEqualTo(before);
        }
    }

    @Test
    void resumingGuestDoesNotConsumeNewGuestCapacityAndNewSessionsDoNotResetTheIpLimit() throws Exception {
        try (HttpClient first = client(); HttpClient second = client(); HttpClient third = client()) {
            var created = post(first, "/api/auth/guest", null, "192.0.2.50");
            assertThat(created.statusCode()).isEqualTo(200);
            String id = JsonPath.read(created.body(), "$.userId");
            for (int i = 0; i < 4; i++) {
                var resumed = post(first, "/api/auth/guest", null, "192.0.2.50");
                assertThat(resumed.statusCode()).isEqualTo(200);
                assertThat(JsonPath.<String>read(resumed.body(), "$.userId")).isEqualTo(id);
            }
            assertThat(post(second, "/api/auth/guest", null, "192.0.2.50").statusCode()).isEqualTo(200);
            var csrf = third.send(builder("/api/auth/csrf", "192.0.2.50").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(csrf.statusCode()).isEqualTo(200);
            long sessions = sessionCount();
            assertRateLimited(post(third, "/api/auth/guest", null, "192.0.2.50"));
            assertThat(users.count()).isEqualTo(2);
            assertThat(sessionCount()).isEqualTo(sessions);
        }
    }

    @Test
    void untrustedHeaderChangesCannotAvoidTheSocketIpBudgetAndHealthStaysAvailable() throws Exception {
        for (int i = 0; i < 120; i++) limits.check(REQUEST_IP, "127.0.0.1");
        try (HttpClient client = client()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf"))
                    .header("X-Forwarded-For", "192.0.2.99")
                    .header(ClientIpResolver.CLIENT_IP_HEADER, "192.0.2.98")
                    .header(ClientIpResolver.PROXY_TOKEN_HEADER, "invalid")
                    .GET().build();
            assertRateLimited(client.send(request, HttpResponse.BodyHandlers.ofString()));
            assertThat(sessionCount()).isZero();
            assertThat(client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void guestQuotaAndUploadFailuresHaveJsonResponsesAndDoNotLeavePartialImports() throws Exception {
        try (HttpClient client = client()) {
            assertThat(post(client, "/api/auth/guest", null, "192.0.2.60").statusCode()).isEqualTo(200);
            var first = post(client, "/api/brokerage-accounts", accountBody("First"), "192.0.2.60");
            assertThat(first.statusCode()).isEqualTo(201);
            String accountId = JsonPath.read(first.body(), "$.id");
            assertThat(post(client, "/api/brokerage-accounts", accountBody("Second"), "192.0.2.60").statusCode()).isEqualTo(201);
            var quota = post(client, "/api/brokerage-accounts", accountBody("Third"), "192.0.2.60");
            assertThat(quota.statusCode()).isEqualTo(409);
            assertThat(JsonPath.<String>read(quota.body(), "$.message")).contains("Guest account limit");

            assertThat(upload(client, accountId, "x".repeat(1024 * 1024 + 1)).statusCode()).isEqualTo(413);
            String row = "Interest on cash,2026-10-01 00:00:00+00:00,1,EUR\n";
            String header = "Action,Time (UTC),Total,Currency (Total)\n";
            var tooManyRows = upload(client, accountId, header + row.repeat(1001));
            assertThat(tooManyRows.statusCode()).isEqualTo(413);
            assertThat(JsonPath.<String>read(tooManyRows.body(), "$.message")).contains("row limit");
            assertThat(jdbc.queryForObject("select count(*) from import_batch", Long.class)).isZero();

            for (int i = 0; i < 3; i++) {
                assertThat(upload(client, accountId, header + row.replace(",1,EUR", "," + (i + 2) + ",EUR"))
                        .statusCode()).isEqualTo(201);
            }
            assertRateLimited(upload(client, accountId, header + row));
            assertThat(jdbc.queryForObject("select count(*) from import_batch", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from account_activity", Long.class)).isEqualTo(3);
        }
    }

    @Test
    void servletRejectsFilesAboveTheGlobalLimitWithJson() throws Exception {
        try (HttpClient client = client()) {
            assertThat(post(client, "/api/auth/guest", null, "192.0.2.60").statusCode()).isEqualTo(200);
            var created = post(client, "/api/brokerage-accounts", accountBody("First"), "192.0.2.60");
            String id = JsonPath.read(created.body(), "$.id");
            var response = upload(client, id, "x".repeat(5 * 1024 * 1024 + 1));
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(413);
        }
    }

    @Test
    void malformedWritesOutsideApiCannotCreateSessionsAfterTheIpBudgetIsExhausted() throws Exception {
        for (int i = 0; i < 120; i++) limits.check(REQUEST_IP, "192.0.2.70");
        try (HttpClient client = client()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/unknown"))
                    .header(ClientIpResolver.PROXY_TOKEN_HEADER, SECRET)
                    .header(ClientIpResolver.CLIENT_IP_HEADER, "192.0.2.70")
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertRateLimited(client.send(request, HttpResponse.BodyHandlers.ofString()));
            assertThat(sessionCount()).isZero();
        }
    }

    private HttpRequest.Builder builder(String path, String ip) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Origin", "http://localhost:3000")
                .header(ClientIpResolver.PROXY_TOKEN_HEADER, SECRET)
                .header(ClientIpResolver.CLIENT_IP_HEADER, ip)
                .header("X-Forwarded-For", "198.51.100." + (System.nanoTime() % 200 + 1));
    }

    private HttpResponse<String> post(HttpClient client, String path, String body, String ip) throws Exception {
        var builder = withCsrf(client, builder(path, ip), port);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.POST(body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    private String accountBody(String name) {
        return "{\"brokerName\":\"Trading 212\",\"accountName\":\"" + name + "\",\"baseCurrency\":\"EUR\"}";
    }

    private HttpResponse<String> upload(HttpClient client, String account, String csv) throws Exception {
        String body = "--TestBoundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n" + csv + "\r\n--TestBoundary--\r\n";
        var request = withCsrf(client, builder("/api/brokerage-accounts/" + account + "/account-activities/imports", "192.0.2.60"), port)
                .header("Content-Type", "multipart/form-data; boundary=TestBoundary")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"test-passphrase-12345\"}";
    }

    private String registration(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"test-passphrase-12345\",\"displayName\":\"Test\"}";
    }

    private String currentUser(HttpClient client) throws Exception {
        var response = client.send(builder("/api/auth/me", "192.0.2.40").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.userId");
    }

    private long sessionCount() { return jdbc.queryForObject("select count(*) from spring_session", Long.class); }

    private void assertRateLimited(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After")).hasValueSatisfying(value ->
                assertThat(Long.parseLong(value)).isPositive());
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(value ->
                assertThat(value).contains("no-store"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary MutableTimeMeter testTimeMeter() { return new MutableTimeMeter(); }
    }
}
