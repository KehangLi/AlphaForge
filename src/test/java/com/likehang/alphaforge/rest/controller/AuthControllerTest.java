package com.likehang.alphaforge.rest.controller;

import com.jayway.jsonpath.JsonPath;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.service.BrokerageAccountService;
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
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.likehang.alphaforge.support.SecurityTestSupport.withCsrf;
import static com.likehang.alphaforge.support.SecurityTestSupport.clearClientSession;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "alphaforge.dev.seed-data.enabled=false",
                "alphaforge.cors.allowed-origins=http://localhost:3000",
                "server.servlet.session.cookie.secure=false"
        }
)
class AuthControllerTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BrokerageAccountRepository brokerageAccountRepository;

    @Autowired
    private BrokerageAccountService brokerageAccountService;

    private final Set<UUID> userIds = new HashSet<>();
    private final Map<UUID, UUID> accountOwners = new HashMap<>();
    private final List<HttpClient> clients = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        accountOwners.forEach((accountId, userId) -> {
            if (brokerageAccountRepository.existsById(accountId)) {
                brokerageAccountService.deleteBrokerageAccount(userId, accountId);
            }
        });
        for (UUID userId : userIds) {
            sessionRepository.findByPrincipalName(userId.toString()).keySet()
                    .forEach(sessionRepository::deleteById);
            appUserRepository.deleteById(userId);
        }
        clients.forEach(client -> {
            clearClientSession(client, sessionRepository);
            client.close();
        });
    }

    @Test
    void returnsUnauthorizedWithoutCreatingSessionWhenReadingCurrentUser() throws Exception {
        HttpResponse<String> response = send(newClient(), "GET", "/api/auth/me");

        assertUnauthorized(response);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void returnsGuestDetailsAndReusesExistingSession() throws Exception {
        HttpClient client = newClient();
        UUID userId = createGuest(client);
        String sessionId = userSession(userId).getId();

        HttpResponse<String> response = send(client, "GET", "/api/auth/me");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Map<String, Object>>read(response.body(), "$"))
                .containsOnlyKeys("userId", "userType", "displayName");
        assertThat(JsonPath.<String>read(response.body(), "$.userId")).isEqualTo(userId.toString());
        assertThat(JsonPath.<String>read(response.body(), "$.userType")).isEqualTo("GUEST");
        assertThat(JsonPath.<String>read(response.body(), "$.displayName")).isEqualTo("Guest");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(userSession(userId).getId()).isEqualTo(sessionId);
    }

    @Test
    void returnsRegisteredUserDetailsThroughSameEndpoint() throws Exception {
        HttpClient client = newClient();
        UUID userId = createGuest(client);
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        user.setUserType(AppUserType.REGISTERED);
        user.setEmail(userId + "@example.com");
        user.setDisplayName("Registered User");
        appUserRepository.saveAndFlush(user);

        HttpResponse<String> response = send(client, "GET", "/api/auth/me");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.userId")).isEqualTo(userId.toString());
        assertThat(JsonPath.<String>read(response.body(), "$.userType")).isEqualTo("REGISTERED");
        assertThat(JsonPath.<String>read(response.body(), "$.displayName")).isEqualTo("Registered User");
    }

    @Test
    void returnsUnauthorizedWhenSessionHasExpired() throws Exception {
        HttpClient client = newClient();
        UUID userId = createGuest(client);
        expireSession(userSession(userId));

        assertUnauthorized(send(client, "GET", "/api/auth/me"));
        assertThat(appUserRepository.existsById(userId)).isTrue();
    }

    @Test
    void returnsUnauthorizedWhenSessionUserWasDeleted() throws Exception {
        HttpClient client = newClient();
        UUID userId = createGuest(client);
        appUserRepository.deleteById(userId);

        assertUnauthorized(send(client, "GET", "/api/auth/me"));
    }

    @Test
    void logoutClearsCookieRejectsReplayAndPreservesUserData() throws Exception {
        HttpClient client = newClient();
        HttpResponse<String> guestResponse = send(client, "POST", "/api/auth/guest");
        UUID userId = trackGuest(guestResponse);
        String sessionId = userSession(userId).getId();
        HttpCookie oldCookie = HttpCookie.parse(guestResponse.headers().firstValue("Set-Cookie").orElseThrow()).getFirst();
        HttpRequest createAccount = withCsrf(client, requestBuilder("/api/brokerage-accounts"), port)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"brokerName":"Trading 212","accountName":"Logout test","baseCurrency":"EUR"}
                        """))
                .build();
        HttpResponse<String> accountResponse = client.send(createAccount, HttpResponse.BodyHandlers.ofString());
        assertThat(accountResponse.statusCode()).isEqualTo(201);
        UUID accountId = UUID.fromString(JsonPath.read(accountResponse.body(), "$.id"));
        accountOwners.put(accountId, userId);

        HttpResponse<String> response = send(client, "POST", "/api/auth/logout");

        assertLoggedOut(response);
        assertThat(sessionRepository.findById(sessionId)).isNull();
        assertThat(appUserRepository.existsById(userId)).isTrue();
        assertThat(brokerageAccountRepository.findByIdAndUser_Id(accountId, userId)).isPresent();
        assertUnauthorized(send(client, "GET", "/api/auth/me"));
        assertUnauthorized(send(client, "GET", "/api/brokerage-accounts"));
        HttpRequest replay = requestBuilder("/api/auth/me")
                .header("Cookie", oldCookie.getName() + "=" + oldCookie.getValue())
                .GET()
                .build();
        assertUnauthorized(newClient().send(replay, HttpResponse.BodyHandlers.ofString()));
        assertLoggedOut(send(client, "POST", "/api/auth/logout"));
        assertThat(createGuest(client)).isNotEqualTo(userId);
    }

    @Test
    void logoutClearsCookieEvenWithoutValidSession() throws Exception {
        assertLoggedOut(send(newClient(), "POST", "/api/auth/logout"));

        HttpClient client = newClient();
        UUID userId = createGuest(client);
        Session session = userSession(userId);
        expireSession(session);

        assertLoggedOut(send(client, "POST", "/api/auth/logout"));
        assertThat(sessionRepository.findById(session.getId())).isNull();
        assertThat(appUserRepository.existsById(userId)).isTrue();
    }

    @Test
    void logoutDoesNotEndAnotherBrowsersSession() throws Exception {
        HttpClient firstClient = newClient();
        createGuest(firstClient);
        HttpClient secondClient = newClient();
        UUID secondUserId = createGuest(secondClient);

        assertLoggedOut(send(firstClient, "POST", "/api/auth/logout"));
        HttpResponse<String> response = send(secondClient, "GET", "/api/auth/me");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.userId")).isEqualTo(secondUserId.toString());
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

    private HttpResponse<String> send(HttpClient client, String method, String path) throws Exception {
        HttpRequest.Builder builder = requestBuilder(path);
        if (!method.equals("GET")) {
            withCsrf(client, builder, port);
        }
        HttpRequest request = builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private UUID createGuest(HttpClient client) throws Exception {
        return trackGuest(send(client, "POST", "/api/auth/guest"));
    }

    private UUID trackGuest(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        UUID userId = UUID.fromString(JsonPath.read(response.body(), "$.userId"));
        userIds.add(userId);
        return userId;
    }

    private Session userSession(UUID userId) {
        var sessions = sessionRepository.findByPrincipalName(userId.toString());
        assertThat(sessions).hasSize(1);
        return sessions.values().iterator().next();
    }

    private void expireSession(Session session) {
        long lastAccessTime = System.currentTimeMillis() - 60_000;
        jdbcTemplate.update("""
                UPDATE spring_session
                SET last_access_time = ?, max_inactive_interval = 1, expiry_time = ?
                WHERE session_id = ?
                """, lastAccessTime, lastAccessTime + 1_000, session.getId());
    }

    private void assertUnauthorized(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.message")).isEqualTo("A valid user session is required");
    }

    private void assertLoggedOut(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().allValues("Set-Cookie")).singleElement().asString()
                .contains("ALPHAFORGE_SESSION=;", "Max-Age=0", "Path=/", "HttpOnly", "SameSite=Lax");
    }
}
