package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.exception.UnauthorizedException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.rest.dto.request.LoginRequest;
import com.likehang.alphaforge.rest.dto.request.RegisterRequest;
import com.likehang.alphaforge.rest.dto.response.CurrentUserResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public CurrentUserResponse register(RegisterRequest request) {
        if (appUserRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email is already registered");
        }

        AppUser user = new AppUser(request.email(), request.displayName(), AppUserType.REGISTERED);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            return toResponse(appUserRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // The unique index also protects against concurrent registrations.
            if (isDuplicateEmail(exception)) {
                throw new ConflictException("Email is already registered");
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
            );
        } catch (AuthenticationException exception) {
            throw new UnauthorizedException("Invalid email or password");
        }

        UUID userId = UUID.fromString(authentication.getName());
        return appUserRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    }

    private boolean isDuplicateEmail(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return "idx_app_user_email".equals(violation.getConstraintName())
                        || "idx_app_user_email_normalized".equals(violation.getConstraintName());
            }
        }
        return false;
    }

    private CurrentUserResponse toResponse(AppUser user) {
        return new CurrentUserResponse(user.getId(), user.getUserType(), user.getDisplayName());
    }
}
