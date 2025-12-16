package com.warduel.warduel.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warduel.warduel.dto.*;
import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.Player;
import com.warduel.warduel.model.Question;
import com.warduel.warduel.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Tests für GameWebSocketHandler
 */
@ExtendWith(MockitoExtension.class)
class GameWebSocketHandlerTest {

    @Mock
    private GameService gameService;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private GameWebSocketHandler handler;
    private ObjectMapper objectMapper;
    private GameSession mockGame;

    @BeforeEach
    void setUp() {
        handler = new GameWebSocketHandler(gameService);
        objectMapper = new ObjectMapper();

        // Lenient für alle Session-Mocks
        lenient().when(session1.getId()).thenReturn("player1");
        lenient().when(session1.isOpen()).thenReturn(true);
        lenient().when(session2.getId()).thenReturn("player2");
        lenient().when(session2.isOpen()).thenReturn(true);

        // Mock Game erstellen
        mockGame = new GameSession();
        Player player1 = new Player("player1", session1, "Spieler 1");
        Player player2 = new Player("player2", session2, "Spieler 2");
        mockGame.addPlayer(player1);
        mockGame.addPlayer(player2);

        // Fragen hinzufügen
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        questions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));
        mockGame.setQuestions(questions);
    }

    @Test
    void shouldHandleConnectionEstablished() throws Exception {
        when(gameService.joinGame(session1)).thenReturn(mockGame);

        handler.afterConnectionEstablished(session1);

        verify(gameService).joinGame(session1);
        verify(session1, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldHandleCorrectAnswer() throws Exception {
        mockGame.startGame();

        when(gameService.getGameByPlayerId("player1")).thenReturn(mockGame);
        lenient().when(gameService.submitAnswer(eq("player1"), eq(8))).thenAnswer(invocation -> {
            mockGame.getPlayer1().incrementScore();
            return true;
        });
        lenient().when(gameService.getOpponent(mockGame, "player1")).thenReturn(mockGame.getPlayer2());

        AnswerMessage answerMsg = new AnswerMessage(8);
        String json = objectMapper.writeValueAsString(answerMsg);
        TextMessage message = new TextMessage(json);

        handler.handleTextMessage(session1, message);

        verify(gameService).submitAnswer("player1", 8);
        assertEquals(1, mockGame.getPlayer1().getScore());
    }

    @Test
    void shouldHandleIncorrectAnswer() throws Exception {
        mockGame.startGame();

        when(gameService.getGameByPlayerId("player1")).thenReturn(mockGame);
        lenient().when(gameService.submitAnswer("player1", 999)).thenReturn(false);
        lenient().when(gameService.getOpponent(mockGame, "player1")).thenReturn(mockGame.getPlayer2());

        AnswerMessage answerMsg = new AnswerMessage(999);
        String json = objectMapper.writeValueAsString(answerMsg);
        TextMessage message = new TextMessage(json);

        handler.handleTextMessage(session1, message);

        verify(gameService).submitAnswer("player1", 999);
        assertEquals(0, mockGame.getPlayer1().getScore());
    }

    @Test
    void shouldHandleRematchRequestOnePlayer() throws Exception {
        mockGame.startGame();
        mockGame.endGame();

        when(gameService.getGameByPlayerId("player1")).thenReturn(mockGame);
        when(gameService.requestRematch("player1")).thenReturn(false);
        when(gameService.getOpponent(mockGame, "player1")).thenReturn(mockGame.getPlayer2());

        RematchMessage rematchMsg = new RematchMessage(true, false, "");
        String json = objectMapper.writeValueAsString(rematchMsg);
        TextMessage message = new TextMessage(json);

        handler.handleTextMessage(session1, message);

        verify(gameService).requestRematch("player1");
        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldHandleRematchRequestBothPlayers() throws Exception {
        mockGame.startGame();
        mockGame.endGame();

        when(gameService.getGameByPlayerId("player1")).thenReturn(mockGame);
        when(gameService.requestRematch("player1")).thenAnswer(invocation -> {
            mockGame.resetForRematch();
            mockGame.setQuestions(List.of(
                    new Question("1 + 1", 2, Question.OperationType.ADD)
            ));
            mockGame.startGame();
            return true;
        });

        RematchMessage rematchMsg = new RematchMessage(true, false, "");
        String json = objectMapper.writeValueAsString(rematchMsg);
        TextMessage message = new TextMessage(json);

        handler.handleTextMessage(session1, message);

        verify(gameService).requestRematch("player1");
        assertTrue(mockGame.getStatus() == GameSession.GameStatus.RUNNING);
    }

    @Test
    void shouldHandleConnectionClosed() throws Exception {
        when(gameService.getGameByPlayerId("player1")).thenReturn(mockGame);
        when(gameService.getOpponent(mockGame, "player1")).thenReturn(mockGame.getPlayer2());

        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        verify(gameService).removePlayer("player1");
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldRejectAnswerWhenGameNotRunning() throws Exception {
        when(gameService.getGameByPlayerId("player1")).thenReturn(mockGame);

        AnswerMessage answerMsg = new AnswerMessage(8);
        String json = objectMapper.writeValueAsString(answerMsg);
        TextMessage message = new TextMessage(json);

        handler.handleTextMessage(session1, message);

        verify(gameService, never()).submitAnswer(anyString(), anyInt());
        verify(session1).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldHandleInvalidJson() throws Exception {
        TextMessage invalidMessage = new TextMessage("{ invalid json }");

        assertDoesNotThrow(() -> handler.handleTextMessage(session1, invalidMessage));
        verify(session1).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldSendScoreUpdateToBothPlayers() throws Exception {
        mockGame.startGame();

        when(gameService.getGameByPlayerId("player1")).thenReturn(mockGame);
        lenient().when(gameService.submitAnswer(eq("player1"), eq(8))).thenAnswer(invocation -> {
            mockGame.getPlayer1().incrementScore();
            return true;
        });
        lenient().when(gameService.getOpponent(mockGame, "player1")).thenReturn(mockGame.getPlayer2());

        AnswerMessage answerMsg = new AnswerMessage(8);
        String json = objectMapper.writeValueAsString(answerMsg);
        TextMessage message = new TextMessage(json);

        handler.handleTextMessage(session1, message);

        ArgumentCaptor<TextMessage> captor1 = ArgumentCaptor.forClass(TextMessage.class);
        ArgumentCaptor<TextMessage> captor2 = ArgumentCaptor.forClass(TextMessage.class);

        verify(session1, atLeastOnce()).sendMessage(captor1.capture());
        verify(session2, atLeastOnce()).sendMessage(captor2.capture());

        boolean found1 = captor1.getAllValues().stream()
                .anyMatch(msg -> msg.getPayload().contains("SCORE_UPDATE"));
        boolean found2 = captor2.getAllValues().stream()
                .anyMatch(msg -> msg.getPayload().contains("SCORE_UPDATE"));

        assertTrue(found1);
        assertTrue(found2);
    }
}