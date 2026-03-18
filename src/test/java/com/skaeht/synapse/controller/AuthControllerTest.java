package com.skaeht.synapse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.request.LoginRequest;
import com.skaeht.synapse.dto.request.RegisterRequest;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.security.JwtTokenProvider;
import com.skaeht.synapse.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.skaeht.synapse.service.UserService;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import com.skaeht.synapse.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.skaeht.synapse.service.PresenceService;
import com.skaeht.synapse.service.RoomService;
import org.mockito.Mock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import static org.mockito.ArgumentMatchers.any;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, com.skaeht.synapse.security.JwtAuthFilter.class })
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationManager authenticationManager;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private com.skaeht.synapse.security.UserDetailsServiceImpl userDetailsService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private RoomService roomService;
    @MockitoBean
    private PresenceService presenceService;

    @Mock
    private Authentication authentication;

    @Autowired
    private AuthController authController;

    private User testUser;
    private Room room1;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(100L).email("test@example.com").username("testUser").build();
        room1 = Room.builder().id("room-A").build();
    }

    @Test
    void logoutUser_Success_BroadcastsOfflineStatus() throws Exception {
        when(authentication.getName()).thenReturn("test@example.com");
        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(roomService.getUserRooms(100L)).thenReturn(List.of(room1));

        ResponseEntity<?> response = authController.logoutUser(authentication);
        assertEquals(200, response.getStatusCode().value());

        // Because broadcasting is multithreaded (CompletableFuture), add a short sleep in the test to verify async code
        Thread.sleep(100);
        verify(presenceService, times(1)).userLeftRoom("100", "room-A");
    }

    @Test
    void testLoginUser_Success() throws Exception {
        LoginRequest req = new LoginRequest("test@example.com", "password123");
        String testToken = "test.jwt.token";

        UserDetailsImpl mockUserDetails = new UserDetailsImpl();
        mockUserDetails.setEmail("test@example.com");
        mockUserDetails.setActualUsername("testuser");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUserDetails);
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