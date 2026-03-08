package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.AuthResponse;
import com.skaeht.synapse.dto.LoginRequest;
import com.skaeht.synapse.dto.RegisterRequest;
import com.skaeht.synapse.security.JwtTokenProvider;
import com.skaeht.synapse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations (login and registration).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Authenticate user and return JWT token.
     *
     * @param loginRequest Login credentials
     * @return JWT token and username
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        // 1️⃣ Find user using email
        var userOptional = userService.findByEmail(loginRequest.email());

        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Invalid email or password", null));
        }

        var user = userOptional.get();

        // 2️⃣ Authenticate using username internally
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        loginRequest.password()));

        // 3️⃣ Set authentication
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 4️⃣ Generate JWT
        String jwt = jwtTokenProvider.generateToken(authentication);

        // 5️⃣ Return token + username
        return ResponseEntity.ok(new AuthResponse(jwt, user.getUsername(), user.getId()));
    }

    /**
     * Register a new user.
     *
     * @param registerRequest User registration details
     * @return Success or error message
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            com.skaeht.synapse.entity.User registered = userService.registerUser(
                    registerRequest.username(),
                    registerRequest.email(),
                    registerRequest.password());

            // Auto-login: authenticate the just-registered user to get JWT
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            registerRequest.username(),
                            registerRequest.password()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtTokenProvider.generateToken(authentication);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(jwt, registered.getUsername(), registered.getId()));

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            // Determine if it's a username or email conflict (409) or other validation
            // (400)
            HttpStatus status = (msg != null && (msg.contains("Username") || msg.contains("Email")))
                    ? HttpStatus.CONFLICT
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(java.util.Map.of("message", msg != null ? msg : "Registration failed"));
        }
    }
}