package com.likehang.alphaforge.security.limits;

/*
Token matches       → Trust the IP address provided by the proxy
Token does not match → Allow the request to proceed, but use only the direct connection’s IP address for rate limiting
*/

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ClientIpResolver {

    public static final String PROXY_TOKEN_HEADER = "X-Alphaforge-Proxy-Token";
    public static final String CLIENT_IP_HEADER = "X-Alphaforge-Client-IP";

    private final ClientIpProperties properties;

    public ClientIpResolver(ClientIpProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String token = request.getHeader(PROXY_TOKEN_HEADER);
        if (!properties.proxySecret().isEmpty() && token != null && token.length() <= 256
                && MessageDigest.isEqual(properties.proxySecret().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            String address = canonicalAddress(request.getHeader(CLIENT_IP_HEADER));
            if (address != null) return address;
        }
        // Forwarded headers alone never prove who sent a request.
        String address = canonicalAddress(request.getRemoteAddr());
        return address == null ? "unknown" : address;
    }

    private String canonicalAddress(String address) {
        if (address == null || address.length() > 64) return null;
        if (address.indexOf(':') >= 0 && address.matches("[0-9a-fA-F:.]+")) {
            try {
                return InetAddress.getByName(address).getHostAddress();
            } catch (UnknownHostException ignored) {
                return null;
            }
        }
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) return null;
        StringBuilder canonical = new StringBuilder();
        for (String octet : octets) {
            if (!octet.matches("[0-9]{1,3}")) return null;
            int value = Integer.parseInt(octet);
            if (value > 255) return null;
            if (!canonical.isEmpty()) canonical.append('.');
            canonical.append(value);
        }
        return canonical.toString();
    }
}
