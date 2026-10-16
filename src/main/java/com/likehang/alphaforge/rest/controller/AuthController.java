package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.rest.dto.response.CurrentUserResponse;
import com.likehang.alphaforge.rest.dto.response.CsrfTokenResponse;
import com.likehang.alphaforge.rest.dto.request.LoginRequest;
import com.likehang.alphaforge.rest.dto.request.RegisterRequest;
import com.likehang.alphaforge.service.AuthService;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUserService;
    private final AuthService authService;
    private final UserSessionService userSessionService;
    private final CookieSerializer cookieSerializer;

    public AuthController(
            CurrentUserService currentUserService,
            AuthService authService,
            UserSessionService userSessionService,
            // Boot's embedded-server and WAR bean definitions are mutually exclusive.
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
            CookieSerializer cookieSerializer
    ) {
        this.currentUserService = currentUserService;
        this.authService = authService;
        this.userSessionService = userSessionService;
        this.cookieSerializer = cookieSerializer;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> getCsrfToken(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/register")
    public ResponseEntity<CurrentUserResponse> register(
            @Valid @RequestBody RegisterRequest registration,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        CurrentUserResponse user = authService.register(registration);
        userSessionService.startSession(request, response, user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(user);
    }

    @PostMapping("/login")
    public ResponseEntity<CurrentUserResponse> login(
            @Valid @RequestBody LoginRequest login,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        CurrentUserResponse user = authService.login(login);
        userSessionService.startSession(request, response, user.userId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(currentUserService.getCurrentUser());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Spring Session clears invalidated sessions' cookies; also clear stale cookies.
        if (!userSessionService.endSession(request)) {
            cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, ""));
        }

        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
