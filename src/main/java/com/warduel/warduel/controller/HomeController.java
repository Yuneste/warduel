package com.warduel.warduel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * HomeController - Liefert die HTML-Seiten aus
 */
@Controller
public class HomeController {

    /**
     * Zeigt die Hauptseite (Spiel-Interface)
     */
    @GetMapping("/")
    public String index() {
        return "index"; // Gibt templates/index.html zurück
    }
}