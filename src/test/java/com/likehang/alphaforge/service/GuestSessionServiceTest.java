package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.rest.dto.response.GuestSessionResponse;
import com.likehang.alphaforge.security.limits.RateLimitService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestSessionServiceTest {

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final RateLimitService rateLimits = mock(RateLimitService.class);
    private final GuestSessionService service = new GuestSessionService(appUserRepository, rateLimits);
    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void createsGuestWithoutEmail() {
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser guest = invocation.getArgument(0);
            guest.setId(userId);
            return guest;
        });

        GuestSessionResponse result = service.createOrResumeGuest(null, "127.0.0.1");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.userType()).isEqualTo(AppUserType.GUEST);
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
        assertThat(captor.getValue().getUserType()).isEqualTo(AppUserType.GUEST);
    }

    @Test
    void reusesExistingGuest() {
        AppUser guest = new AppUser(null, "Guest", AppUserType.GUEST);
        guest.setId(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(guest));

        GuestSessionResponse result = service.createOrResumeGuest(userId, "127.0.0.1");

        assertThat(result.userId()).isEqualTo(userId);
        verify(appUserRepository, never()).save(any(AppUser.class));
        verify(rateLimits, never()).check(RateLimitService.Rule.GUEST_CREATION_IP, "127.0.0.1");
    }

    @Test
    void createsNewGuestWhenSessionUserWasDeleted() {
        UUID deletedUserId = UUID.randomUUID();
        when(appUserRepository.findById(deletedUserId)).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser guest = invocation.getArgument(0);
            guest.setId(userId);
            return guest;
        });

        assertThat(service.createOrResumeGuest(deletedUserId, "127.0.0.1").userId()).isEqualTo(userId);
        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    void doesNotReplaceRegisteredUserWithGuest() {
        AppUser registeredUser = new AppUser("registered@example.com", "Registered User");
        registeredUser.setId(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(registeredUser));

        assertThatThrownBy(() -> service.createOrResumeGuest(userId, "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A registered user session is already active");
        verify(appUserRepository, never()).save(any(AppUser.class));
    }
}
