package com.warduel.warduel.service;

import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.Player;
import com.warduel.warduel.model.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for GameService class
 * Tests matchmaking, game management, answer handling, and rematch functionality
 */
@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock(lenient = true)
    private QuestionGeneratorService questionGenerator;

    private GameService gameService;
    private WebSocketSession mockSession1;
    private WebSocketSession mockSession2;
    private WebSocketSession mockSession3;

    @BeforeEach
    void setUp() {
        gameService = new GameService(questionGenerator);

        mockSession1 = mock(WebSocketSession.class);
        mockSession2 = mock(WebSocketSession.class);
        mockSession3 = mock(WebSocketSession.class);

        lenient().when(mockSession1.getId()).thenReturn("player1");
        lenient().when(mockSession2.getId()).thenReturn("player2");
        lenient().when(mockSession3.getId()).thenReturn("player3");

        lenient().when(mockSession1.isOpen()).thenReturn(true);
        lenient().when(mockSession2.isOpen()).thenReturn(true);
        lenient().when(mockSession3.isOpen()).thenReturn(true);

        // Setup mock question generator
        lenient().when(questionGenerator.generateQuestions(anyInt())).thenAnswer(invocation -> createMockQuestions(20));
    }

    // ========== Matchmaking Tests ==========

    @Test
    @DisplayName("Should create new waiting game for first player")
    void testFirstPlayerJoin() {
        GameSession game = gameService.joinGame(mockSession1);

        assertNotNull(game);
        assertEquals(GameSession.GameStatus.WAITING, game.getStatus());
        assertNotNull(game.getPlayer1());
        assertNull(game.getPlayer2());
        assertEquals("player1", game.getPlayer1().getPlayerId());
        assertFalse(game.isFull());
    }

    @Test
    @DisplayName("Should add second player to waiting game and start it")
    void testSecondPlayerJoin() {
        GameSession game1 = gameService.joinGame(mockSession1);
        GameSession game2 = gameService.joinGame(mockSession2);

        assertSame(game1, game2); // Should be the same game
        assertTrue(game2.isFull());
        assertEquals(GameSession.GameStatus.RUNNING, game2.getStatus());
        assertNotNull(game2.getPlayer1());
        assertNotNull(game2.getPlayer2());
        assertEquals("player1", game2.getPlayer1().getPlayerId());
        assertEquals("player2", game2.getPlayer2().getPlayerId());

        // Verify questions were generated
        verify(questionGenerator, times(2)).generateQuestions(20);
    }

    @Test
    @DisplayName("Should create new game for third player when first game is full")
    void testThirdPlayerCreatesNewGame() {
        GameSession game1 = gameService.joinGame(mockSession1);
        GameSession game2 = gameService.joinGame(mockSession2);
        GameSession game3 = gameService.joinGame(mockSession3);

        assertSame(game1, game2);
        assertNotSame(game1, game3);
        assertTrue(game2.isFull());
        assertFalse(game3.isFull());
        assertEquals(GameSession.GameStatus.WAITING, game3.getStatus());
    }

    @Test
    @DisplayName("Should not add player twice to same game")
    void testPlayerJoinTwice() {
        GameSession game1 = gameService.joinGame(mockSession1);
        GameSession game2 = gameService.joinGame(mockSession1);

        assertSame(game1, game2);
        assertFalse(game1.isFull());
        assertNotNull(game1.getPlayer1());
        assertNull(game1.getPlayer2());
    }

    @Test
    @DisplayName("Should assign display names to players")
    void testPlayerDisplayNames() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");

        assertEquals("Spieler 1", game.getPlayer1().getDisplayName());
        assertEquals("Spieler 2", game.getPlayer2().getDisplayName());
    }

    @Test
    @DisplayName("Should generate unique questions for each player")
    void testUniqueQuestionsPerPlayer() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        // Verify questions were generated twice (once for each player)
        verify(questionGenerator, times(2)).generateQuestions(20);
    }

    // ========== Game Retrieval Tests ==========

    @Test
    @DisplayName("Should retrieve game by player ID")
    void testGetGameByPlayerId() {
        gameService.joinGame(mockSession1);

        GameSession game = gameService.getGameByPlayerId("player1");

        assertNotNull(game);
        assertEquals("player1", game.getPlayer1().getPlayerId());
    }

    @Test
    @DisplayName("Should return null for non-existent player")
    void testGetGameByNonExistentPlayerId() {
        GameSession game = gameService.getGameByPlayerId("non-existent");
        assertNull(game);
    }

    @Test
    @DisplayName("Should retrieve game for both players")
    void testGetGameForBothPlayers() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game1 = gameService.getGameByPlayerId("player1");
        GameSession game2 = gameService.getGameByPlayerId("player2");

        assertSame(game1, game2);
    }

    // ========== Answer Submission Tests ==========

    @Test
    @DisplayName("Should handle correct answer submission")
    void testSubmitCorrectAnswer() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        Question question = game.getPlayer1().getCurrentQuestion();

        boolean result = gameService.submitAnswer("player1", question.getCorrectAnswer());

        assertTrue(result);
    }

    @Test
    @DisplayName("Should handle incorrect answer submission")
    void testSubmitIncorrectAnswer() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        Question question = game.getPlayer1().getCurrentQuestion();

        boolean result = gameService.submitAnswer("player1", question.getCorrectAnswer() + 999);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false when submitting answer for non-existent player")
    void testSubmitAnswerNonExistentPlayer() {
        boolean result = gameService.submitAnswer("non-existent", 42);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false when game is not running")
    void testSubmitAnswerGameNotRunning() {
        gameService.joinGame(mockSession1);

        // Game is in WAITING state (only one player)
        boolean result = gameService.submitAnswer("player1", 42);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should handle answer submission for both players")
    void testSubmitAnswerBothPlayers() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        Question q1 = game.getPlayer1().getCurrentQuestion();
        Question q2 = game.getPlayer2().getCurrentQuestion();

        boolean result1 = gameService.submitAnswer("player1", q1.getCorrectAnswer());
        boolean result2 = gameService.submitAnswer("player2", q2.getCorrectAnswer());

        assertTrue(result1);
        assertTrue(result2);
    }

    // ========== Player Removal Tests ==========

    @Test
    @DisplayName("Should remove player from game")
    void testRemovePlayer() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        gameService.removePlayer("player1");

        GameSession game = gameService.getGameByPlayerId("player1");
        assertNull(game); // Player removed from map

        GameSession player2Game = gameService.getGameByPlayerId("player2");
        assertNotNull(player2Game);
        assertNull(player2Game.getPlayer1());
        assertNotNull(player2Game.getPlayer2());
    }

    @Test
    @DisplayName("Should handle removing non-existent player gracefully")
    void testRemoveNonExistentPlayer() {
        assertDoesNotThrow(() -> gameService.removePlayer("non-existent"));
    }

    @Test
    @DisplayName("Should clean up empty game after both players leave")
    void testCleanupEmptyGame() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        gameService.removePlayer("player1");
        gameService.removePlayer("player2");

        assertNull(gameService.getGameByPlayerId("player1"));
        assertNull(gameService.getGameByPlayerId("player2"));
    }

    @Test
    @DisplayName("Should allow new player to join after player leaves waiting game")
    void testRejoinAfterPlayerLeaves() {
        gameService.joinGame(mockSession1);
        gameService.removePlayer("player1");

        gameService.joinGame(mockSession2);
        gameService.joinGame(mockSession3);

        GameSession game = gameService.getGameByPlayerId("player2");
        assertTrue(game.isFull());
        assertEquals("player2", game.getPlayer1().getPlayerId());
        assertEquals("player3", game.getPlayer2().getPlayerId());
    }

    // ========== Opponent Retrieval Tests ==========

    @Test
    @DisplayName("Should get opponent for player 1")
    void testGetOpponentForPlayer1() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        Player opponent = gameService.getOpponent(game, "player1");

        assertNotNull(opponent);
        assertEquals("player2", opponent.getPlayerId());
    }

    @Test
    @DisplayName("Should get opponent for player 2")
    void testGetOpponentForPlayer2() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player2");
        Player opponent = gameService.getOpponent(game, "player2");

        assertNotNull(opponent);
        assertEquals("player1", opponent.getPlayerId());
    }

    @Test
    @DisplayName("Should return null opponent for non-existent player")
    void testGetOpponentForNonExistentPlayer() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        Player opponent = gameService.getOpponent(game, "non-existent");

        assertNull(opponent);
    }

    @Test
    @DisplayName("Should return null opponent when game not full")
    void testGetOpponentWhenGameNotFull() {
        gameService.joinGame(mockSession1);

        GameSession game = gameService.getGameByPlayerId("player1");
        Player opponent = gameService.getOpponent(game, "player1");

        assertNull(opponent);
    }

    // ========== Rematch Functionality Tests ==========

    @Test
    @DisplayName("Should handle single player rematch request")
    void testSinglePlayerRematch() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        game.endGame();

        boolean result = gameService.requestRematch("player1");

        assertFalse(result); // Only one player wants rematch
        assertEquals(GameSession.GameStatus.FINISHED, game.getStatus());
        assertTrue(game.isPlayer1WantsRematch());
        assertFalse(game.isPlayer2WantsRematch());
    }

    @Test
    @DisplayName("Should start rematch when both players agree")
    void testBothPlayersRematch() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        game.endGame();

        gameService.requestRematch("player1");
        boolean result = gameService.requestRematch("player2");

        assertTrue(result);
        assertEquals(GameSession.GameStatus.RUNNING, game.getStatus());
        assertFalse(game.isPlayer1WantsRematch()); // Reset after rematch
        assertFalse(game.isPlayer2WantsRematch());

        // Verify new questions were generated
        verify(questionGenerator, times(4)).generateQuestions(20); // 2 for initial game + 2 for rematch
    }

    @Test
    @DisplayName("Should not allow rematch for non-existent player")
    void testRematchNonExistentPlayer() {
        boolean result = gameService.requestRematch("non-existent");
        assertFalse(result);
    }

    @Test
    @DisplayName("Should not allow rematch when game is not finished")
    void testRematchGameNotFinished() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        // Game is RUNNING, not FINISHED
        boolean result = gameService.requestRematch("player1");

        assertFalse(result);
    }

    @Test
    @DisplayName("Should reset scores and questions for rematch")
    void testRematchResetsState() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        Player player1 = game.getPlayer1();
        Player player2 = game.getPlayer2();

        // Simulate some gameplay
        player1.incrementScore();
        player1.incrementScore();
        player2.incrementScore();
        player1.nextQuestion();
        player2.nextQuestion();

        game.endGame();

        gameService.requestRematch("player1");
        gameService.requestRematch("player2");

        // Verify reset
        assertEquals(0, player1.getScore());
        assertEquals(0, player2.getScore());
        assertEquals(0, player1.getCurrentQuestionIndex());
        assertEquals(0, player2.getCurrentQuestionIndex());
        assertEquals(GameSession.GameStatus.RUNNING, game.getStatus());
    }

    @Test
    @DisplayName("Should maintain same players in rematch")
    void testRematchSamePlayers() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        String player1Id = game.getPlayer1().getPlayerId();
        String player2Id = game.getPlayer2().getPlayerId();

        game.endGame();
        gameService.requestRematch("player1");
        gameService.requestRematch("player2");

        assertEquals(player1Id, game.getPlayer1().getPlayerId());
        assertEquals(player2Id, game.getPlayer2().getPlayerId());
    }

    // ========== Concurrent Access Tests ==========

    @Test
    @DisplayName("Should handle concurrent player joins safely")
    void testConcurrentPlayerJoins() throws InterruptedException {
        List<WebSocketSession> sessions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            WebSocketSession session = mock(WebSocketSession.class);
            lenient().when(session.getId()).thenReturn("concurrent-player-" + i);
            lenient().when(session.isOpen()).thenReturn(true);
            sessions.add(session);
        }

        List<Thread> threads = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            Thread thread = new Thread(() -> gameService.joinGame(session));
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Verify that games were created properly
        // With 10 players, we should have 5 full games
        int fullGames = 0;
        int waitingGames = 0;

        for (int i = 0; i < 10; i++) {
            GameSession game = gameService.getGameByPlayerId("concurrent-player-" + i);
            if (game != null) {
                if (game.isFull()) {
                    fullGames++;
                } else {
                    waitingGames++;
                }
            }
        }

        // At least some games should be full
        assertTrue(fullGames > 0);
    }

    @Test
    @DisplayName("Should handle concurrent player removals safely")
    void testConcurrentPlayerRemovals() throws InterruptedException {
        // Create multiple games
        for (int i = 0; i < 10; i++) {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("remove-player-" + i);
            gameService.joinGame(session);
        }

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            Thread thread = new Thread(() -> gameService.removePlayer("remove-player-" + index));
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All players should be removed
        for (int i = 0; i < 10; i++) {
            assertNull(gameService.getGameByPlayerId("remove-player-" + i));
        }
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle answer submission when player has no current question")
    void testSubmitAnswerNoQuestion() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player1");
        Player player1 = game.getPlayer1();

        // Move past all questions
        player1.setQuestions(new ArrayList<>());

        boolean result = gameService.submitAnswer("player1", 42);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should handle rematch request from only player 2")
    void testRematchPlayer2First() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        GameSession game = gameService.getGameByPlayerId("player2");
        game.endGame();

        boolean result = gameService.requestRematch("player2");

        assertFalse(result);
        assertTrue(game.isPlayer2WantsRematch());
        assertFalse(game.isPlayer1WantsRematch());
    }

    @Test
    @DisplayName("Should generate exactly 20 questions per player")
    void testQuestionCount() {
        gameService.joinGame(mockSession1);
        gameService.joinGame(mockSession2);

        verify(questionGenerator, times(2)).generateQuestions(20);
    }

    // Helper method to create mock questions
    private List<Question> createMockQuestions(int count) {
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(new Question((i + 1) + " + " + (i + 1), (i + 1) * 2, Question.OperationType.ADD));
        }
        return questions;
    }
}
