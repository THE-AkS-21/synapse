package com.skaeht.synapse.dto.response;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String displayId) {
}
