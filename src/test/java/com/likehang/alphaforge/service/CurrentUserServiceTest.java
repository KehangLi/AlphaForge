package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.UnauthorizedException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CurrentUserServiceTest {

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final CurrentUserService service = new CurrentUserService(
            appUserRepository, new UserSessionService(), request
    );

    @Test
    void resolvesUserIdFromExistingSession() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(null, "Guest", AppUserType.GUEST);
        user.setId(userId);
        request.getSession().setAttribute(UserSessionService.USER_ID_ATTRIBUTE, userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service.getCurrentUserId()).isEqualTo(userId);
        verify(appUserRepository).findById(userId);
    }

    @Test
    void returnsCurrentUserDetails() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser("registered@example.com", "Registered User", AppUserType.REGISTERED);
        user.setId(userId);
        request.getSession().setAttribute(UserSessionService.USER_ID_ATTRIBUTE, userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

        var response = service.getCurrentUser();

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.userType()).isEqualTo(AppUserType.REGISTERED);
        assertThat(response.displayName()).isEqualTo("Registered User");
    }

    @Test
    void rejectsMissingSessionWithoutCreatingOne() {
        assertUnauthorized();

        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(appUserRepository);
    }

    @Test
    void rejectsSessionWithoutUserId() {
        request.getSession();

        assertUnauthorized();
        verifyNoInteractions(appUserRepository);
    }

    @Test
    void rejectsInvalidUserIdAttribute() {
        request.getSession().setAttribute(UserSessionService.USER_ID_ATTRIBUTE, "invalid-user-id");

        assertUnauthorized();
        verifyNoInteractions(appUserRepository);
    }

    @Test
    void rejectsSessionWhoseUserWasDeleted() {
        UUID userId = UUID.randomUUID();
        request.getSession().setAttribute(UserSessionService.USER_ID_ATTRIBUTE, userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertUnauthorized();
        verify(appUserRepository).findById(userId);
    }

    private void assertUnauthorized() {
        assertThatThrownBy(service::getCurrentUserId)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("A valid user session is required");
    }
}
