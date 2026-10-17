package com.warduel.warduel.service;

import com.warduel.warduel.config.GameConfiguration;
import com.warduel.warduel.model.ChallengeType;
import com.warduel.warduel.model.Question;
import com.warduel.warduel.model.Question.AnswerFormat;
import com.warduel.warduel.support.Solver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every challenge type, checked against the independent {@link Solver}.
 */
class ChallengeQuestionsTest {

    private static final int GAMES = 200;

    private GameConfiguration config;
    private QuestionGeneratorService generator;

    @BeforeEach
    void setUp() {
        config = new GameConfiguration();
        generator = new QuestionGeneratorService(config);
    }

    @ParameterizedTest
    @EnumSource(ChallengeType.class)
    void everyAnswerKeyIsRight(ChallengeType type) {
        for(Question q : manyQuestions(type)) {
            int expected = Solver.answer(q.getFormat().name(), q.getQuestionText(), q.getOptions());
            assertThat(q.getCorrectAnswer()).as("%s: %s", type, q.getQuestionText()).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @EnumSource(ChallengeType.class)
    void noProblemRepeatsWithinAGame(ChallengeType type) {
        for(int game = 0; game < 50; game++) {
            List<String> texts = generator.generateQuestions(type, 20).stream().map(Question::getQuestionText).toList();
            assertThat(texts).as(type.name()).doesNotHaveDuplicates();
        }
    }

    @Test
    void trueOrFalseIsBalancedAndWrongResultsAreClose() {
        List<Question> questions = manyQuestions(ChallengeType.TRUE_FALSE);
        long trueCount = questions.stream().filter(q -> q.getCorrectAnswer() == 1).count();

        assertThat(questions).allSatisfy(q -> {
            assertThat(q.getFormat()).isEqualTo(AnswerFormat.TRUE_FALSE);
            assertThat(q.getOptions()).containsExactly("True", "False");
        });
        assertThat(trueCount).isBetween(questions.size() * 35L / 100, questions.size() * 65L / 100);
        // A false statement is off by at most 10, or has its digits swapped: never absurd
        questions.stream().filter(q -> q.getCorrectAnswer() == 0).forEach(q -> {
            String[] sides = q.getQuestionText().split(" = ");
            long real = Math.round(Solver.value(sides[0]));
            long shown = Long.parseLong(sides[1]);
            boolean swapped = new StringBuilder(String.valueOf(real)).reverse().toString().equals(sides[1]);
            assertThat(Math.abs(real - shown) <= 10 || swapped).as(q.getQuestionText()).isTrue();
        });
    }

    @Test
    void missingNumberHidesOnePositiveOperand() {
        assertThat(manyQuestions(ChallengeType.MISSING_NUMBER)).allSatisfy(q -> {
            assertThat(q.getQuestionText().chars().filter(c -> c == '?').count()).isEqualTo(1);
            assertThat(q.getCorrectAnswer()).isPositive();
        });
    }

    @Test
    void compareNeverOffersEqualSidesAndBothSidesWin() {
        List<Question> questions = manyQuestions(ChallengeType.COMPARE);
        assertThat(questions).allSatisfy(q -> {
            assertThat(q.getFormat()).isEqualTo(AnswerFormat.CHOICE);
            assertThat(q.getOptions()).hasSize(2);
            assertThat(Solver.value(q.getOptions().get(0))).isNotEqualTo(Solver.value(q.getOptions().get(1)));
        });
        assertThat(questions).extracting(Question::getCorrectAnswer).contains(0, 1);
        // the hard third brings fractions, powers and roots
        assertThat(questions).anySatisfy(q -> assertThat(q.getQuestionText()).contains("/"));
        assertThat(questions).anySatisfy(q -> assertThat(q.getQuestionText()).containsAnyOf("²", "³"));
        assertThat(questions).noneSatisfy(q -> assertThat(q.getQuestionText()).containsAnyOf("⁴", "⁵"));
        assertThat(questions).anySatisfy(q -> assertThat(q.getQuestionText()).contains("√"));
    }

    @Test
    void problemsGetHarderThroughTheGame() {
        config.setMinNumber(1);
        config.setMaxNumber(30);
        List<Integer> early = new ArrayList<>();
        List<Integer> late = new ArrayList<>();
        for(int game = 0; game < GAMES; game++) {
            List<Question> questions = generator.generateQuestions(ChallengeType.CLASSIC, 21);
            IntStream.range(0, 7).forEach(i -> early.addAll(additionOperands(questions.get(i))));
            IntStream.range(14, 21).forEach(i -> late.addAll(additionOperands(questions.get(i))));
        }
        // First third: lowest third of the range (1..11); last third: the whole range
        assertThat(early).allSatisfy(n -> assertThat(n).isBetween(1, 11));
        assertThat(late).anySatisfy(n -> assertThat(n).isGreaterThan(20));
    }

    @Test
    void answersAreDescribedInWords() {
        Question tf = Question.trueFalse("2 + 2 = 4", true, Question.OperationType.ADD);
        Question choice = Question.choice("3/4", "0.7", 0);

        assertThat(tf.formatAnswer(1)).isEqualTo("True");
        assertThat(tf.formatAnswer(0)).isEqualTo("False");
        assertThat(choice.formatAnswer(1)).isEqualTo("0.7");
        assertThat(choice.formatAnswer(7)).isEqualTo("?");
        assertThat(new Question("2 + 2", 4, Question.OperationType.ADD).formatAnswer(-3)).isEqualTo("-3");
    }

    private List<Question> manyQuestions(ChallengeType type) {
        List<Question> all = new ArrayList<>();
        for(int game = 0; game < GAMES; game++) {
            all.addAll(generator.generateQuestions(type, 20));
        }
        return all;
    }

    private static List<Integer> additionOperands(Question q) {
        String[] t = q.getQuestionText().split(" ");
        return t[1].equals("+") || t[1].equals("-") ? List.of(Integer.parseInt(t[0]), Integer.parseInt(t[2])) : List.of();
    }
}
