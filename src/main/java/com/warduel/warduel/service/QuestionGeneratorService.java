package com.warduel.warduel.service;

import com.warduel.warduel.config.GameConfiguration;
import com.warduel.warduel.model.Question;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * QuestionGeneratorService - Generiert zufällige mathematische Fragen
 * Erstellt Add-, Sub-, Mult-, Div-Aufgaben
 */

@Service
public class QuestionGeneratorService {

    private final GameConfiguration gameConfig;

    public QuestionGeneratorService(GameConfiguration gameConfig) {
        this.gameConfig = gameConfig;
    }

    /**
     * Generiert eine Liste von zufälligen Fragen
     * @param count Anzahl der zu generierenden Fragen
     * @return Liste von Fragen
     */
    public List<Question> generateQuestions(int count) {
        // Wir erzeugen eine Liste namens questions, die Objekte vom Typ Question aufnehmen kann. (Eine Liste, die Question-Objekte enthält)
        List<Question> questions = new ArrayList<>();

        for(int i = 0; i < count; i++) {
            // zufälliger Operationstyp
                // types ist jetzt ein Array mit allen 4 Werten:
                // types[0] = ADD
                // types[1] = SUBTRACT
                // types[2] = MULTIPLY
                // types[3] = DIVIDE
            Question.OperationType[] types = Question.OperationType.values();
            Question.OperationType randomType = types[ThreadLocalRandom.current().nextInt(types.length)]; // holt einen zufälligen Operator aus dem Enum

            Question question = generateQuestionsByType(randomType);
            questions.add(question);
        }
        return questions;
    }

    private Question generateQuestionsByType(Question.OperationType type) {
        return switch (type) {
            case ADD -> generateAddition();
            case SUBTRACT -> generateSubtraction();
            case MULTIPLY -> generateMultiplication();
            case DIVIDE -> generateDivision();
        };
    }

    /**
     * Generiert eine Additionsaufgabe
     * Beispiel: 5 + 3 = 8
     */
    private Question generateAddition() {
        int num1 = ThreadLocalRandom.current().nextInt(gameConfig.getMinNumber(), gameConfig.getMaxNumber() + 1);
        int num2 = ThreadLocalRandom.current().nextInt(gameConfig.getMinNumber(), gameConfig.getMaxNumber() + 1);

        int answer = num1 + num2;

        String questionText = num1 + " + " + num2;

        return new Question(questionText, answer, Question.OperationType.ADD);
    }

    /**
     * Generiert eine Subtraktionsaufgabe
     * Stellt sicher dass das Ergebnis positiv ist
     */
    private Question generateSubtraction() {
        int num1 = ThreadLocalRandom.current().nextInt(gameConfig.getMinNumber(), gameConfig.getMaxNumber() + 1);
        int num2 = ThreadLocalRandom.current().nextInt(gameConfig.getMinNumber(), gameConfig.getMaxNumber() + 1);

        int larger = Math.max(num1, num2);
        int smaller = Math.min(num1, num2);

        int answer = larger - smaller;

        String questionText = larger + " - " + smaller;

        return new Question(questionText, answer, Question.OperationType.SUBTRACT);
    }

    /**
     * Generiert eine Multiplikationsaufgabe
     * Nutzt kleinere Zahlen für einfachere Multiplikation
     */
    private Question generateMultiplication() {
        int num1 = ThreadLocalRandom.current().nextInt(gameConfig.getMultiplicationMin(), gameConfig.getMultiplicationMax() + 1);
        int num2 = ThreadLocalRandom.current().nextInt(gameConfig.getMultiplicationMin(), gameConfig.getMultiplicationMax() + 1);

        int answer = num1 * num2;

        String questionText = num1 + " × " + num2;

        return new Question(questionText, answer, Question.OperationType.MULTIPLY);
    }

    /**
     * Generiert eine Divisionsaufgabe
     * Stellt sicher dass die Division aufgeht (kein Rest)
     */
    private Question generateDivision() {
        // Erst das Ergebnis wählen, dann rückwärts rechnen
        int answer = ThreadLocalRandom.current().nextInt(gameConfig.getDivisionMin(), gameConfig.getDivisionMax() + 1);
        int num2 = ThreadLocalRandom.current().nextInt(gameConfig.getDivisionMin(), gameConfig.getDivisionMax() + 1);
        int num1 = answer * num2;

        String questionText = num1 + " ÷ " + num2;

        return new Question(questionText, answer, Question.OperationType.DIVIDE);
    }
}
