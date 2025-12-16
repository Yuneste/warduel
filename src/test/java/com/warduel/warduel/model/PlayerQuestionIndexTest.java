package com.warduel.warduel.model;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test: Jeder Spieler hat unabhängigen Fragen-Index
 */
class PlayerQuestionIndexTest {

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @Test
    void shouldTrackQuestionIndexPerPlayer() {
        // Arrange
        GameSession game = new GameSession();
        Player player1 = new Player("p1", session1, "Player 1");
        Player player2 = new Player("p2", session2, "Player 2");

        game.addPlayer(player1);
        game.addPlayer(player2);

        List<Question> questions = List.of(
                new Question("1+1", 2, Question.OperationType.ADD),
                new Question("2+2", 4, Question.OperationType.ADD),
                new Question("3+3", 6, Question.OperationType.ADD)
        );
        game.setQuestions(questions);

        // Act & Assert
        // Beide starten bei Frage 0
        assertEquals(0, player1.getCurrentQuestionIndex());
        assertEquals(0, player2.getCurrentQuestionIndex());

        // Player 1 antwortet
        player1.nextQuestion();
        assertEquals(1, player1.getCurrentQuestionIndex());
        assertEquals(0, player2.getCurrentQuestionIndex()); // Player 2 unverändert!

        // Player 2 antwortet
        player2.nextQuestion();
        assertEquals(1, player1.getCurrentQuestionIndex());
        assertEquals(1, player2.getCurrentQuestionIndex());

        // Player 1 antwortet wieder
        player1.nextQuestion();
        assertEquals(2, player1.getCurrentQuestionIndex());
        assertEquals(1, player2.getCurrentQuestionIndex()); // Player 2 noch bei 1!
    }

    @Test
    void shouldGetCorrectQuestionForEachPlayer() {
        // Arrange
        GameSession game = new GameSession();
        Player player1 = new Player("p1", session1, "Player 1");
        Player player2 = new Player("p2", session2, "Player 2");

        game.addPlayer(player1);
        game.addPlayer(player2);

        List<Question> questions = List.of(
                new Question("1+1", 2, Question.OperationType.ADD),
                new Question("2+2", 4, Question.OperationType.ADD),
                new Question("3+3", 6, Question.OperationType.ADD)
        );
        game.setQuestions(questions);

        // Act
        Question q1_p1 = game.getCurrentQuestionForPlayer(player1);
        Question q1_p2 = game.getCurrentQuestionForPlayer(player2);

        // Assert - Beide bei Frage 1
        assertEquals("1+1", q1_p1.getQuestionText());
        assertEquals("1+1", q1_p2.getQuestionText());

        // Player 1 geht weiter
        player1.nextQuestion();

        Question q2_p1 = game.getCurrentQuestionForPlayer(player1);
        Question q2_p2 = game.getCurrentQuestionForPlayer(player2);

        // Assert - Player 1 bei Frage 2, Player 2 noch bei Frage 1
        assertEquals("2+2", q2_p1.getQuestionText());
        assertEquals("1+1", q2_p2.getQuestionText());
    }
}