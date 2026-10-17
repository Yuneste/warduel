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
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
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

    // A connection that hasn't sent JOIN_GAME by then is closed
    private static final long JOIN_TIMEOUT_SECONDS = 15;

    // Game timing constants
    private static final int COUNTDOWN_DURATION_SECONDS = 3;
    private static final long REMATCH_DELAY_SECONDS = 1;

    // Validation constants
    private static final int MAX_ANSWER_VALUE = 1_000_000;

    // Executor service constants
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;

    // WebSocketSession.sendMessage is not thread-safe; scheduler threads and handler threads both send,
    // so every session is wrapped in a decorator that serializes sends
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 64 * 1024;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

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
        game.addScheduledTask(scheduler.scheduleAtFixedRate(() -> {
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
        }, CONNECTION_CHECK_INTERVAL_SECONDS, CONNECTION_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS));
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

            WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                    session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT);
            sessions.put(playerId, safeSession);

            // Matchmaking starts with the client's JOIN_GAME (it carries the nickname / room code)
            scheduler.schedule(() -> {
                if(safeSession.isOpen() && gameService.getGameByPlayerId(playerId) == null) {
                    log.info("Closing connection {} - no JOIN_GAME within {}s", playerId, JOIN_TIMEOUT_SECONDS);
                    try {
                        safeSession.close(CloseStatus.POLICY_VIOLATION);
                    } catch (IOException e) {
                        log.warn("Error closing idle connection {}", playerId, e);
                    }
                }
            }, JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

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

        log.debug("Received message from {}: {}", playerId, payload);

        try {
            BaseMessage baseMsg = objectMapper.readValue(payload, BaseMessage.class);

            switch(baseMsg.getType()) {
                case "JOIN_GAME":
                    handleJoin(session, (JoinGameMessage) baseMsg);
                    break;
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
            log.error("Error handling message from {}: {}", playerId, e.getMessage(), e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }

    /**
     * Puts the player into public matchmaking, a new private game, or a friend's private game
     */
    private void handleJoin(WebSocketSession session, JoinGameMessage join) throws IOException {
        String playerId = session.getId();
        if(gameService.getGameByPlayerId(playerId) != null) {
            log.warn("Player {} sent JOIN_GAME twice", playerId);
            return;
        }
        WebSocketSession safeSession = sessions.getOrDefault(playerId, session);

        GameSession game;
        if(join.isCreateRoom()) {
            game = gameService.createPrivateGame(safeSession, join.getPlayerName());
            sendMessage(session, new RoomCreatedMessage(game.getRoomCode()));
            return; // the game starts when the friend joins
        } else if(join.getRoomCode() != null && !join.getRoomCode().isBlank()) {
            game = gameService.joinPrivateGame(safeSession, join.getPlayerName(), join.getRoomCode());
            if(game == null) {
                sendError(session, "Room not found");
                return;
            }
        } else {
            game = gameService.joinGame(safeSession, join.getPlayerName());
        }

        // Wenn Spiel voll ist, starte es
        if(game.isFull()) {
            startGame(game);
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
        Player player = findPlayerById(game, playerId);
        Player opponent = findOpponentById(game, playerId);

        if(player == null) {
            sendError(session, "Player not found");
            return;
        }

        // Check, score and advance as one step per player, so concurrent answers can't both count
        synchronized(player) {
            if(game.getStatus() != GameSession.GameStatus.RUNNING) {
                sendError(session, "Game not found or not active");
                return;
            }

            int currentQuestionIndex = player.getCurrentQuestionIndex();

            // SECURITY: Check if player already answered this question
            if(player.hasAnsweredQuestion(currentQuestionIndex)) {
                log.warn("Player {} attempted to answer question {} multiple times", playerId, currentQuestionIndex);
                sendError(session, "Already answered this question");
                return;
            }

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
            player.getAnswerLog().add(new AnswerRecord(currentQuestionIndex + 1, currentQuestion.getQuestionText(),
                    String.valueOf(answerMsg.getAnswer()), correct,
                    System.currentTimeMillis() - player.getQuestionShownAtMillis()));

            // Sende Score Update an beide Spieler
            sendScoreUpdate(player, opponent, correct);
            if(opponent != null) {
                sendScoreUpdate(opponent, player, null); // null: the opponent didn't answer
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
    }

    /**
     * Verarbeitet Rematch-Anfrage
     */
    private void handleRematch(WebSocketSession session) throws IOException {
        String playerId = session.getId();

        GameSession game = gameService.getGameByPlayerId(playerId);
        if(game == null) {
            sendError(session, "Opponent left");
            return;
        }

        // Both players' requests go through this lock, so the second request's "starting" message
        // can never overtake the first request's "waiting" message
        synchronized(game.getRematchLock()) {
            // CRITICAL: Check if opponent is still connected before allowing rematch
            Player opponent = findOpponentById(game, playerId);
            if(opponent == null || opponent.getSession() == null || !opponent.getSession().isOpen()) {
                log.warn("Rematch rejected - opponent disconnected");
                sendError(session, "Opponent left");
                return;
            }

            boolean bothWantRematch = gameService.requestRematch(playerId);

            if(bothWantRematch) {
                // Beide wollen Rematch - starte neues Spiel
                sendToAllPlayers(game, new RematchMessage(true, true, "Rematch starting!"));

                // Schedule rematch start after delay (give players time to see message)
                game.addScheduledTask(scheduler.schedule(() -> {
                    try {
                        startGame(game, false); // Skip countdown for rematch
                    } catch (Exception e) {
                        log.error("Error starting rematch for game {}", game.getGameId(), e);
                    }
                }, REMATCH_DELAY_SECONDS, TimeUnit.SECONDS));
            } else if(game.getStatus() == GameSession.GameStatus.FINISHED) {
                // Warte auf Gegner
                sendMessage(session, new RematchMessage(true, false, "Waiting for opponent..."));
            }
        }
    }

    /**
     * Verarbeitet Forfeit (Spieler gibt auf)
     */
    private void handleForfeit(WebSocketSession session) {
        String playerId = session.getId();
        log.info("Player {} forfeited the game", playerId);

        GameSession game = gameService.getGameByPlayerId(playerId);
        if(game == null || game.getStatus() != GameSession.GameStatus.RUNNING) {
            log.warn("Cannot forfeit - game not found or not running");
            return;
        }

        if(!game.endGame()) {
            return; // Game ended concurrently (timer, win score or disconnect)
        }

        Player forfeitingPlayer = findPlayerById(game, playerId);
        Player opponent = findOpponentById(game, playerId);
        String name = forfeitingPlayer != null ? forfeitingPlayer.getDisplayName() : "Opponent";

        sendWinByDefault(opponent, forfeitingPlayer, name + " forfeited");
        log.info("FORFEIT_COMPLETED game={}", game.getGameId());
    }

    /**
     * Ends a game early in favour of {@code winner}: the winner gets a win with {@code reason},
     * the loser (if still connected) a loss. Real scores are shown to both.
     */
    private void sendWinByDefault(@Nullable Player winner, @Nullable Player loser, String reason) {
        int winnerScore = winner != null ? winner.getScore() : 0;
        int loserScore = loser != null ? loser.getScore() : 0;
        String winnerName = winner != null ? winner.getDisplayName() : "Opponent";

        try {
            if(winner != null) {
                sendMessage(winner.getSession(), withStats(
                        new GameOverMessage(winnerScore, loserScore, true, false, winnerName, reason), winner, loser));
            }
            if(loser != null) {
                sendMessage(loser.getSession(), withStats(
                        new GameOverMessage(loserScore, winnerScore, false, false, winnerName), loser, winner));
            }
        } catch (IOException e) {
            log.error("Error sending early game over", e);
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
        sessions.remove(playerId);

        GameSession game = gameService.getGameByPlayerId(playerId);
        if(game == null) {
            return;
        }

        // Look the players up before removal: removePlayer() clears the slot
        Player disconnectedPlayer = findPlayerById(game, playerId);
        Player opponent = gameService.getOpponent(game, playerId);
        String name = disconnectedPlayer != null ? disconnectedPlayer.getDisplayName() : "Opponent";

        // Entferne Spieler
        gameService.removePlayer(playerId);

        GameSession.GameStatus gameStatus = game.getStatus();
        boolean opponentConnected = opponent != null && opponent.getSession() != null && opponent.getSession().isOpen();

        switch(gameStatus) {
            case FINISHED -> {
                // Player left the result screen: disable the opponent's rematch button
                log.info("Game {} already finished - player {} left result screen", game.getGameId(), playerId);
                if(opponentConnected) {
                    sendQuietly(opponent.getSession(), new ErrorMessage("Opponent left"));
                }
            }
            case RUNNING -> {
                // Opponent wins by default
                if(game.endGame()) {
                    log.info("Game {} ended because player {} disconnected", game.getGameId(), playerId);
                    if(opponentConnected) {
                        sendWinByDefault(opponent, null, name + " disconnected");
                        // A rematch is impossible now; lets the client disable its button
                        sendQuietly(opponent.getSession(), new ErrorMessage("Opponent left"));
                    }
                }
            }
            case READY, WAITING -> {
                // Countdown, queue or pending rematch: cancel the match
                boolean pendingRematch = game.getRound() > 0;
                if(game.endGame()) {
                    log.info("Game {} cancelled because player {} left during {}", game.getGameId(), playerId, gameStatus);
                    if(opponentConnected) {
                        sendQuietly(opponent.getSession(),
                                new ErrorMessage(pendingRematch ? "Opponent left" : "Opponent left the queue"));
                    }
                }
            }
        }
    }

    private static GameOverMessage withStats(GameOverMessage msg, @Nullable Player you, @Nullable Player opponent) {
        msg.setYourStats(PlayerStats.of(you, true));
        msg.setOpponentStats(PlayerStats.of(opponent, false));
        return msg;
    }

    private void sendQuietly(WebSocketSession session, BaseMessage message) {
        try {
            sendMessage(session, message);
        } catch (IOException e) {
            log.error("Error sending {} to session {}", message.getType(), session.getId(), e);
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
            log.info("Game {} starting countdown", game.getGameId());

            // Introduce the players to each other before the countdown
            sendMessage(player1.getSession(), new GameStateMessage(GameSession.GameStatus.READY,
                    player1.getDisplayName(), player2.getDisplayName(), (long) game.getDurationSeconds()));
            sendMessage(player2.getSession(), new GameStateMessage(GameSession.GameStatus.READY,
                    player2.getDisplayName(), player1.getDisplayName(), (long) game.getDurationSeconds()));

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
                game.addScheduledTask(scheduler.schedule(() -> {
                    try {
                        CountdownMessage countdownMsg = new CountdownMessage(countdown, randomTip);
                        sendToAllPlayers(game, countdownMsg);
                    } catch (Exception e) {
                        log.error("Error sending countdown message", e);
                    }
                }, (COUNTDOWN_DURATION_SECONDS - i), TimeUnit.SECONDS));
            }

            // Schedule game start after countdown finishes
            game.addScheduledTask(scheduler.schedule(() -> {
                try {
                    actuallyStartGame(game, player1, player2);
                } catch (Exception e) {
                    log.error("Error starting game after countdown", e);
                }
            }, COUNTDOWN_DURATION_SECONDS, TimeUnit.SECONDS));

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
            // CRITICAL: Check if both players are still connected before starting
            // (prevents race condition if player disconnects during countdown)
            boolean player1Connected = player1 != null && player1.getSession() != null && player1.getSession().isOpen();
            boolean player2Connected = player2 != null && player2.getSession() != null && player2.getSession().isOpen();

            if(!player1Connected || !player2Connected) {
                log.warn("Game {} cannot start - player disconnected during countdown (p1={}, p2={})",
                        game.getGameId(), player1Connected, player2Connected);
                return;
            }

            // NOW start the game: status READY -> RUNNING and the clock starts here, after the countdown
            if(!game.startGame()) {
                log.info("Game {} not started - status is {}", game.getGameId(), game.getStatus());
                return;
            }
            log.info("Game {} officially started (round {})", game.getGameId(), game.getRound());

            // Reset last message time for both players since countdown doesn't send messages
            lastMessageTime.put(player1.getPlayerId(), Instant.now());
            lastMessageTime.put(player2.getPlayerId(), Instant.now());

            // Send FULL duration to clients (not remaining seconds which would be less due to countdown delay)
            long fullDuration = game.getDurationSeconds();

            // Tell clients the game is running (enables the client's forfeit flow)
            sendMessage(player1.getSession(), new GameStateMessage(GameSession.GameStatus.RUNNING,
                    player1.getDisplayName(), player2.getDisplayName(), fullDuration));
            sendMessage(player2.getSession(), new GameStateMessage(GameSession.GameStatus.RUNNING,
                    player2.getDisplayName(), player1.getDisplayName(), fullDuration));

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
        player.setQuestionShownAtMillis(System.currentTimeMillis());

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
        game.addScheduledTask(scheduler.schedule(() -> {
            try {
                endGame(game);
            } catch (Exception e) {
                log.error("Error ending game", e);
            }
        }, game.getDurationSeconds(), TimeUnit.SECONDS));
    }

    /**
     * Beendet das Spiel
     */
    private void endGame(GameSession game) throws IOException {
        // Only the first caller (timer or win score) announces the result; endGame() also cancels the round's tasks
        if(!game.endGame()) {
            return;
        }

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
        // Decided by score, not by name: two players may pick the same nickname
        boolean youWon = !isDraw && playerScore > opponentScore;

        log.info("Sending GameOver to {}: yourScore={}, opponentScore={}, youWon={}, isDraw={}, winnerName={}",
                player.getDisplayName(), playerScore, opponentScore, youWon, isDraw, winnerName);

        GameOverMessage msg = new GameOverMessage(
                playerScore,
                opponentScore,
                youWon,
                isDraw,
                winnerName
        );
        withStats(msg, player, opponent);

        sendMessage(player.getSession(), msg);
    }

    /**
     * Finds player by ID in a game session
     */
    @Nullable
    private Player findPlayerById(GameSession game, String playerId) {
        if(game.getPlayer1() != null && game.getPlayer1().getPlayerId().equals(playerId)) {
            return game.getPlayer1();
        } else if(game.getPlayer2() != null && game.getPlayer2().getPlayerId().equals(playerId)) {
            return game.getPlayer2();
        }
        return null;
    }

    /**
     * Finds opponent by player ID in a game session
     */
    @Nullable
    private Player findOpponentById(GameSession game, String playerId) {
        if(game.getPlayer1() != null && game.getPlayer1().getPlayerId().equals(playerId)) {
            return game.getPlayer2();
        } else if(game.getPlayer2() != null && game.getPlayer2().getPlayerId().equals(playerId)) {
            return game.getPlayer1();
        }
        return null;
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
            // Always send through the thread-safe decorator registered for this connection
            WebSocketSession target = sessions.getOrDefault(session.getId(), session);
            try {
                String json = objectMapper.writeValueAsString(message);
                target.sendMessage(new TextMessage(json));
            } catch (IOException | IllegalStateException e) {
                // Session closed between isOpen() check and send, or send buffer overflowed - log and continue
                // so one failed recipient doesn't stop messages to the other player
                log.warn("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}