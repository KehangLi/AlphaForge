package com.likehang.alphaforge.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class UserSessionService {

    public static final String USER_ID_ATTRIBUTE = "alphaforge.userId";

    public UUID getUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
        return userId instanceof UUID id ? id : null;
    }

    public void startSession(HttpServletRequest request, UUID userId) {
        Objects.requireNonNull(userId, "User id is required");

        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID_ATTRIBUTE, userId);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.toString());
    }

    public boolean endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }

        session.invalidate();
        return true;
    }
}
