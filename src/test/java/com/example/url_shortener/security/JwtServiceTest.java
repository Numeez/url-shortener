package com.example.url_shortener.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

    private static final String SECRET = "Cy2pIXWar+ZLwVIR2V/D4pC/vGAvHg8hR4FrgJx8Ruw=";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void generatedTokenCarriesUsernameAsSubject() {
        UserDetails user = userDetails("alice@example.com");

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice@example.com");
    }

    @Test
    void tokenIsValidForTheUserItWasIssuedTo() {
        UserDetails user = userDetails("alice@example.com");

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void tokenIsInvalidForADifferentUser() {
        UserDetails alice = userDetails("alice@example.com");
        UserDetails bob = userDetails("bob@example.com");

        String token = jwtService.generateToken(alice);

        assertThat(jwtService.isTokenValid(token, bob)).isFalse();
    }

    @Test
    void expiredTokenFailsToParse() {
        JwtService shortLivedJwtService = new JwtService(SECRET, -1_000);
        UserDetails user = userDetails("alice@example.com");

        String token = shortLivedJwtService.generateToken(user);

        assertThatThrownBy(() -> shortLivedJwtService.isTokenValid(token, user))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private static UserDetails userDetails(String email) {
        return User.withUsername(email).password("irrelevant").authorities("ROLE_USER").build();
    }
}
