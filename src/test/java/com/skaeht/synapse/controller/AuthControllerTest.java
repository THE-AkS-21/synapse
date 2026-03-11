
package com.skaeht.synapse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.LoginRequest;
import com.skaeht.synapse.dto.RegisterRequest;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.security.JwtTokenProvider;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import static io.reactivex.rxjava3.core.Single.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static io.reactivex.rxjava3.core.Single.never;

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
    private Room room2;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(100L).username("testUser").build();
        room1 = Room.builder().id("room-A").build();
        room2 = Room.builder().id("room-B").build();
    }

    @Test
    void logoutUser_Success_BroadcastsOfflineStatus() {
        // Arrange
        when(authentication.getName()).thenReturn("testUser");
        when(userService.findByUsername("testUser")).thenReturn(Optional.of(testUser));
        when(roomService.getUserRooms(100L)).thenReturn(List.of(room1, room2));

        // Act
        ResponseEntity<?> response = authController.logoutUser(authentication);

        // Assert
        assertEquals(200, response.getStatusCode().value());

        // Verify that userLeftRoom was called for every room the user is in
        verify(presenceService, times(1)).userLeftRoom("100", "room-A");
        verify(presenceService, times(1)).userLeftRoom("100", "room-B");
    }

    @Test
    void logoutUser_Unauthenticated_ReturnsOkWithoutCrashing() {
        // Act
        ResponseEntity<?> response = authController.logoutUser(null);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        verify(presenceService, org.mockito.Mockito.never()).userLeftRoom(anyString(), anyString());
    }

    @Test
    void testRegisterUser_Success() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "password123", "test@test.com");
        User mockUser = new User();

        when(userService.registerUser("testuser", "test@test.com", "password123")).thenReturn(mockUser);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void testRegisterUser_UsernameTaken() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "password123", "test@test.com");

        when(userService.registerUser("testuser", "test@test.com", "password123"))
                .thenThrow(new IllegalArgumentException("Username is already taken!"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void testLoginUser_Success() throws Exception {
        LoginRequest req = new LoginRequest("test@test.com", "password123");
        String testToken = "test.jwt.token";

        User mockUser = new User();
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@test.com");

        when(userService.findByEmail("test@test.com")).thenReturn(Optional.of(mockUser));

        Authentication auth = mock(Authentication.class);
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