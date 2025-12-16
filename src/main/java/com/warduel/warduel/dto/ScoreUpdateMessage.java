package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

/**
 * ScoreUpdateMessage - Server informiert über aktuelle Punktestände
 * Wird nach jeder Antwort gesendet
 */

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SCORE_UPDATE")
public class ScoreUpdateMessage extends BaseMessage {

    private int yourScore;
    private int opponentScore;
    private boolean wasCorrect;

    public ScoreUpdateMessage() {
        super();
        setType("SCORE_UPDATE");
    }

    public ScoreUpdateMessage(int yourScore, int opponentScore, boolean wasCorrect) {
        this();
        this.yourScore = yourScore;
        this.opponentScore = opponentScore;
        this.wasCorrect = wasCorrect;
    }
}
