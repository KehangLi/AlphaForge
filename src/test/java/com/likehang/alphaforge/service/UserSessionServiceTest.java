package com.likehang.alphaforge.service;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserSessionServiceTest {

    private final UserSessionService service = new UserSessionService();

    @Test
    void doesNotCreateSessionWhenReadingMissingUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(service.getUserId(request)).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void rejectsUnexpectedSessionAttributeType() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserSessionService.USER_ID_ATTRIBUTE, "invalid-user-id");

        assertThat(service.getUserId(request)).isNull();
    }

    @Test
    void replacesOldSessionWhenEstablishingUserIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession oldSession = (MockHttpSession) request.getSession();
        String oldSessionId = oldSession.getId();
        UUID userId = UUID.randomUUID();
        oldSession.setAttribute(UserSessionService.USER_ID_ATTRIBUTE, userId);
        oldSession.setAttribute("oldAttribute", "oldValue");

        service.startSession(request, userId);

        HttpSession newSession = request.getSession(false);
        assertThat(oldSession.isInvalid()).isTrue();
        assertThat(newSession.getId()).isNotEqualTo(oldSessionId);
        assertThat(newSession.getAttribute("oldAttribute")).isNull();
        assertThat(service.getUserId(request)).isEqualTo(userId);
        assertThat(newSession.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
                .isEqualTo(userId.toString());
    }

    @Test
    void invalidatesExistingSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();

        assertThat(service.endSession(request)).isTrue();
        assertThat(session.isInvalid()).isTrue();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void doesNotCreateSessionWhenEndingMissingSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(service.endSession(request)).isFalse();
        assertThat(request.getSession(false)).isNull();
    }
}
