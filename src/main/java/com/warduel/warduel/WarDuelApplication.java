package com.warduel.warduel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// No user accounts: skip Spring Security's default in-memory user (and its generated password)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class WarDuelApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarDuelApplication.class, args);
    }

}
