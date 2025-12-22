package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,  // ← GEÄNDERT!
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = JoinGameMessage.class, name = "JOIN_GAME"),
        @JsonSubTypes.Type(value = GameStateMessage.class, name = "GAME_STATE"),
        @JsonSubTypes.Type(value = CountdownMessage.class, name = "COUNTDOWN"),
        @JsonSubTypes.Type(value = QuestionMessage.class, name = "QUESTION"),
        @JsonSubTypes.Type(value = AnswerMessage.class, name = "ANSWER"),
        @JsonSubTypes.Type(value = ScoreUpdateMessage.class, name = "SCORE_UPDATE"),
        @JsonSubTypes.Type(value = GameOverMessage.class, name = "GAME_OVER"),
        @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR"),
        @JsonSubTypes.Type(value = RematchMessage.class, name = "REMATCH"),
        @JsonSubTypes.Type(value = ForfeitMessage.class, name = "FORFEIT"),
        @JsonSubTypes.Type(value = HeartbeatMessage.class, name = "HEARTBEAT")
})
public abstract class BaseMessage {

    private String type;

    public BaseMessage(String type) {
        this.type = type;
    }

    public BaseMessage() {
    }

}