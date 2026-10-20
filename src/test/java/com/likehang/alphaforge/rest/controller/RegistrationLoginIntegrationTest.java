package com.likehang.alphaforge.rest.controller;

import com.jayway.jsonpath.JsonPath;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.likehang.alphaforge.support.SecurityTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationLoginIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "A long test passphrase 123!";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private BrokerageAccountRepository brokerageAccountRepository;

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<HttpClient> clients = new ArrayList<>();

    @AfterEach
    void closeClients() {
        clients.forEach(HttpClient::close);
    }

    @Test
    void registersUserWithNormalizedEmailAndHashedPassword() throws Exception {
        HttpClient client = newClient();
        String email = uniqueEmail();

        HttpResponse<String> response = post(client, "/api/auth/register", registration(" " + email.toUpperCase(Locale.ROOT) + " "));
        UUID userId = userIdFrom(response, 201);

        assertThat(JsonPath.<Map<String, Object>>read(response.body(), "$"))
                .containsOnlyKeys("userId", "userType", "displayName");
        assertThat(JsonPath.<String>read(response.body(), "$.userType")).isEqualTo("REGISTERED");
        assertThat(JsonPath.<String>read(response.body(), "$.displayName")).isEqualTo("Test User");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie").orElseThrow()).contains("HttpOnly", "SameSite=Lax");
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getPasswordHash()).startsWith("{pbkdf2}").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        SecurityContext context = userSession(userId).getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).isEqualTo(userId);
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertCurrentUser(client, userId);
    }

    @Test
    void logsInAfterLogoutAndRecoversExistingAccount() throws Exception {
        HttpClient client = newClient();
        String email = uniqueEmail();
        UUID userId = userIdFrom(post(client, "/api/auth/register", registration(email)), 201);
        UUID accountId = createAccount(client);
        HttpRequest upload = withCsrf(client, builder("/api/brokerage-accounts/" + accountId + "/account-activities/imports"), port)
                .header("Content-Type", "multipart/form-data; boundary=AuthCsvBoundary")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "--AuthCsvBoundary\r\n"
                                + "Content-Disposition: form-data; name=\"file\"; filename=\"login.csv\"\r\n"
                                + "Content-Type: text/csv\r\n\r\n"
                                + "Action,Time (UTC),Total,Currency (Total)\r\n"
                                + "Interest on cash,2026-10-01 01:07:00+00:00,0.03,EUR\r\n"
                                + "--AuthCsvBoundary--\r\n"
                )).build();
        assertThat(client.send(upload, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
        String firstSessionId = userSession(userId).getId();

        assertThat(post(client, "/api/auth/logout", null).statusCode()).isEqualTo(204);
        assertThat(get(client, "/api/auth/me").statusCode()).isEqualTo(401);
        HttpResponse<String> login = post(client, "/api/auth/login", credentials(" " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD));

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(login.body(), "$.userId")).isEqualTo(userId.toString());
        assertThat(userSession(userId).getId()).isNotEqualTo(firstSessionId);
        assertThat(sessionRepository.findById(firstSessionId)).isNull();
        assertCurrentUser(client, userId);
        assertThat(JsonPath.<List<String>>read(get(client, "/api/brokerage-accounts").body(), "$[*].id"))
                .containsExactly(accountId.toString());
        HttpResponse<String> activities = get(client, "/api/brokerage-accounts/" + accountId + "/account-activities");
        assertThat(activities.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(activities.body(), "$.totalElements")).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateRegistrationWithoutReplacingGuestIdentity() throws Exception {
        String email = uniqueEmail();
        UUID registeredUserId = userIdFrom(post(newClient(), "/api/auth/register", registration(email)), 201);
        HttpClient guestClient = newClient();
        UUID guestId = userIdFrom(post(guestClient, "/api/auth/guest", null), 200);

        HttpResponse<String> response = post(guestClient, "/api/auth/register", registration(" " + email.toUpperCase(Locale.ROOT) + " "));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(response.body(), "$.message")).isEqualTo("Email is already registered");
        assertCurrentUser(guestClient, guestId);
        assertThat(appUserRepository.findByEmailIgnoreCase(email)).hasValueSatisfying(user ->
                assertThat(user.getId()).isEqualTo(registeredUserId));
    }

    @Test
    void handlesConcurrentRegistrationOfSameEmail() throws Exception {
        String email = uniqueEmail();
        HttpClient firstClient = newClient();
        HttpClient secondClient = newClient();
        HttpRequest firstRequest = jsonRequest(firstClient, "/api/auth/register", registration(email));
        HttpRequest secondRequest = jsonRequest(secondClient, "/api/auth/register", registration(email.toUpperCase(Locale.ROOT)));
        var first = firstClient.sendAsync(firstRequest, HttpResponse.BodyHandlers.ofString());
        var second = secondClient.sendAsync(secondRequest, HttpResponse.BodyHandlers.ofString());
        List<HttpResponse<String>> responses = List.of(first.join(), second.join());
        responses.stream().filter(response -> response.statusCode() == 201).forEach(response -> userIdFrom(response, 201));

        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void returnsSameLoginErrorForWrongPasswordUnknownEmailAndAccountWithoutPassword() throws Exception {
        String email = uniqueEmail();
        userIdFrom(post(newClient(), "/api/auth/register", registration(email)), 201);
        AppUser placeholder = appUserRepository.saveAndFlush(new AppUser(uniqueEmail(), "No Password"));
        HttpClient guestClient = newClient();
        UUID guestId = userIdFrom(post(guestClient, "/api/auth/guest", null), 200);

        for (Map<String, String> attempt : List.of(
                credentials(email, "incorrect password"),
                credentials(uniqueEmail(), PASSWORD),
                credentials(placeholder.getEmail(), PASSWORD)
        )) {
            HttpResponse<String> response = post(guestClient, "/api/auth/login", attempt);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(JsonPath.<String>read(response.body(), "$.message")).isEqualTo("Invalid email or password");
            assertCurrentUser(guestClient, guestId);
        }
    }

    @Test
    void loginReplacesGuestSessionWithoutMigratingGuestData() throws Exception {
        String email = uniqueEmail();
        UUID registeredId = userIdFrom(post(newClient(), "/api/auth/register", registration(email)), 201);
        HttpClient client = newClient();
        HttpResponse<String> guestResponse = post(client, "/api/auth/guest", null);
        UUID guestId = userIdFrom(guestResponse, 200);
        UUID guestAccountId = createAccount(client);
        String oldSessionId = userSession(guestId).getId();
        HttpCookie oldCookie = HttpCookie.parse(guestResponse.headers().firstValue("Set-Cookie").orElseThrow()).getFirst();
        HttpResponse<String> csrf = get(client, "/api/auth/csrf");

        HttpResponse<String> response = post(client, "/api/auth/login", credentials(email, PASSWORD));

        assertThat(response.statusCode()).isEqualTo(200);
        assertCurrentUser(client, registeredId);
        assertThat(sessionRepository.findById(oldSessionId)).isNull();
        assertThat(brokerageAccountRepository.findByIdAndUser_Id(guestAccountId, guestId)).isPresent();
        assertThat(JsonPath.<List<Object>>read(get(client, "/api/brokerage-accounts").body(), "$")).isEmpty();
        HttpRequest replay = builder("/api/auth/me")
                .header("Cookie", oldCookie.getName() + "=" + oldCookie.getValue()).GET().build();
        assertThat(newClient().send(replay, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        HttpRequest oldCsrf = builder("/api/brokerage-accounts/" + guestAccountId)
                .header(JsonPath.read(csrf.body(), "$.headerName"), JsonPath.read(csrf.body(), "$.token"))
                .DELETE().build();
        assertThat(client.send(oldCsrf, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void registrationFromGuestCreatesSeparateUserWithoutUpgradingGuest() throws Exception {
        HttpClient client = newClient();
        UUID guestId = userIdFrom(post(client, "/api/auth/guest", null), 200);
        UUID guestAccountId = createAccount(client);

        UUID registeredId = userIdFrom(post(client, "/api/auth/register", registration(uniqueEmail())), 201);

        assertThat(registeredId).isNotEqualTo(guestId);
        assertCurrentUser(client, registeredId);
        assertThat(appUserRepository.findById(guestId)).hasValueSatisfying(user ->
                assertThat(user.getUserType()).isEqualTo(AppUserType.GUEST));
        assertThat(brokerageAccountRepository.findByIdAndUser_Id(guestAccountId, guestId)).isPresent();
    }

    @Test
    void validatesRegistrationInputWithoutEchoingPasswords() throws Exception {
        HttpClient client = newClient();
        for (Map<String, String> invalid : List.of(
                Map.of("email", "invalid-email", "password", PASSWORD, "displayName", "Test"),
                Map.of("email", uniqueEmail(), "password", "too-short", "displayName", "Test"),
                Map.of("email", uniqueEmail(), "password", "x".repeat(129), "displayName", "Test"),
                Map.of("email", uniqueEmail(), "password", PASSWORD, "displayName", "  ")
        )) {
            HttpResponse<String> response = post(client, "/api/auth/register", invalid);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain(invalid.get("password"));
        }
        assertThat(get(client, "/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsMissingBodyAndMalformedJsonWithJsonErrors() throws Exception {
        HttpClient client = newClient();
        assertThat(post(client, "/api/auth/login", null).statusCode()).isEqualTo(400);
        HttpRequest request = withCsrf(client, builder("/api/auth/register"), port)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"password\":\"never-echo-this\""))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.message")).isEqualTo("Invalid JSON request");
        assertThat(response.body()).doesNotContain("never-echo-this");
    }

    @Test
    void requiresCsrfOnAuthenticationAndBusinessWrites() throws Exception {
        HttpClient client = newClient();
        for (String path : List.of("/api/auth/register", "/api/auth/login", "/api/auth/guest", "/api/auth/logout")) {
            HttpResponse<String> response = client.send(
                    builder(path).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()
            );
            assertCsrfRejected(response);
        }
        UUID userId = userIdFrom(post(client, "/api/auth/register", registration(uniqueEmail())), 201);
        HttpRequest missingCsrf = builder("/api/brokerage-accounts")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(accountJson())).build();
        assertCsrfRejected(client.send(missingCsrf, HttpResponse.BodyHandlers.ofString()));
        assertThat(brokerageAccountRepository.findByUser_IdOrderByCreatedAtDesc(userId)).isEmpty();
    }

    @Test
    void rejectsCsrfTokenFromDifferentSession() throws Exception {
        HttpResponse<String> csrf = get(newClient(), "/api/auth/csrf");
        HttpClient otherClient = newClient();
        get(otherClient, "/api/auth/csrf");
        HttpRequest request = builder("/api/auth/guest")
                .header(JsonPath.read(csrf.body(), "$.headerName"), JsonPath.read(csrf.body(), "$.token"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();

        assertCsrfRejected(otherClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void logoutEndsOnlyCurrentSessionOfRegisteredUser() throws Exception {
        HttpClient firstClient = newClient();
        String email = uniqueEmail();
        UUID userId = userIdFrom(post(firstClient, "/api/auth/register", registration(email)), 201);
        HttpClient secondClient = newClient();
        assertThat(post(secondClient, "/api/auth/login", credentials(email, PASSWORD)).statusCode()).isEqualTo(200);
        assertThat(sessionRepository.findByPrincipalName(userId.toString())).hasSize(2);

        assertThat(post(firstClient, "/api/auth/logout", null).statusCode()).isEqualTo(204);

        assertThat(get(firstClient, "/api/auth/me").statusCode()).isEqualTo(401);
        assertCurrentUser(secondClient, userId);
        assertThat(sessionRepository.findByPrincipalName(userId.toString())).hasSize(1);
    }

    @Test
    void keepsRegisteredUsersAccountsIsolated() throws Exception {
        HttpClient firstClient = newClient();
        userIdFrom(post(firstClient, "/api/auth/register", registration(uniqueEmail())), 201);
        UUID firstAccountId = createAccount(firstClient);
        HttpClient secondClient = newClient();
        userIdFrom(post(secondClient, "/api/auth/register", registration(uniqueEmail())), 201);
        UUID secondAccountId = createAccount(secondClient);

        assertThat(get(secondClient, "/api/brokerage-accounts/" + firstAccountId + "/account-activities").statusCode())
                .isEqualTo(404);
        HttpRequest delete = withCsrf(secondClient, builder("/api/brokerage-accounts/" + firstAccountId), port)
                .DELETE().build();
        assertThat(secondClient.send(delete, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        assertThat(brokerageAccountRepository.existsById(firstAccountId)).isTrue();
        assertThat(JsonPath.<List<String>>read(get(secondClient, "/api/brokerage-accounts").body(), "$[*].id"))
                .containsExactly(secondAccountId.toString());
    }

    @Test
    void healthRemainsPublic() throws Exception {
        assertThat(get(newClient(), "/health").statusCode()).isEqualTo(200);
    }

    private HttpClient newClient() {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
        clients.add(client);
        return client;
    }

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Origin", "http://localhost:3000");
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(builder(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest jsonRequest(HttpClient client, String path, Map<String, String> body) throws Exception {
        HttpRequest.Builder builder = withCsrf(client, builder(path), port);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return builder.POST(body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
    }

    private HttpResponse<String> post(HttpClient client, String path, Map<String, String> body) throws Exception {
        return client.send(jsonRequest(client, path, body), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, String> registration(String email) {
        return Map.of("email", email, "password", PASSWORD, "displayName", " Test User ");
    }

    private Map<String, String> credentials(String email, String password) {
        return Map.of("email", email, "password", password);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private UUID userIdFrom(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        UUID userId = UUID.fromString(JsonPath.read(response.body(), "$.userId"));
        return userId;
    }

    private UUID createAccount(HttpClient client) throws Exception {
        HttpRequest request = withCsrf(client, builder("/api/brokerage-accounts"), port)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(accountJson())).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        UUID accountId = UUID.fromString(JsonPath.read(response.body(), "$.id"));
        return accountId;
    }

    private String accountJson() {
        return """
                {"brokerName":"Trading 212","accountName":"Login test","baseCurrency":"EUR"}
                """;
    }

    private Session userSession(UUID userId) {
        var sessions = sessionRepository.findByPrincipalName(userId.toString());
        assertThat(sessions).hasSize(1);
        return sessions.values().iterator().next();
    }

    private void assertCurrentUser(HttpClient client, UUID userId) throws Exception {
        HttpResponse<String> response = get(client, "/api/auth/me");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.userId")).isEqualTo(userId.toString());
    }

    private void assertCsrfRejected(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(JsonPath.<String>read(response.body(), "$.message")).isEqualTo("A valid CSRF token is required");
    }
}
