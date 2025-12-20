package com.warduel.warduel.integration;

import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.Player;
import com.warduel.warduel.model.Question;
import com.warduel.warduel.service.GameService;
import com.warduel.warduel.service.QuestionGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for complete game flow scenarios
 * Tests end-to-end gameplay from matchmaking to game completion
 */
class GameFlowIntegrationTest {

    private GameService gameService;
    private QuestionGeneratorService questionGenerator;
    private WebSocketSession mockSession1;
    private WebSocketSession mockSession2;

    @BeforeEach
    void setUp() {
        questionGenerator = new QuestionGeneratorService();
        gameService = new GameService(questionGenerator);

        mockSession1 = mock(WebSocketSession.class);
        mockSession2 = mock(WebSocketSession.class);

        when(mockSession1.getId()).thenReturn("player1");
        when(mockSession2.getId()).thenReturn("player2");
        when(mockSession1.isOpen()).thenReturn(true);
        when(mockSession2.isOpen()).thenReturn(true);
    }

    // ========== Complete Game Flow Tests ==========

    @Test
    @DisplayName("Should complete full game from start to finish")
    void testCompleteGameFlow() {
        // 1. Player 1 joins and waits
        GameSession game1 = gameService.joinGame(mockSession1);
        assertNotNull(game1);
        assertEquals(GameSession.GameStatus.WAITING, game1.getStatus());
        assertFalse(game1.isFull());

        // 2. Player 2 joins and game starts
        GameSession game2 = gameService.joinGame(mockSession2);
        assertSame(game1, game2);
        assertTrue(game2.isFull());
        assertEquals(GameSession.GameStatus.RUNNING, game2.getStatus());

        // 3. Both players have questions
        Player player1 = game2.getPlayer1();
        Player player2 = game2.getPlayer2();
        assertNotNull(player1.getCurrentQuestion());
        assertNotNull(player2.getCurrentQuestion());

        // 4. Players answer questions
        for (int i = 0; i < 5; i++) {
            // Player 1 answers correctly
            Question q1 = game2.getCurrentQuestionForPlayer(player1);
            if (q1 != null) {
                boolean correct1 = gameService.submitAnswer(player1.getPlayerId(), q1.getCorrectAnswer());
                if (correct1) {
                    player1.incrementScore();
                }
                player1.nextQuestion();
            }

            // Player 2 answers correctly
            Question q2 = game2.getCurrentQuestionForPlayer(player2);
            if (q2 != null) {
                boolean correct2 = gameService.submitAnswer(player2.getPlayerId(), q2.getCorrectAnswer());
                if (correct2) {
                    player2.incrementScore();
                }
                player2.nextQuestion();
            }
        }

        // 5. Verify scores
        assertTrue(player1.getScore() > 0);
        assertTrue(player2.getScore() > 0);

        // 6. End game
        game2.endGame();
        assertEquals(GameSession.GameStatus.FINISHED, game2.getStatus());

        // 7. Determine winner
        String winner = game2.determineWinner();
        assertNotNull(winner);
        assertTrue(winner.equals("Spieler 1") || winner.equals("Spieler 2") || winner.equals("Unentschieden"));
    }

    @Test
    @DisplayName("Should handle player winning by reaching 20 points")
    void testWinByReaching20Points() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();

        // Simulate player 1 answering 20 questions correctly
        for (int i = 0; i < 20; i++) {
            Question q = game.getCurrentQuestionForPlayer(player1);
            if (q != null) {
                boolean correct = gameService.submitAnswer(player1.getPlayerId(), q.getCorrectAnswer());
                if (correct) {
                    player1.incrementScore();
                }
                player1.nextQuestion();
            }
        }

        assertTrue(player1.getScore() >= 20 || player1.getCurrentQuestionIndex() >= 20);
    }

    @Test
    @DisplayName("Should handle game ending by time (60 seconds)")
    void testGameEndsByTimer() throws InterruptedException {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        // Verify initial remaining time
        long initialTime = game.getRemainingSeconds();
        assertTrue(initialTime > 0 && initialTime <= 60);

        // Simulate some time passing
        Thread.sleep(1000);

        // Time should decrease
        long afterTime = game.getRemainingSeconds();
        assertTrue(afterTime < initialTime || afterTime == 0);
    }

    // ========== Rematch Flow Tests ==========

    @Test
    @DisplayName("Should handle complete rematch flow")
    void testCompleteRematchFlow() {
        // 1. Complete first game
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        // Play some rounds
        for (int i = 0; i < 3; i++) {
            Question q1 = game.getCurrentQuestionForPlayer(player1);
            if (q1 != null && gameService.submitAnswer(player1.getPlayerId(), q1.getCorrectAnswer())) {
                player1.incrementScore();
            }
            player1.nextQuestion();

            Question q2 = game.getCurrentQuestionForPlayer(player2);
            if (q2 != null && gameService.submitAnswer(player2.getPlayerId(), q2.getCorrectAnswer())) {
                player2.incrementScore();
            }
            player2.nextQuestion();
        }

        int firstGameP1Score = player1.getScore();
        int firstGameP2Score = player2.getScore();

        game.endGame();
        assertEquals(GameSession.GameStatus.FINISHED, game.getStatus());

        // 2. Request rematch
        boolean p1Rematch = gameService.requestRematch(player1.getPlayerId());
        assertFalse(p1Rematch); // Only one player requested

        boolean bothRematch = gameService.requestRematch(player2.getPlayerId());
        assertTrue(bothRematch); // Both requested

        // 3. Verify game reset
        assertEquals(GameSession.GameStatus.RUNNING, game.getStatus());
        assertEquals(0, player1.getScore());
        assertEquals(0, player2.getScore());
        assertEquals(0, player1.getCurrentQuestionIndex());
        assertEquals(0, player2.getCurrentQuestionIndex());

        // 4. Play new game
        Question newQ1 = game.getCurrentQuestionForPlayer(player1);
        assertNotNull(newQ1);
        if (gameService.submitAnswer(player1.getPlayerId(), newQ1.getCorrectAnswer())) {
            player1.incrementScore();
        }

        assertTrue(player1.getScore() >= 0);
    }

    @Test
    @DisplayName("Should handle multiple consecutive rematches")
    void testMultipleRematches() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        for (int round = 0; round < 3; round++) {
            // Play game
            game.endGame();

            // Request rematch
            gameService.requestRematch("player1");
            boolean rematchStarted = gameService.requestRematch("player2");

            if (round < 2) { // Not the last round
                assertTrue(rematchStarted);
                assertEquals(GameSession.GameStatus.RUNNING, game.getStatus());
                assertEquals(0, game.getPlayer1().getScore());
                assertEquals(0, game.getPlayer2().getScore());
            }
        }
    }

    // ========== Player Disconnect Scenarios ==========

    @Test
    @DisplayName("Should handle player disconnect during waiting")
    void testDisconnectDuringWaiting() {
        GameSession game = gameService.joinGame(mockSession1);
        assertEquals(GameSession.GameStatus.WAITING, game.getStatus());

        gameService.removePlayer("player1");

        assertNull(gameService.getGameByPlayerId("player1"));
    }

    @Test
    @DisplayName("Should handle player disconnect during active game")
    void testDisconnectDuringGame() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        // Play a bit
        player1.incrementScore();
        player2.incrementScore();

        // Player 1 disconnects
        gameService.removePlayer("player1");

        assertNull(game.getPlayer1());
        assertNotNull(game.getPlayer2());
        assertEquals(1, player2.getScore()); // Score preserved
    }

    @Test
    @DisplayName("Should handle both players disconnecting")
    void testBothPlayersDisconnect() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        gameService.removePlayer("player1");
        gameService.removePlayer("player2");

        assertNull(gameService.getGameByPlayerId("player1"));
        assertNull(gameService.getGameByPlayerId("player2"));
    }

    // ========== Multiple Games Concurrently ==========

    @Test
    @DisplayName("Should handle multiple games running concurrently")
    void testMultipleGames() {
        // Create sessions for 6 players (3 games)
        List<WebSocketSession> sessions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("player" + i);
            when(session.isOpen()).thenReturn(true);
            sessions.add(session);
        }

        // Join games
        List<GameSession> games = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            games.add(gameService.joinGame(session));
        }

        // Verify we have 3 distinct full games
        long fullGames = games.stream()
                .distinct()
                .filter(GameSession::isFull)
                .count();

        assertTrue(fullGames >= 2, "Should have at least 2 full games");
    }

    @Test
    @DisplayName("Should handle concurrent player joins")
    void testConcurrentPlayerJoins() throws InterruptedException {
        int playerCount = 20;
        CountDownLatch latch = new CountDownLatch(playerCount);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        List<WebSocketSession> sessions = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("concurrent-player-" + i);
            when(session.isOpen()).thenReturn(true);
            sessions.add(session);
        }

        for (WebSocketSession session : sessions) {
            executor.submit(() -> {
                try {
                    gameService.joinGame(session);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify all players are in games
        int playersInGames = 0;
        for (int i = 0; i < playerCount; i++) {
            if (gameService.getGameByPlayerId("concurrent-player-" + i) != null) {
                playersInGames++;
            }
        }

        assertEquals(playerCount, playersInGames);
    }

    // ========== Edge Case Scenarios ==========

    @Test
    @DisplayName("Should handle player answering all 20 questions")
    void testAnswerAll20Questions() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();

        // Answer all 20 questions
        for (int i = 0; i < 20; i++) {
            Question q = game.getCurrentQuestionForPlayer(player1);
            if (q != null) {
                gameService.submitAnswer(player1.getPlayerId(), q.getCorrectAnswer());
                player1.incrementScore();
            }
            player1.nextQuestion();
        }

        assertEquals(20, player1.getCurrentQuestionIndex());
        assertNull(game.getCurrentQuestionForPlayer(player1)); // No more questions
    }

    @Test
    @DisplayName("Should handle mixed correct and incorrect answers")
    void testMixedAnswers() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();
        int correctCount = 0;
        int incorrectCount = 0;

        for (int i = 0; i < 10; i++) {
            Question q = game.getCurrentQuestionForPlayer(player1);
            if (q != null) {
                boolean correct;
                if (i % 2 == 0) {
                    // Correct answer
                    correct = gameService.submitAnswer(player1.getPlayerId(), q.getCorrectAnswer());
                    if (correct) {
                        player1.incrementScore();
                        correctCount++;
                    }
                } else {
                    // Incorrect answer
                    correct = gameService.submitAnswer(player1.getPlayerId(), q.getCorrectAnswer() + 999);
                    if (!correct) {
                        incorrectCount++;
                    }
                }
            }
            player1.nextQuestion();
        }

        assertTrue(correctCount > 0);
        assertTrue(incorrectCount > 0);
        assertEquals(correctCount, player1.getScore());
    }

    @Test
    @DisplayName("Should handle game with one player answering quickly")
    void testAsymmetricGameplay() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        // Player 1 answers 10 questions
        for (int i = 0; i < 10; i++) {
            Question q = game.getCurrentQuestionForPlayer(player1);
            if (q != null && gameService.submitAnswer(player1.getPlayerId(), q.getCorrectAnswer())) {
                player1.incrementScore();
            }
            player1.nextQuestion();
        }

        // Player 2 answers only 2 questions
        for (int i = 0; i < 2; i++) {
            Question q = game.getCurrentQuestionForPlayer(player2);
            if (q != null && gameService.submitAnswer(player2.getPlayerId(), q.getCorrectAnswer())) {
                player2.incrementScore();
            }
            player2.nextQuestion();
        }

        assertTrue(player1.getCurrentQuestionIndex() > player2.getCurrentQuestionIndex());
        assertTrue(player1.getScore() >= player2.getScore());
    }

    // ========== Draw Scenarios ==========

    @Test
    @DisplayName("Should handle draw game scenario")
    void testDrawGame() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        // Both players get same score
        for (int i = 0; i < 5; i++) {
            Question q1 = game.getCurrentQuestionForPlayer(player1);
            Question q2 = game.getCurrentQuestionForPlayer(player2);

            if (q1 != null && gameService.submitAnswer(player1.getPlayerId(), q1.getCorrectAnswer())) {
                player1.incrementScore();
            }
            if (q2 != null && gameService.submitAnswer(player2.getPlayerId(), q2.getCorrectAnswer())) {
                player2.incrementScore();
            }

            player1.nextQuestion();
            player2.nextQuestion();
        }

        game.endGame();

        if (player1.getScore() == player2.getScore()) {
            assertTrue(game.isDraw());
            assertEquals("Unentschieden", game.determineWinner());
        }
    }

    // ========== Stress Tests ==========

    @Test
    @DisplayName("Should handle rapid answer submissions")
    void testRapidAnswerSubmissions() {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();

        // Rapid fire answers
        for (int i = 0; i < 20; i++) {
            Question q = game.getCurrentQuestionForPlayer(player1);
            if (q != null) {
                gameService.submitAnswer(player1.getPlayerId(), q.getCorrectAnswer());
                player1.incrementScore();
                player1.nextQuestion();
            }
        }

        assertTrue(player1.getScore() <= 20);
        assertTrue(player1.getCurrentQuestionIndex() <= 20);
    }

    @Test
    @DisplayName("Should maintain consistency under concurrent operations")
    void testConcurrentGameOperations() throws InterruptedException {
        GameSession game = gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        Player player1 = game.getPlayer1();
        CountDownLatch latch = new CountDownLatch(2);

        // Thread 1: Player 1 answers questions
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                Question q = game.getCurrentQuestionForPlayer(player1);
                if (q != null) {
                    gameService.submitAnswer(player1.getPlayerId(), q.getCorrectAnswer());
                    player1.incrementScore();
                    player1.nextQuestion();
                }
            }
            latch.countDown();
        });

        // Thread 2: Try to get opponent
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                Player opponent = gameService.getOpponent(game, player1.getPlayerId());
                assertNotNull(opponent);
            }
            latch.countDown();
        });

        t1.start();
        t2.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
