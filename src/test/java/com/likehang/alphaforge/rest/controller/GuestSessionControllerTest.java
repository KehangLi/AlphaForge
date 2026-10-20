package com.likehang.alphaforge.rest.controller;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.likehang.alphaforge.support.SecurityTestSupport.withCsrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GuestSessionControllerTest extends AbstractPostgresIntegrationTest {

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
    void createsGuestAndPersistsSessionWithHttpOnlyCookie() throws Exception {
        HttpResponse<String> response = postGuest(newClient());
        UUID userId = guestUserId(response);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.userType")).isEqualTo("GUEST");
        assertThat(response.headers().firstValue("Set-Cookie").orElseThrow())
                .contains("ALPHAFORGE_SESSION=", "HttpOnly", "SameSite=Lax", "Path=/")
                .doesNotContain("Secure");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow())
                .isEqualTo("true");
        assertThat(appUserRepository.findById(userId)).hasValueSatisfying(guest -> {
            assertThat(guest.getEmail()).isNull();
            assertThat(guest.getUserType()).isEqualTo(AppUserType.GUEST);
        });
        Session session = userSession(userId);
        SecurityContext context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).isEqualTo(userId);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void reusesGuestAndSessionWhenCookieIsSentAgain() throws Exception {
        HttpClient client = newClient();
        UUID firstUserId = guestUserId(postGuest(client));
        String sessionId = userSession(firstUserId).getId();

        HttpResponse<String> response = postGuest(client);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(guestUserId(response)).isEqualTo(firstUserId);
        assertThat(userSession(firstUserId).getId()).isEqualTo(sessionId);
    }

    @Test
    void createsSeparateUsersForSeparateBrowsers() throws Exception {
        UUID firstUserId = guestUserId(postGuest(newClient()));
        UUID secondUserId = guestUserId(postGuest(newClient()));

        assertThat(firstUserId).isNotEqualTo(secondUserId);
        assertThat(userSession(firstUserId).getId()).isNotEqualTo(userSession(secondUserId).getId());
    }

    @Test
    void createsNewGuestWhenSessionHasExpired() throws Exception {
        HttpClient client = newClient();
        UUID firstUserId = guestUserId(postGuest(client));
        Session oldSession = userSession(firstUserId);
        long lastAccessTime = System.currentTimeMillis() - 60_000;
        jdbcTemplate.update("""
                UPDATE spring_session
                SET last_access_time = ?, max_inactive_interval = 1, expiry_time = ?
                WHERE session_id = ?
                """, lastAccessTime, lastAccessTime + 1_000, oldSession.getId());

        HttpResponse<String> response = postGuest(client);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(guestUserId(response)).isNotEqualTo(firstUserId);
        assertThat(sessionRepository.findById(oldSession.getId())).isNull();
    }

    @Test
    void rejectsGuestEntryWhenSessionBelongsToRegisteredUser() throws Exception {
        HttpClient client = newClient();
        UUID userId = guestUserId(postGuest(client));
        String sessionId = userSession(userId).getId();
        appUserRepository.findById(userId).ifPresent(user -> {
            user.setUserType(AppUserType.REGISTERED);
            user.setEmail(userId + "@example.com");
            appUserRepository.saveAndFlush(user);
        });

        HttpResponse<String> response = postGuest(client);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(response.body(), "$.message"))
                .isEqualTo("A registered user session is already active");
        assertThat(userSession(userId).getId()).isEqualTo(sessionId);
        assertThat(appUserRepository.findById(userId)).hasValueSatisfying(user ->
                assertThat(user.getUserType()).isEqualTo(AppUserType.REGISTERED));
    }

    @Test
    void allowsCredentialedPreflightFromConfiguredFrontend() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(guestUri())
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = newClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow())
                .isEqualTo("http://localhost:3000");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow())
                .isEqualTo("true");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void rejectsRequestsFromUnconfiguredOrigin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(guestUri())
                .header("Origin", "https://untrusted.example.com")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = newClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    private HttpClient newClient() {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        clients.add(client);
        return client;
    }

    private HttpResponse<String> postGuest(HttpClient client) throws Exception {
        HttpRequest request = withCsrf(client, HttpRequest.newBuilder(guestUri()), port)
                .timeout(Duration.ofSeconds(10))
                .header("Origin", "http://localhost:3000")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI guestUri() {
        return URI.create("http://localhost:" + port + "/api/auth/guest");
    }

    private UUID guestUserId(HttpResponse<String> response) {
        UUID userId = UUID.fromString(JsonPath.read(response.body(), "$.userId"));
        return userId;
    }

    private Session userSession(UUID userId) {
        var sessions = sessionRepository.findByPrincipalName(userId.toString());
        assertThat(sessions).hasSize(1);
        return sessions.values().iterator().next();
    }
}
