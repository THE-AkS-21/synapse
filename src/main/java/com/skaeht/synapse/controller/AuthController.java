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

        // 1. Authenticate the user (checks password)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.username(),
                        loginRequest.password()));

        // 2. Set the authentication in the security context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Generate the JWT
        String jwt = jwtTokenProvider.generateToken(authentication);

        // 4. Send response
        return ResponseEntity.ok(new AuthResponse(jwt, loginRequest.username()));
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
            // Delegate to service layer
            userService.registerUser(
                    registerRequest.username(),
                    registerRequest.email(),
                    registerRequest.password());

            return new ResponseEntity<>("User registered successfully", HttpStatus.CREATED);

        } catch (IllegalArgumentException e) {
            // Handle validation errors (username/email already exists)
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}