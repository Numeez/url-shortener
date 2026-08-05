package com.example.url_shortener.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.url_shortener.auth.dto.AuthResponse;
import com.example.url_shortener.auth.dto.LoginRequest;
import com.example.url_shortener.auth.dto.RegisterRequest;
import com.example.url_shortener.common.exception.EmailAlreadyExistsException;
import com.example.url_shortener.security.JwtService;
import com.example.url_shortener.user.Role;
import com.example.url_shortener.user.User;
import com.example.url_shortener.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @InjectMocks
    private AuthService authService;

    @Test
    void registerSavesUserWithEncodedPasswordAndUserRole() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "password123");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(jwtService.generateToken(any())).thenReturn("token123");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("token123");
        assertThat(response.tokenType()).isEqualTo("Bearer");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void registerRejectsDuplicateEmailWithoutSaving() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "password123");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginAuthenticatesAndReturnsTokenForKnownUser() {
        LoginRequest request = new LoginRequest("alice@example.com", "password123");
        User user = User.builder()
                .id(1L)
                .email("alice@example.com")
                .passwordHash("hashed-password")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any())).thenReturn("token123");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("token123");
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void loginPropagatesBadCredentialsWithoutIssuingToken() {
        LoginRequest request = new LoginRequest("alice@example.com", "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }
}
