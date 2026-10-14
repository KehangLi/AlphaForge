package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.rest.dto.response.CurrentUserResponse;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUserService;
    private final UserSessionService userSessionService;
    private final CookieSerializer cookieSerializer;

    public AuthController(
            CurrentUserService currentUserService,
            UserSessionService userSessionService,
            // Boot's embedded-server and WAR bean definitions are mutually exclusive.
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
            CookieSerializer cookieSerializer
    ) {
        this.currentUserService = currentUserService;
        this.userSessionService = userSessionService;
        this.cookieSerializer = cookieSerializer;
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
