package com.warduel.warduel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration Test - Testet ob die Anwendung korrekt startet
 */
@SpringBootTest
@TestPropertySource(properties = {
        "server.port=0" // Random port für Tests
})
class WarDuelIntegrationTest {

    /**
     * Test: Application Context lädt erfolgreich
     */
    @Test
    void contextLoads() {
        // Wenn dieser Test durchläuft, ist die Spring-Konfiguration korrekt
    }
}