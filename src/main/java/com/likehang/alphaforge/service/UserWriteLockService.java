package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserWriteLockService {

    private final AppUserRepository users;

    public UserWriteLockService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AppUser lockUser(UUID userId) {
        // All account and import mutations lock the owner until the caller's transaction ends.
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
