package com.likehang.alphaforge.security.limits;

/*
RateLimitProperties provides rate-limit rules
        ↓
        Request → RateLimitFilter
        ├─ ClientIpResolver: resolves the client IP address
        │      └─ ClientIpProperties: provides proxy validation settings
        └─ RateLimitService: checks and consumes request quota
        ↓
        Allowed: continue processing the request
        Rate limit exceeded: return HTTP 429
*/

import com.likehang.alphaforge.exception.RateLimitException;
import com.likehang.alphaforge.rest.dto.response.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

import static com.likehang.alphaforge.security.limits.RateLimitService.Rule.*;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService limits;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService limits, ClientIpResolver clientIpResolver, ObjectMapper objectMapper) {
        this.limits = limits;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod())
                || ("GET".equals(request.getMethod()) && "/health".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String ip = clientIpResolver.resolve(request);
            limits.check(REQUEST_IP, ip);
            String path = request.getServletPath();
            if ("POST".equals(request.getMethod())) {
                if ("/api/auth/login".equals(path)) limits.check(LOGIN_IP, ip);
                if ("/api/auth/register".equals(path)) limits.check(REGISTER_IP, ip);
            }
            if ("GET".equals(request.getMethod()) && "/api/auth/csrf".equals(path)) limits.check(CSRF_IP, ip);
        } catch (RateLimitException exception) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()));
            objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                    Instant.now(), 429, "Too Many Requests", exception.getMessage()));
            return;
        }
        chain.doFilter(request, response);
    }
}

/*
LOGIN_EAMIL would be check in AuthService.login()
GUEST_CREATION_IP --> GuestSessionService
UPLOAD_USER --> CSV import service
*/
