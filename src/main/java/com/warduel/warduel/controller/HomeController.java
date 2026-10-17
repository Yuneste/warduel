package com.warduel.warduel.controller;

import com.warduel.warduel.config.GameConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * HomeController - Liefert die HTML-Seiten aus
 */
@Controller
public class HomeController {

    private final GameConfiguration gameConfig;

    public HomeController(GameConfiguration gameConfig) {
        this.gameConfig = gameConfig;
    }

    /**
     * Zeigt die Hauptseite (Spiel-Interface)
     */
    @GetMapping("/")
    public String index(Model model) {
        // The page shows the real game settings instead of hardcoded numbers
        model.addAttribute("questionsPerGame", gameConfig.getQuestionsPerGame());
        model.addAttribute("durationSeconds", gameConfig.getDurationSeconds());
        model.addAttribute("winScore", gameConfig.hasWinScore() ? gameConfig.getWinScore() : gameConfig.getQuestionsPerGame());
        return "index"; // Gibt templates/index.html zurück
    }

    /**
     * Zeigt die Impressum-Seite (Legal Notice)
     */
    @GetMapping("/impressum")
    public String impressum() {
        return "impressum";
    }

    /**
     * Zeigt die Datenschutzerklärung (Privacy Policy)
     */
    @GetMapping("/datenschutz")
    public String datenschutz() {
        return "datenschutz";
    }
}