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
        assertUnauthorized(request(client, "GET", accountPath + "/holdings"));
        assertUnauthorized(request(client, "GET", accountPath + "/realized-trading-summary"));
        assertUnauthorized(request(client, "GET", accountPath + "/instruments/ISIN:US67066G1040"));
        assertUnauthorized(uploadCsv(client, accountId));
        assertUnauthorized(request(client, "GET", accountPath + "/import-batches"));
        assertUnauthorized(request(client, "DELETE", accountPath + "/import-batches/" + UUID.randomUUID()));
        assertUnauthorized(request(client, "PUT", accountPath + "/account-activities/" + UUID.randomUUID() + "/journal-note",
                "{\"journalNote\":\"Review\"}"));
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
        assertThat(request(secondClient, "GET", firstAccountPath + "/holdings").statusCode()).isEqualTo(404);
        assertThat(request(secondClient, "GET", firstAccountPath + "/realized-trading-summary").statusCode()).isEqualTo(404);
        assertThat(request(secondClient, "GET", firstAccountPath + "/instruments/ISIN:US67066G1040").statusCode()).isEqualTo(404);
        assertThat(request(secondClient, "GET", batchesPath).statusCode()).isEqualTo(404);
        assertThat(request(secondClient, "DELETE", firstAccountPath).statusCode()).isEqualTo(404);
        assertThat(request(secondClient, "DELETE", batchesPath + "/" + batchId).statusCode()).isEqualTo(404);
        assertThat(uploadCsv(secondClient, firstAccountId).statusCode()).isEqualTo(400);
        HttpResponse<String> firstActivities = request(firstClient, "GET", activitiesPath);
        String activityId = JsonPath.read(firstActivities.body(), "$.activities[0].id");
        assertThat(request(secondClient, "PUT", activitiesPath + "/" + activityId + "/journal-note",
                "{\"journalNote\":\"Unauthorized edit\"}").statusCode()).isEqualTo(404);

        assertTotalElements(request(firstClient, "GET", activitiesPath), 1);
        assertTotalElements(request(firstClient, "GET", firstAccountPath + "/holdings"), 0);
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

    @Test
    void returnsCalculatedHoldingsThroughTheAuthenticatedHttpEndpoint() throws Exception {
        HttpClient client = newClient();
        createGuest(client);
        UUID accountId = createAccount(client);
        String csv = """
                Action,Time (UTC),ISIN,Ticker,Name,ID,No. of shares,Total,Currency (Total)
                Limit buy,2026-10-01 10:00:00+00:00,US67066G1040,NVDA,NVIDIA,BUY-1,5,500,EUR
                Limit sell,2026-10-02 10:00:00+00:00,US67066G1040,NVDA,NVIDIA,SELL-1,2,220,EUR
                """;
        assertThat(uploadCsv(client, accountId, csv).statusCode()).isEqualTo(201);
        String path = "/api/brokerage-accounts/" + accountId + "/holdings";
        HttpResponse<String> response = request(client, "GET", path + "?status=ALL&search=nvda&page=0&size=1");
        assertTotalElements(response, 1);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(JsonPath.<String>read(response.body(), "$.holdings[0].instrumentKey")).isEqualTo("ISIN:US67066G1040");
        assertThat(JsonPath.<Number>read(response.body(), "$.holdings[0].quantity").doubleValue()).isEqualTo(3);
        assertThat(JsonPath.<String>read(response.body(), "$.holdings[0].status")).isEqualTo("OPEN");
        assertTotalElements(request(client, "GET", path + "?status=CLOSED"), 0);
        assertThat(request(client, "GET", path + "?search=" + "x".repeat(121)).statusCode()).isEqualTo(400);
    }

    @Test
    void returnsInstrumentSummaryAndPagedHistoryUsingTheAuthenticatedSession() throws Exception {
        HttpClient client = newClient();
        createGuest(client);
        UUID accountId = createAccount(client);
        String csv = """
                Action,Time (UTC),ISIN,Ticker,Name,ID,No. of shares,Total,Currency (Total),Result,Currency (Result)
                Limit buy,2026-10-01 10:00:00+00:00,US67066G1040,NVDA,NVIDIA,BUY-1,5,500,EUR,,
                Limit sell,2026-10-02 10:00:00+00:00,US67066G1040,NVDA,NVIDIA,SELL-1,2,220,EUR,12.50,EUR
                Dividend (Dividend),2026-10-03 10:00:00+00:00,US67066G1040,NVDA,NVIDIA,,5,1,EUR,,
                """;
        assertThat(uploadCsv(client, accountId, csv).statusCode()).isEqualTo(201);
        String path = "/api/brokerage-accounts/" + accountId + "/instruments/";
        HttpResponse<String> first = request(client, "GET", path + "isin%3Aus67066g1040?page=0&size=1");
        assertTotalElements(first, 3);
        assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(JsonPath.<String>read(first.body(), "$.instrumentKey")).isEqualTo("ISIN:US67066G1040");
        assertThat(JsonPath.<Number>read(first.body(), "$.holding.quantity").doubleValue()).isEqualTo(3);
        assertThat(JsonPath.<String>read(first.body(), "$.activities[0].action")).isEqualTo("Dividend (Dividend)");
        assertThat(JsonPath.<Number>read(first.body(), "$.realizedSummary.currencies[0].totalResult").doubleValue()).isEqualTo(12.5);
        assertThat(JsonPath.<String>read(first.body(), "$.activities[0].importFilename")).isEqualTo("session-test.csv");
        HttpResponse<String> last = request(client, "GET", path + "ISIN:US67066G1040?page=2&size=1");
        assertThat(JsonPath.<Number>read(last.body(), "$.holding.quantity").doubleValue()).isEqualTo(3);
        assertThat(JsonPath.<String>read(last.body(), "$.activities[0].externalTransactionId")).isEqualTo("BUY-1");
        assertThat(JsonPath.<Number>read(last.body(), "$.realizedSummary.currencies[0].totalResult").doubleValue()).isEqualTo(12.5);

        String activityId = JsonPath.read(last.body(), "$.activities[0].id");
        String journalPath = "/api/brokerage-accounts/" + accountId + "/account-activities/" + activityId + "/journal-note";
        HttpRequest missingCsrf = requestBuilder(journalPath).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"journalNote\":\"Review\"}")).build();
        assertThat(client.send(missingCsrf, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        HttpResponse<String> saved = request(client, "PUT", journalPath, "{\"journalNote\":\"Entry reason\"}");
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(saved.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(JsonPath.<String>read(saved.body(), "$.journalNote")).isEqualTo("Entry reason");
        HttpResponse<String> withNote = request(client, "GET", path + "ISIN:US67066G1040?page=2&size=1");
        assertThat(JsonPath.<String>read(withNote.body(), "$.activities[0].journalNote")).isEqualTo("Entry reason");
        assertThat(request(client, "PUT", journalPath, "{}").statusCode()).isEqualTo(400);
        assertThat(request(client, "PUT", journalPath, "{\"journalNote\":\"\"}").statusCode()).isEqualTo(200);
        HttpResponse<String> cleared = request(client, "GET", path + "ISIN:US67066G1040?page=2&size=1");
        assertThat(JsonPath.<String>read(cleared.body(), "$.activities[0].journalNote")).isNull();
        assertThat(request(client, "GET", path + "ISIN:US0378331005").statusCode()).isEqualTo(404);
        assertThat(request(client, "GET", path + "NAME:NVDA").statusCode()).isEqualTo(400);
        assertThat(request(client, "GET", path + "TICKER:" + "A".repeat(41)).statusCode()).isEqualTo(400);
    }

    @Test
    void returnsAccountRealizedOverviewAndPagedContributionsUsingSessionAuthentication() throws Exception {
        HttpClient client = newClient();
        createGuest(client);
        UUID accountId = createAccount(client);
        String csv = """
                Action,Time (UTC),ISIN,Ticker,Name,ID,No. of shares,Total,Currency (Total),Result,Currency (Result)
                Limit buy,2026-09-01 10:00:00+00:00,US67066G1040,NVDA,NVIDIA,BUY-N,10,1000,EUR,,
                Limit buy,2026-09-01 11:00:00+00:00,US0378331005,AAPL,Apple,BUY-A,5,500,EUR,,
                Limit sell,2026-09-30 10:00:00+00:00,US67066G1040,NVDA,NVIDIA,BEFORE,1,110,EUR,100,EUR
                Limit sell,2026-10-01 10:00:00+00:00,US67066G1040,NVDA,NVIDIA,SELL-N,2,220,EUR,12.50,EUR
                Stop sell,2026-10-31 23:59:59+00:00,US0378331005,AAPL,Apple,SELL-A,1,90,EUR,-2,EUR
                Limit sell,2026-11-01 00:00:00+00:00,US67066G1040,NVDA,NVIDIA,AFTER,1,120,EUR,200,EUR
                """;
        assertThat(uploadCsv(client, accountId, csv).statusCode()).isEqualTo(201);
        String path = "/api/brokerage-accounts/" + accountId + "/realized-trading-summary";
        String filters = "?from=2026-10-01&to=2026-10-31&currency=eur&size=1";
        HttpResponse<String> first = request(client, "GET", path + filters);
        assertTotalElements(first, 2);
        assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(JsonPath.<String>read(first.body(), "$.from")).isEqualTo("2026-10-01");
        assertThat(JsonPath.<String>read(first.body(), "$.currency")).isEqualTo("EUR");
        assertThat(JsonPath.<Number>read(first.body(), "$.realizedSummary.currencies[0].totalResult").doubleValue()).isEqualTo(10.5);
        assertThat(JsonPath.<String>read(first.body(), "$.contributions[0].instrumentKey")).isEqualTo("ISIN:US67066G1040");
        HttpResponse<String> second = request(client, "GET", path + filters + "&page=1");
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(second.body(), "$.realizedSummary.currencies[0].totalResult").doubleValue()).isEqualTo(10.5);
        assertThat(JsonPath.<String>read(second.body(), "$.contributions[0].ticker")).isEqualTo("AAPL");
        assertThat(request(client, "GET", path + "?from=2026-10-31&to=2026-10-01").statusCode()).isEqualTo(400);
        assertThat(request(client, "GET", path + "?currency=EURO").statusCode()).isEqualTo(400);
        HttpResponse<String> all = request(client, "GET", path);
        assertThat(all.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(all.body(), "$.realizedSummary.currencies[0].totalResult").doubleValue()).isEqualTo(310.5);
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
        return uploadCsv(client, accountId, CSV);
    }

    private HttpResponse<String> uploadCsv(HttpClient client, UUID accountId, String csv) throws Exception {
        String boundary = "AlphaForgeSessionTestBoundary";
        String multipart = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"session-test.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"
                + csv.replace("\n", "\r\n") + "\r\n--" + boundary + "--\r\n";
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
