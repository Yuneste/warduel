package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

/**
 * QuestionMessage - Server sendet eine neue Frage an beide Spieler
 * Beispiel: {"type": "QUESTION", "questionText": "5 + 3", "questionNumber": 1}
 */

@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("QUESTION")
public class QuestionMessage extends BaseMessage {

    private String questionText;
    private int questionNumber;
    private long remainingSeconds;

    public QuestionMessage() {
        super();
        setType("QUESTION");
    }

    public QuestionMessage(String questionText, int questionNumber, long remainingSeconds) {
        this();
        this.questionText = questionText;
        this.questionNumber = questionNumber;
        this.remainingSeconds = remainingSeconds;
    }
}
