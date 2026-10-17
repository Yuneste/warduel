package com.warduel.warduel.dto;

import com.warduel.warduel.model.Question;
import java.util.List;

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

    public static QuestionMessage of(Question question, int questionNumber, long remainingSeconds) {
        QuestionMessage msg = new QuestionMessage(question.getQuestionText(), questionNumber, remainingSeconds);
        msg.setFormat(question.getFormat().name());
        msg.setOptions(question.getOptions());
        return msg;
    }


    private String questionText;
    private int questionNumber;
    private long remainingSeconds;
    private String format = "NUMBER";        // NUMBER, TRUE_FALSE or CHOICE
    private List<String> options = List.of(); // the two buttons of a CHOICE question

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
