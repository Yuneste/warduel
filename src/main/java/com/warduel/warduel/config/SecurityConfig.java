package com.warduel.warduel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig - Temporäre Security-Konfiguration
 *
 * WICHTIG: Diese Konfiguration erlaubt aktuell alle Anfragen ohne Authentifizierung
 * um die bestehende Funktionalität nicht zu beeinträchtigen.
 *
 * TODO: Morgen korrekte Authentifizierung implementieren für:
 *  - User Login/Registration
 *  - JWT Token-basierte Auth
 *  - WebSocket Security
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
