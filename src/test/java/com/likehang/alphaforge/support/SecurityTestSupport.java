package com.likehang.alphaforge.support;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public final class SecurityTestSupport {

    private SecurityTestSupport() {
    }

    public static HttpRequest.Builder withCsrf(HttpClient client, HttpRequest.Builder builder, int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf"))
                .header("Origin", "http://localhost:3000").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return builder.header(JsonPath.read(response.body(), "$.headerName"), JsonPath.read(response.body(), "$.token"));
    }

    public static void clearClientSession(HttpClient client, JdbcIndexedSessionRepository repository) {
        CookieManager cookies = (CookieManager) client.cookieHandler().orElseThrow();
        cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("ALPHAFORGE_SESSION"))
                .forEach(cookie -> repository.deleteById(new String(
                        Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8
                )));
    }

    public static void authenticateRequest(HttpServletRequest request, UUID userId) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
        new HttpSessionSecurityContextRepository().saveContext(context, request, new MockHttpServletResponse());
    }
}
