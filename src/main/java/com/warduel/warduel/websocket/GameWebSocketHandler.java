package com.warduel.warduel.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.warduel.warduel.dto.*;
import com.warduel.warduel.model.*;
import com.warduel.warduel.service.GameService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameService gameService;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    public GameWebSocketHandler(GameService gameService, ObjectMapper objectMapper) {
        this.gameService = gameService;
        this.objectMapper = objectMapper;
    }

    /**
     * Wird aufgerufen wenn neuer Client verbindet
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("New WebSocket connection: {}", session.getId());

        try {
            GameSession game = gameService.joinGame(session);

            // Wenn Spiel voll ist, starte es
            if(game.isFull()) {
                Thread.sleep(500);
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
                default:
                    log.warn("Unknown message type: {}", baseMsg.getType());
            }
        } catch (Exception e) {
            log.error("Error handling message from {}: {}", playerId, e.getMessage());
            sendError(session, "Fehler beim Verarbeiten der Nachricht");
        }
    }

    /**
     * Verarbeitet Antworten
     */
    private void handleAnswer(WebSocketSession session, @SuppressWarnings("unused") String payload) throws IOException {
        String playerId = session.getId();
        AnswerMessage answerMsg = objectMapper.readValue(payload, AnswerMessage.class);

        GameSession game = gameService.getGameByPlayerId(playerId);
        if(game == null || game.getStatus() != GameSession.GameStatus.RUNNING) {
            sendError(session, "Spiel nicht gefunden oder nicht aktiv");
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
            sendError(session, "Spieler nicht gefunden");
            return;
        }

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

        if(player.getScore() >= 20) {
            log.info("Player {} reached 20 points! Ending game immediately", playerId);
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
                RematchMessage msg = new RematchMessage(true, true, "Rematch startet!");
                sendToAllPlayers(game, msg);

                Thread.sleep(1000);
                startGame(game);
            }
        } else {
            // Warte auf Gegner
            RematchMessage msg = new RematchMessage(true, false, "Warte auf Gegner...");
            sendMessage(session, msg);
        }
    }

    /**
     * Wird aufgerufen wenn Verbindung geschlossen wird
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @Nullable CloseStatus status) {
        if (session == null) {
            log.warn("afterConnectionClosed called with null session");
            return;
        }

        String playerId = session.getId();
        log.info("WebSocket connection closed: {} - Status: {}", playerId, status);

        GameSession game = gameService.getGameByPlayerId(playerId);
        if(game != null) {
            Player opponent = gameService.getOpponent(game, playerId);

            // Entferne Spieler
            gameService.removePlayer(playerId);

            // WICHTIG: Beende das Spiel sofort
            if(game.getStatus() == GameSession.GameStatus.RUNNING) {
                try {
                    game.endGame();
                    log.info("Game {} ended because player {} disconnected", game.getGameId(), playerId);
                } catch (Exception e) {
                    log.error("Error ending game", e);
                }
            }

            // Informiere Gegner
            if(opponent != null && opponent.getSession() != null && opponent.getSession().isOpen()) {
                try {
                    // Sende Game Over an Gegner
                    GameOverMessage msg = new GameOverMessage(
                            opponent.getScore(),
                            0,  // Disconnected player gets 0
                            true,  // Opponent wins
                            false,  // Not a draw
                            opponent.getDisplayName()
                    );
                    sendMessage(opponent.getSession(), msg);

                    // Schließe auch die Gegner-Session
                    opponent.getSession().close(CloseStatus.NORMAL);
                } catch (IOException e) {
                    log.error("Error notifying opponent", e);
                }
            }
        }
    }

    /**
     * Startet das Spiel
     */
    private void startGame(GameSession game) throws IOException, InterruptedException {
        game.startGame();

        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        log.info("Game {} started with {} questions", game.getGameId(), game.getQuestions().size());

        // Kurze Pause damit Clients bereit sind
        Thread.sleep(200);

        // Calculate remaining seconds ONCE for both players
        long remainingSeconds = game.getRemainingSeconds();

        // Sende erste Frage an beide Spieler mit gleicher Zeit
        sendNextQuestion(player1, game, remainingSeconds);
        sendNextQuestion(player2, game, remainingSeconds);

        // Starte Timer
        startGameTimer(game);
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
        }, GameSession.GAME_DURATION_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Beendet das Spiel
     */
    private void endGame(GameSession game) throws IOException {
        game.endGame();

        String winnerName = game.determineWinner();
        boolean isDraw = game.isDraw();

        log.info("Game {} ended. Winner: {}", game.getGameId(), winnerName);

        // Sende Game Over an beide Spieler
        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

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

        int opponentScore = (opponent != null) ? opponent.getScore() : 0;
        boolean youWon = !isDraw && player.getDisplayName().equals(winnerName);

        GameOverMessage msg = new GameOverMessage(
                player.getScore(),
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
        if(game.getPlayer1() != null && game.getPlayer1().getSession().isOpen()) {
            sendMessage(game.getPlayer1().getSession(), message);
        }
        if(game.getPlayer2() != null && game.getPlayer2().getSession().isOpen()) {
            sendMessage(game.getPlayer2().getSession(), message);
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
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }
}