package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.UnauthorizedException;
import com.likehang.alphaforge.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;
    private final UserSessionService userSessionService;
    private final HttpServletRequest request;

    public CurrentUserService(
            AppUserRepository appUserRepository,
            UserSessionService userSessionService,
            HttpServletRequest request
    ) {
        this.appUserRepository = appUserRepository;
        this.userSessionService = userSessionService;
        this.request = request;
    }

    @Transactional(readOnly = true)
    public UUID getCurrentUserId() {
        UUID userId = userSessionService.getUserId(request);
        if (userId == null || !appUserRepository.existsById(userId)) {
            throw new UnauthorizedException("A valid user session is required");
        }

        return userId;
    }
}
