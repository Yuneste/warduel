package com.warduel.warduel.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for Player class
 * Tests score management, question tracking, and thread safety
 */
class PlayerTest {

    private Player player;
    private WebSocketSession mockSession;

    @BeforeEach
    void setUp() {
        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn("test-player-id");
        when(mockSession.isOpen()).thenReturn(true);

        player = new Player("test-player-id", mockSession, "Test Player");
    }

    // ========== Constructor and Initialization Tests ==========

    @Test
    @DisplayName("Should initialize Player with correct values")
    void testPlayerInitialization() {
        assertEquals("test-player-id", player.getPlayerId());
        assertEquals(mockSession, player.getSession());
        assertEquals("Test Player", player.getDisplayName());
        assertEquals(0, player.getScore());
        assertEquals(0, player.getCurrentQuestionIndex());
        assertNotNull(player.getQuestions());
        assertTrue(player.getQuestions().isEmpty());
    }

    @Test
    @DisplayName("Should allow null session in constructor")
    void testPlayerWithNullSession() {
        Player playerWithNullSession = new Player("id", null, "Name");
        assertNull(playerWithNullSession.getSession());
        assertEquals("id", playerWithNullSession.getPlayerId());
    }

    @Test
    @DisplayName("Should allow empty display name")
    void testPlayerWithEmptyDisplayName() {
        Player playerWithEmptyName = new Player("id", mockSession, "");
        assertEquals("", playerWithEmptyName.getDisplayName());
    }

    // ========== Score Management Tests ==========

    @Test
    @DisplayName("Should start with score of 0")
    void testInitialScore() {
        assertEquals(0, player.getScore());
    }

    @Test
    @DisplayName("Should increment score correctly")
    void testIncrementScore() {
        player.incrementScore();
        assertEquals(1, player.getScore());

        player.incrementScore();
        assertEquals(2, player.getScore());

        player.incrementScore();
        assertEquals(3, player.getScore());
    }

    @Test
    @DisplayName("Should reset score to 0")
    void testResetScore() {
        player.incrementScore();
        player.incrementScore();
        player.incrementScore();
        assertEquals(3, player.getScore());

        player.resetScore();
        assertEquals(0, player.getScore());
    }

    @Test
    @DisplayName("Should handle multiple score resets")
    void testMultipleScoreResets() {
        player.incrementScore();
        player.resetScore();
        assertEquals(0, player.getScore());

        player.resetScore();
        assertEquals(0, player.getScore());
    }

    @Test
    @DisplayName("Should increment score to high values")
    void testHighScoreValues() {
        for (int i = 0; i < 100; i++) {
            player.incrementScore();
        }
        assertEquals(100, player.getScore());
    }

    // ========== Question Index Management Tests ==========

    @Test
    @DisplayName("Should start with question index of 0")
    void testInitialQuestionIndex() {
        assertEquals(0, player.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("Should advance to next question")
    void testNextQuestion() {
        player.nextQuestion();
        assertEquals(1, player.getCurrentQuestionIndex());

        player.nextQuestion();
        assertEquals(2, player.getCurrentQuestionIndex());

        player.nextQuestion();
        assertEquals(3, player.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("Should reset question index to 0")
    void testResetQuestionIndex() {
        player.nextQuestion();
        player.nextQuestion();
        player.nextQuestion();
        assertEquals(3, player.getCurrentQuestionIndex());

        player.resetQuestionIndex();
        assertEquals(0, player.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("Should handle multiple question index resets")
    void testMultipleQuestionIndexResets() {
        player.nextQuestion();
        player.resetQuestionIndex();
        assertEquals(0, player.getCurrentQuestionIndex());

        player.resetQuestionIndex();
        assertEquals(0, player.getCurrentQuestionIndex());
    }

    // ========== Question List Management Tests ==========

    @Test
    @DisplayName("Should set questions list")
    void testSetQuestions() {
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        questions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));

        player.setQuestions(questions);

        assertEquals(2, player.getQuestions().size());
        assertEquals("5 + 3", player.getQuestions().get(0).getQuestionText());
        assertEquals("10 - 4", player.getQuestions().get(1).getQuestionText());
    }

    @Test
    @DisplayName("Should create defensive copy of questions list")
    void testQuestionsListDefensiveCopy() {
        List<Question> originalQuestions = new ArrayList<>();
        originalQuestions.add(new Question("5 + 3", 8, Question.OperationType.ADD));

        player.setQuestions(originalQuestions);

        // Modify original list
        originalQuestions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));

        // Player's list should not be affected
        assertEquals(1, player.getQuestions().size());
    }

    @Test
    @DisplayName("Should handle empty questions list")
    void testEmptyQuestionsList() {
        player.setQuestions(new ArrayList<>());
        assertTrue(player.getQuestions().isEmpty());
    }

    @Test
    @DisplayName("Should replace questions list when set multiple times")
    void testReplaceQuestionsList() {
        List<Question> questions1 = new ArrayList<>();
        questions1.add(new Question("5 + 3", 8, Question.OperationType.ADD));

        player.setQuestions(questions1);
        assertEquals(1, player.getQuestions().size());

        List<Question> questions2 = new ArrayList<>();
        questions2.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));
        questions2.add(new Question("2 × 3", 6, Question.OperationType.MULTIPLY));

        player.setQuestions(questions2);
        assertEquals(2, player.getQuestions().size());
        assertEquals("10 - 4", player.getQuestions().get(0).getQuestionText());
    }

    // ========== Current Question Tests ==========

    @Test
    @DisplayName("Should get current question at index 0")
    void testGetCurrentQuestion() {
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        questions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));

        player.setQuestions(questions);

        Question currentQuestion = player.getCurrentQuestion();
        assertNotNull(currentQuestion);
        assertEquals("5 + 3", currentQuestion.getQuestionText());
        assertEquals(8, currentQuestion.getCorrectAnswer());
    }

    @Test
    @DisplayName("Should get current question after advancing")
    void testGetCurrentQuestionAfterAdvancing() {
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        questions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));
        questions.add(new Question("2 × 3", 6, Question.OperationType.MULTIPLY));

        player.setQuestions(questions);

        player.nextQuestion();
        Question q2 = player.getCurrentQuestion();
        assertEquals("10 - 4", q2.getQuestionText());

        player.nextQuestion();
        Question q3 = player.getCurrentQuestion();
        assertEquals("2 × 3", q3.getQuestionText());
    }

    @Test
    @DisplayName("Should return null when question index is out of bounds")
    void testGetCurrentQuestionOutOfBounds() {
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));

        player.setQuestions(questions);

        player.nextQuestion(); // index = 1, out of bounds

        assertNull(player.getCurrentQuestion());
    }

    @Test
    @DisplayName("Should return null when questions list is empty")
    void testGetCurrentQuestionEmptyList() {
        player.setQuestions(new ArrayList<>());
        assertNull(player.getCurrentQuestion());
    }

    @Test
    @DisplayName("Should return null when no questions are set")
    void testGetCurrentQuestionNoQuestionsSet() {
        // Questions list is initialized but empty
        assertNull(player.getCurrentQuestion());
    }

    // ========== Display Name Tests ==========

    @Test
    @DisplayName("Should get and set display name")
    void testDisplayName() {
        player.setDisplayName("Player One");
        assertEquals("Player One", player.getDisplayName());

        player.setDisplayName("Champion");
        assertEquals("Champion", player.getDisplayName());
    }

    @Test
    @DisplayName("Should handle null display name")
    void testNullDisplayName() {
        player.setDisplayName(null);
        assertNull(player.getDisplayName());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should handle complete game flow for a player")
    void testCompleteGameFlow() {
        // Setup questions
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        questions.add(new Question("10 - 4", 6, Question.OperationType.SUBTRACT));
        questions.add(new Question("2 × 3", 6, Question.OperationType.MULTIPLY));

        player.setQuestions(questions);
        player.setDisplayName("Test Player");

        // Answer first question correctly
        Question q1 = player.getCurrentQuestion();
        assertEquals("5 + 3", q1.getQuestionText());
        if (q1.isCorrect(8)) {
            player.incrementScore();
        }
        player.nextQuestion();

        // Answer second question incorrectly
        Question q2 = player.getCurrentQuestion();
        assertEquals("10 - 4", q2.getQuestionText());
        if (q2.isCorrect(5)) { // Wrong answer
            player.incrementScore();
        }
        player.nextQuestion();

        // Answer third question correctly
        Question q3 = player.getCurrentQuestion();
        assertEquals("2 × 3", q3.getQuestionText());
        if (q3.isCorrect(6)) {
            player.incrementScore();
        }
        player.nextQuestion();

        // Verify final state
        assertEquals(2, player.getScore()); // 2 correct answers
        assertEquals(3, player.getCurrentQuestionIndex());
        assertNull(player.getCurrentQuestion()); // No more questions
    }

    @Test
    @DisplayName("Should handle game reset correctly")
    void testGameReset() {
        // Setup and play
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("5 + 3", 8, Question.OperationType.ADD));
        player.setQuestions(questions);

        player.incrementScore();
        player.incrementScore();
        player.nextQuestion();

        assertEquals(2, player.getScore());
        assertEquals(1, player.getCurrentQuestionIndex());

        // Reset for new game
        player.resetScore();
        player.resetQuestionIndex();

        assertEquals(0, player.getScore());
        assertEquals(0, player.getCurrentQuestionIndex());
    }

    // ========== Thread Safety Tests ==========

    @Test
    @DisplayName("Should handle concurrent score increments safely")
    void testConcurrentScoreIncrements() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                player.incrementScore();
                latch.countDown();
            });
            threads.add(thread);
            thread.start();
        }

        latch.await();

        assertEquals(threadCount, player.getScore());
    }

    @Test
    @DisplayName("Should handle concurrent question index increments safely")
    void testConcurrentQuestionIndexIncrements() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                player.nextQuestion();
                latch.countDown();
            });
            threads.add(thread);
            thread.start();
        }

        latch.await();

        assertEquals(threadCount, player.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("Should handle concurrent score and question operations")
    void testConcurrentMixedOperations() throws InterruptedException {
        int operationsPerType = 50;
        CountDownLatch latch = new CountDownLatch(operationsPerType * 2);
        List<Thread> threads = new ArrayList<>();

        // Score increment threads
        for (int i = 0; i < operationsPerType; i++) {
            Thread thread = new Thread(() -> {
                player.incrementScore();
                latch.countDown();
            });
            threads.add(thread);
            thread.start();
        }

        // Question advancement threads
        for (int i = 0; i < operationsPerType; i++) {
            Thread thread = new Thread(() -> {
                player.nextQuestion();
                latch.countDown();
            });
            threads.add(thread);
            thread.start();
        }

        latch.await();

        assertEquals(operationsPerType, player.getScore());
        assertEquals(operationsPerType, player.getCurrentQuestionIndex());
    }

    @Test
    @DisplayName("Should handle concurrent resets safely")
    void testConcurrentResets() throws InterruptedException {
        player.incrementScore();
        player.incrementScore();
        player.nextQuestion();
        player.nextQuestion();

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                if (index % 2 == 0) {
                    player.resetScore();
                } else {
                    player.resetQuestionIndex();
                }
                latch.countDown();
            });
            threads.add(thread);
            thread.start();
        }

        latch.await();

        assertEquals(0, player.getScore());
        assertEquals(0, player.getCurrentQuestionIndex());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle very large question lists")
    void testLargeQuestionList() {
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            questions.add(new Question(i + " + " + i, i * 2, Question.OperationType.ADD));
        }

        player.setQuestions(questions);
        assertEquals(1000, player.getQuestions().size());

        // Advance to middle
        for (int i = 0; i < 500; i++) {
            player.nextQuestion();
        }

        Question q = player.getCurrentQuestion();
        assertNotNull(q);
        assertEquals("500 + 500", q.getQuestionText());
    }

    @Test
    @DisplayName("Should handle toString method")
    void testToString() {
        String result = player.toString();
        assertNotNull(result);
        assertTrue(result.contains("test-player-id"));
        // Session should be excluded from toString
        assertFalse(result.contains("session"));
    }
}
