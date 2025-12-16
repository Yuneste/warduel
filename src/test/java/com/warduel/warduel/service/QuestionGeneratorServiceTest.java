package com.warduel.warduel.service;

import com.warduel.warduel.model.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionGeneratorServiceTest {

    private QuestionGeneratorService service;

    @BeforeEach
    void setUp() {
        service = new QuestionGeneratorService();
    }

    @Test
    void shouldGenerateCorrectNumberOfQuestions() {
        // Given
        int count = 10;

        // When
        List<Question> questions = service.generateQuestions(count);

        // Then
        assertEquals(count, questions.size());
    }

    @Test
    void shouldGenerateValidQuestions() {
        // When
        List<Question> questions = service.generateQuestions(20);

        // Then
        for(Question q : questions) {
            assertNotNull(q.getQuestionText());
            assertNotNull(q.getOperationType());
            assertFalse(q.getQuestionText().isEmpty());
        }
    }

    @Test
    void shouldGenerateAllOperationTypes() {
        // When
        List<Question> questions = service.generateQuestions(100);

        // Then
        boolean hasAdd = false;
        boolean hasSubtract = false;
        boolean hasMultiply = false;
        boolean hasDivide = false;

        for(Question q : questions) {
            switch(q.getOperationType()) {
                case ADD -> hasAdd = true;
                case SUBTRACT -> hasSubtract = true;
                case MULTIPLY -> hasMultiply = true;
                case DIVIDE -> hasDivide = true;
            }
        }

        assertTrue(hasAdd, "Should have addition questions");
        assertTrue(hasSubtract, "Should have subtraction questions");
        assertTrue(hasMultiply, "Should have multiplication questions");
        assertTrue(hasDivide, "Should have division questions");
    }

    @Test
    void shouldHaveCorrectAnswers() {
        // When
        List<Question> questions = service.generateQuestions(50);

        // Then
        for(Question q : questions) {
            String[] parts = q.getQuestionText().split(" ");
            int num1 = Integer.parseInt(parts[0]);
            String operator = parts[1];
            int num2 = Integer.parseInt(parts[2]);

            int expectedAnswer = switch(operator) {
                case "+" -> num1 + num2;
                case "-" -> num1 - num2;
                case "×" -> num1 * num2;
                case "÷" -> num1 / num2;
                default -> throw new IllegalStateException("Unknown operator");
            };

            assertEquals(expectedAnswer, q.getCorrectAnswer(),
                    "Question: " + q.getQuestionText());
        }
    }
}