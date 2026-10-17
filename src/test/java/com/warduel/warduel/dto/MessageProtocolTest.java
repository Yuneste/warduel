package com.warduel.warduel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warduel.warduel.model.GameSession.GameStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the JSON shape the frontend sends and reads (see static/js/modules), so a renamed field
 * fails here instead of silently breaking the game.
 */
class MessageProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"type\":\"ANSWER\",\"answer\":-12}                                                                 | AnswerMessage",
            "{\"type\":\"REMATCH\",\"requestRematch\":true,\"opponentAccepted\":false,\"statusMessage\":\"\"}     | RematchMessage",
            "{\"type\":\"FORFEIT\"}                                                                               | ForfeitMessage",
            "{\"type\":\"HEARTBEAT\"}                                                                             | HeartbeatMessage",
    })
    void clientMessagesDeserializeToTheirType(String json, String expectedClass) throws Exception {
        BaseMessage message = mapper.readValue(json, BaseMessage.class);

        assertThat(message.getClass().getSimpleName()).isEqualTo(expectedClass);
    }

    @Test
    void answerValueIsRead() throws Exception {
        AnswerMessage answer = (AnswerMessage) mapper.readValue("{\"type\":\"ANSWER\",\"answer\":-12}", BaseMessage.class);
        assertThat(answer.getAnswer()).isEqualTo(-12);
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> mapper.readValue("{\"type\":\"CHEAT\"}", BaseMessage.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void serverMessagesCarryTheFieldsTheClientReads() throws Exception {
        assertFields(new QuestionMessage("3 × 4", 2, 58), "QUESTION", "questionText", "questionNumber", "remainingSeconds");
        assertFields(new ScoreUpdateMessage(3, 1, true), "SCORE_UPDATE", "yourScore", "opponentScore", "wasCorrect");
        assertFields(new CountdownMessage(3, "tip"), "COUNTDOWN", "countdown", "message");
        assertFields(new GameStateMessage(GameStatus.RUNNING, "Player 1", "Player 2", 60L), "GAME_STATE", "gameStatus");
        assertFields(new RematchMessage(true, false, "Waiting"), "REMATCH", "opponentAccepted", "statusMessage");
        assertFields(new ErrorMessage("Opponent left"), "ERROR", "errorMessage");
        assertFields(new GameOverMessage(5, 3, true, false, "Player 1", "Player 2 forfeited"),
                "GAME_OVER", "yourScore", "opponentScore", "youWon", "draw", "disconnectMessage");
    }

    @Test
    void gameOverRoundTrips() throws Exception {
        String json = mapper.writeValueAsString(new GameOverMessage(5, 5, false, true, "Draw"));

        GameOverMessage back = (GameOverMessage) mapper.readValue(json, BaseMessage.class);

        assertThat(back.isDraw()).isTrue();
        assertThat(back.getYourScore()).isEqualTo(5);
        assertThat(back.getWinnerName()).isEqualTo("Draw");
    }

    private void assertFields(BaseMessage message, String type, String... fields) throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(message));
        assertThat(json.path("type").asText()).isEqualTo(type);
        for(String field : fields) {
            assertThat(json.has(field)).as("%s has field %s in %s", type, field, json).isTrue();
        }
    }
}
