package com.skaeht.synapse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(
        @NotBlank(message = "Current password cannot be blank") String currentPassword,

        @NotBlank(message = "New password cannot be blank") @Size(min = 6, message = "New password must be at least 6 characters") String newPassword) {
}
