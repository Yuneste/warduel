package com.warduel.warduel.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameSession - Repräsentiert eine Spielsitzung zwischen zwei Spielern
 * Thread-safe durch synchronized Methoden
 */
@Getter
@Setter
@ToString(exclude = {"player1", "player2"})
public class GameSession {

    /**
     * Game Status Enum
     */
    public enum GameStatus {
        WAITING,    // Wartet auf zweiten Spieler
        READY,      // Beide Spieler bereit, Spiel startet gleich
        RUNNING,    // Spiel läuft
        FINISHED    // Spiel beendet
    }

    // Konstanten
    public static final int GAME_DURATION_SECONDS = 60;

    // Spiel-Identifikation
    private final String gameId;

    // Spieler (volatile für Thread-Sicherheit)
    private volatile Player player1;
    private volatile Player player2;

    // Spiel-Status (volatile für Thread-Sicherheit)
    private volatile GameStatus status;

    // Fragen
    private final List<Question> questions;

    // Zeit-Tracking (volatile für Thread-Sicherheit)
    private volatile LocalDateTime startTime;
    private volatile LocalDateTime endTime;

    // Rematch Flags (volatile für Thread-Sicherheit)
    private volatile boolean player1WantsRematch;
    private volatile boolean player2WantsRematch;

    /**
     * Konstruktor
     */
    public GameSession() {
        this.gameId = UUID.randomUUID().toString();
        this.status = GameStatus.WAITING;
        this.questions = new ArrayList<>();
        this.player1WantsRematch = false;
        this.player2WantsRematch = false;
    }

    /**
     * Thread-safe: Fügt einen Spieler zum Spiel hinzu
     */
    public synchronized boolean addPlayer(Player player) {
        if(player1 == null) {
            player1 = player;
            player.setDisplayName("Spieler 1");
            return true;
        } else if(player2 == null) {
            player2 = player;
            player.setDisplayName("Spieler 2");
            this.status = GameStatus.READY;
            return true;
        }
        return false;
    }

    /**
     * Thread-safe: Entfernt einen Spieler aus dem Spiel
     */
    public synchronized boolean removePlayer(String playerId) {
        if(player1 != null && player1.getPlayerId().equals(playerId)) {
            player1 = null;
            return true;
        } else if(player2 != null && player2.getPlayerId().equals(playerId)) {
            player2 = null;
            return true;
        }
        return false;
    }

    /**
     * Prüft ob das Spiel voll ist
     */
    public boolean isFull() {
        return player1 != null && player2 != null;
    }

    /**
     * Thread-safe: Startet das Spiel
     */
    public synchronized boolean startGame() {
        if(this.status != GameStatus.READY) {
            return false;
        }
        this.status = GameStatus.RUNNING;
        this.startTime = LocalDateTime.now();
        this.endTime = this.startTime.plusSeconds(GAME_DURATION_SECONDS);

        // Reset Fragen-Indizes und Scores für beide Spieler
        if(player1 != null) {
            player1.resetScore();
            player1.resetQuestionIndex();
        }
        if(player2 != null) {
            player2.resetScore();
            player2.resetQuestionIndex();
        }

        return true;
    }

    /**
     * Thread-safe: Beendet das Spiel
     */
    public synchronized void endGame() {
        this.status = GameStatus.FINISHED;
        this.endTime = LocalDateTime.now();
    }

    /**
     * Prüft ob die Zeit abgelaufen ist
     */
    public boolean isTimeUp() {
        if(endTime == null || status != GameStatus.RUNNING) {
            return false;
        }
        return LocalDateTime.now().isAfter(endTime);
    }

    /**
     * Gibt die verbleibenden Sekunden zurück
     */
    public long getRemainingSeconds() {
        if(endTime == null || status != GameStatus.RUNNING) {
            return GAME_DURATION_SECONDS;
        }

        LocalDateTime now = LocalDateTime.now();
        if(now.isAfter(endTime)) {
            return 0;
        }

        return java.time.Duration.between(now, endTime).getSeconds();
    }

    /**
     * Thread-safe: Holt die aktuelle Frage für einen bestimmten Spieler
     */
    public Question getCurrentQuestionForPlayer(Player player) {
        if(player == null) return null;
        return player.getCurrentQuestion();  // ← Nutze Player's eigene Liste!
    }

    /**
     * Bestimmt den Gewinner
     */
    public String determineWinner() {
        if(player1 == null || player2 == null) {
            return "Unbekannt";
        }

        int score1 = player1.getScore();
        int score2 = player2.getScore();

        if(score1 > score2) {
            return player1.getDisplayName();
        } else if(score2 > score1) {
            return player2.getDisplayName();
        } else {
            return "Unentschieden";
        }
    }

    /**
     * Prüft ob das Spiel unentschieden ist
     */
    public boolean isDraw() {
        if(player1 == null || player2 == null) {
            return false;
        }
        return player1.getScore() == player2.getScore();
    }

    /**
     * Setzt Fragen für das Spiel
     */
    public synchronized void setQuestions(List<Question> questions) {
        this.questions.clear();
        this.questions.addAll(questions);
    }

    // Rematch-bezogene Methoden

    /**
     * Setzt Rematch-Flag für Spieler 1
     */
    public synchronized void setPlayer1Rematch(boolean wants) {
        this.player1WantsRematch = wants;
    }

    /**
     * Setzt Rematch-Flag für Spieler 2
     */
    public synchronized void setPlayer2Rematch(boolean wants) {
        this.player2WantsRematch = wants;
    }

    /**
     * Gibt zurück ob Spieler 1 Rematch will
     */
    public synchronized boolean isPlayer1WantsRematch() {
        return this.player1WantsRematch;
    }

    /**
     * Gibt zurück ob Spieler 2 Rematch will
     */
    public synchronized boolean isPlayer2WantsRematch() {
        return this.player2WantsRematch;
    }

    /**
     * Prüft ob beide Spieler Rematch wollen
     */
    public synchronized boolean bothWantRematch() {
        return this.player1WantsRematch && this.player2WantsRematch;
    }

    /**
     * Prüft ob ein bestimmter Spieler Rematch will
     */
    public synchronized boolean doesPlayerWantRematch(String playerId) {
        if(player1 != null && player1.getPlayerId().equals(playerId)) {
            return player1WantsRematch;
        } else if(player2 != null && player2.getPlayerId().equals(playerId)) {
            return player2WantsRematch;
        }
        return false;
    }

    /**
     * Setzt das Spiel für ein Rematch zurück
     */
    public synchronized boolean resetForRematch() {
        if(this.status != GameStatus.FINISHED) {
            return false;
        }

        // Reset Status
        this.status = GameStatus.READY;
        this.startTime = null;
        this.endTime = null;

        // Reset Spieler
        if(player1 != null) {
            player1.resetScore();
            player1.resetQuestionIndex();
        }
        if(player2 != null) {
            player2.resetScore();
            player2.resetQuestionIndex();
        }

        // Reset Rematch-Flags
        this.player1WantsRematch = false;
        this.player2WantsRematch = false;

        // Fragen werden vom Service neu generiert
        this.questions.clear();

        return true;
    }

    // ALLE GETTER AM ENDE WURDEN ENTFERNT - Lombok macht das automatisch!
}