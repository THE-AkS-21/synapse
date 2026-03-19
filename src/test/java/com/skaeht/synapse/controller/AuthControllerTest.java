package com.skaeht.synapse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.request.LoginRequest;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.security.JwtAuthFilter;
import com.skaeht.synapse.security.JwtTokenProvider;
import com.skaeht.synapse.security.UserDetailsImpl;
import com.skaeht.synapse.service.PresenceService;
import com.skaeht.synapse.service.RoomService;
import com.skaeht.synapse.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ARCHITECTURE NOTE: Web Layer Isolation Testing
 * This suite utilizes @WebMvcTest to spin up only the web slice of the application.
 * It validates HTTP routing, JSON serialization/deserialization, and status codes
 * without booting the entire Spring Context or database.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private PresenceService presenceService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private User testUser;
    private Room room1;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(100L).email("test@example.com").username("testUser").build();
        room1 = Room.builder().id("room-A").build();
    }

    /**
     * BEHAVIORAL NOTE: Async Side-Effect Verification
     * The logout endpoint executes the presence broadcast asynchronously to prevent
     * blocking the HTTP response. We use Mockito's timeout() verification instead of
     * Thread.sleep() to safely assert cross-thread behavior without introducing flaky tests.
     */
    @Test
    void logoutUser_Success_BroadcastsOfflineStatus() throws Exception {
        UsernamePasswordAuthenticationToken mockAuth =
                new UsernamePasswordAuthenticationToken("test@example.com", null);

        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(roomService.getUserRooms(100L)).thenReturn(List.of(room1));

        mockMvc.perform(post("/api/auth/logout")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(presenceService, timeout(500).times(1)).userLeftRoom("100", "room-A");
    }

    @Test
    void testLoginUser_Success() throws Exception {
        LoginRequest req = new LoginRequest("test@example.com", "password123");
        String testToken = "test.jwt.token";

        UserDetailsImpl mockUserDetails = new UserDetailsImpl();
        mockUserDetails.setEmail("test@example.com");
        mockUserDetails.setActualUsername("testuser");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mockUserDetails, null);

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(auth)).thenReturn(testToken);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(testToken))
                .andExpect(jsonPath("$.username").value("testuser"));
    }
}