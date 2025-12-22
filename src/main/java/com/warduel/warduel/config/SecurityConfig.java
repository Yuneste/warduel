package com.warduel.warduel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig - Security configuration for MathWars game
 *
 * CURRENT STATE: Permits all requests without authentication
 *
 * This is intentional for the current phase - the game is designed as
 * a stateless, anonymous multiplayer experience where players can join
 * instantly without creating accounts.
 *
 * FUTURE ENHANCEMENTS (when user accounts are added):
 *  - User registration and login endpoints
 *  - JWT token-based authentication
 *  - WebSocket session validation
 *  - Player statistics and leaderboards
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Disable CSRF für API/WebSocket
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()  // Temporär: Erlaube alle Anfragen
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
