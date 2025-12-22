package com.warduel.warduel.service;

import com.warduel.warduel.config.GameConfiguration;
import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.Player;
import com.warduel.warduel.model.Question;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameService - Verwaltet alle laufenden Spielsessions
 * Hauptlogik für Matchmaking, Spielverwaltung und Spieleraktionen
 */
@Service
@Slf4j
public class GameService {

    private final QuestionGeneratorService questionGenerator;
    private final GameConfiguration gameConfig;

    // Map: PlayerId -> GameSession (um schnell das Spiel eines Spielers zu finden)
    private final Map<String, GameSession> playerToGame = new ConcurrentHashMap<>();

    // Warteschlange für Spieler die ein Spiel suchen
    private GameSession waitingGame = null;

    public GameService(QuestionGeneratorService questionGenerator, GameConfiguration gameConfig) {
        this.questionGenerator = questionGenerator;
        this.gameConfig = gameConfig;
    }

    /**
     * Spieler tritt einem Spiel bei (Matchmaking)
     * Entweder wird er zum wartenden Spiel hinzugefügt, oder ein neues erstellt
     */
    public synchronized GameSession joinGame(WebSocketSession session) {
        String playerId = session.getId();

        // Prüfe ob Spieler bereits in einem Spiel ist
        if(playerToGame.containsKey(playerId)) {
            log.warn("Player {} already in a game", playerId);
            return playerToGame.get(playerId);
        }

        Player player = new Player(playerId, session, "");

        // Gibt es ein wartendes Spiel?
        // CRITICAL: Skip finished/running games (only join WAITING games)
        if(waitingGame != null &&
           waitingGame.getStatus() == GameSession.GameStatus.WAITING &&
           !waitingGame.isFull()) {
            // Füge Spieler zum wartenden Spiel hinzu
            boolean added = waitingGame.addPlayer(player);

            if(added) {
                log.info("Player {} joined waiting game {}", playerId, waitingGame.getGameId());
                playerToGame.put(playerId, waitingGame);

                // Spiel ist jetzt voll, starte es
                if(waitingGame.isFull()) {
                    prepareGame(waitingGame);
                    GameSession fullGame = waitingGame;
                    waitingGame = null;
                    return fullGame;
                }
                return waitingGame;
            }
        }

        // Erstelle neues Spiel und setze es als wartendes Spiel
        GameSession newGame = new GameSession();
        newGame.addPlayer(player);

        playerToGame.put(playerId, newGame);
        waitingGame = newGame;

        log.info("player {} created new game {} and is waiting", playerId, newGame.getGameId());

        return newGame;
    }

    /**
     * Bereitet Spiel vor und startet es
     */
    private void prepareGame(GameSession game) {
        // Setze Spiel-Konfiguration
        game.setDurationSeconds(gameConfig.getDurationSeconds());

        // Generiere ZWEI verschiedene Fragenlisten
        List<Question> questionsP1 = questionGenerator.generateQuestions(gameConfig.getQuestionsPerGame());
        List<Question> questionsP2 = questionGenerator.generateQuestions(gameConfig.getQuestionsPerGame());

        // Gib jedem Spieler seine eigenen Fragen
        game.getPlayer1().setQuestions(questionsP1);
        game.getPlayer2().setQuestions(questionsP2);

        // Game-Liste = Player 1 Fragen (für Kompatibilität)
        game.setQuestions(questionsP1);

        game.startGame();

        log.info("Game {} started - Duration: {}s, P1: {} questions, P2: {} questions",
                game.getGameId(), gameConfig.getDurationSeconds(), questionsP1.size(), questionsP2.size());
    }

    /**
     * Verarbeitet die Antwort eines Spielers
     * @return true wenn Antwort korrekt war
     */
    public boolean submitAnswer(String playerId, int answer) {
        GameSession game = playerToGame.get(playerId);
        if(game == null || game.getStatus() != GameSession.GameStatus.RUNNING) {
            return false;
        }

        // Finde den Spieler
        Player player = null;
        if(game.getPlayer1() != null && game.getPlayer1().getPlayerId().equals(playerId)) {
            player = game.getPlayer1();
        } else if(game.getPlayer2() != null && game.getPlayer2().getPlayerId().equals(playerId)) {
            player = game.getPlayer2();
        }

        if(player == null) {
            return false;
        }

        // Hole aktuelle Frage für diesen Spieler
        Question currentQuestion = game.getCurrentQuestionForPlayer(player);
        if(currentQuestion == null) {
            return false;
        }

        return currentQuestion.isCorrect(answer);
    }

    /**
     * Holt das Spiel eines Spielers
     */
    public GameSession getGameByPlayerId(String playerId) {
        return playerToGame.get(playerId);
    }

    /**
     * Entfernt einen Spieler aus seinem Spiel
     */
    public synchronized void removePlayer(String playerId) {
        GameSession game = playerToGame.get(playerId);

        if(game != null) {
            boolean removed = game.removePlayer(playerId);  // ← BENUTZE den Return-Wert
            playerToGame.remove(playerId);

            // CRITICAL: Clear waitingGame if it's this game (prevents ghost matchmaking)
            if(waitingGame == game) {
                log.info("Clearing waitingGame {} after player {} removed", game.getGameId(), playerId);
                waitingGame = null;
            }

            if(removed) {
                log.info("Player {} removed from game {}", playerId, game.getGameId());
            }

            // Wenn Spiel leer ist, entferne es
            if(game.getPlayer1() == null && game.getPlayer2() == null) {
                if(waitingGame == game) {
                    waitingGame = null;
                }
                log.info("Game {} removed (empty)", game.getGameId());
            }
        }
    }

    /**
     * Verarbeitet Rematch-Anfrage
     */
    public synchronized boolean requestRematch(String playerId) {
        GameSession game = playerToGame.get(playerId);

        if(game == null || game.getStatus() != GameSession.GameStatus.FINISHED) {
            log.warn("Cannot rematch: game={}, status={}",
                    game != null ? game.getGameId() : "null",
                    game != null ? game.getStatus() : "null");
            return false;
        }

        // Setze Rematch-Flag für diesen Spieler
        if(game.getPlayer1() != null && game.getPlayer1().getPlayerId().equals(playerId)) {
            game.setPlayer1Rematch(true);
            log.info("Player 1 ({}) wants rematch", playerId);
        } else if(game.getPlayer2() != null && game.getPlayer2().getPlayerId().equals(playerId)) {
            game.setPlayer2Rematch(true);
            log.info("Player 2 ({}) wants rematch", playerId);
        }

        log.info("Rematch status for game {}: P1={}, P2={}",
                game.getGameId(), game.isPlayer1WantsRematch(), game.isPlayer2WantsRematch());

        // Wenn beide wollen, starte Rematch
        if(game.bothWantRematch()) {
            log.info("Both players want rematch! Resetting game {}", game.getGameId());

            boolean success = game.resetForRematch();
            if(success) {
                log.info("Game {} reset successfully. Preparing new game...", game.getGameId());
                prepareGame(game);
                log.info("Rematch started for game {}", game.getGameId());
                return true;
            } else {
                log.error("Failed to reset game {} for rematch", game.getGameId());
            }
        }

        return false;
    }

    /**
     * Holt den Gegner eines Spielers
     */
    public Player getOpponent(GameSession game, String playerId) {
        if(game.getPlayer1() != null && game.getPlayer1().getPlayerId().equals(playerId)) {
            return game.getPlayer2();
        }
        if(game.getPlayer2() != null && game.getPlayer2().getPlayerId().equals(playerId)) {
            return game.getPlayer1();
        }
        return null;
    }
}