package com.warduel.warduel.model;

import lombok.*;

import java.util.List;

/**
 * Question - Repräsentiert eine mathematische Frage
 * Diese Klasse speichert eine Frage, die richtige Antwort und den Fragetyp
 *
 * Every answer travels as an int: the number itself for NUMBER questions,
 * 1 (true) / 0 (false) for TRUE_FALSE, and the index of the chosen option for CHOICE.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    private String questionText; // Die Frage als String, z.B. "5 + 3"
    private int correctAnswer; // Die richtige Antwort, z.B. 8
    private OperationType operationType; // Der Typ der Operation: ADD, Substract, Multiply, Divide
    private AnswerFormat format = AnswerFormat.NUMBER;
    private List<String> options = List.of(); // the two choices of a CHOICE question

    /**
     * Enum für die verschiedenen Rechenarten
     */
    public enum OperationType {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE
    }

    /**
     * How the player answers: type a number, say true/false, or pick one of two options
     */
    public enum AnswerFormat {
        NUMBER,
        TRUE_FALSE,
        CHOICE
    }

    public Question(String questionText, int correctAnswer, OperationType operationType) {
        this(questionText, correctAnswer, operationType, AnswerFormat.NUMBER, List.of());
    }

    public static Question trueFalse(String statement, boolean isTrue, OperationType operationType) {
        return new Question(statement, isTrue ? 1 : 0, operationType, AnswerFormat.TRUE_FALSE, List.of("True", "False"));
    }

    public static Question choice(String left, String right, int correctIndex) {
        return new Question(left + " or " + right, correctIndex, null, AnswerFormat.CHOICE, List.of(left, right));
    }

    /**
     * Überprüft ob die gegebene Antwort korrekt ist
     * @param answer - Die Antwort des Spielers
     * @return true - wenn korrekt, false wenn falsch
     */
    public boolean isCorrect(int answer) {
        return this.correctAnswer == answer;
    }

    /**
     * The player's answer in words, for the post-game answer list ("42", "True", "3/4")
     */
    public String formatAnswer(int answer) {
        return switch(format) {
            case NUMBER -> String.valueOf(answer);
            case TRUE_FALSE -> answer == 1 ? "True" : answer == 0 ? "False" : "?";
            case CHOICE -> answer >= 0 && answer < options.size() ? options.get(answer) : "?";
        };
    }
}
