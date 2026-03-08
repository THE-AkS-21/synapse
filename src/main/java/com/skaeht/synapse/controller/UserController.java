package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.PasswordUpdateRequest;
import com.skaeht.synapse.dto.UserProfileResponse;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Get the current authenticated user's profile (includes displayId). */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        Optional<User> userOpt = userService.findByUsername(username);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        User user = userOpt.get();
        return ResponseEntity.ok(new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayId()));
    }

    /**
     * Look up a user by their display ID — used to validate invite targets.
     * Returns only the username (no sensitive data).
     */
    @GetMapping("/by-display-id/{displayId}")
    public ResponseEntity<Map<String, String>> getUserByDisplayId(@PathVariable String displayId) {
        Optional<User> userOpt = userService.findByDisplayId(displayId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Only expose the username, not email or internal ID
        return ResponseEntity.ok(Map.of("username", userOpt.get().getUsername()));
    }

    /** Update password. */
    @PutMapping("/me/password")
    public ResponseEntity<String> updatePassword(Authentication authentication,
            @Valid @RequestBody PasswordUpdateRequest request) {
        String username = authentication.getName();
        Optional<User> userOpt = userService.findByUsername(username);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Incorrect current password");
        }

        boolean updated = userService.updatePassword(username, request.newPassword());
        if (updated) {
            return ResponseEntity.ok("Password updated successfully");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update password");
        }
    }

    /** Update username. */
    @PutMapping("/me/username")
    public ResponseEntity<String> updateUsername(Authentication authentication,
            @RequestBody Map<String, String> request) {
        String newUsername = request.get("username");
        if (newUsername == null || newUsername.trim().length() < 3) {
            return ResponseEntity.badRequest().body("Username must be at least 3 characters");
        }
        String currentUsername = authentication.getName();
        boolean updated = userService.updateUsername(currentUsername, newUsername.trim());
        if (updated) {
            return ResponseEntity.ok("Username updated successfully");
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already taken");
        }
    }
}
