package com.warduel.warduel.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

/**
 * GameSession - Repräsentiert eine Spielsitzung zwischen zwei Spielern
 * Thread-safe durch synchronized Methoden
 */
@Getter
@Setter
@ToString(exclude = {"player1", "player2", "scheduledTasks", "rematchLock"})
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

    // Spiel-Identifikation
    private final String gameId;

    // Spiel-Konfiguration (wird von GameService gesetzt)
    private int durationSeconds = 60;  // Default-Wert

    // Spieler (volatile für Thread-Sicherheit)
    private volatile Player player1;
    private volatile Player player2;

    // Spiel-Status (volatile für Thread-Sicherheit)
    private volatile GameStatus status;

    // Number of rounds started so far (1 = first match, 2+ = rematches)
    private volatile int round;

    // Code of a private game shared by link; null for public matchmaking
    private volatile String roomCode;

    // Kind of problems for this game (kept for rematches)
    private volatile ChallengeType challengeType = ChallengeType.CLASSIC;

    // Zeit-Tracking (volatile für Thread-Sicherheit)
    private volatile LocalDateTime startTime;
    private volatile LocalDateTime endTime;

    // Rematch Flags (volatile für Thread-Sicherheit)
    private volatile boolean player1WantsRematch;
    private volatile boolean player2WantsRematch;

    // Countdown, game timer and connection monitor of the current round; cancelled when the round ends
    private final List<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<>();

    // Serializes rematch requests so "waiting" and "starting" messages go out in a consistent order
    private final Object rematchLock = new Object();

    /**
     * Konstruktor
     */
    public GameSession() {
        this.gameId = UUID.randomUUID().toString();
        this.status = GameStatus.WAITING;
        this.player1WantsRematch = false;
        this.player2WantsRematch = false;
    }

    /**
     * Thread-safe: Fügt einen Spieler zum Spiel hinzu
     */
    public synchronized boolean addPlayer(Player player) {
        if(player1 == null) {
            player1 = player;
            nameOrDefault(player, "Player 1");
            return true;
        } else if(player2 == null) {
            player2 = player;
            nameOrDefault(player, "Player 2");
            // Two players with the same nickname would be indistinguishable on screen
            if(player.getDisplayName().equalsIgnoreCase(player1.getDisplayName())) {
                player.setDisplayName(player.getDisplayName() + " (2)");
            }
            this.status = GameStatus.READY;
            return true;
        }
        return false;
    }

    private static void nameOrDefault(Player player, String fallback) {
        if(player.getDisplayName() == null || player.getDisplayName().isBlank()) {
            player.setDisplayName(fallback);
        }
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
        this.round++;
        this.startTime = LocalDateTime.now();
        this.endTime = this.startTime.plusSeconds(durationSeconds);

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
     * @return true only for the call that actually finished the game (later calls are no-ops)
     */
    public synchronized boolean endGame() {
        if(this.status == GameStatus.FINISHED) {
            return false;
        }
        this.status = GameStatus.FINISHED;
        this.endTime = LocalDateTime.now();
        cancelScheduledTasks();
        return true;
    }

    /**
     * Registers a task belonging to the current round so it can be cancelled when the round ends
     */
    public void addScheduledTask(ScheduledFuture<?> task) {
        scheduledTasks.add(task);
    }

    /**
     * Cancels all pending tasks of the current round (countdown, game timer, connection monitor)
     */
    public void cancelScheduledTasks() {
        scheduledTasks.forEach(task -> task.cancel(false));
        scheduledTasks.clear();
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
            return durationSeconds;
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
        return player.getCurrentQuestion();
    }

    /**
     * Bestimmt den Gewinner
     */
    public String determineWinner() {
        if(player1 == null || player2 == null) {
            return "Unknown";
        }

        int score1 = player1.getScore();
        int score2 = player2.getScore();

        if(score1 > score2) {
            return player1.getDisplayName();
        } else if(score2 > score1) {
            return player2.getDisplayName();
        } else {
            return "Draw";
        }
    }

    public boolean isPrivate() {
        return roomCode != null;
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

        return true;
    }
}