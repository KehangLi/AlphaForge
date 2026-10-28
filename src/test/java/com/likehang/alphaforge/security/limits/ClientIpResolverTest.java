package com.likehang.alphaforge.security.limits;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static final String SECRET = "test-proxy-secret-with-at-least-32-characters";
    private final ClientIpResolver resolver = new ClientIpResolver(new ClientIpProperties(SECRET));

    @Test
    void ignoresForwardedAndApplicationHeadersWithoutProxyAuthentication() {
        var request = request();
        request.addHeader("X-Forwarded-For", "192.0.2.1");
        request.addHeader("Forwarded", "for=192.0.2.2");
        request.addHeader(ClientIpResolver.CLIENT_IP_HEADER, "192.0.2.3");
        request.addHeader(ClientIpResolver.PROXY_TOKEN_HEADER, "wrong-token");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.1");
    }

    @Test
    void authenticatedProxyCanSupplyOneLiteralAddress() {
        var request = request();
        request.addHeader(ClientIpResolver.PROXY_TOKEN_HEADER, SECRET);
        request.addHeader(ClientIpResolver.CLIENT_IP_HEADER, "192.0.2.3");
        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.3");
    }

    @Test
    void ipv6SpellingsResolveToTheSameIdentity() {
        var first = request();
        first.addHeader(ClientIpResolver.PROXY_TOKEN_HEADER, SECRET);
        first.addHeader(ClientIpResolver.CLIENT_IP_HEADER, "2001:db8::1");
        var second = request();
        second.addHeader(ClientIpResolver.PROXY_TOKEN_HEADER, SECRET);
        second.addHeader(ClientIpResolver.CLIENT_IP_HEADER, "2001:0DB8:0:0:0:0:0:1");
        assertThat(resolver.resolve(first)).isEqualTo(resolver.resolve(second));
    }

    @Test
    void hostnamesListsZoneIdentifiersAndInvalidAddressesAreNotTrusted() {
        for (String address : new String[]{"example.com", "192.0.2.1, 192.0.2.2", "fe80::1%eth0", "999.1.1.1"}) {
            var request = request();
            request.addHeader(ClientIpResolver.PROXY_TOKEN_HEADER, SECRET);
            request.addHeader(ClientIpResolver.CLIENT_IP_HEADER, address);
            assertThat(resolver.resolve(request)).isEqualTo("203.0.113.1");
        }
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.1");
        return request;
    }
}
