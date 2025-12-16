package com.warduel.warduel.dto;

import lombok.*;

/**
 * AnswerMessage - Client sendet seine Antwort an den Server
 * Beispiel: {"type": "ANSWER", "answer": 8}
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class AnswerMessage extends BaseMessage {

    private int answer; // Antwort des Spielers

    public AnswerMessage() {
        super();
        setType("ANSWER");
    }

    public AnswerMessage(int answer) {
        this();
        this.answer = answer;
    }
}
