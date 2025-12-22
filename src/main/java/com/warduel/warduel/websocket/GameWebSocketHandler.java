package com.warduel.warduel.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.warduel.warduel.config.GameConfiguration;
import com.warduel.warduel.dto.*;
import com.warduel.warduel.model.*;
import com.warduel.warduel.service.GameService;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameService gameService;
    private final GameConfiguration gameConfig;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    // SECURITY: Rate limiting - max messages per second per player
    private static final int MAX_MESSAGES_PER_SECOND = 10;
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    // Connection timeout tracking
    private static final long CONNECTION_TIMEOUT_SECONDS = 10;
    private static final long CONNECTION_CHECK_INTERVAL_SECONDS = 3;
    private final Map<String, Instant> lastMessageTime = new ConcurrentHashMap<>();

    // Game timing constants
    private static final int COUNTDOWN_DURATION_SECONDS = 3;
    private static final long REMATCH_DELAY_SECONDS = 1;

    // Validation constants
    private static final int MAX_ANSWER_VALUE = 1_000_000;

    // Executor service constants
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static class RateLimiter {
        private final AtomicInteger messageCount = new AtomicInteger(0);
        private volatile Instant windowStart = Instant.now();

        public boolean allowMessage() {
            Instant now = Instant.now();

            // Reset window if more than 1 second has passed
            if(now.isAfter(windowStart.plusSeconds(1))) {
                windowStart = now;
                messageCount.set(0);
            }

            // Check if under limit
            int count = messageCount.incrementAndGet();
            return count <= MAX_MESSAGES_PER_SECOND;
        }
    }

    public GameWebSocketHandler(GameService gameService, GameConfiguration gameConfig, ObjectMapper objectMapper) {
        this.gameService = gameService;
        this.gameConfig = gameConfig;
        this.objectMapper = objectMapper;
    }

    // cleanup to avoid thread pool resource leak
    @PreDestroy
    public void cleanup() {
        scheduler.shutdown();
        try {
            if(!scheduler.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts connection timeout checker for a game
     */
    private void startConnectionMonitor(GameSession game) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if(game.getStatus() != GameSession.GameStatus.RUNNING) {
                    return; // Only monitor running games
                }

                Instant now = Instant.now();

                // Check both players
                Player player1 = game.getPlayer1();
                Player player2 = game.getPlayer2();

                if(player1 != null) {
                    checkPlayerConnection(player1, now, game);
                }
                if(player2 != null) {
                    checkPlayerConnection(player2, now, game);
                }
            } catch (Exception e) {
                log.error("Error in connection monitor", e);
            }
        }, CONNECTION_CHECK_INTERVAL_SECONDS, CONNECTION_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void checkPlayerConnection(Player player, Instant now, GameSession game) {
        String playerId = player.getPlayerId();
        Instant lastMessage = lastMessageTime.get(playerId);

        if(lastMessage != null) {
            long secondsSinceLastMessage = now.getEpochSecond() - lastMessage.getEpochSecond();

            if(secondsSinceLastMessage > CONNECTION_TIMEOUT_SECONDS) {
                log.warn("Player {} timed out - no message in {} seconds", playerId, secondsSinceLastMessage);

                try {
                    // Close the stale connection
                    if(player.getSession() != null && player.getSession().isOpen()) {
                        player.getSession().close(CloseStatus.GOING_AWAY);
                    }
                } catch (Exception e) {
                    log.error("Error closing stale connection for player {}", playerId, e);
                }
            }
        }
    }

    /**
     * Wird aufgerufen wenn neuer Client verbindet
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("New WebSocket connection: {}", session.getId());

        try {
            String playerId = session.getId();

            // Track connection time
            lastMessageTime.put(playerId, Instant.now());

            GameSession game = gameService.joinGame(session);

            // Wenn Spiel voll ist, starte es
            if(game.isFull()) {
                startGame(game);
            }

        } catch (Exception e) {
            log.error("Error during connection establishment", e);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception closeEx) {
                log.error("Error closing session", closeEx);
            }
        }
    }

    /**
     * Verarbeitet eingehende Nachrichten
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String playerId = session.getId();
        String payload = message.getPayload();

        // Update last message time for timeout detection
        lastMessageTime.put(playerId, Instant.now());

        // SECURITY: Rate limiting check
        RateLimiter limiter = rateLimiters.computeIfAbsent(playerId, k -> new RateLimiter());
        if(!limiter.allowMessage()) {
            log.warn("Rate limit exceeded for player {}", playerId);
            sendError(session, "Too many messages - slow down!");
            return;
        }

        log.info("Received message from {}: {}", playerId, payload);

        try {
            BaseMessage baseMsg = objectMapper.readValue(payload, BaseMessage.class);

            switch(baseMsg.getType()) {
                case "ANSWER":
                    handleAnswer(session, payload);
                    break;
                case "REMATCH":
                    handleRematch(session);
                    break;
                case "FORFEIT":
                    handleForfeit(session);
                    break;
                case "HEARTBEAT":
                    // Just update last message time to keep connection alive
                    // No other action needed
                    break;
                default:
                    log.warn("Unknown message type: {}", baseMsg.getType());
            }
        } catch (Exception e) {
            log.error("Error handling message from {}: {}", playerId, e.getMessage());
            sendError(session, "Error processing message");
        }
    }

    /**
     * Verarbeitet Antworten
     */
    private void handleAnswer(WebSocketSession session, String payload) throws IOException {
        String playerId = session.getId();
        AnswerMessage answerMsg = objectMapper.readValue(payload, AnswerMessage.class);

        // SECURITY: Validate answer bounds to prevent extreme values
        if(Math.abs(answerMsg.getAnswer()) > MAX_ANSWER_VALUE) {
            log.warn("Answer out of bounds from player {}: {}", playerId, answerMsg.getAnswer());
            sendError(session, "Answer out of valid range");
            return;
        }

        GameSession game = gameService.getGameByPlayerId(playerId);
        if(game == null || game.getStatus() != GameSession.GameStatus.RUNNING) {
            sendError(session, "Game not found or not active");
            return;
        }

        // SECURITY: Check if time has expired
        if(game.isTimeUp()) {
            log.warn("Answer rejected from player {} - time expired", playerId);
            sendError(session, "Time has expired");
            return;
        }

        // Finde Spieler
        Player player = null;
        Player opponent = null;

        if(game.getPlayer1() != null && game.getPlayer1().getPlayerId().equals(playerId)) {
            player = game.getPlayer1();
            opponent = game.getPlayer2();
        } else if(game.getPlayer2() != null && game.getPlayer2().getPlayerId().equals(playerId)) {
            player = game.getPlayer2();
            opponent = game.getPlayer1();
        }

        if(player == null) {
            sendError(session, "Player not found");
            return;
        }

        int currentQuestionIndex = player.getCurrentQuestionIndex();

        // SECURITY: Check if player already answered this question
        if(player.hasAnsweredQuestion(currentQuestionIndex)) {
            log.warn("Player {} attempted to answer question {} multiple times", playerId, currentQuestionIndex);
            sendError(session, "Already answered this question");
            return;
        }

        // Mark question as answered IMMEDIATELY to prevent race conditions
        player.markQuestionAnswered(currentQuestionIndex);

        // Prüfe Antwort
        Question currentQuestion = game.getCurrentQuestionForPlayer(player);
        if(currentQuestion == null) {
            log.warn("No question for player {}", playerId);
            return;
        }

        boolean correct = currentQuestion.isCorrect(answerMsg.getAnswer());

        if(correct) {
            player.incrementScore();
        }

        // Sende Score Update an beide Spieler
        sendScoreUpdate(player, opponent, correct);
        if(opponent != null) {
            sendScoreUpdate(opponent, player, false);
        }

        // Prüfe vorzeitigen Sieg (wenn aktiviert)
        if(gameConfig.hasWinScore() && player.getScore() >= gameConfig.getWinScore()) {
            log.info("Player {} reached {} points! Ending game immediately", playerId, gameConfig.getWinScore());
            endGame(game);
            return;  // Wichtig: Keine weitere Frage senden!
        }

        // Nächste Frage
        player.nextQuestion();

        Question nextQuestion = game.getCurrentQuestionForPlayer(player);
        if(nextQuestion != null) {
            sendNextQuestion(player, game);
        }
    }

    /**
     * Verarbeitet Rematch-Anfrage
     */
    private void handleRematch(WebSocketSession session) throws IOException, InterruptedException {
        String playerId = session.getId();

        boolean bothWantRematch = gameService.requestRematch(playerId);

        if(bothWantRematch) {
            // Beide wollen Rematch - starte neues Spiel
            GameSession game = gameService.getGameByPlayerId(playerId);
            if(game != null) {
                RematchMessage msg = new RematchMessage(true, true, "Rematch starting!");
                sendToAllPlayers(game, msg);

                // Schedule rematch start after delay (give players time to see message)
                scheduler.schedule(() -> {
                    try {
                        startGame(game, false); // Skip countdown for rematch
                    } catch (Exception e) {
                        log.error("Error starting rematch for game {}", game.getGameId(), e);
                    }
                }, REMATCH_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        } else {
            // Warte auf Gegner
            RematchMessage msg = new RematchMessage(true, false, "Waiting for opponent...");
            sendMessage(session, msg);
        }
    }

    /**
     * Verarbeitet Forfeit (Spieler gibt auf)
     */
    private void handleForfeit(WebSocketSession session) throws IOException {
        String playerId = session.getId();
        log.info("Player {} forfeited the game", playerId);

        GameSession game = gameService.getGameByPlayerId(playerId);
        if(game == null || game.getStatus() != GameSession.GameStatus.RUNNING) {
            log.warn("Cannot forfeit - game not found or not running");
            return;
        }

        Player opponent = gameService.getOpponent(game, playerId);
        Player forfeitingPlayer = (game.getPlayer1() != null && game.getPlayer1().getPlayerId().equals(playerId))
            ? game.getPlayer1() : game.getPlayer2();

        // End the game
        game.endGame();

        // Send game over to BOTH players
        try {
            // 1. Send to opponent (they win)
            if(opponent != null && opponent.getSession() != null && opponent.getSession().isOpen()) {
                GameOverMessage opponentMsg = new GameOverMessage(
                        opponent.getScore(),
                        0,  // Forfeiting player gets 0
                        true,  // Opponent wins
                        false,  // Not a draw
                        opponent.getDisplayName()
                );
                sendMessage(opponent.getSession(), opponentMsg);
                log.info("FORFEIT_OPPONENT_WINS player={} youWon=true", opponent.getPlayerId());
            }

            // 2. Send to forfeiting player (they lose)
            if(forfeitingPlayer != null && session.isOpen()) {
                int forfeitScore = forfeitingPlayer.getScore();
                int opponentScore = opponent != null ? opponent.getScore() : 0;

                GameOverMessage forfeitMsg = new GameOverMessage(
                        forfeitScore,
                        opponentScore,
                        false,  // Forfeiting player loses
                        false,  // Not a draw
                        opponent != null ? opponent.getDisplayName() : "Opponent"
                );
                sendMessage(session, forfeitMsg);
                log.info("FORFEIT_PLAYER_LOSES player={} youWon=false yourScore={} opponentScore={}",
                    playerId, forfeitScore, opponentScore);
            } else {
                log.error("FORFEIT_SEND_FAILED player={} forfeitingPlayerExists={} sessionOpen={}",
                    playerId, forfeitingPlayer != null, session.isOpen());
            }

            log.info("FORFEIT_COMPLETED game={}", game.getGameId());

        } catch (Exception e) {
            log.error("FORFEIT_ERROR game={}", game.getGameId(), e);
        }
    }

    /**
     * Wird aufgerufen wenn Verbindung geschlossen wird
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @Nullable CloseStatus status) {

        String playerId = session.getId();
        log.info("WebSocket connection closed: {} - Status: {}", playerId, status);

        // SECURITY: Clean up rate limiter and timeout tracker to prevent memory leak
        rateLimiters.remove(playerId);
        lastMessageTime.remove(playerId);

        GameSession game = gameService.getGameByPlayerId(playerId);
        if(game != null) {
            Player opponent = gameService.getOpponent(game, playerId);

            // Entferne Spieler
            gameService.removePlayer(playerId);

            GameSession.GameStatus gameStatus = game.getStatus();

            // WICHTIG: Behandle Disconnection je nach Game Status
            if(gameStatus == GameSession.GameStatus.RUNNING) {
                // Check if actual gameplay occurred (at least one question answered)
                Player disconnectedPlayer = null;
                if(game.getPlayer1() != null && game.getPlayer1().getPlayerId().equals(playerId)) {
                    disconnectedPlayer = game.getPlayer1();
                } else if(game.getPlayer2() != null && game.getPlayer2().getPlayerId().equals(playerId)) {
                    disconnectedPlayer = game.getPlayer2();
                }

                boolean gameplayStarted = false;
                if(disconnectedPlayer != null && opponent != null) {
                    // Check if any player has progressed past first question
                    gameplayStarted = disconnectedPlayer.getCurrentQuestionIndex() > 0 || opponent.getCurrentQuestionIndex() > 0;
                }

                if(gameplayStarted) {
                    // Actual gameplay occurred - opponent wins
                    try {
                        game.endGame();
                        log.info("Game {} ended because player {} disconnected during RUNNING (gameplay started)", game.getGameId(), playerId);

                        // Informiere Gegner mit Game Over
                        if(opponent != null && opponent.getSession() != null && opponent.getSession().isOpen()) {
                            String disconnectedPlayerName = disconnectedPlayer != null ?
                                disconnectedPlayer.getDisplayName() : "Opponent";
                            String disconnectMsg = disconnectedPlayerName + " disconnected";

                            GameOverMessage msg = new GameOverMessage(
                                    opponent.getScore(),
                                    0,  // Disconnected player gets 0
                                    true,  // Opponent wins
                                    false,  // Not a draw
                                    opponent.getDisplayName(),
                                    disconnectMsg
                            );
                            sendMessage(opponent.getSession(), msg);
                            log.info("Game over sent to opponent {} - disconnect message: {}", opponent.getPlayerId(), disconnectMsg);
                        }
                    } catch (Exception e) {
                        log.error("Error ending game", e);
                    }
                } else {
                    // Game just started, no questions answered yet - treat as canceled
                    try {
                        game.endGame();
                        log.info("Game {} cancelled because player {} left immediately after start (no gameplay)", game.getGameId(), playerId);

                        // Informiere Gegner dass Spiel abgebrochen wurde
                        if(opponent != null && opponent.getSession() != null && opponent.getSession().isOpen()) {
                            ErrorMessage msg = new ErrorMessage("Opponent left the game");
                            sendMessage(opponent.getSession(), msg);
                            log.info("Error message sent to opponent {} - game cancelled early", opponent.getPlayerId());
                        }
                    } catch (Exception e) {
                        log.error("Error cancelling game", e);
                    }
                }
            } else if(gameStatus == GameSession.GameStatus.READY || gameStatus == GameSession.GameStatus.WAITING) {
                // Spiel noch nicht gestartet (Countdown oder Warteschlange) - Abbrechen
                try {
                    game.endGame();
                    log.info("Game {} cancelled because player {} left during {}", game.getGameId(), playerId, gameStatus);

                    // Informiere Gegner dass Spiel abgebrochen wurde
                    if(opponent != null && opponent.getSession() != null && opponent.getSession().isOpen()) {
                        ErrorMessage msg = new ErrorMessage("Opponent left the queue");
                        sendMessage(opponent.getSession(), msg);
                        log.info("Error message sent to opponent {} - game cancelled", opponent.getPlayerId());
                    }
                } catch (Exception e) {
                    log.error("Error cancelling game", e);
                }
            }
        }
    }

    /**
     * Startet das Spiel
     */
    private void startGame(GameSession game) throws IOException {
        startGame(game, true); // Default: show countdown
    }

    /**
     * Startet das Spiel mit optionalem Countdown
     */
    private void startGame(GameSession game, boolean showCountdown) throws IOException {
        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        if(showCountdown) {
            log.info("Game {} starting countdown with {} questions", game.getGameId(), game.getQuestions().size());

            // Tips to show during countdown
            String[] tips = {
                "Solve math problems faster than your opponent!",
                "Type your answer and press Enter to submit",
                "First to answer 20 questions correctly wins!"
            };

            // Pick one random tip to show throughout countdown
            String randomTip = tips[java.util.concurrent.ThreadLocalRandom.current().nextInt(tips.length)];

            // Send countdown messages asynchronously
            for(int i = COUNTDOWN_DURATION_SECONDS; i >= 1; i--) {
                final int countdown = i;
                scheduler.schedule(() -> {
                    try {
                        CountdownMessage countdownMsg = new CountdownMessage(countdown, randomTip);
                        sendToAllPlayers(game, countdownMsg);
                    } catch (Exception e) {
                        log.error("Error sending countdown message", e);
                    }
                }, (COUNTDOWN_DURATION_SECONDS - i), TimeUnit.SECONDS);
            }

            // Schedule game start after countdown finishes
            scheduler.schedule(() -> {
                try {
                    actuallyStartGame(game, player1, player2);
                } catch (Exception e) {
                    log.error("Error starting game after countdown", e);
                }
            }, COUNTDOWN_DURATION_SECONDS, TimeUnit.SECONDS);

        } else {
            log.info("Game {} starting immediately (rematch)", game.getGameId());
            actuallyStartGame(game, player1, player2);
        }
    }

    /**
     * Actually starts the game (called after countdown or immediately for rematch)
     */
    private void actuallyStartGame(GameSession game, Player player1, Player player2) {
        try {
            // NOW start the game (status changes to RUNNING)
            game.startGame();
            log.info("Game {} officially started", game.getGameId());

            // Reset last message time for both players since countdown doesn't send messages
            lastMessageTime.put(player1.getPlayerId(), Instant.now());
            lastMessageTime.put(player2.getPlayerId(), Instant.now());

            // Send FULL duration to clients (not remaining seconds which would be less due to countdown delay)
            long fullDuration = game.getDurationSeconds();

            // Sende erste Frage an beide Spieler mit gleicher Zeit
            sendNextQuestion(player1, game, fullDuration);
            sendNextQuestion(player2, game, fullDuration);

            // Starte Timer
            startGameTimer(game);

            // Start connection monitor to detect mobile disconnects
            startConnectionMonitor(game);
        } catch (Exception e) {
            log.error("Error starting game {}", game.getGameId(), e);
        }
    }

    /**
     * Sendet nächste Frage an Spieler
     */
    private void sendNextQuestion(Player player, GameSession game) throws IOException {
        sendNextQuestion(player, game, game.getRemainingSeconds());
    }

    /**
     * Sendet nächste Frage an Spieler mit spezifischer verbleibender Zeit
     */
    private void sendNextQuestion(Player player, GameSession game, long remainingSeconds) throws IOException {
        if(player == null || player.getSession() == null || !player.getSession().isOpen()) {
            log.warn("Cannot send question - player session is null or closed");
            return;
        }

        Question question = game.getCurrentQuestionForPlayer(player);
        if(question == null) {
            log.warn("No question available for player {}", player.getPlayerId());
            return;
        }

        int questionNumber = player.getCurrentQuestionIndex() + 1;

        QuestionMessage msg = new QuestionMessage(
                question.getQuestionText(),
                questionNumber,
                remainingSeconds
        );

        sendMessage(player.getSession(), msg);
    }

    /**
     * Sendet Score Update
     */
    private void sendScoreUpdate(Player player, Player opponent, Boolean correct) throws IOException {
        if(player == null || player.getSession() == null || !player.getSession().isOpen()) {
            return;
        }

        int opponentScore = (opponent != null) ? opponent.getScore() : 0;

        ScoreUpdateMessage msg = new ScoreUpdateMessage(
                player.getScore(),
                opponentScore,
                correct
        );

        sendMessage(player.getSession(), msg);
    }

    /**
     * Startet Game Timer
     */
    private void startGameTimer(GameSession game) {
        scheduler.schedule(() -> {
            try {
                endGame(game);
            } catch (Exception e) {
                log.error("Error ending game", e);
            }
        }, game.getDurationSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Beendet das Spiel
     */
    private void endGame(GameSession game) throws IOException {
        game.endGame();

        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        int score1 = (player1 != null) ? player1.getScore() : 0;
        int score2 = (player2 != null) ? player2.getScore() : 0;

        String winnerName = game.determineWinner();
        boolean isDraw = game.isDraw();

        log.info("Game {} ended. Scores: {}={}, {}={}, Winner: {}, IsDraw: {}",
                game.getGameId(),
                (player1 != null ? player1.getDisplayName() : "null"), score1,
                (player2 != null ? player2.getDisplayName() : "null"), score2,
                winnerName, isDraw);

        // Sende Game Over an beide Spieler
        if(player1 != null) {
            sendGameOver(player1, player2, winnerName, isDraw);
        }

        if(player2 != null) {
            sendGameOver(player2, player1, winnerName, isDraw);
        }
    }

    /**
     * Sendet Game Over an einen Spieler
     */
    private void sendGameOver(Player player, Player opponent, String winnerName, boolean isDraw) throws IOException {
        if(player == null || player.getSession() == null || !player.getSession().isOpen()) {
            return;
        }

        int playerScore = player.getScore();
        int opponentScore = (opponent != null) ? opponent.getScore() : 0;
        boolean youWon = !isDraw && player.getDisplayName().equals(winnerName);

        log.info("Sending GameOver to {}: yourScore={}, opponentScore={}, youWon={}, isDraw={}, winnerName={}",
                player.getDisplayName(), playerScore, opponentScore, youWon, isDraw, winnerName);

        GameOverMessage msg = new GameOverMessage(
                playerScore,
                opponentScore,
                youWon,
                isDraw,
                winnerName
        );

        sendMessage(player.getSession(), msg);
    }

    /**
     * Sendet Nachricht an alle Spieler
     */
    private void sendToAllPlayers(GameSession game, BaseMessage message) throws IOException {
        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        if(player1 != null && player1.getSession() != null && player1.getSession().isOpen()) {
            sendMessage(player1.getSession(), message);
        }
        if(player2 != null && player2.getSession() != null && player2.getSession().isOpen()) {
            sendMessage(player2.getSession(), message);
        }
    }

    /**
     * Sendet Error-Nachricht
     */
    private void sendError(WebSocketSession session, String errorMessage) throws IOException {
        ErrorMessage msg = new ErrorMessage(errorMessage);
        sendMessage(session, msg);
    }

    /**
     * Sendet Nachricht an Client
     */
    private void sendMessage(WebSocketSession session, BaseMessage message) throws IOException {
        if(session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                // Session closed between isOpen() check and send - log and ignore
                log.warn("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                throw e; // Re-throw to maintain existing error handling contract
            }
        }
    }
}