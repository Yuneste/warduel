package com.warduel.warduel.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DTO message serialization and deserialization
 * Ensures JSON messages can be properly converted to/from Java objects
 */
class MessageSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ========== AnswerMessage Tests ==========

    @Test
    @DisplayName("Should serialize and deserialize AnswerMessage")
    void testAnswerMessageSerialization() throws Exception {
        AnswerMessage original = new AnswerMessage(42);

        String json = objectMapper.writeValueAsString(original);
        AnswerMessage deserialized = objectMapper.readValue(json, AnswerMessage.class);

        assertEquals("ANSWER", deserialized.getType());
        assertEquals(42, deserialized.getAnswer());
    }

    @Test
    @DisplayName("Should deserialize AnswerMessage from JSON string")
    void testAnswerMessageDeserialization() throws Exception {
        String json = "{\"type\":\"ANSWER\",\"answer\":15}";

        AnswerMessage message = objectMapper.readValue(json, AnswerMessage.class);

        assertEquals("ANSWER", message.getType());
        assertEquals(15, message.getAnswer());
    }

    // ========== ErrorMessage Tests ==========

    @Test
    @DisplayName("Should serialize and deserialize ErrorMessage")
    void testErrorMessageSerialization() throws Exception {
        ErrorMessage original = new ErrorMessage("Test error");

        String json = objectMapper.writeValueAsString(original);
        ErrorMessage deserialized = objectMapper.readValue(json, ErrorMessage.class);

        assertEquals("ERROR", deserialized.getType());
        assertEquals("Test error", deserialized.getErrorMessage());
    }

    @Test
    @DisplayName("Should deserialize ErrorMessage from JSON string")
    void testErrorMessageDeserialization() throws Exception {
        String json = "{\"type\":\"ERROR\",\"errorMessage\":\"Something went wrong\"}";

        ErrorMessage message = objectMapper.readValue(json, ErrorMessage.class);

        assertEquals("ERROR", message.getType());
        assertEquals("Something went wrong", message.getErrorMessage());
    }

    // ========== GameOverMessage Tests ==========

    @Test
    @DisplayName("Should serialize and deserialize GameOverMessage")
    void testGameOverMessageSerialization() throws Exception {
        GameOverMessage original = new GameOverMessage(15, 10, true, false, "Player 1");

        String json = objectMapper.writeValueAsString(original);
        GameOverMessage deserialized = objectMapper.readValue(json, GameOverMessage.class);

        assertEquals("GAME_OVER", deserialized.getType());
        assertEquals(15, deserialized.getYourScore());
        assertEquals(10, deserialized.getOpponentScore());
        assertTrue(deserialized.isYouWon());
        assertFalse(deserialized.isDraw());
        assertEquals("Player 1", deserialized.getWinnerName());
    }

    @Test
    @DisplayName("Should deserialize GameOverMessage from JSON string")
    void testGameOverMessageDeserialization() throws Exception {
        String json = "{\"type\":\"GAME_OVER\",\"yourScore\":20,\"opponentScore\":18," +
                "\"youWon\":true,\"draw\":false,\"winnerName\":\"Champion\"}";

        GameOverMessage message = objectMapper.readValue(json, GameOverMessage.class);

        assertEquals("GAME_OVER", message.getType());
        assertEquals(20, message.getYourScore());
        assertEquals(18, message.getOpponentScore());
        assertTrue(message.isYouWon());
        assertFalse(message.isDraw());
        assertEquals("Champion", message.getWinnerName());
    }

    @Test
    @DisplayName("Should handle draw scenario in GameOverMessage")
    void testGameOverMessageDraw() throws Exception {
        GameOverMessage original = new GameOverMessage(10, 10, false, true, "Unentschieden");

        String json = objectMapper.writeValueAsString(original);
        GameOverMessage deserialized = objectMapper.readValue(json, GameOverMessage.class);

        assertEquals(10, deserialized.getYourScore());
        assertEquals(10, deserialized.getOpponentScore());
        assertFalse(deserialized.isYouWon());
        assertTrue(deserialized.isDraw());
        assertEquals("Unentschieden", deserialized.getWinnerName());
    }

    // ========== QuestionMessage Tests ==========

    @Test
    @DisplayName("Should serialize and deserialize QuestionMessage")
    void testQuestionMessageSerialization() throws Exception {
        QuestionMessage original = new QuestionMessage("5 + 3", 1, 60);

        String json = objectMapper.writeValueAsString(original);
        QuestionMessage deserialized = objectMapper.readValue(json, QuestionMessage.class);

        assertEquals("QUESTION", deserialized.getType());
        assertEquals("5 + 3", deserialized.getQuestionText());
        assertEquals(1, deserialized.getQuestionNumber());
        assertEquals(60, deserialized.getRemainingSeconds());
    }

    @Test
    @DisplayName("Should deserialize QuestionMessage from JSON string")
    void testQuestionMessageDeserialization() throws Exception {
        String json = "{\"type\":\"QUESTION\",\"questionText\":\"10 - 4\"," +
                "\"questionNumber\":5,\"remainingSeconds\":45}";

        QuestionMessage message = objectMapper.readValue(json, QuestionMessage.class);

        assertEquals("QUESTION", message.getType());
        assertEquals("10 - 4", message.getQuestionText());
        assertEquals(5, message.getQuestionNumber());
        assertEquals(45, message.getRemainingSeconds());
    }

    // ========== ScoreUpdateMessage Tests ==========

    @Test
    @DisplayName("Should serialize and deserialize ScoreUpdateMessage")
    void testScoreUpdateMessageSerialization() throws Exception {
        ScoreUpdateMessage original = new ScoreUpdateMessage(5, 3, true);

        String json = objectMapper.writeValueAsString(original);
        ScoreUpdateMessage deserialized = objectMapper.readValue(json, ScoreUpdateMessage.class);

        assertEquals("SCORE_UPDATE", deserialized.getType());
        assertEquals(5, deserialized.getYourScore());
        assertEquals(3, deserialized.getOpponentScore());
        assertTrue(deserialized.isWasCorrect());
    }

    @Test
    @DisplayName("Should deserialize ScoreUpdateMessage from JSON string")
    void testScoreUpdateMessageDeserialization() throws Exception {
        String json = "{\"type\":\"SCORE_UPDATE\",\"yourScore\":12," +
                "\"opponentScore\":15,\"wasCorrect\":false}";

        ScoreUpdateMessage message = objectMapper.readValue(json, ScoreUpdateMessage.class);

        assertEquals("SCORE_UPDATE", message.getType());
        assertEquals(12, message.getYourScore());
        assertEquals(15, message.getOpponentScore());
        assertFalse(message.isWasCorrect());
    }

    // ========== RematchMessage Tests ==========

    @Test
    @DisplayName("Should serialize and deserialize RematchMessage")
    void testRematchMessageSerialization() throws Exception {
        RematchMessage original = new RematchMessage(true, false, "Waiting for opponent");

        String json = objectMapper.writeValueAsString(original);
        RematchMessage deserialized = objectMapper.readValue(json, RematchMessage.class);

        assertEquals("REMATCH", deserialized.getType());
        assertTrue(deserialized.isRequestRematch());
        assertFalse(deserialized.isOpponentAccepted());
        assertEquals("Waiting for opponent", deserialized.getStatusMessage());
    }

    @Test
    @DisplayName("Should deserialize RematchMessage from JSON string")
    void testRematchMessageDeserialization() throws Exception {
        String json = "{\"type\":\"REMATCH\",\"requestRematch\":true," +
                "\"opponentAccepted\":true,\"statusMessage\":\"Starting rematch!\"}";

        RematchMessage message = objectMapper.readValue(json, RematchMessage.class);

        assertEquals("REMATCH", message.getType());
        assertTrue(message.isRequestRematch());
        assertTrue(message.isOpponentAccepted());
        assertEquals("Starting rematch!", message.getStatusMessage());
    }

    // ========== BaseMessage Polymorphic Tests ==========

    @Test
    @DisplayName("Should deserialize as BaseMessage and determine type")
    void testPolymorphicDeserialization() throws Exception {
        String answerJson = "{\"type\":\"ANSWER\",\"answer\":42}";
        String errorJson = "{\"type\":\"ERROR\",\"errorMessage\":\"Test error\"}";
        String questionJson = "{\"type\":\"QUESTION\",\"questionText\":\"5+3\",\"questionNumber\":1,\"remainingSeconds\":60}";

        BaseMessage answerMsg = objectMapper.readValue(answerJson, BaseMessage.class);
        BaseMessage errorMsg = objectMapper.readValue(errorJson, BaseMessage.class);
        BaseMessage questionMsg = objectMapper.readValue(questionJson, BaseMessage.class);

        assertEquals("ANSWER", answerMsg.getType());
        assertEquals("ERROR", errorMsg.getType());
        assertEquals("QUESTION", questionMsg.getType());

        assertTrue(answerMsg instanceof AnswerMessage);
        assertTrue(errorMsg instanceof ErrorMessage);
        assertTrue(questionMsg instanceof QuestionMessage);
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle negative scores in ScoreUpdateMessage")
    void testNegativeScores() throws Exception {
        ScoreUpdateMessage message = new ScoreUpdateMessage(-1, -1, false);

        String json = objectMapper.writeValueAsString(message);
        ScoreUpdateMessage deserialized = objectMapper.readValue(json, ScoreUpdateMessage.class);

        assertEquals(-1, deserialized.getYourScore());
        assertEquals(-1, deserialized.getOpponentScore());
    }

    @Test
    @DisplayName("Should handle zero scores")
    void testZeroScores() throws Exception {
        GameOverMessage message = new GameOverMessage(0, 0, false, true, "Unentschieden");

        String json = objectMapper.writeValueAsString(message);
        GameOverMessage deserialized = objectMapper.readValue(json, GameOverMessage.class);

        assertEquals(0, deserialized.getYourScore());
        assertEquals(0, deserialized.getOpponentScore());
        assertTrue(deserialized.isDraw());
    }

    @Test
    @DisplayName("Should handle empty strings")
    void testEmptyStrings() throws Exception {
        ErrorMessage message = new ErrorMessage("");

        String json = objectMapper.writeValueAsString(message);
        ErrorMessage deserialized = objectMapper.readValue(json, ErrorMessage.class);

        assertEquals("", deserialized.getErrorMessage());
    }

    @Test
    @DisplayName("Should handle null message in ErrorMessage")
    void testNullMessage() throws Exception {
        ErrorMessage message = new ErrorMessage(null);

        String json = objectMapper.writeValueAsString(message);
        ErrorMessage deserialized = objectMapper.readValue(json, ErrorMessage.class);

        assertNull(deserialized.getErrorMessage());
    }

    @Test
    @DisplayName("Should handle very long question text")
    void testLongQuestionText() throws Exception {
        String longText = "1234567890".repeat(100);
        QuestionMessage message = new QuestionMessage(longText, 1, 60);

        String json = objectMapper.writeValueAsString(message);
        QuestionMessage deserialized = objectMapper.readValue(json, QuestionMessage.class);

        assertEquals(longText, deserialized.getQuestionText());
    }

    @Test
    @DisplayName("Should handle large numbers in AnswerMessage")
    void testLargeAnswer() throws Exception {
        AnswerMessage message = new AnswerMessage(Integer.MAX_VALUE);

        String json = objectMapper.writeValueAsString(message);
        AnswerMessage deserialized = objectMapper.readValue(json, AnswerMessage.class);

        assertEquals(Integer.MAX_VALUE, deserialized.getAnswer());
    }

    @Test
    @DisplayName("Should handle special characters in strings")
    void testSpecialCharacters() throws Exception {
        String specialText = "Test äöü ß €  \n\t";
        QuestionMessage message = new QuestionMessage(specialText, 1, 60);

        String json = objectMapper.writeValueAsString(message);
        QuestionMessage deserialized = objectMapper.readValue(json, QuestionMessage.class);

        assertEquals(specialText, deserialized.getQuestionText());
    }

    // ========== Round-trip Tests ==========

    @Test
    @DisplayName("Should maintain data integrity through multiple serialization cycles")
    void testMultipleSerializationCycles() throws Exception {
        GameOverMessage original = new GameOverMessage(15, 12, true, false, "Winner");

        String json1 = objectMapper.writeValueAsString(original);
        GameOverMessage deserialized1 = objectMapper.readValue(json1, GameOverMessage.class);

        String json2 = objectMapper.writeValueAsString(deserialized1);
        GameOverMessage deserialized2 = objectMapper.readValue(json2, GameOverMessage.class);

        assertEquals(original.getYourScore(), deserialized2.getYourScore());
        assertEquals(original.getOpponentScore(), deserialized2.getOpponentScore());
        assertEquals(original.isYouWon(), deserialized2.isYouWon());
        assertEquals(original.isDraw(), deserialized2.isDraw());
        assertEquals(original.getWinnerName(), deserialized2.getWinnerName());
    }

    @Test
    @DisplayName("Should handle all message types in sequence")
    void testAllMessageTypes() throws Exception {
        BaseMessage[] messages = {
                new AnswerMessage(42),
                new ErrorMessage("Error"),
                new GameOverMessage(10, 8, true, false, "Winner"),
                new QuestionMessage("5 + 3", 1, 60),
                new ScoreUpdateMessage(5, 4, true),
                new RematchMessage(true, false, "Waiting")
        };

        for (BaseMessage original : messages) {
            String json = objectMapper.writeValueAsString(original);
            BaseMessage deserialized = objectMapper.readValue(json, BaseMessage.class);

            assertEquals(original.getType(), deserialized.getType());
            assertEquals(original.getClass(), deserialized.getClass());
        }
    }
}
