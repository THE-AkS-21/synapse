package com.skaeht.synapse.dto;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String displayId) {
}
