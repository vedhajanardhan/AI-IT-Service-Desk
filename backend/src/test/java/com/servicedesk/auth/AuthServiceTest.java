package com.servicedesk.auth;

import com.servicedesk.auth.dto.AuthResponse;
import com.servicedesk.auth.dto.LoginRequest;
import com.servicedesk.auth.dto.RegisterRequest;
import com.servicedesk.common.exception.DuplicateResourceException;
import com.servicedesk.security.JwtService;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import com.servicedesk.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the spec's required "Login" test flow,
 * plus registration and its duplicate-email guard.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtService
        );
    }

    @Test
    void register_createsUserWithHashedPasswordAndReturnsTokens() {

        RegisterRequest request = new RegisterRequest(
                "New.User@Company.com",
                "supersecret1",
                "New User",
                Role.EMPLOYEE,
                "IT"
        );

        /*
         * AuthService checks for an existing user using the email
         * exactly as it appears in the registration request.
         */
        when(userRepository.existsByEmail("New.User@Company.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("supersecret1"))
                .thenReturn("hashed-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> {
                    User user = inv.getArgument(0);
                    user.setId(UUID.randomUUID());
                    return user;
                });

        when(jwtService.generateAccessToken(any()))
                .thenReturn("access-token");

        when(jwtService.generateRefreshToken(any()))
                .thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertThat(response.email())
                .isEqualTo("new.user@company.com");

        assertThat(response.accessToken())
                .isEqualTo("access-token");

        assertThat(response.refreshToken())
                .isEqualTo("refresh-token");

        verify(passwordEncoder)
                .encode("supersecret1");

        /*
         * The user stored in the database should contain:
         * 1. the normalized lowercase email
         * 2. the encoded password
         */
        verify(userRepository).save(argThat(user ->
                user.getPasswordHash().equals("hashed-password")
                        && user.getEmail().equals("new.user@company.com")
        ));
    }

    @Test
    void register_rejectsDuplicateEmail() {

        RegisterRequest request = new RegisterRequest(
                "existing@company.com",
                "supersecret1",
                "Existing User",
                Role.EMPLOYEE,
                null
        );

        when(userRepository.existsByEmail("existing@company.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never())
                .save(any());
    }

    @Test
    void login_authenticatesAndReturnsTokensForValidCredentials() {

        User user = User.builder()
                .email("engineer@company.com")
                .fullName("Evan Engineer")
                .role(Role.ENGINEER)
                .build();

        user.setId(UUID.randomUUID());

        when(userRepository.findByEmail("engineer@company.com"))
                .thenReturn(Optional.of(user));

        when(jwtService.generateAccessToken(user))
                .thenReturn("access-token");

        when(jwtService.generateRefreshToken(user))
                .thenReturn("refresh-token");

        AuthResponse response = authService.login(
                new LoginRequest(
                        "engineer@company.com",
                        "correct-password"
                )
        );

        assertThat(response.userId())
                .isEqualTo(user.getId());

        assertThat(response.role())
                .isEqualTo(Role.ENGINEER);

        assertThat(response.accessToken())
                .isEqualTo("access-token");

        verify(authenticationManager)
                .authenticate(any());
    }

    @Test
    void login_propagatesBadCredentialsForWrongPassword() {

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any());

        assertThatThrownBy(() ->
                authService.login(
                        new LoginRequest(
                                "engineer@company.com",
                                "wrong-password"
                        )
                ))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtService);
    }
}