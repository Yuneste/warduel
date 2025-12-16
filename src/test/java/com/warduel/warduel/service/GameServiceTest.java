package com.warduel.warduel.service;

import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameServiceTest {

    private GameService gameService;
    private QuestionGeneratorService questionGenerator;

    @BeforeEach
    void setUp() {
        questionGenerator = new QuestionGeneratorService();
        gameService = new GameService(questionGenerator);
    }

    @Test
    void shouldCreateNewGameForFirstPlayer() {
        // Given
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("player1");

        // When
        GameSession game = gameService.joinGame(session);

        // Then
        assertNotNull(game);
        assertEquals(GameSession.GameStatus.WAITING, game.getStatus());
        assertNotNull(game.getPlayer1());
        assertNull(game.getPlayer2());
    }

    @Test
    void shouldMatchTwoPlayersAndStartGame() {
        // Given
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("player1");

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("player2");

        // When
        GameSession game1 = gameService.joinGame(session1);
        GameSession game2 = gameService.joinGame(session2);

        // Then
        assertEquals(game1.getGameId(), game2.getGameId()); // Same game!
        assertEquals(GameSession.GameStatus.RUNNING, game1.getStatus());
        assertTrue(game1.isFull());
        assertFalse(game1.getQuestions().isEmpty());
    }

    @Test
    void shouldNotJoinSamePlayerTwice() {
        // Given
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("player1");

        // When
        GameSession game1 = gameService.joinGame(session);
        GameSession game2 = gameService.joinGame(session);

        // Then
        assertEquals(game1.getGameId(), game2.getGameId());
    }

    @Test
    void shouldSubmitAnswer() {
        // Given
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("player1");

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("player2");

        gameService.joinGame(session1);
        GameSession game = gameService.joinGame(session2);

        int correctAnswer = game.getCurrentQuestion().getCorrectAnswer();

        // When
        boolean correct = gameService.submitAnswer("player1", correctAnswer);

        // Then
        assertTrue(correct);
    }

    @Test
    void shouldRemovePlayer() {
        // Given
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("player1");

        GameSession game = gameService.joinGame(session);
        String gameId = game.getGameId();

        // When
        gameService.removePlayer("player1");

        // Then
        assertNull(gameService.getGameByPlayerId("player1"));
    }

    @Test
    void shouldHandleRematch() {
        // Given
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("player1");

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("player2");

        gameService.joinGame(session1);
        GameSession game = gameService.joinGame(session2);
        game.endGame();

        // When
        boolean rematch1 = gameService.requestRematch("player1");
        boolean rematch2 = gameService.requestRematch("player2");

        // Then
        assertFalse(rematch1); // Nur ein Spieler will
        assertTrue(rematch2);  // Beide wollen jetzt
        assertEquals(GameSession.GameStatus.RUNNING, game.getStatus());
    }
}