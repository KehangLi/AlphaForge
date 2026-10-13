package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.rest.dto.response.GuestSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GuestSessionService {

    private static final Logger log = LoggerFactory.getLogger(GuestSessionService.class);

    private final AppUserRepository appUserRepository;

    public GuestSessionService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public GuestSessionResponse createOrResumeGuest(UUID existingUserId) {
        if (existingUserId != null) {
            AppUser existingUser = appUserRepository.findById(existingUserId).orElse(null);
            if (existingUser != null) {
                if (existingUser.getUserType() != AppUserType.GUEST) {
                    throw new ConflictException("A registered user session is already active");
                }
                return toResponse(existingUser);
            }
        }

        AppUser guest = appUserRepository.save(new AppUser(null, "Guest", AppUserType.GUEST));
        log.info("Created guest user: user={}", guest.getId());
        return toResponse(guest);
    }

    private GuestSessionResponse toResponse(AppUser user) {
        return new GuestSessionResponse(user.getId(), user.getUserType(), user.getDisplayName());
    }
}
