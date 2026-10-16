package com.likehang.alphaforge.rest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 15, max = 128) String password,
        @NotBlank @Size(max = 120) String displayName
) {
    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null ? null : displayName.trim();
    }

    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}
