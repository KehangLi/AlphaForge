package com.likehang.alphaforge.service;

import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;
    private final String defaultUserEmail;

    public CurrentUserService(
            AppUserRepository appUserRepository,
            @Value("${alphaforge.dev.user.email:}") String defaultUserEmail
    ) {
        this.appUserRepository = appUserRepository;
        this.defaultUserEmail = defaultUserEmail.trim();
    }

    @Transactional(readOnly = true)
    public UUID getCurrentUserId() {
        if (defaultUserEmail.isBlank()) {
            throw new BadRequestException("Current user is not configured");
        }

        AppUser appUser = appUserRepository.findByEmailIgnoreCase(defaultUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found: " + defaultUserEmail));

        return appUser.getId();
    }
}
