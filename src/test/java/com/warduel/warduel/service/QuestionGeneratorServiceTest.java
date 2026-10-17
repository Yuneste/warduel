package com.warduel.warduel.service;

import com.warduel.warduel.config.GameConfiguration;
import com.warduel.warduel.model.Question;
import com.warduel.warduel.model.Question.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionGeneratorServiceTest {

    private static final int SAMPLES = 2_000;

    private GameConfiguration config;
    private QuestionGeneratorService generator;

    @BeforeEach
    void setUp() {
        config = new GameConfiguration();
        generator = new QuestionGeneratorService(config);
    }

    @Test
    void generatesRequestedNumberOfQuestions() {
        assertThat(generator.generateQuestions(20)).hasSize(20);
        assertThat(generator.generateQuestions(0)).isEmpty();
    }

    @Test
    void usesAllFourOperations() {
        Set<OperationType> seen = generator.generateQuestions(SAMPLES).stream()
                .map(Question::getOperationType)
                .collect(Collectors.toSet());

        assertThat(seen).isEqualTo(EnumSet.allOf(OperationType.class));
    }

    @Test
    void answersMatchTheQuestionText() {
        for(Question q : generator.generateQuestions(SAMPLES)) {
            String[] parts = q.getQuestionText().split(" ");
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[2]);
            int expected = switch(parts[1]) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "×" -> a * b;
                case "÷" -> a / b;
                default -> throw new AssertionError("Unknown operator in " + q.getQuestionText());
            };
            assertThat(q.getCorrectAnswer()).as(q.getQuestionText()).isEqualTo(expected);
            assertThat(q.isCorrect(expected)).isTrue();
            assertThat(q.isCorrect(expected + 1)).isFalse();
        }
    }

    @Test
    void subtractionNeverGoesNegative() {
        ofType(OperationType.SUBTRACT).forEach(q ->
                assertThat(q.getCorrectAnswer()).as(q.getQuestionText()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void divisionAlwaysComesOutEven() {
        ofType(OperationType.DIVIDE).forEach(q -> {
            String[] parts = q.getQuestionText().split(" ");
            assertThat(Integer.parseInt(parts[0]) % Integer.parseInt(parts[2])).as(q.getQuestionText()).isZero();
        });
    }

    @Test
    void respectsConfiguredRanges() {
        config.setMinNumber(5);
        config.setMaxNumber(6);
        config.setMultiplicationMin(2);
        config.setMultiplicationMax(3);

        for(Question q : generator.generateQuestions(SAMPLES)) {
            String[] parts = q.getQuestionText().split(" ");
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[2]);
            switch(q.getOperationType()) {
                case ADD, SUBTRACT -> assertThat(List.of(a, b)).allSatisfy(n -> assertThat(n).isBetween(5, 6));
                case MULTIPLY -> assertThat(List.of(a, b)).allSatisfy(n -> assertThat(n).isBetween(2, 3));
                case DIVIDE -> assertThat(q.getCorrectAnswer()).isBetween(config.getDivisionMin(), config.getDivisionMax());
            }
        }
    }

    private List<Question> ofType(OperationType type) {
        List<Question> questions = generator.generateQuestions(SAMPLES).stream()
                .filter(q -> q.getOperationType() == type)
                .toList();
        assertThat(questions).isNotEmpty();
        return questions;
    }
}
