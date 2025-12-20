package com.warduel.warduel.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for GameSession class
 * Tests all game states, player management, timing, and rematch functionality
 */
class GameSessionTest {

    private GameSession gameSession;
    private Player player1;
    private Player player2;
    private WebSocketSession mockSession1;
    private WebSocketSession mockSession2;

    @BeforeEach
    void setUp() {
        gameSession = new GameSession();
        mockSession1 = mock(WebSocketSession.class);
        mockSession2 = mock(WebSocketSession.class);

        when(mockSession1.getId()).thenReturn("player1-id");
        when(mockSession2.getId()).thenReturn("player2-id");
        when(mockSession1.isOpen()).thenReturn(true);
        when(mockSession2.isOpen()).thenReturn(true);

        player1 = new Player("player1-id", mockSession1, "");
        player2 = new Player("player2-id", mockSession2, "");
    }

    // ========== Constructor and Initialization Tests ==========

    @Test
    @DisplayName("Should initialize GameSession with WAITING status")
    void testInitialState() {
        assertNotNull(gameSession.getGameId());
        assertEquals(GameSession.GameStatus.WAITING, gameSession.getStatus());
        assertNull(gameSession.getPlayer1());
        assertNull(gameSession.getPlayer2());
        assertFalse(gameSession.isFull());
        assertNotNull(gameSession.getQuestions());
        assertTrue(gameSession.getQuestions().isEmpty());
        assertFalse(gameSession.isPlayer1WantsRematch());
        assertFalse(gameSession.isPlayer2WantsRematch());
    }

    @Test
    @DisplayName("Should generate unique game IDs for different sessions")
    void testUniqueGameIds() {
        GameSession session1 = new GameSession();
        GameSession session2 = new GameSession();
        assertNotEquals(session1.getGameId(), session2.getGameId());
    }

    // ========== Player Management Tests ==========

    @Test
    @DisplayName("Should add first player as Player 1")
    void testAddFirstPlayer() {
        assertTrue(gameSession.addPlayer(player1));
        assertEquals(player1, gameSession.getPlayer1());
        assertNull(gameSession.getPlayer2());
        assertEquals("Spieler 1", player1.getDisplayName());
        assertEquals(GameSession.GameStatus.WAITING, gameSession.getStatus());
        assertFalse(gameSession.isFull());
    }

    @Test
    @DisplayName("Should add second player as Player 2 and set READY status")
    void testAddSecondPlayer() {
        gameSession.addPlayer(player1);
        assertTrue(gameSession.addPlayer(player2));

        assertEquals(player1, gameSession.getPlayer1());
        assertEquals(player2, gameSession.getPlayer2());
        assertEquals("Spieler 1", player1.getDisplayName());
        assertEquals("Spieler 2", player2.getDisplayName());
        assertEquals(GameSession.GameStatus.READY, gameSession.getStatus());
        assertTrue(gameSession.isFull());
    }

    @Test
    @DisplayName("Should reject third player when game is full")
    void testRejectThirdPlayer() {
        Player player3 = new Player("player3-id", mock(WebSocketSession.class), "");

        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        assertFalse(gameSession.addPlayer(player3));
        assertTrue(gameSession.isFull());
    }

    @Test
    @DisplayName("Should remove player 1 correctly")
    void testRemovePlayer1() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        assertTrue(gameSession.removePlayer("player1-id"));
        assertNull(gameSession.getPlayer1());
        assertEquals(player2, gameSession.getPlayer2());
        assertFalse(gameSession.isFull());
    }

    @Test
    @DisplayName("Should remove player 2 correctly")
    void testRemovePlayer2() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        assertTrue(gameSession.removePlayer("player2-id"));
        assertEquals(player1, gameSession.getPlayer1());
        assertNull(gameSession.getPlayer2());
        assertFalse(gameSession.isFull());
    }

    @Test
    @DisplayName("Should return false when removing non-existent player")
    void testRemoveNonExistentPlayer() {
        gameSession.addPlayer(player1);
        assertFalse(gameSession.removePlayer("non-existent-id"));
    }

    @Test
    @DisplayName("Should handle removing from empty game")
    void testRemoveFromEmptyGame() {
        assertFalse(gameSession.removePlayer("any-id"));
    }

    // ========== Game Flow Tests ==========

    @Test
    @DisplayName("Should start game successfully when READY")
    void testStartGame() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        assertTrue(gameSession.startGame());
        assertEquals(GameSession.GameStatus.RUNNING, gameSession.getStatus());
        assertNotNull(gameSession.getStartTime());
        assertNotNull(gameSession.getEndTime());
        assertEquals(0, player1.getScore());
        assertEquals(0, player2.getScore());
        assertEquals(0, player1.getCurrentQuestionIndex());
        assertEquals(0, player2.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("Should not start game when not READY")
    void testStartGameWhenNotReady() {
        gameSession.addPlayer(player1);
        // Only one player, status is WAITING

        assertFalse(gameSession.startGame());
        assertEquals(GameSession.GameStatus.WAITING, gameSession.getStatus());
    }

    @Test
    @DisplayName("Should calculate end time correctly (60 seconds)")
    void testGameDuration() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        gameSession.startGame();

        LocalDateTime startTime = gameSession.getStartTime();
        LocalDateTime endTime = gameSession.getEndTime();

        assertEquals(GameSession.GAME_DURATION_SECONDS,
                java.time.Duration.between(startTime, endTime).getSeconds());
    }

    @Test
    @DisplayName("Should end game and set status to FINISHED")
    void testEndGame() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        gameSession.startGame();

        gameSession.endGame();

        assertEquals(GameSession.GameStatus.FINISHED, gameSession.getStatus());
        assertNotNull(gameSession.getEndTime());
    }

    // ========== Timing Tests ==========

    @Test
    @DisplayName("Should return correct remaining seconds")
    void testGetRemainingSeconds() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        gameSession.startGame();

        long remainingSeconds = gameSession.getRemainingSeconds();
        assertTrue(remainingSeconds > 0 && remainingSeconds <= GameSession.GAME_DURATION_SECONDS);
    }

    @Test
    @DisplayName("Should return 0 remaining seconds when time is up")
    void testRemainingSecondsWhenTimeUp() throws InterruptedException {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        gameSession.startGame();

        // Manually set end time to the past
        gameSession.setEndTime(LocalDateTime.now().minusSeconds(5));

        assertEquals(0, gameSession.getRemainingSeconds());
    }

    @Test
    @DisplayName("Should return full duration when game not running")
    void testRemainingSecondsWhenNotRunning() {
        assertEquals(GameSession.GAME_DURATION_SECONDS, gameSession.getRemainingSeconds());
    }

    @Test
    @DisplayName("Should detect when time is up")
    void testIsTimeUp() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        gameSession.startGame();

        assertFalse(gameSession.isTimeUp());

        // Manually set end time to the past
        gameSession.setEndTime(LocalDateTime.now().minusSeconds(1));

        assertTrue(gameSession.isTimeUp());
    }

    @Test
    @DisplayName("Should return false for isTimeUp when game not running")
    void testIsTimeUpWhenNotRunning() {
        assertFalse(gameSession.isTimeUp());
    }

    // ========== Question Management Tests ==========

    @Test
    @DisplayName("Should set and retrieve questions")
    void testSetQuestions() {
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        questions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));

        gameSession.setQuestions(questions);

        assertEquals(2, gameSession.getQuestions().size());
        assertEquals("5 + 3", gameSession.getQuestions().get(0).getQuestionText());
    }

    @Test
    @DisplayName("Should get current question for player")
    void testGetCurrentQuestionForPlayer() {
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        questions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));

        player1.setQuestions(questions);

        Question currentQuestion = gameSession.getCurrentQuestionForPlayer(player1);
        assertNotNull(currentQuestion);
        assertEquals("5 + 3", currentQuestion.getQuestionText());
    }

    @Test
    @DisplayName("Should return null for current question when player is null")
    void testGetCurrentQuestionForNullPlayer() {
        assertNull(gameSession.getCurrentQuestionForPlayer(null));
    }

    @Test
    @DisplayName("Should handle player advancing through questions")
    void testPlayerQuestionProgression() {
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        questions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));

        player1.setQuestions(questions);

        assertEquals(0, player1.getCurrentQuestionIndex());
        Question q1 = gameSession.getCurrentQuestionForPlayer(player1);
        assertEquals("5 + 3", q1.getQuestionText());

        player1.nextQuestion();
        assertEquals(1, player1.getCurrentQuestionIndex());
        Question q2 = gameSession.getCurrentQuestionForPlayer(player1);
        assertEquals("10 - 4", q2.getQuestionText());
    }

    // ========== Winner Determination Tests ==========

    @Test
    @DisplayName("Should determine Player 1 as winner")
    void testDetermineWinnerPlayer1() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        player1.incrementScore();
        player1.incrementScore();
        player2.incrementScore();

        String winner = gameSession.determineWinner();
        assertEquals("Spieler 1", winner);
    }

    @Test
    @DisplayName("Should determine Player 2 as winner")
    void testDetermineWinnerPlayer2() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        player1.incrementScore();
        player2.incrementScore();
        player2.incrementScore();
        player2.incrementScore();

        String winner = gameSession.determineWinner();
        assertEquals("Spieler 2", winner);
    }

    @Test
    @DisplayName("Should determine draw when scores are equal")
    void testDetermineWinnerDraw() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        player1.incrementScore();
        player1.incrementScore();
        player2.incrementScore();
        player2.incrementScore();

        String winner = gameSession.determineWinner();
        assertEquals("Unentschieden", winner);
        assertTrue(gameSession.isDraw());
    }

    @Test
    @DisplayName("Should return unknown winner when players are missing")
    void testDetermineWinnerWithMissingPlayers() {
        String winner = gameSession.determineWinner();
        assertEquals("Unbekannt", winner);
    }

    @Test
    @DisplayName("Should detect draw correctly")
    void testIsDraw() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        assertTrue(gameSession.isDraw()); // Both have 0 score

        player1.incrementScore();
        assertFalse(gameSession.isDraw());

        player2.incrementScore();
        assertTrue(gameSession.isDraw());
    }

    @Test
    @DisplayName("Should return false for isDraw when players are missing")
    void testIsDrawWithMissingPlayers() {
        assertFalse(gameSession.isDraw());
    }

    // ========== Rematch Functionality Tests ==========

    @Test
    @DisplayName("Should set Player 1 rematch flag")
    void testSetPlayer1Rematch() {
        assertFalse(gameSession.isPlayer1WantsRematch());
        gameSession.setPlayer1Rematch(true);
        assertTrue(gameSession.isPlayer1WantsRematch());
    }

    @Test
    @DisplayName("Should set Player 2 rematch flag")
    void testSetPlayer2Rematch() {
        assertFalse(gameSession.isPlayer2WantsRematch());
        gameSession.setPlayer2Rematch(true);
        assertTrue(gameSession.isPlayer2WantsRematch());
    }

    @Test
    @DisplayName("Should detect when both players want rematch")
    void testBothWantRematch() {
        assertFalse(gameSession.bothWantRematch());

        gameSession.setPlayer1Rematch(true);
        assertFalse(gameSession.bothWantRematch());

        gameSession.setPlayer2Rematch(true);
        assertTrue(gameSession.bothWantRematch());
    }

    @Test
    @DisplayName("Should check if specific player wants rematch")
    void testDoesPlayerWantRematch() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        gameSession.setPlayer1Rematch(true);

        assertTrue(gameSession.doesPlayerWantRematch("player1-id"));
        assertFalse(gameSession.doesPlayerWantRematch("player2-id"));
        assertFalse(gameSession.doesPlayerWantRematch("unknown-id"));
    }

    @Test
    @DisplayName("Should reset game for rematch successfully")
    void testResetForRematch() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        gameSession.startGame();

        player1.incrementScore();
        player1.incrementScore();
        player2.incrementScore();
        player1.nextQuestion();
        player2.nextQuestion();

        gameSession.endGame();
        gameSession.setPlayer1Rematch(true);
        gameSession.setPlayer2Rematch(true);

        assertTrue(gameSession.resetForRematch());

        assertEquals(GameSession.GameStatus.READY, gameSession.getStatus());
        assertEquals(0, player1.getScore());
        assertEquals(0, player2.getScore());
        assertEquals(0, player1.getCurrentQuestionIndex());
        assertEquals(0, player2.getCurrentQuestionIndex());
        assertFalse(gameSession.isPlayer1WantsRematch());
        assertFalse(gameSession.isPlayer2WantsRematch());
        assertNull(gameSession.getStartTime());
        assertNull(gameSession.getEndTime());
        assertTrue(gameSession.getQuestions().isEmpty());
    }

    @Test
    @DisplayName("Should not reset for rematch when game is not FINISHED")
    void testResetForRematchWhenNotFinished() {
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        gameSession.startGame();

        assertFalse(gameSession.resetForRematch());
        assertEquals(GameSession.GameStatus.RUNNING, gameSession.getStatus());
    }

    // ========== Thread Safety Tests ==========

    @Test
    @DisplayName("Should handle concurrent player additions safely")
    void testConcurrentPlayerAdditions() throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                WebSocketSession mockSession = mock(WebSocketSession.class);
                when(mockSession.getId()).thenReturn("player-" + index);
                Player player = new Player("player-" + index, mockSession, "");
                synchronized (results) {
                    results.add(gameSession.addPlayer(player));
                }
            });
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Only 2 players should be added successfully
        long successCount = results.stream().filter(r -> r).count();
        assertEquals(2, successCount);
        assertTrue(gameSession.isFull());
    }

    @Test
    @DisplayName("Should handle concurrent score updates safely")
    void testConcurrentScoreUpdates() throws InterruptedException {
        gameSession.addPlayer(player1);

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> player1.incrementScore());
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(10, player1.getScore());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle null session when adding player")
    void testAddPlayerWithNullSession() {
        Player playerWithNullSession = new Player("test-id", null, "Test");
        assertTrue(gameSession.addPlayer(playerWithNullSession));
        assertEquals(playerWithNullSession, gameSession.getPlayer1());
    }

    @Test
    @DisplayName("Should handle empty questions list")
    void testEmptyQuestionsList() {
        player1.setQuestions(new ArrayList<>());
        assertNull(gameSession.getCurrentQuestionForPlayer(player1));
    }

    @Test
    @DisplayName("Should handle question index out of bounds")
    void testQuestionIndexOutOfBounds() {
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        player1.setQuestions(questions);

        player1.nextQuestion(); // Move to index 1
        player1.nextQuestion(); // Move to index 2 (out of bounds)

        assertNull(gameSession.getCurrentQuestionForPlayer(player1));
    }
}
