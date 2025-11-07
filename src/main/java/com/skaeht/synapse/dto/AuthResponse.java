package com.skaeht.synapse.dto;

// This is what we send back to the client upon successful login
public record AuthResponse(
        String token,
        String username
) {}