package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.request.LoginRequest;
import com.skaeht.synapse.dto.request.RegisterRequest;
import com.skaeht.synapse.dto.response.AuthResponse;
import com.skaeht.synapse.dto.response.UserProfileResponse;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.security.JwtTokenProvider;
import com.skaeht.synapse.security.UserDetailsImpl;
import com.skaeht.synapse.service.PresenceService;
import com.skaeht.synapse.service.RoomService;
import com.skaeht.synapse.service.UserService;
import com.skaeht.synapse.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final PresenceService presenceService;
    private final RoomService roomService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = tokenProvider.generateToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            log.info("User {} logged in successfully", userDetails.getEmail());
            return ResponseEntity.ok(new AuthResponse(jwt, userDetails.getActualUsername(), userDetails.getId()));

        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for email: {}", loginRequest.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password"));
        } catch (Exception e) {
            log.error("Unexpected login error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<UserProfileResponse> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("Registering new user with email: {}", registerRequest.email());

        User user = userService.registerUser(
                registerRequest.username(),
                registerRequest.email(),
                registerRequest.password()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayId())
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(Authentication authentication) {
        try {
            String email = SecurityUtil.getCurrentUserEmail(authentication);

            userService.findByEmail(email).ifPresent(user -> {
                /*
                 * ARCHITECTURE NOTE:
                 * Using CompletableFuture here prevents the HTTP response from blocking while Redis updates.
                 * Future Iteration: Move this logic entirely into an @Async method inside PresenceService
                 * to keep the Controller strictly focused on HTTP routing.
                 */
                CompletableFuture.runAsync(() -> {
                    roomService.getUserRooms(user.getId()).forEach(room ->
                            presenceService.userLeftRoom(user.getId().toString(), room.getId())
                    );
                    log.info("Offline status broadcasted async for user email: {}", email);
                });
            });
        } catch (IllegalStateException e) {
            log.debug("Logout called with empty security context");
        } finally {
            // Always clear context regardless of presence broadcast success
            SecurityContextHolder.clearContext();
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}