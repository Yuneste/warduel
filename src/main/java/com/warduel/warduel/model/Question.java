package com.warduel.warduel.model;

import lombok.*;

/**
 * Question - Repräsentiert eine mathematische Frage
 * Diese Klasse speichert eine Frage, die richtige Antwort und den Fragetyp
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    private String questionText; // Die Frage als String, z.B. "5 + 3"
    private int correctAnswer; // Die richtige Antwort, z.B. 8
    private OperationType operationType; // Der Typ der Operation: ADD, Substract, Multiply, Divide

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
     * Überprüft ob die gegebene Antwort korrekt ist
     * @param answer - Die Antwort des Spielers
     * @return true - wenn korrekt, false wenn falsch
     */
    public boolean isCorrect(int answer) {
        return this.correctAnswer == answer;
    }
}
