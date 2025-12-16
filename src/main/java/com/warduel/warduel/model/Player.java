package com.warduel.warduel.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.socket.WebSocketSession;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Player - Repräsentiert einen Spieler in einer GameSession
 */
@Getter
@Setter
@ToString(exclude = "session")
public class Player {

    private String playerId;
    private volatile String displayName;
    private WebSocketSession session;
    private final AtomicInteger score = new AtomicInteger(0);

    // Jeder Spieler hat seinen eigenen Fragen-Index
    private final AtomicInteger currentQuestionIndex = new AtomicInteger(0);

    /**
     * Konstruktor für neuen Spieler
     */
    public Player(String playerId, WebSocketSession session, String displayName) {
        this.playerId = playerId;
        this.session = session;
        this.displayName = displayName;
    }

    /**
     * Erhöht den Score um 1
     */
    public void incrementScore() {
        this.score.incrementAndGet();
    }

    /**
     * Setzt Score auf 0 zurück
     */
    public void resetScore() {
        this.score.set(0);
    }

    /**
     * Gibt aktuellen Score zurück
     */
    public int getScore() {
        return this.score.get();
    }

    /**
     * Gibt aktuellen Fragen-Index zurück
     */
    public int getCurrentQuestionIndex() {
        return this.currentQuestionIndex.get();
    }

    /**
     * Geht zur nächsten Frage
     */
    public void nextQuestion() {
        this.currentQuestionIndex.incrementAndGet();
    }

    /**
     * Setzt Fragen-Index auf 0 zurück
     */
    public void resetQuestionIndex() {
        this.currentQuestionIndex.set(0);
    }
}