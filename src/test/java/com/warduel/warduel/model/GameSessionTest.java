package com.warduel.warduel.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameSessionTest {

    private GameSession gameSession;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        gameSession = new GameSession();

        // Mock WebSocketSession
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);

        player1 = new Player("player1", session1, "");
        player2 = new Player("player2", session2, "");
    }

    @Test
    void shouldStartWithWaitingStatus() {
        assertEquals(GameSession.GameStatus.WAITING, gameSession.getStatus());
    }

    @Test
    void shouldAddFirstPlayer() {
        // When
        boolean added = gameSession.addPlayer(player1);

        // Then
        assertTrue(added);
        assertEquals("Spieler 1", player1.getDisplayName());
        assertEquals(GameSession.GameStatus.WAITING, gameSession.getStatus());
    }

    @Test
    void shouldAddSecondPlayerAndBecomeReady() {
        // Given
        gameSession.addPlayer(player1);

        // When
        boolean added = gameSession.addPlayer(player2);

        // Then
        assertTrue(added);
        assertEquals("Spieler 2", player2.getDisplayName());
        assertEquals(GameSession.GameStatus.READY, gameSession.getStatus());
        assertTrue(gameSession.isFull());
    }

    @Test
    void shouldNotAddThirdPlayer() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        WebSocketSession session3 = mock(WebSocketSession.class);
        Player player3 = new Player("player3", session3, "");

        // When
        boolean added = gameSession.addPlayer(player3);

        // Then
        assertFalse(added);
    }

    @Test
    void shouldStartGameWhenReady() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        gameSession.setQuestions(questions);

        // When
        boolean started = gameSession.startGame();

        // Then
        assertTrue(started);
        assertEquals(GameSession.GameStatus.RUNNING, gameSession.getStatus());
        assertNotNull(gameSession.getStartTime());
        assertNotNull(gameSession.getEndTime());
    }

    @Test
    void shouldNotStartGameWhenNotReady() {
        // Given - nur ein Spieler
        gameSession.addPlayer(player1);

        // When
        boolean started = gameSession.startGame();

        // Then
        assertFalse(started);
        assertEquals(GameSession.GameStatus.WAITING, gameSession.getStatus());
    }

    @Test
    void shouldSubmitCorrectAnswer() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        gameSession.setQuestions(questions);
        gameSession.startGame();

        // When
        boolean correct = gameSession.submitAnswer(player1, 8);

        // Then
        assertTrue(correct);
        assertEquals(1, player1.getScore());
    }

    @Test
    void shouldSubmitWrongAnswer() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        gameSession.setQuestions(questions);
        gameSession.startGame();

        // When
        boolean correct = gameSession.submitAnswer(player1, 7);

        // Then
        assertFalse(correct);
        assertEquals(0, player1.getScore());
    }

    @Test
    void shouldDetermineWinner() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        player1.incrementScore();
        player1.incrementScore();
        player1.incrementScore();
        player2.incrementScore();

        // When
        Player winner = gameSession.getWinner();

        // Then
        assertEquals(player1, winner);
    }

    @Test
    void shouldReturnNullWhenTied() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);
        player1.incrementScore();
        player2.incrementScore();

        // When
        Player winner = gameSession.getWinner();

        // Then
        assertNull(winner);
    }

    @Test
    void shouldRemovePlayer() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        // When
        gameSession.removePlayer("player1");

        // Then
        assertNull(gameSession.getPlayer1());
        assertNotNull(gameSession.getPlayer2());
        assertEquals(GameSession.GameStatus.WAITING, gameSession.getStatus());
    }

    @Test
    void shouldResetForRematch() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        gameSession.setQuestions(questions);

        gameSession.startGame();
        gameSession.endGame();

        player1.incrementScore();
        player2.incrementScore();

        // When
        boolean reset = gameSession.resetForRematch();

        // Then
        assertTrue(reset);
        assertEquals(GameSession.GameStatus.READY, gameSession.getStatus());
        assertEquals(0, player1.getScore());
        assertEquals(0, player2.getScore());
        assertEquals(0, gameSession.getQuestions().size());
    }

    @Test
    void shouldHandleRematchFlags() {
        // Given
        gameSession.addPlayer(player1);
        gameSession.addPlayer(player2);

        // When
        gameSession.setPlayer1Rematch(true);

        // Then
        assertTrue(gameSession.doesPlayerWantRematch("player1"));
        assertFalse(gameSession.doesPlayerWantRematch("player2"));
        assertFalse(gameSession.bothWantRematch());

        // When both want rematch
        gameSession.setPlayer2Rematch(true);

        // Then
        assertTrue(gameSession.bothWantRematch());
    }
}