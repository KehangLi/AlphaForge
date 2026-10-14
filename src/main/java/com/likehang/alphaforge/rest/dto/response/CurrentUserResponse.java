package com.likehang.alphaforge.rest.dto.response;

import com.likehang.alphaforge.model.entity.AppUserType;

import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        AppUserType userType,
        String displayName
) {
}
