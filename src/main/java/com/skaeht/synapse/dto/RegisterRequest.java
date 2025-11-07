package com.skaeht.synapse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username cannot be blank")
        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
        String username,

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 6, max = 40, message = "Password must be between 6 and 40 characters")
        String password,

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Please provide a valid email address")
        String email
) {}