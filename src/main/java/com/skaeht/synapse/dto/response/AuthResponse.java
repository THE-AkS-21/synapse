package com.skaeht.synapse.dto.response;

// This is what we send back to the client upon successful login
public record AuthResponse(
        String token,
        String username,
        Long id
) {}