package com.warduel.warduel.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warduel.warduel.model.GameSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für DTO Serialisierung/Deserialisierung
 * Stellt sicher dass JSON korrekt zu/von Java-Objekten konvertiert wird
 */
class DtoSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    /**
     * Test: AnswerMessage Serialisierung
     */
    @Test
    void shouldSerializeAnswerMessage() throws Exception {
        // Arrange
        AnswerMessage message = new AnswerMessage(42);

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"type\":\"ANSWER\""));
        assertTrue(json.contains("\"answer\":42"));
    }

    /**
     * Test: AnswerMessage Deserialisierung
     */
    @Test
    void shouldDeserializeAnswerMessage() throws Exception {
        // Arrange
        String json = "{\"type\":\"ANSWER\",\"answer\":42}";

        // Act
        BaseMessage baseMessage = objectMapper.readValue(json, BaseMessage.class);

        // Assert
        assertInstanceOf(AnswerMessage.class, baseMessage);
        AnswerMessage answerMessage = (AnswerMessage) baseMessage;
        assertEquals(42, answerMessage.getAnswer());
    }

    /**
     * Test: GameStateMessage Serialisierung
     */
    @Test
    void shouldSerializeGameStateMessage() throws Exception {
        // Arrange
        GameStateMessage message = new GameStateMessage(
                GameSession.GameStatus.RUNNING,
                "Spieler 1",
                "Spieler 2",
                60L
        );

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"type\":\"GAME_STATE\""));
        assertTrue(json.contains("\"gameStatus\":\"RUNNING\""));
        assertTrue(json.contains("\"yourName\":\"Spieler 1\""));
        assertTrue(json.contains("\"opponentName\":\"Spieler 2\""));
        assertTrue(json.contains("\"remainingSeconds\":60"));
    }

    /**
     * Test: GameStateMessage Deserialisierung
     */
    @Test
    void shouldDeserializeGameStateMessage() throws Exception {
        // Arrange
        String json = "{\"type\":\"GAME_STATE\",\"gameStatus\":\"RUNNING\"," +
                "\"yourName\":\"Spieler 1\",\"opponentName\":\"Spieler 2\"," +
                "\"remainingSeconds\":60}";

        // Act
        BaseMessage baseMessage = objectMapper.readValue(json, BaseMessage.class);

        // Assert
        assertInstanceOf(GameStateMessage.class, baseMessage);
        GameStateMessage gameStateMessage = (GameStateMessage) baseMessage;
        assertEquals(GameSession.GameStatus.RUNNING, gameStateMessage.getGameStatus());
        assertEquals("Spieler 1", gameStateMessage.getYourName());
        assertEquals("Spieler 2", gameStateMessage.getOpponentName());
        assertEquals(60L, gameStateMessage.getRemainingSeconds());
    }

    /**
     * Test: QuestionMessage Serialisierung
     */
    @Test
    void shouldSerializeQuestionMessage() throws Exception {
        // Arrange
        QuestionMessage message = new QuestionMessage("5 + 3", 1, 55L);

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"type\":\"QUESTION\""));
        assertTrue(json.contains("\"questionText\":\"5 + 3\""));
        assertTrue(json.contains("\"questionNumber\":1"));
        assertTrue(json.contains("\"remainingSeconds\":55"));
    }

    /**
     * Test: QuestionMessage Deserialisierung
     */
    @Test
    void shouldDeserializeQuestionMessage() throws Exception {
        // Arrange
        String json = "{\"type\":\"QUESTION\",\"questionText\":\"10 - 4\"," +
                "\"questionNumber\":2,\"remainingSeconds\":50}";

        // Act
        BaseMessage baseMessage = objectMapper.readValue(json, BaseMessage.class);

        // Assert
        assertInstanceOf(QuestionMessage.class, baseMessage);
        QuestionMessage questionMessage = (QuestionMessage) baseMessage;
        assertEquals("10 - 4", questionMessage.getQuestionText());
        assertEquals(2, questionMessage.getQuestionNumber());
        assertEquals(50L, questionMessage.getRemainingSeconds());
    }

    /**
     * Test: ScoreUpdateMessage Serialisierung
     */
    @Test
    void shouldSerializeScoreUpdateMessage() throws Exception {
        // Arrange
        ScoreUpdateMessage message = new ScoreUpdateMessage(5, 3, true);

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"type\":\"SCORE_UPDATE\""));
        assertTrue(json.contains("\"yourScore\":5"));
        assertTrue(json.contains("\"opponentScore\":3"));
        assertTrue(json.contains("\"wasCorrect\":true"));
    }

    /**
     * Test: ScoreUpdateMessage Deserialisierung
     */
    @Test
    void shouldDeserializeScoreUpdateMessage() throws Exception {
        // Arrange
        String json = "{\"type\":\"SCORE_UPDATE\",\"yourScore\":7," +
                "\"opponentScore\":4,\"wasCorrect\":false}";

        // Act
        BaseMessage baseMessage = objectMapper.readValue(json, BaseMessage.class);

        // Assert
        assertInstanceOf(ScoreUpdateMessage.class, baseMessage);
        ScoreUpdateMessage scoreUpdateMessage = (ScoreUpdateMessage) baseMessage;
        assertEquals(7, scoreUpdateMessage.getYourScore());
        assertEquals(4, scoreUpdateMessage.getOpponentScore());
        assertFalse(scoreUpdateMessage.isWasCorrect());
    }

    /**
     * Test: GameOverMessage Serialisierung
     */
    @Test
    void shouldSerializeGameOverMessage() throws Exception {
        // Arrange
        GameOverMessage message = new GameOverMessage(10, 8, true, false, "Spieler 1");

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"type\":\"GAME_OVER\""));
        assertTrue(json.contains("\"yourScore\":10"));
        assertTrue(json.contains("\"opponentScore\":8"));
        assertTrue(json.contains("\"youWon\":true"));
        assertTrue(json.contains("\"draw\":false"));
        assertTrue(json.contains("\"winnerName\":\"Spieler 1\""));
    }

    /**
     * Test: GameOverMessage Deserialisierung
     */
    @Test
    void shouldDeserializeGameOverMessage() throws Exception {
        // Arrange
        String json = "{\"type\":\"GAME_OVER\",\"yourScore\":8,\"opponentScore\":10," +
                "\"youWon\":false,\"draw\":false,\"winnerName\":\"Spieler 2\"}";

        // Act
        BaseMessage baseMessage = objectMapper.readValue(json, BaseMessage.class);

        // Assert
        assertInstanceOf(GameOverMessage.class, baseMessage);
        GameOverMessage gameOverMessage = (GameOverMessage) baseMessage;
        assertEquals(8, gameOverMessage.getYourScore());
        assertEquals(10, gameOverMessage.getOpponentScore());
        assertFalse(gameOverMessage.isYouWon());
        assertFalse(gameOverMessage.isDraw());
        assertEquals("Spieler 2", gameOverMessage.getWinnerName());
    }

    /**
     * Test: GameOverMessage bei Unentschieden
     */
    @Test
    void shouldSerializeGameOverMessageWithDraw() throws Exception {
        // Arrange
        GameOverMessage message = new GameOverMessage(10, 10, false, true, "Unentschieden");

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"draw\":true"));
        assertTrue(json.contains("\"winnerName\":\"Unentschieden\""));
    }

    /**
     * Test: ErrorMessage Serialisierung
     */
    @Test
    void shouldSerializeErrorMessage() throws Exception {
        // Arrange
        ErrorMessage message = new ErrorMessage("Verbindungsfehler", "ERR_CONNECTION");

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"type\":\"ERROR\""));
        assertTrue(json.contains("\"errorMessage\":\"Verbindungsfehler\""));
        assertTrue(json.contains("\"errorCode\":\"ERR_CONNECTION\""));
    }

    /**
     * Test: ErrorMessage Deserialisierung
     */
    @Test
    void shouldDeserializeErrorMessage() throws Exception {
        // Arrange
        String json = "{\"type\":\"ERROR\",\"errorMessage\":\"Timeout\"," +
                "\"errorCode\":\"ERR_TIMEOUT\"}";

        // Act
        BaseMessage baseMessage = objectMapper.readValue(json, BaseMessage.class);

        // Assert
        assertInstanceOf(ErrorMessage.class, baseMessage);
        ErrorMessage errorMessage = (ErrorMessage) baseMessage;
        assertEquals("Timeout", errorMessage.getErrorMessage());
        assertEquals("ERR_TIMEOUT", errorMessage.getErrorCode());
    }

    /**
     * Test: RematchMessage Serialisierung
     */
    @Test
    void shouldSerializeRematchMessage() throws Exception {
        // Arrange
        RematchMessage message = new RematchMessage(true, false, "Warte auf Gegner...");

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"type\":\"REMATCH\""));
        assertTrue(json.contains("\"requestRematch\":true"));
        assertTrue(json.contains("\"opponentAccepted\":false"));
        assertTrue(json.contains("\"statusMessage\":\"Warte auf Gegner...\""));
    }

    /**
     * Test: RematchMessage Deserialisierung
     */
    @Test
    void shouldDeserializeRematchMessage() throws Exception {
        // Arrange
        String json = "{\"type\":\"REMATCH\",\"requestRematch\":true," +
                "\"opponentAccepted\":true,\"statusMessage\":\"Rematch startet!\"}";

        // Act
        BaseMessage baseMessage = objectMapper.readValue(json, BaseMessage.class);

        // Assert
        assertInstanceOf(RematchMessage.class, baseMessage);
        RematchMessage rematchMessage = (RematchMessage) baseMessage;
        assertTrue(rematchMessage.isRequestRematch());
        assertTrue(rematchMessage.isOpponentAccepted());
        assertEquals("Rematch startet!", rematchMessage.getStatusMessage());
    }

    /**
     * Test: JoinGameMessage Serialisierung
     */
    @Test
    void shouldSerializeJoinGameMessage() throws Exception {
        // Arrange
        JoinGameMessage message = new JoinGameMessage();

        // Act
        String json = objectMapper.writeValueAsString(message);

        // Assert
        assertTrue(json.contains("\"type\":\"JOIN_GAME\""));
    }

    /**
     * Test: JoinGameMessage Deserialisierung
     */
    @Test
    void shouldDeserializeJoinGameMessage() throws Exception {
        // Arrange
        String json = "{\"type\":\"JOIN_GAME\"}";

        // Act
        BaseMessage baseMessage = objectMapper.readValue(json, BaseMessage.class);

        // Assert
        assertInstanceOf(JoinGameMessage.class, baseMessage);
    }

    /**
     * Test: Polymorphismus - BaseMessage erkennt verschiedene Typen
     */
    @Test
    void shouldHandlePolymorphicDeserialization() throws Exception {
        // Arrange
        String[] jsons = {
                "{\"type\":\"ANSWER\",\"answer\":5}",
                "{\"type\":\"QUESTION\",\"questionText\":\"1+1\",\"questionNumber\":1,\"remainingSeconds\":60}",
                "{\"type\":\"ERROR\",\"errorMessage\":\"Test\",\"errorCode\":null}",
                "{\"type\":\"GAME_STATE\",\"gameStatus\":\"WAITING\",\"yourName\":\"P1\",\"opponentName\":null,\"remainingSeconds\":null}"
        };

        Class<?>[] expectedTypes = {
                AnswerMessage.class,
                QuestionMessage.class,
                ErrorMessage.class,
                GameStateMessage.class
        };

        // Act & Assert
        for(int i = 0; i < jsons.length; i++) {
            BaseMessage message = objectMapper.readValue(jsons[i], BaseMessage.class);
            assertInstanceOf(expectedTypes[i], message,
                    "Failed to deserialize: " + jsons[i]);
        }
    }

    /**
     * Test: Null-Werte werden korrekt behandelt
     */
    @Test
    void shouldHandleNullValues() throws Exception {
        // Arrange
        GameStateMessage message = new GameStateMessage(
                GameSession.GameStatus.WAITING,
                "Spieler 1",
                null,  // Noch kein Gegner
                null   // Noch keine Zeit
        );

        // Act
        String json = objectMapper.writeValueAsString(message);
        GameStateMessage deserialized = objectMapper.readValue(json, GameStateMessage.class);

        // Assert
        assertNotNull(deserialized);
        assertEquals("Spieler 1", deserialized.getYourName());
        assertNull(deserialized.getOpponentName());
        assertNull(deserialized.getRemainingSeconds());
    }

    /**
     * Test: Unbekannter Message-Typ wirft keine Exception
     */
    @Test
    void shouldHandleUnknownMessageType() {
        // Arrange
        String json = "{\"type\":\"UNKNOWN_TYPE\",\"someField\":\"value\"}";

        // Act & Assert
        assertThrows(Exception.class, () -> {
            objectMapper.readValue(json, BaseMessage.class);
        }, "Unknown message type should throw exception");
    }
}