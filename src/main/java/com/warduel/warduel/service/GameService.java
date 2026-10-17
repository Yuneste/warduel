package com.warduel.warduel.service;

import com.warduel.warduel.config.GameConfiguration;
import com.warduel.warduel.model.ChallengeType;
import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.Player;
import com.warduel.warduel.model.PlayerNames;
import com.warduel.warduel.model.Question;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.security.SecureRandom;
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

    // Private games waiting for the invited friend, by room code
    private final Map<String, GameSession> privateRooms = new ConcurrentHashMap<>();
    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I/L
    private static final int ROOM_CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    public GameService(QuestionGeneratorService questionGenerator, GameConfiguration gameConfig) {
        this.questionGenerator = questionGenerator;
        this.gameConfig = gameConfig;
    }

    /**
     * Spieler tritt einem Spiel bei (Matchmaking)
     * Entweder wird er zum wartenden Spiel hinzugefügt, oder ein neues erstellt
     */
    public synchronized GameSession joinGame(WebSocketSession session) {
        return joinGame(session, null);
    }

    /**
     * Public matchmaking with a chosen nickname (null or blank gets "Player 1"/"Player 2")
     */
    public synchronized GameSession joinGame(WebSocketSession session, String playerName) {
        String playerId = session.getId();

        // Prüfe ob Spieler bereits in einem Spiel ist
        if(playerToGame.containsKey(playerId)) {
            log.warn("Player {} already in a game", playerId);
            return playerToGame.get(playerId);
        }

        Player player = new Player(playerId, session, PlayerNames.sanitize(playerName));

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
        // Public games get a random challenge type so the queue never splits by preference
        GameSession newGame = new GameSession();
        ChallengeType fixed = ChallengeType.parse(gameConfig.getPublicChallengeType());
        newGame.setChallengeType(fixed != null ? fixed : ChallengeType.random());
        newGame.addPlayer(player);

        playerToGame.put(playerId, newGame);
        waitingGame = newGame;

        log.info("player {} created new game {} and is waiting", playerId, newGame.getGameId());

        return newGame;
    }

    /**
     * Opens a private game that only the friend with the room code can join
     */
    public synchronized GameSession createPrivateGame(WebSocketSession session, String playerName) {
        return createPrivateGame(session, playerName, null);
    }

    /**
     * @param challengeType the host's choice, or null for a random one
     */
    public synchronized GameSession createPrivateGame(WebSocketSession session, String playerName, ChallengeType challengeType) {
        String playerId = session.getId();
        if(playerToGame.containsKey(playerId)) {
            return playerToGame.get(playerId);
        }

        String code;
        do {
            StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
            for(int i = 0; i < ROOM_CODE_LENGTH; i++) {
                sb.append(ROOM_CODE_ALPHABET.charAt(random.nextInt(ROOM_CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while(privateRooms.containsKey(code));

        GameSession game = new GameSession();
        game.setRoomCode(code);
        game.setChallengeType(challengeType != null ? challengeType : ChallengeType.random());
        game.addPlayer(new Player(playerId, session, PlayerNames.sanitize(playerName)));
        privateRooms.put(code, game);
        playerToGame.put(playerId, game);
        log.info("Player {} opened private game {} (room {})", playerId, game.getGameId(), code);
        return game;
    }

    /**
     * Joins a friend's private game
     * @return the now full game, or null if the code is unknown, expired or the game is already full
     */
    public synchronized GameSession joinPrivateGame(WebSocketSession session, String playerName, String roomCode) {
        String playerId = session.getId();
        if(playerToGame.containsKey(playerId)) {
            return playerToGame.get(playerId);
        }

        GameSession game = roomCode == null ? null : privateRooms.get(roomCode.trim().toUpperCase());
        if(game == null || game.isFull() || game.getStatus() != GameSession.GameStatus.WAITING) {
            return null;
        }

        game.addPlayer(new Player(playerId, session, PlayerNames.sanitize(playerName)));
        playerToGame.put(playerId, game);
        privateRooms.remove(game.getRoomCode()); // a room code works once
        prepareGame(game);
        log.info("Player {} joined private game {}", playerId, game.getGameId());
        return game;
    }

    /**
     * Prepares a game (duration + questions); it is started only after the countdown
     */
    private void prepareGame(GameSession game) {
        // Setze Spiel-Konfiguration
        game.setDurationSeconds(gameConfig.getDurationSeconds());

        // Generiere ZWEI verschiedene Fragenlisten
        List<Question> questionsP1 = questionGenerator.generateQuestions(game.getChallengeType(), gameConfig.getQuestionsPerGame());
        List<Question> questionsP2 = questionGenerator.generateQuestions(game.getChallengeType(), gameConfig.getQuestionsPerGame());

        // Gib jedem Spieler seine eigenen Fragen
        game.getPlayer1().setQuestions(questionsP1);
        game.getPlayer2().setQuestions(questionsP2);

        // The clock starts later in GameWebSocketHandler, once the countdown has finished
        log.info("Game {} prepared - Duration: {}s, P1: {} questions, P2: {} questions",
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
            boolean removed = game.removePlayer(playerId);
            playerToGame.remove(playerId);

            // A private game whose host left can no longer be joined
            if(game.getRoomCode() != null) {
                privateRooms.remove(game.getRoomCode(), game);
            }

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