package com.example.url_shortener.auth;

import com.example.url_shortener.auth.dto.AuthResponse;
import com.example.url_shortener.auth.dto.LoginRequest;
import com.example.url_shortener.auth.dto.RegisterRequest;
import com.example.url_shortener.common.exception.EmailAlreadyExistsException;
import com.example.url_shortener.security.JwtService;
import com.example.url_shortener.security.UserPrincipal;
import com.example.url_shortener.user.Role;
import com.example.url_shortener.user.User;
import com.example.url_shortener.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(new UserPrincipal(user));
        return AuthResponse.bearer(token);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + request.email()));

        String token = jwtService.generateToken(new UserPrincipal(user));
        return AuthResponse.bearer(token);
    }
}
