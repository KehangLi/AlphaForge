package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.rest.dto.response.GuestSessionResponse;
import com.likehang.alphaforge.service.GuestSessionService;
import com.likehang.alphaforge.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class GuestSessionController {

    private final GuestSessionService guestSessionService;
    private final UserSessionService userSessionService;

    public GuestSessionController(GuestSessionService guestSessionService, UserSessionService userSessionService) {
        this.guestSessionService = guestSessionService;
        this.userSessionService = userSessionService;
    }

    @PostMapping("/guest")
    public ResponseEntity<GuestSessionResponse> createGuestSession(HttpServletRequest request) {
        UUID existingUserId = userSessionService.getUserId(request);
        GuestSessionResponse response = guestSessionService.createOrResumeGuest(existingUserId);

        if (!response.userId().equals(existingUserId)) {
            userSessionService.startSession(request, response.userId());
        }

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}


