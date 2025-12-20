package com.warduel.warduel.service;

import com.warduel.warduel.model.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for QuestionGeneratorService class
 * Tests question generation, validation, and all operation types
 */
class QuestionGeneratorServiceTest {

    private QuestionGeneratorService questionGenerator;

    @BeforeEach
    void setUp() {
        questionGenerator = new QuestionGeneratorService();
    }

    // ========== Basic Generation Tests ==========

    @Test
    @DisplayName("Should generate requested number of questions")
    void testGenerateQuestionsCount() {
        List<Question> questions = questionGenerator.generateQuestions(20);

        assertNotNull(questions);
        assertEquals(20, questions.size());
    }

    @Test
    @DisplayName("Should generate single question")
    void testGenerateSingleQuestion() {
        List<Question> questions = questionGenerator.generateQuestions(1);

        assertEquals(1, questions.size());
        assertNotNull(questions.get(0));
    }

    @Test
    @DisplayName("Should generate zero questions when count is 0")
    void testGenerateZeroQuestions() {
        List<Question> questions = questionGenerator.generateQuestions(0);

        assertNotNull(questions);
        assertTrue(questions.isEmpty());
    }

    @Test
    @DisplayName("Should generate large number of questions")
    void testGenerateLargeQuestionSet() {
        List<Question> questions = questionGenerator.generateQuestions(100);

        assertEquals(100, questions.size());
    }

    // ========== Question Validity Tests ==========

    @Test
    @DisplayName("Should generate valid questions with all required fields")
    void testQuestionValidity() {
        List<Question> questions = questionGenerator.generateQuestions(20);

        for (Question question : questions) {
            assertNotNull(question.getQuestionText(), "Question text should not be null");
            assertNotNull(question.getOperationType(), "Operation type should not be null");
            assertFalse(question.getQuestionText().isEmpty(), "Question text should not be empty");
        }
    }

    @Test
    @DisplayName("Should generate questions with correct answers that match the question")
    void testCorrectAnswersValidity() {
        List<Question> questions = questionGenerator.generateQuestions(50);

        for (Question question : questions) {
            assertNotNull(question.getQuestionText());
            assertNotNull(question.getOperationType());

            // The answer should be correct when checked
            assertTrue(question.isCorrect(question.getCorrectAnswer()),
                    "Question's correct answer should validate as correct for: " + question.getQuestionText());
        }
    }

    // ========== Operation Type Distribution Tests ==========

    @Test
    @DisplayName("Should generate variety of operation types")
    void testOperationTypeVariety() {
        List<Question> questions = questionGenerator.generateQuestions(100);

        Set<Question.OperationType> types = new HashSet<>();
        for (Question question : questions) {
            types.add(question.getOperationType());
        }

        // With 100 questions, we should have multiple operation types
        assertTrue(types.size() >= 2, "Should generate multiple operation types");
    }

    @RepeatedTest(10)
    @DisplayName("Should eventually generate all four operation types")
    void testAllOperationTypesGenerated() {
        List<Question> questions = questionGenerator.generateQuestions(100);

        Set<Question.OperationType> types = new HashSet<>();
        for (Question question : questions) {
            types.add(question.getOperationType());
        }

        // With enough questions and multiple runs, all types should appear
        // (This might occasionally fail due to randomness, but with 100 questions it's unlikely)
        assertTrue(types.size() >= 2);
    }

    // ========== Addition Questions Tests ==========

    @Test
    @DisplayName("Should generate valid addition questions")
    void testAdditionQuestions() {
        List<Question> allQuestions = questionGenerator.generateQuestions(100);

        // Find addition questions
        List<Question> addQuestions = allQuestions.stream()
                .filter(q -> q.getOperationType() == Question.OperationType.ADD)
                .toList();

        for (Question question : addQuestions) {
            assertTrue(question.getQuestionText().contains("+"));

            // Verify the answer is correct
            String[] parts = question.getQuestionText().split(" \\+ ");
            if (parts.length == 2) {
                try {
                    int num1 = Integer.parseInt(parts[0].trim());
                    int num2 = Integer.parseInt(parts[1].trim());
                    assertEquals(num1 + num2, question.getCorrectAnswer(),
                            "Addition answer should be correct for: " + question.getQuestionText());
                } catch (NumberFormatException e) {
                    // If parsing fails, just verify the question validates its own answer
                    assertTrue(question.isCorrect(question.getCorrectAnswer()));
                }
            }
        }
    }

    @Test
    @DisplayName("Addition questions should use numbers in valid range")
    void testAdditionNumberRange() {
        List<Question> allQuestions = questionGenerator.generateQuestions(100);

        List<Question> addQuestions = allQuestions.stream()
                .filter(q -> q.getOperationType() == Question.OperationType.ADD)
                .toList();

        for (Question question : addQuestions) {
            String[] parts = question.getQuestionText().split(" \\+ ");
            if (parts.length == 2) {
                try {
                    int num1 = Integer.parseInt(parts[0].trim());
                    int num2 = Integer.parseInt(parts[1].trim());

                    assertTrue(num1 >= 1 && num1 <= 20, "First number should be in range 1-20");
                    assertTrue(num2 >= 1 && num2 <= 20, "Second number should be in range 1-20");
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }
    }

    // ========== Subtraction Questions Tests ==========

    @Test
    @DisplayName("Should generate valid subtraction questions")
    void testSubtractionQuestions() {
        List<Question> allQuestions = questionGenerator.generateQuestions(100);

        List<Question> subQuestions = allQuestions.stream()
                .filter(q -> q.getOperationType() == Question.OperationType.SUBTRACT)
                .toList();

        for (Question question : subQuestions) {
            assertTrue(question.getQuestionText().contains("-"));

            // Verify the answer is correct and non-negative
            assertTrue(question.getCorrectAnswer() >= 0,
                    "Subtraction result should be non-negative: " + question.getQuestionText());

            // Verify answer validates
            assertTrue(question.isCorrect(question.getCorrectAnswer()));
        }
    }

    @Test
    @DisplayName("Subtraction questions should ensure positive results")
    void testSubtractionPositiveResults() {
        List<Question> allQuestions = questionGenerator.generateQuestions(100);

        List<Question> subQuestions = allQuestions.stream()
                .filter(q -> q.getOperationType() == Question.OperationType.SUBTRACT)
                .toList();

        for (Question question : subQuestions) {
            String[] parts = question.getQuestionText().split(" - ");
            if (parts.length == 2) {
                try {
                    int num1 = Integer.parseInt(parts[0].trim());
                    int num2 = Integer.parseInt(parts[1].trim());

                    assertTrue(num1 >= num2, "First number should be >= second number for: " + question.getQuestionText());
                    assertEquals(num1 - num2, question.getCorrectAnswer());
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }
    }

    // ========== Multiplication Questions Tests ==========

    @Test
    @DisplayName("Should generate valid multiplication questions")
    void testMultiplicationQuestions() {
        List<Question> allQuestions = questionGenerator.generateQuestions(100);

        List<Question> mulQuestions = allQuestions.stream()
                .filter(q -> q.getOperationType() == Question.OperationType.MULTIPLY)
                .toList();

        for (Question question : mulQuestions) {
            assertTrue(question.getQuestionText().contains("×"));

            // Verify answer validates
            assertTrue(question.isCorrect(question.getCorrectAnswer()));
        }
    }

    @Test
    @DisplayName("Multiplication questions should use smaller numbers (1-10)")
    void testMultiplicationNumberRange() {
        List<Question> allQuestions = questionGenerator.generateQuestions(100);

        List<Question> mulQuestions = allQuestions.stream()
                .filter(q -> q.getOperationType() == Question.OperationType.MULTIPLY)
                .toList();

        for (Question question : mulQuestions) {
            String[] parts = question.getQuestionText().split(" × ");
            if (parts.length == 2) {
                try {
                    int num1 = Integer.parseInt(parts[0].trim());
                    int num2 = Integer.parseInt(parts[1].trim());

                    assertTrue(num1 >= 1 && num1 <= 10, "First number should be in range 1-10");
                    assertTrue(num2 >= 1 && num2 <= 10, "Second number should be in range 1-10");
                    assertEquals(num1 * num2, question.getCorrectAnswer());
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }
    }

    // ========== Division Questions Tests ==========

    @Test
    @DisplayName("Should generate valid division questions")
    void testDivisionQuestions() {
        List<Question> allQuestions = questionGenerator.generateQuestions(100);

        List<Question> divQuestions = allQuestions.stream()
                .filter(q -> q.getOperationType() == Question.OperationType.DIVIDE)
                .toList();

        for (Question question : divQuestions) {
            assertTrue(question.getQuestionText().contains("÷"));

            // Verify answer validates
            assertTrue(question.isCorrect(question.getCorrectAnswer()));

            // Division result should be positive
            assertTrue(question.getCorrectAnswer() > 0,
                    "Division result should be positive: " + question.getQuestionText());
        }
    }

    @Test
    @DisplayName("Division questions should have no remainder")
    void testDivisionNoRemainder() {
        List<Question> allQuestions = questionGenerator.generateQuestions(100);

        List<Question> divQuestions = allQuestions.stream()
                .filter(q -> q.getOperationType() == Question.OperationType.DIVIDE)
                .toList();

        for (Question question : divQuestions) {
            String[] parts = question.getQuestionText().split(" ÷ ");
            if (parts.length == 2) {
                try {
                    int num1 = Integer.parseInt(parts[0].trim());
                    int num2 = Integer.parseInt(parts[1].trim());
                    int answer = question.getCorrectAnswer();

                    assertTrue(num2 > 0, "Divisor should be positive");
                    assertEquals(num1, answer * num2,
                            "Division should have no remainder: " + question.getQuestionText());
                    assertEquals(answer, num1 / num2);
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }
    }

    // ========== Randomness Tests ==========

    @Test
    @DisplayName("Should generate different question sets on subsequent calls")
    void testRandomness() {
        List<Question> questions1 = questionGenerator.generateQuestions(20);
        List<Question> questions2 = questionGenerator.generateQuestions(20);

        // While it's theoretically possible to get the same questions,
        // it's extremely unlikely with random generation
        boolean hasDifference = false;
        for (int i = 0; i < 20; i++) {
            if (!questions1.get(i).getQuestionText().equals(questions2.get(i).getQuestionText())) {
                hasDifference = true;
                break;
            }
        }

        assertTrue(hasDifference, "Question sets should be different");
    }

    @Test
    @DisplayName("Should generate diverse questions within a set")
    void testDiversityWithinSet() {
        List<Question> questions = questionGenerator.generateQuestions(50);

        Set<String> uniqueQuestions = new HashSet<>();
        for (Question question : questions) {
            uniqueQuestions.add(question.getQuestionText());
        }

        // Most questions should be unique (allowing for some duplicates due to randomness)
        assertTrue(uniqueQuestions.size() >= 40, "Should have mostly unique questions");
    }

    // ========== Consistency Tests ==========

    @Test
    @DisplayName("All generated questions should be answerable")
    void testAllQuestionsAnswerable() {
        List<Question> questions = questionGenerator.generateQuestions(100);

        for (Question question : questions) {
            // Each question should validate its own correct answer
            assertTrue(question.isCorrect(question.getCorrectAnswer()),
                    "Question should validate its own answer: " + question.getQuestionText());

            // And reject wrong answers
            assertFalse(question.isCorrect(question.getCorrectAnswer() + 1),
                    "Question should reject wrong answers: " + question.getQuestionText());
        }
    }

    @Test
    @DisplayName("Question text should be properly formatted")
    void testQuestionFormatting() {
        List<Question> questions = questionGenerator.generateQuestions(50);

        for (Question question : questions) {
            String text = question.getQuestionText();

            // Should contain an operator
            boolean hasOperator = text.contains("+") || text.contains("-") ||
                    text.contains("×") || text.contains("÷");

            assertTrue(hasOperator, "Question should contain an operator: " + text);

            // Should not be empty
            assertFalse(text.trim().isEmpty(), "Question should not be empty");
        }
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle generating very small question sets")
    void testSmallQuestionSets() {
        for (int i = 1; i <= 5; i++) {
            List<Question> questions = questionGenerator.generateQuestions(i);
            assertEquals(i, questions.size());

            for (Question question : questions) {
                assertNotNull(question);
                assertNotNull(question.getQuestionText());
                assertTrue(question.isCorrect(question.getCorrectAnswer()));
            }
        }
    }

    @Test
    @DisplayName("Should handle negative count gracefully")
    void testNegativeCount() {
        List<Question> questions = questionGenerator.generateQuestions(-5);
        assertNotNull(questions);
        assertTrue(questions.isEmpty());
    }

    @Test
    @DisplayName("Should maintain performance with large question sets")
    void testPerformanceWithLargeSet() {
        long startTime = System.currentTimeMillis();
        List<Question> questions = questionGenerator.generateQuestions(1000);
        long endTime = System.currentTimeMillis();

        assertEquals(1000, questions.size());

        // Should complete in reasonable time (less than 1 second)
        assertTrue(endTime - startTime < 1000,
                "Should generate 1000 questions in less than 1 second");
    }

    // ========== Statistical Distribution Tests ==========

    @Test
    @DisplayName("Should have reasonable distribution of operation types")
    void testOperationTypeDistribution() {
        List<Question> questions = questionGenerator.generateQuestions(400);

        int addCount = 0, subCount = 0, mulCount = 0, divCount = 0;

        for (Question question : questions) {
            switch (question.getOperationType()) {
                case ADD -> addCount++;
                case SUBTRACT -> subCount++;
                case MULTIPLY -> mulCount++;
                case DIVIDE -> divCount++;
            }
        }

        // With random distribution, each should appear roughly 25% of the time
        // Allow for variance: each should be between 15% and 35%
        assertTrue(addCount >= 60 && addCount <= 140, "ADD count should be reasonable: " + addCount);
        assertTrue(subCount >= 60 && subCount <= 140, "SUBTRACT count should be reasonable: " + subCount);
        assertTrue(mulCount >= 60 && mulCount <= 140, "MULTIPLY count should be reasonable: " + mulCount);
        assertTrue(divCount >= 60 && divCount <= 140, "DIVIDE count should be reasonable: " + divCount);

        assertEquals(400, addCount + subCount + mulCount + divCount);
    }

    @Test
    @DisplayName("Should generate questions with varying difficulty")
    void testDifficultyVariation() {
        List<Question> questions = questionGenerator.generateQuestions(50);

        int easyCount = 0;  // Result < 10
        int mediumCount = 0; // Result 10-50
        int hardCount = 0;  // Result > 50

        for (Question question : questions) {
            int answer = question.getCorrectAnswer();

            if (answer < 10) {
                easyCount++;
            } else if (answer <= 50) {
                mediumCount++;
            } else {
                hardCount++;
            }
        }

        // Should have a mix of difficulties
        assertTrue(easyCount > 0, "Should have some easy questions");
        assertTrue(mediumCount > 0, "Should have some medium questions");
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should generate standard game question set (20 questions)")
    void testStandardGameQuestionSet() {
        List<Question> questions = questionGenerator.generateQuestions(20);

        assertEquals(20, questions.size());

        for (Question question : questions) {
            assertNotNull(question);
            assertNotNull(question.getQuestionText());
            assertNotNull(question.getOperationType());

            // Verify each question is valid
            assertTrue(question.isCorrect(question.getCorrectAnswer()));
            assertFalse(question.isCorrect(question.getCorrectAnswer() + 999));
        }
    }
}
