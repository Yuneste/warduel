package com.warduel.warduel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * GameConfiguration - Zentrale Konfiguration für Spielparameter
 * Ermöglicht einfache Anpassung über application.properties
 * Bereitet vor für zukünftige Game Modes
 */
@Configuration
@ConfigurationProperties(prefix = "game")
@Getter
@Setter
public class GameConfiguration {

    /**
     * Spiel-Dauer in Sekunden
     */
    private int durationSeconds = 60;

    /**
     * Anzahl der Fragen pro Spiel
     */
    private int questionsPerGame = 20;

    /**
     * Punkte-Schwelle für vorzeitigen Sieg
     * (null = kein vorzeitiger Sieg, Spiel läuft bis Zeit abgelaufen)
     */
    private Integer winScore = 20;

    /**
     * Minimale Zahl für Fragen-Generierung
     */
    private int minNumber = 1;

    /**
     * Maximale Zahl für Fragen-Generierung
     */
    private int maxNumber = 20;

    /**
     * Minimale Zahl für Multiplikationsaufgaben
     */
    private int multiplicationMin = 1;

    /**
     * Maximale Zahl für Multiplikationsaufgaben
     */
    private int multiplicationMax = 10;

    /**
     * Minimale Zahl für Divisionsaufgaben
     */
    private int divisionMin = 1;

    /**
     * Maximale Zahl für Divisionsaufgaben
     */
    private int divisionMax = 10;

    /**
     * Prüft ob vorzeitiger Sieg aktiviert ist
     */
    public boolean hasWinScore() {
        return winScore != null && winScore > 0;
    }
}
