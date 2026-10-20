package com.likehang.alphaforge.rest.controller;

import com.jayway.jsonpath.JsonPath;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.likehang.alphaforge.support.SecurityTestSupport.withCsrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CurrentUserSessionIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ACCOUNT_REQUEST = """
            {"brokerName":"Trading 212","accountName":"Session account","baseCurrency":"EUR"}
            """;
    private static final String CSV = """
            Action,Time (UTC),Total,Currency (Total)
            Interest on cash,2026-10-01 01:07:00+00:00,0.03,EUR
            """;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<HttpClient> clients = new ArrayList<>();

    @AfterEach
    void closeClients() {
        clients.forEach(HttpClient::close);
    }

    @Test
    void requiresSessionOnAllBusinessEndpoints() throws Exception {
        HttpClient client = newClient();
        UUID accountId = UUID.randomUUID();
        String accountPath = "/api/brokerage-accounts/" + accountId;
        HttpResponse<String> response = request(client, "GET", "/api/brokerage-accounts");

        assertUnauthorized(response);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertUnauthorized(request(client, "POST", "/api/brokerage-accounts", ACCOUNT_REQUEST));
        assertUnauthorized(request(client, "DELETE", accountPath));
        assertUnauthorized(request(client, "GET", accountPath + "/account-activities"));
        assertUnauthorized(uploadCsv(client, accountId));
        assertUnauthorized(request(client, "GET", accountPath + "/import-batches"));
        assertUnauthorized(request(client, "DELETE", accountPath + "/import-batches/" + UUID.randomUUID()));
    }

    @Test
    void doesNotAcceptClientSuppliedUserIdAsAuthentication() throws Exception {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", "Test User"));
        HttpRequest request = requestBuilder("/api/brokerage-accounts?userId=" + user.getId())
                .header("X-User-Id", user.getId().toString())
                .GET()
                .build();

        assertUnauthorized(newClient().send(request, HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void rejectsUnknownSessionCookie() throws Exception {
        String sessionId = Base64.getEncoder().encodeToString(
                UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
        );
        HttpRequest request = requestBuilder("/api/brokerage-accounts")
                .header("Cookie", "ALPHAFORGE_SESSION=" + sessionId)
                .GET()
                .build();

        assertUnauthorized(newClient().send(request, HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void rejectsExpiredSession() throws Exception {
        HttpClient client = newClient();
        UUID userId = createGuest(client);
        Session session = userSession(userId);
        long lastAccessTime = System.currentTimeMillis() - 60_000;
        jdbcTemplate.update("""
                UPDATE spring_session
                SET last_access_time = ?, max_inactive_interval = 1, expiry_time = ?
                WHERE session_id = ?
                """, lastAccessTime, lastAccessTime + 1_000, session.getId());

        assertUnauthorized(request(client, "GET", "/api/brokerage-accounts"));
        assertThat(appUserRepository.existsById(userId)).isTrue();
    }

    @Test
    void rejectsSessionWhoseUserWasDeleted() throws Exception {
        HttpClient client = newClient();
        UUID userId = createGuest(client);
        appUserRepository.deleteById(userId);

        assertUnauthorized(request(client, "GET", "/api/brokerage-accounts"));
    }

    @Test
    void isolatesAccountsImportsAndActivitiesBetweenGuests() throws Exception {
        HttpClient firstClient = newClient();
        createGuest(firstClient);
        UUID firstAccountId = createAccount(firstClient);
        HttpResponse<String> importResponse = uploadCsv(firstClient, firstAccountId);
        assertThat(importResponse.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(importResponse.body(), "$.status")).isEqualTo("COMPLETED");
        UUID batchId = UUID.fromString(JsonPath.read(importResponse.body(), "$.importBatchId"));

        HttpClient secondClient = newClient();
        createGuest(secondClient);
        UUID secondAccountId = createAccount(secondClient);
        String firstAccountPath = "/api/brokerage-accounts/" + firstAccountId;
        String activitiesPath = firstAccountPath + "/account-activities";
        String batchesPath = firstAccountPath + "/import-batches";

        assertListedAccount(firstClient, firstAccountId);
        assertListedAccount(secondClient, secondAccountId);
        assertThat(request(secondClient, "GET", activitiesPath).statusCode()).isEqualTo(404);
        assertThat(request(secondClient, "GET", batchesPath).statusCode()).isEqualTo(404);
        assertThat(request(secondClient, "DELETE", firstAccountPath).statusCode()).isEqualTo(404);
        assertThat(request(secondClient, "DELETE", batchesPath + "/" + batchId).statusCode()).isEqualTo(404);
        assertThat(uploadCsv(secondClient, firstAccountId).statusCode()).isEqualTo(400);

        assertTotalElements(request(firstClient, "GET", activitiesPath), 1);
        assertTotalElements(request(firstClient, "GET", batchesPath), 1);
        assertListedAccount(firstClient, firstAccountId);
        assertThat(request(firstClient, "DELETE", batchesPath + "/" + batchId).statusCode()).isEqualTo(204);
        assertTotalElements(request(firstClient, "GET", activitiesPath), 0);
        assertTotalElements(request(firstClient, "GET", batchesPath), 0);
        assertThat(request(firstClient, "DELETE", firstAccountPath).statusCode()).isEqualTo(204);
        assertListedAccount(secondClient, secondAccountId);
    }

    @Test
    void resolvesRegisteredUserUsingSameSessionMechanism() throws Exception {
        HttpClient client = newClient();
        UUID userId = createGuest(client);
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        user.setUserType(AppUserType.REGISTERED);
        user.setEmail(userId + "@example.com");
        appUserRepository.saveAndFlush(user);

        UUID accountId = createAccount(client);

        assertListedAccount(client, accountId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM brokerage_account WHERE id = ?", UUID.class, accountId
        )).isEqualTo(userId);
    }

    private HttpClient newClient() {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        clients.add(client);
        return client;
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Origin", "http://localhost:3000");
    }

    private HttpResponse<String> request(HttpClient client, String method, String path) throws Exception {
        return request(client, method, path, null);
    }

    private HttpResponse<String> request(HttpClient client, String method, String path, String json) throws Exception {
        HttpRequest.Builder builder = requestBuilder(path);
        if (!method.equals("GET")) {
            withCsrf(client, builder, port);
        }
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private UUID createGuest(HttpClient client) throws Exception {
        HttpResponse<String> response = request(client, "POST", "/api/auth/guest");
        assertThat(response.statusCode()).isEqualTo(200);
        UUID userId = UUID.fromString(JsonPath.read(response.body(), "$.userId"));
        return userId;
    }

    private UUID createAccount(HttpClient client) throws Exception {
        HttpResponse<String> response = request(client, "POST", "/api/brokerage-accounts", ACCOUNT_REQUEST);
        assertThat(response.statusCode()).isEqualTo(201);
        UUID accountId = UUID.fromString(JsonPath.read(response.body(), "$.id"));
        return accountId;
    }

    private HttpResponse<String> uploadCsv(HttpClient client, UUID accountId) throws Exception {
        String boundary = "AlphaForgeSessionTestBoundary";
        String multipart = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"session-test.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"
                + CSV.replace("\n", "\r\n") + "\r\n--" + boundary + "--\r\n";
        HttpRequest request = withCsrf(client, requestBuilder("/api/brokerage-accounts/" + accountId + "/account-activities/imports"), port)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(multipart))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Session userSession(UUID userId) {
        var sessions = sessionRepository.findByPrincipalName(userId.toString());
        assertThat(sessions).hasSize(1);
        return sessions.values().iterator().next();
    }

    private void assertUnauthorized(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.error")).isEqualTo("Unauthorized");
        assertThat(JsonPath.<String>read(response.body(), "$.message")).isEqualTo("A valid user session is required");
    }

    private void assertListedAccount(HttpClient client, UUID accountId) throws Exception {
        HttpResponse<String> response = request(client, "GET", "/api/brokerage-accounts");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(response.body(), "$[*].id")).containsExactly(accountId.toString());
    }

    private void assertTotalElements(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(response.body(), "$.totalElements").intValue()).isEqualTo(expected);
    }
}
