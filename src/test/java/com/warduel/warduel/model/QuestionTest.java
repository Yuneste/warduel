package com.warduel.warduel.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Question class
 * Tests question creation, answer validation, and all operation types
 */
class QuestionTest {

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("Should create question with all parameters")
    void testQuestionCreation() {
        Question question = new Question("5 + 3", 8, Question.OperationType.ADD);

        assertEquals("5 + 3", question.getQuestionText());
        assertEquals(8, question.getCorrectAnswer());
        assertEquals(Question.OperationType.ADD, question.getOperationType());
    }

    @Test
    @DisplayName("Should create question with no-args constructor")
    void testNoArgsConstructor() {
        Question question = new Question();
        assertNotNull(question);
    }

    @Test
    @DisplayName("Should create question with different operation types")
    void testDifferentOperationTypes() {
        Question add = new Question("5 + 3", 8, Question.OperationType.ADD);
        Question subtract = new Question("10 - 4", 6, Question.OperationType.SUBTRACT);
        Question multiply = new Question("3 × 4", 12, Question.OperationType.MULTIPLY);
        Question divide = new Question("12 ÷ 3", 4, Question.OperationType.DIVIDE);

        assertEquals(Question.OperationType.ADD, add.getOperationType());
        assertEquals(Question.OperationType.SUBTRACT, subtract.getOperationType());
        assertEquals(Question.OperationType.MULTIPLY, multiply.getOperationType());
        assertEquals(Question.OperationType.DIVIDE, divide.getOperationType());
    }

    // ========== Answer Validation Tests ==========

    @Test
    @DisplayName("Should return true for correct answer")
    void testCorrectAnswer() {
        Question question = new Question("5 + 3", 8, Question.OperationType.ADD);
        assertTrue(question.isCorrect(8));
    }

    @Test
    @DisplayName("Should return false for incorrect answer")
    void testIncorrectAnswer() {
        Question question = new Question("5 + 3", 8, Question.OperationType.ADD);
        assertFalse(question.isCorrect(7));
        assertFalse(question.isCorrect(9));
        assertFalse(question.isCorrect(0));
    }

    @Test
    @DisplayName("Should handle negative correct answers")
    void testNegativeCorrectAnswer() {
        Question question = new Question("5 - 10", -5, Question.OperationType.SUBTRACT);
        assertTrue(question.isCorrect(-5));
        assertFalse(question.isCorrect(5));
    }

    @Test
    @DisplayName("Should handle zero as correct answer")
    void testZeroCorrectAnswer() {
        Question question = new Question("5 - 5", 0, Question.OperationType.SUBTRACT);
        assertTrue(question.isCorrect(0));
        assertFalse(question.isCorrect(1));
    }

    @Test
    @DisplayName("Should handle large numbers")
    void testLargeNumbers() {
        Question question = new Question("1000 + 2000", 3000, Question.OperationType.ADD);
        assertTrue(question.isCorrect(3000));
        assertFalse(question.isCorrect(2999));
    }

    // ========== Addition Operation Tests ==========

    @Test
    @DisplayName("Should validate addition questions correctly")
    void testAdditionQuestions() {
        Question q1 = new Question("5 + 3", 8, Question.OperationType.ADD);
        Question q2 = new Question("10 + 15", 25, Question.OperationType.ADD);
        Question q3 = new Question("0 + 7", 7, Question.OperationType.ADD);

        assertTrue(q1.isCorrect(8));
        assertTrue(q2.isCorrect(25));
        assertTrue(q3.isCorrect(7));

        assertFalse(q1.isCorrect(9));
        assertFalse(q2.isCorrect(24));
        assertFalse(q3.isCorrect(0));
    }

    // ========== Subtraction Operation Tests ==========

    @Test
    @DisplayName("Should validate subtraction questions correctly")
    void testSubtractionQuestions() {
        Question q1 = new Question("10 - 3", 7, Question.OperationType.SUBTRACT);
        Question q2 = new Question("20 - 15", 5, Question.OperationType.SUBTRACT);
        Question q3 = new Question("5 - 5", 0, Question.OperationType.SUBTRACT);

        assertTrue(q1.isCorrect(7));
        assertTrue(q2.isCorrect(5));
        assertTrue(q3.isCorrect(0));

        assertFalse(q1.isCorrect(13));
        assertFalse(q2.isCorrect(35));
        assertFalse(q3.isCorrect(10));
    }

    // ========== Multiplication Operation Tests ==========

    @Test
    @DisplayName("Should validate multiplication questions correctly")
    void testMultiplicationQuestions() {
        Question q1 = new Question("3 × 4", 12, Question.OperationType.MULTIPLY);
        Question q2 = new Question("7 × 8", 56, Question.OperationType.MULTIPLY);
        Question q3 = new Question("5 × 0", 0, Question.OperationType.MULTIPLY);

        assertTrue(q1.isCorrect(12));
        assertTrue(q2.isCorrect(56));
        assertTrue(q3.isCorrect(0));

        assertFalse(q1.isCorrect(7));
        assertFalse(q2.isCorrect(15));
        assertFalse(q3.isCorrect(5));
    }

    // ========== Division Operation Tests ==========

    @Test
    @DisplayName("Should validate division questions correctly")
    void testDivisionQuestions() {
        Question q1 = new Question("12 ÷ 3", 4, Question.OperationType.DIVIDE);
        Question q2 = new Question("20 ÷ 4", 5, Question.OperationType.DIVIDE);
        Question q3 = new Question("9 ÷ 9", 1, Question.OperationType.DIVIDE);

        assertTrue(q1.isCorrect(4));
        assertTrue(q2.isCorrect(5));
        assertTrue(q3.isCorrect(1));

        assertFalse(q1.isCorrect(36));
        assertFalse(q2.isCorrect(16));
        assertFalse(q3.isCorrect(0));
    }

    // ========== Setter Tests (Lombok @Data) ==========

    @Test
    @DisplayName("Should allow setting question text")
    void testSetQuestionText() {
        Question question = new Question();
        question.setQuestionText("10 + 5");
        assertEquals("10 + 5", question.getQuestionText());
    }

    @Test
    @DisplayName("Should allow setting correct answer")
    void testSetCorrectAnswer() {
        Question question = new Question();
        question.setCorrectAnswer(15);
        assertEquals(15, question.getCorrectAnswer());
        assertTrue(question.isCorrect(15));
    }

    @Test
    @DisplayName("Should allow setting operation type")
    void testSetOperationType() {
        Question question = new Question();
        question.setOperationType(Question.OperationType.MULTIPLY);
        assertEquals(Question.OperationType.MULTIPLY, question.getOperationType());
    }

    @Test
    @DisplayName("Should allow updating all fields")
    void testUpdateAllFields() {
        Question question = new Question("5 + 3", 8, Question.OperationType.ADD);

        question.setQuestionText("10 - 2");
        question.setCorrectAnswer(8);
        question.setOperationType(Question.OperationType.SUBTRACT);

        assertEquals("10 - 2", question.getQuestionText());
        assertEquals(8, question.getCorrectAnswer());
        assertEquals(Question.OperationType.SUBTRACT, question.getOperationType());
        assertTrue(question.isCorrect(8));
    }

    // ========== Equality Tests (Lombok @Data) ==========

    @Test
    @DisplayName("Should be equal when all fields match")
    void testEquality() {
        Question q1 = new Question("5 + 3", 8, Question.OperationType.ADD);
        Question q2 = new Question("5 + 3", 8, Question.OperationType.ADD);

        assertEquals(q1, q2);
        assertEquals(q1.hashCode(), q2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when fields differ")
    void testInequality() {
        Question q1 = new Question("5 + 3", 8, Question.OperationType.ADD);
        Question q2 = new Question("5 + 3", 9, Question.OperationType.ADD);
        Question q3 = new Question("6 + 3", 8, Question.OperationType.ADD);
        Question q4 = new Question("5 + 3", 8, Question.OperationType.SUBTRACT);

        assertNotEquals(q1, q2); // Different answer
        assertNotEquals(q1, q3); // Different text
        assertNotEquals(q1, q4); // Different operation type
    }

    // ========== toString Tests (Lombok @Data) ==========

    @Test
    @DisplayName("Should generate valid toString output")
    void testToString() {
        Question question = new Question("5 + 3", 8, Question.OperationType.ADD);
        String result = question.toString();

        assertNotNull(result);
        assertTrue(result.contains("5 + 3"));
        assertTrue(result.contains("8"));
        assertTrue(result.contains("ADD"));
    }

    // ========== Operation Type Enum Tests ==========

    @Test
    @DisplayName("Should have all four operation types")
    void testOperationTypeValues() {
        Question.OperationType[] types = Question.OperationType.values();

        assertEquals(4, types.length);
        assertTrue(containsType(types, Question.OperationType.ADD));
        assertTrue(containsType(types, Question.OperationType.SUBTRACT));
        assertTrue(containsType(types, Question.OperationType.MULTIPLY));
        assertTrue(containsType(types, Question.OperationType.DIVIDE));
    }

    @Test
    @DisplayName("Should convert operation type to string")
    void testOperationTypeToString() {
        assertEquals("ADD", Question.OperationType.ADD.toString());
        assertEquals("SUBTRACT", Question.OperationType.SUBTRACT.toString());
        assertEquals("MULTIPLY", Question.OperationType.MULTIPLY.toString());
        assertEquals("DIVIDE", Question.OperationType.DIVIDE.toString());
    }

    @Test
    @DisplayName("Should get operation type by name")
    void testOperationTypeValueOf() {
        assertEquals(Question.OperationType.ADD, Question.OperationType.valueOf("ADD"));
        assertEquals(Question.OperationType.SUBTRACT, Question.OperationType.valueOf("SUBTRACT"));
        assertEquals(Question.OperationType.MULTIPLY, Question.OperationType.valueOf("MULTIPLY"));
        assertEquals(Question.OperationType.DIVIDE, Question.OperationType.valueOf("DIVIDE"));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle null question text")
    void testNullQuestionText() {
        Question question = new Question(null, 8, Question.OperationType.ADD);
        assertNull(question.getQuestionText());
        assertTrue(question.isCorrect(8));
    }

    @Test
    @DisplayName("Should handle empty question text")
    void testEmptyQuestionText() {
        Question question = new Question("", 8, Question.OperationType.ADD);
        assertEquals("", question.getQuestionText());
        assertTrue(question.isCorrect(8));
    }

    @Test
    @DisplayName("Should handle very long question text")
    void testLongQuestionText() {
        String longText = "1234567890 + 9876543210";
        Question question = new Question(longText, (int) 11111111100L, Question.OperationType.ADD);
        assertEquals(longText, question.getQuestionText());
    }

    @Test
    @DisplayName("Should handle Integer.MAX_VALUE")
    void testMaxIntegerValue() {
        Question question = new Question("MAX", Integer.MAX_VALUE, Question.OperationType.ADD);
        assertTrue(question.isCorrect(Integer.MAX_VALUE));
        assertFalse(question.isCorrect(Integer.MAX_VALUE - 1));
    }

    @Test
    @DisplayName("Should handle Integer.MIN_VALUE")
    void testMinIntegerValue() {
        Question question = new Question("MIN", Integer.MIN_VALUE, Question.OperationType.SUBTRACT);
        assertTrue(question.isCorrect(Integer.MIN_VALUE));
        assertFalse(question.isCorrect(Integer.MIN_VALUE + 1));
    }

    @Test
    @DisplayName("Should handle multiple consecutive validations")
    void testMultipleValidations() {
        Question question = new Question("5 + 3", 8, Question.OperationType.ADD);

        assertTrue(question.isCorrect(8));
        assertTrue(question.isCorrect(8));
        assertFalse(question.isCorrect(7));
        assertFalse(question.isCorrect(9));
        assertTrue(question.isCorrect(8));
    }

    // Helper method
    private boolean containsType(Question.OperationType[] types, Question.OperationType target) {
        for (Question.OperationType type : types) {
            if (type == target) {
                return true;
            }
        }
        return false;
    }
}
