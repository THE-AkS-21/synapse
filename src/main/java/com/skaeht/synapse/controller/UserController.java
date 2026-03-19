package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.request.PasswordUpdateRequest;
import com.skaeht.synapse.dto.response.UserProfileResponse;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.exception.ResourceNotFoundException;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.service.UserService;
import com.skaeht.synapse.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        String email = SecurityUtil.getCurrentUserEmail(authentication);

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return ResponseEntity.ok(new UserProfileResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getDisplayId()));
    }

    /**
     * Looks up a user by display ID. Exposed for invite validation.
     * Returns minimal data (username only) to prevent data leakage.
     */
    @GetMapping("/by-display-id/{displayId}")
    public ResponseEntity<Map<String, String>> getUserByDisplayId(@PathVariable String displayId) {
        User user = userService.findByDisplayId(displayId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return ResponseEntity.ok(Map.of("username", user.getUsername()));
    }

    @PutMapping("/me/password")
    public ResponseEntity<String> updatePassword(Authentication authentication,
                                                 @Valid @RequestBody PasswordUpdateRequest request) {
        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Incorrect current password");
        }

        boolean updated = userService.updatePassword(email, request.newPassword());
        return updated
                ? ResponseEntity.ok("Password updated successfully")
                : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update password");
    }

    @PutMapping("/me/username")
    public ResponseEntity<String> updateUsername(Authentication authentication,
                                                 @RequestBody Map<String, String> request) {
        String newUsername = request.get("username");
        if (newUsername == null || newUsername.trim().length() < 3) {
            return ResponseEntity.badRequest().body("Username must be at least 3 characters");
        }

        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Short-circuit to save a DB write if the name hasn't changed
        if (user.getUsername().equalsIgnoreCase(newUsername.trim())) {
            return ResponseEntity.ok("Username is already " + newUsername);
        }

        boolean updated = userService.updateUsername(email, newUsername.trim());
        return updated
                ? ResponseEntity.ok("Username updated successfully")
                : ResponseEntity.status(HttpStatus.CONFLICT).body("Username already taken");
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserProfileResponse>> searchUsers(@RequestParam String query) {
        List<UserProfileResponse> safeUsers = userRepository.findByUsernameContainingIgnoreCase(query)
                .stream()
                .map(user -> new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayId()))
                .toList();

        return ResponseEntity.ok(safeUsers);
    }
}