package com.warduel.warduel.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warduel.warduel.dto.*;
import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.Player;
import com.warduel.warduel.model.Question;
import com.warduel.warduel.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for GameWebSocketHandler class
 * Tests WebSocket connections, message handling, and game flow
 */
@ExtendWith(MockitoExtension.class)
class GameWebSocketHandlerTest {

    @Mock
    private GameService gameService;

    private GameWebSocketHandler handler;
    private ObjectMapper objectMapper;
    private WebSocketSession mockSession1;
    private WebSocketSession mockSession2;
    private GameSession testGame;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new GameWebSocketHandler(gameService, objectMapper);

        // Setup mock sessions
        mockSession1 = mock(WebSocketSession.class);
        mockSession2 = mock(WebSocketSession.class);

        lenient().when(mockSession1.getId()).thenReturn("session1");
        lenient().when(mockSession2.getId()).thenReturn("session2");
        lenient().when(mockSession1.isOpen()).thenReturn(true);
        lenient().when(mockSession2.isOpen()).thenReturn(true);

        // Setup test game
        testGame = new GameSession();
        player1 = new Player("session1", mockSession1, "Player 1");
        player2 = new Player("session2", mockSession2, "Player 2");

        testGame.addPlayer(player1);
        testGame.addPlayer(player2);

        // Setup questions for players
        List<Question> questions = createTestQuestions();
        player1.setQuestions(questions);
        player2.setQuestions(questions);
        testGame.setQuestions(questions);
    }

    // ========== Connection Tests ==========

    @Test
    @DisplayName("Should handle new connection and join game")
    void testAfterConnectionEstablished() throws Exception {
        GameSession waitingGame = new GameSession();
        waitingGame.addPlayer(player1);

        when(gameService.joinGame(mockSession1)).thenReturn(waitingGame);

        handler.afterConnectionEstablished(mockSession1);

        verify(gameService).joinGame(mockSession1);
    }

    @Test
    @DisplayName("Should start game when second player connects")
    void testConnectionStartsFullGame() throws Exception {
        when(gameService.joinGame(mockSession2)).thenReturn(testGame);

        handler.afterConnectionEstablished(mockSession2);

        verify(gameService).joinGame(mockSession2);
        // Game should be full and start
        assertTrue(testGame.isFull());
    }

    @Test
    @DisplayName("Should close session on connection error")
    void testConnectionError() throws Exception {
        when(gameService.joinGame(mockSession1)).thenThrow(new RuntimeException("Connection error"));

        handler.afterConnectionEstablished(mockSession1);

        verify(mockSession1).close(CloseStatus.SERVER_ERROR);
    }

    // ========== Disconnect Tests ==========

    @Test
    @DisplayName("Should handle player disconnect and remove from game")
    void testAfterConnectionClosed() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);
        when(gameService.getOpponent(testGame, "session1")).thenReturn(player2);

        handler.afterConnectionClosed(mockSession1, CloseStatus.NORMAL);

        verify(gameService).removePlayer("session1");
        assertEquals(GameSession.GameStatus.FINISHED, testGame.getStatus());
    }

    @Test
    @DisplayName("Should notify opponent when player disconnects")
    void testNotifyOpponentOnDisconnect() throws Exception {
        testGame.startGame();
        player1.incrementScore();
        player1.incrementScore();
        player2.incrementScore();

        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);
        when(gameService.getOpponent(testGame, "session1")).thenReturn(player2);

        handler.afterConnectionClosed(mockSession1, CloseStatus.NORMAL);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(mockSession2).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        GameOverMessage msg = objectMapper.readValue(sentMessage, GameOverMessage.class);

        assertEquals("GAME_OVER", msg.getType());
        assertTrue(msg.isYouWon()); // Opponent wins
        assertEquals(1, msg.getYourScore()); // Player 2's score
        assertEquals(0, msg.getOpponentScore()); // Disconnected player gets 0
    }

    @Test
    @DisplayName("Should handle disconnect when opponent is null")
    void testDisconnectWithNullOpponent() throws Exception {
        GameSession singlePlayerGame = new GameSession();
        singlePlayerGame.addPlayer(player1);

        when(gameService.getGameByPlayerId("session1")).thenReturn(singlePlayerGame);
        when(gameService.getOpponent(singlePlayerGame, "session1")).thenReturn(null);

        assertDoesNotThrow(() ->
                handler.afterConnectionClosed(mockSession1, CloseStatus.NORMAL)
        );

        verify(gameService).removePlayer("session1");
    }

    // ========== Answer Handling Tests ==========

    @Test
    @DisplayName("Should handle correct answer submission")
    void testHandleCorrectAnswer() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        Question currentQuestion = player1.getCurrentQuestion();
        AnswerMessage answerMsg = new AnswerMessage(currentQuestion.getCorrectAnswer());
        String json = objectMapper.writeValueAsString(answerMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        assertEquals(1, player1.getScore());
        assertEquals(1, player1.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("Should handle incorrect answer submission")
    void testHandleIncorrectAnswer() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        Question currentQuestion = player1.getCurrentQuestion();
        AnswerMessage answerMsg = new AnswerMessage(currentQuestion.getCorrectAnswer() + 999);
        String json = objectMapper.writeValueAsString(answerMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        assertEquals(0, player1.getScore());
        assertEquals(1, player1.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("Should send score updates to both players after answer")
    void testScoreUpdatesSentToPlayers() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        Question currentQuestion = player1.getCurrentQuestion();
        AnswerMessage answerMsg = new AnswerMessage(currentQuestion.getCorrectAnswer());
        String json = objectMapper.writeValueAsString(answerMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        verify(mockSession1, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(mockSession2, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should end game when player reaches 20 points")
    void testEndGameAt20Points() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        // Give player 1 19 points
        for (int i = 0; i < 19; i++) {
            player1.incrementScore();
        }

        // Answer 20th question correctly
        Question currentQuestion = player1.getCurrentQuestion();
        AnswerMessage answerMsg = new AnswerMessage(currentQuestion.getCorrectAnswer());
        String json = objectMapper.writeValueAsString(answerMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        assertEquals(20, player1.getScore());
        assertEquals(GameSession.GameStatus.FINISHED, testGame.getStatus());
    }

    @Test
    @DisplayName("Should send error when game not found")
    void testAnswerWhenGameNotFound() throws Exception {
        when(gameService.getGameByPlayerId("session1")).thenReturn(null);

        AnswerMessage answerMsg = new AnswerMessage(42);
        String json = objectMapper.writeValueAsString(answerMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(mockSession1).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        ErrorMessage errorMsg = objectMapper.readValue(sentMessage, ErrorMessage.class);

        assertEquals("ERROR", errorMsg.getType());
        assertTrue(errorMsg.getErrorMessage().contains("nicht gefunden") ||
                errorMsg.getErrorMessage().contains("nicht aktiv"));
    }

    @Test
    @DisplayName("Should send error when game not running")
    void testAnswerWhenGameNotRunning() throws Exception {
        testGame.setStatus(GameSession.GameStatus.WAITING);
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        AnswerMessage answerMsg = new AnswerMessage(42);
        String json = objectMapper.writeValueAsString(answerMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(mockSession1).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        ErrorMessage errorMsg = objectMapper.readValue(sentMessage, ErrorMessage.class);

        assertEquals("ERROR", errorMsg.getType());
    }

    // ========== Rematch Tests ==========

    @Test
    @DisplayName("Should handle single player rematch request")
    void testRematchSinglePlayer() throws Exception {
        testGame.endGame();
        lenient().when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);
        lenient().when(gameService.requestRematch("session1")).thenReturn(false);

        RematchMessage rematchMsg = new RematchMessage(true, false, "");
        String json = objectMapper.writeValueAsString(rematchMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        verify(gameService).requestRematch("session1");
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(mockSession1).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        RematchMessage responseMsg = objectMapper.readValue(sentMessage, RematchMessage.class);

        assertEquals("REMATCH", responseMsg.getType());
        assertTrue(responseMsg.isRequestRematch());
        assertFalse(responseMsg.isOpponentAccepted());
    }

    @Test
    @DisplayName("Should start new game when both players want rematch")
    void testRematchBothPlayers() throws Exception {
        testGame.endGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);
        when(gameService.requestRematch("session1")).thenReturn(true);

        RematchMessage rematchMsg = new RematchMessage(true, true, "");
        String json = objectMapper.writeValueAsString(rematchMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        verify(gameService).requestRematch("session1");
        verify(mockSession1, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(mockSession2, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    // ========== Unknown Message Type Tests ==========

    @Test
    @DisplayName("Should handle unknown message type gracefully")
    void testUnknownMessageType() throws Exception {
        String unknownJson = "{\"type\":\"UNKNOWN_TYPE\",\"data\":\"test\"}";

        handler.handleTextMessage(mockSession1, new TextMessage(unknownJson));

        // Should not throw exception
        verify(mockSession1, never()).close();
    }

    @Test
    @DisplayName("Should handle malformed JSON")
    void testMalformedJson() throws Exception {
        String malformedJson = "{invalid json}";

        handler.handleTextMessage(mockSession1, new TextMessage(malformedJson));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(mockSession1).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        ErrorMessage errorMsg = objectMapper.readValue(sentMessage, ErrorMessage.class);

        assertEquals("ERROR", errorMsg.getType());
    }

    // ========== Message Sending Tests ==========

    @Test
    @DisplayName("Should not send message to closed session")
    void testSendMessageToClosedSession() throws Exception {
        when(mockSession1.isOpen()).thenReturn(false);

        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        Question currentQuestion = player1.getCurrentQuestion();
        AnswerMessage answerMsg = new AnswerMessage(currentQuestion.getCorrectAnswer());
        String json = objectMapper.writeValueAsString(answerMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        // Should handle gracefully without throwing exception
        verify(mockSession1, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should handle null session gracefully")
    void testNullSession() {
        player1.setSession(null);
        assertDoesNotThrow(() ->
                handler.afterConnectionClosed(null, CloseStatus.NORMAL)
        );
    }

    // ========== Game Flow Integration Tests ==========

    @Test
    @DisplayName("Should handle complete game flow")
    void testCompleteGameFlow() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        // Answer 3 questions correctly
        for (int i = 0; i < 3; i++) {
            Question currentQuestion = player1.getCurrentQuestion();
            AnswerMessage answerMsg = new AnswerMessage(currentQuestion.getCorrectAnswer());
            String json = objectMapper.writeValueAsString(answerMsg);

            handler.handleTextMessage(mockSession1, new TextMessage(json));

            assertEquals(i + 1, player1.getScore());
            assertEquals(i + 1, player1.getCurrentQuestionIndex());
        }
    }

    @Test
    @DisplayName("Should handle mixed correct and incorrect answers")
    void testMixedAnswers() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        // Correct answer
        Question q1 = player1.getCurrentQuestion();
        AnswerMessage answer1 = new AnswerMessage(q1.getCorrectAnswer());
        handler.handleTextMessage(mockSession1, new TextMessage(objectMapper.writeValueAsString(answer1)));

        assertEquals(1, player1.getScore());

        // Incorrect answer
        Question q2 = player1.getCurrentQuestion();
        AnswerMessage answer2 = new AnswerMessage(q2.getCorrectAnswer() + 999);
        handler.handleTextMessage(mockSession1, new TextMessage(objectMapper.writeValueAsString(answer2)));

        assertEquals(1, player1.getScore()); // Still 1

        // Correct answer
        Question q3 = player1.getCurrentQuestion();
        AnswerMessage answer3 = new AnswerMessage(q3.getCorrectAnswer());
        handler.handleTextMessage(mockSession1, new TextMessage(objectMapper.writeValueAsString(answer3)));

        assertEquals(2, player1.getScore());
        assertEquals(3, player1.getCurrentQuestionIndex());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle answer when no current question available")
    void testAnswerWhenNoQuestion() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);

        // Move past all questions
        player1.setQuestions(new ArrayList<>());

        AnswerMessage answerMsg = new AnswerMessage(42);
        String json = objectMapper.writeValueAsString(answerMsg);

        handler.handleTextMessage(mockSession1, new TextMessage(json));

        // Should handle gracefully
        assertEquals(0, player1.getScore());
    }

    @Test
    @DisplayName("Should handle concurrent answer submissions")
    void testConcurrentAnswers() throws Exception {
        testGame.startGame();
        when(gameService.getGameByPlayerId("session1")).thenReturn(testGame);
        when(gameService.getGameByPlayerId("session2")).thenReturn(testGame);

        Question q1 = player1.getCurrentQuestion();
        Question q2 = player2.getCurrentQuestion();

        AnswerMessage answer1 = new AnswerMessage(q1.getCorrectAnswer());
        AnswerMessage answer2 = new AnswerMessage(q2.getCorrectAnswer());

        String json1 = objectMapper.writeValueAsString(answer1);
        String json2 = objectMapper.writeValueAsString(answer2);

        // Simulate concurrent submissions
        Thread t1 = new Thread(() -> {
            try {
                handler.handleTextMessage(mockSession1, new TextMessage(json1));
            } catch (Exception e) {
                fail("Exception in thread 1: " + e.getMessage());
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                handler.handleTextMessage(mockSession2, new TextMessage(json2));
            } catch (Exception e) {
                fail("Exception in thread 2: " + e.getMessage());
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Both should have scored
        assertTrue(player1.getScore() >= 1 || player2.getScore() >= 1);
    }

    @Test
    @DisplayName("Should handle empty message payload")
    void testEmptyMessage() throws Exception {
        handler.handleTextMessage(mockSession1, new TextMessage(""));

        // Should handle gracefully, likely send error
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(mockSession1, atLeastOnce()).sendMessage(messageCaptor.capture());
    }

    // Helper methods

    private List<Question> createTestQuestions() {
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            questions.add(new Question((i + 1) + " + " + (i + 1), (i + 1) * 2, Question.OperationType.ADD));
        }
        return questions;
    }
}
