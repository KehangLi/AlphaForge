package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.UnauthorizedException;
import com.likehang.alphaforge.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
        request.getSession().setAttribute(UserSessionService.USER_ID_ATTRIBUTE, userId);
        when(appUserRepository.existsById(userId)).thenReturn(true);

        assertThat(service.getCurrentUserId()).isEqualTo(userId);
        verify(appUserRepository).existsById(userId);
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
        when(appUserRepository.existsById(userId)).thenReturn(false);

        assertUnauthorized();
        verify(appUserRepository).existsById(userId);
    }

    private void assertUnauthorized() {
        assertThatThrownBy(service::getCurrentUserId)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("A valid user session is required");
    }
}
