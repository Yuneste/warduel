package com.warduel.warduel.service;

import com.warduel.warduel.config.GameConfiguration;
import com.warduel.warduel.model.ChallengeType;
import com.warduel.warduel.model.Question;
import com.warduel.warduel.model.Question.OperationType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * QuestionGeneratorService - Generiert zufällige mathematische Fragen
 *
 * Every challenge type is built on the same arithmetic core. Problems get harder through a game:
 * the first third uses the lower part of the configured number ranges, the last third the full range.
 * No problem repeats within one list.
 */
@Service
public class QuestionGeneratorService {

    private static final int TIERS = 3;
    private static final int MAX_ATTEMPTS = 30;
    private static final String[] SUPERSCRIPT = {"⁰", "¹", "²", "³", "⁴", "⁵"};

    private final GameConfiguration gameConfig;

    public QuestionGeneratorService(GameConfiguration gameConfig) {
        this.gameConfig = gameConfig;
    }

    /** One arithmetic fact, e.g. 6 × 7 = 42 */
    private record Fact(int a, int b, OperationType op, int result) {
        String text() {
            return a + " " + symbol(op) + " " + b;
        }
    }

    /**
     * Generiert eine Liste von zufälligen Fragen (Classic)
     */
    public List<Question> generateQuestions(int count) {
        return generateQuestions(ChallengeType.CLASSIC, count);
    }

    public List<Question> generateQuestions(ChallengeType type, int count) {
        List<Question> questions = new ArrayList<>(count);
        Set<String> seen = new HashSet<>();

        for(int i = 0; i < count; i++) {
            int tier = Math.min(TIERS - 1, i * TIERS / Math.max(count, 1));
            Question question = generate(type, tier);
            // Retry duplicates; tiny configured ranges may make a repeat unavoidable
            for(int attempt = 0; attempt < MAX_ATTEMPTS && !seen.add(question.getQuestionText()); attempt++) {
                question = generate(type, tier);
            }
            questions.add(question);
        }
        return questions;
    }

    private Question generate(ChallengeType type, int tier) {
        return switch(type) {
            case CLASSIC -> classic(tier);
            case TRUE_FALSE -> trueFalse(tier);
            case MISSING_NUMBER -> missingNumber(tier);
            case COMPARE -> compare(tier);
        };
    }

    // ---------- Challenge types ----------

    private Question classic(int tier) {
        Fact fact = fact(randomOperation(), tier);
        return new Question(fact.text(), fact.result(), fact.op());
    }

    /** "7 × 8 = 54": shown result is right about half of the time, otherwise close but wrong */
    private Question trueFalse(int tier) {
        Fact fact = fact(randomOperation(), tier);
        boolean isTrue = random().nextBoolean();
        int shown = isTrue ? fact.result() : plausibleWrong(fact.result());
        return Question.trueFalse(fact.text() + " = " + shown, isTrue, fact.op());
    }

    /** "? × 8 = 56": one operand is hidden */
    private Question missingNumber(int tier) {
        Fact f = fact(randomOperation(), tier);
        String op = symbol(f.op());
        boolean hideFirst = random().nextBoolean();
        String text = hideFirst
                ? "? " + op + " " + f.b() + " = " + f.result()
                : f.a() + " " + op + " ? = " + f.result();
        return new Question(text, hideFirst ? f.a() : f.b(), f.op());
    }

    /** "3/4 or 0.7": pick the bigger side. Warm-up compares a sum or product with a number */
    private Question compare(int tier) {
        String[] left;
        String[] right;
        double[] values = new double[2];
        do {
            String[][] pair = switch(tier) {
                case 0 -> exprVsNumber(tier);
                case 1 -> exprVsExpr(tier);
                default -> hardPair();
            };
            left = pair[0];
            right = pair[1];
            values[0] = Double.parseDouble(left[1]);
            values[1] = Double.parseDouble(right[1]);
        } while(Math.abs(values[0] - values[1]) < 1e-9);

        // Either side may be the bigger one
        if(random().nextBoolean()) {
            String[] tmp = left;
            left = right;
            right = tmp;
            double v = values[0];
            values[0] = values[1];
            values[1] = v;
        }
        return Question.choice(left[0], right[0], values[0] > values[1] ? 0 : 1);
    }

    // Each side is {text, numeric value}
    private String[][] exprVsNumber(int tier) {
        Fact f = fact(random().nextBoolean() ? OperationType.MULTIPLY : OperationType.ADD, tier);
        int delta = random().nextInt(1, 4) * (random().nextBoolean() ? 1 : -1);
        int other = Math.max(0, f.result() + delta);
        return new String[][] {{f.text(), String.valueOf(f.result())}, {String.valueOf(other), String.valueOf(other)}};
    }

    private String[][] exprVsExpr(int tier) {
        Fact a = fact(randomOperation(), tier);
        Fact b = fact(randomOperation(), tier);
        return new String[][] {{a.text(), String.valueOf(a.result())}, {b.text(), String.valueOf(b.result())}};
    }

    private String[][] hardPair() {
        return switch(random().nextInt(3)) {
            case 0 -> fractionVsDecimal();
            case 1 -> powerVsPower();
            default -> rootVsNumber();
        };
    }

    private String[][] fractionVsDecimal() {
        int[] denominators = {2, 3, 4, 5, 8, 10};
        int q = denominators[random().nextInt(denominators.length)];
        int p = random().nextInt(1, q);
        double fraction = (double) p / q;
        double offset = random().nextInt(3, 13) / 100.0 * (random().nextBoolean() ? 1 : -1);
        double decimal = Math.round(Math.min(0.99, Math.max(0.01, fraction + offset)) * 100) / 100.0;
        return new String[][] {{p + "/" + q, String.valueOf(fraction)}, {decimalText(decimal), String.valueOf(decimal)}};
    }

    // Squares and cubes only: the chalk font has ² and ³ but no higher superscripts
    private String[][] powerVsPower() {
        int b1 = random().nextInt(2, 10);
        int e1 = random().nextInt(2, 4);
        int b2 = random().nextInt(2, 10);
        int e2 = random().nextInt(2, 4);
        return new String[][] {
                {b1 + SUPERSCRIPT[e1], String.valueOf(Math.pow(b1, e1))},
                {b2 + SUPERSCRIPT[e2], String.valueOf(Math.pow(b2, e2))}};
    }

    private String[][] rootVsNumber() {
        int n = random().nextInt(4, 13);
        int other = n + (random().nextBoolean() ? 1 : -1);
        return new String[][] {{"√" + (n * n), String.valueOf(n)}, {String.valueOf(other), String.valueOf(other)}};
    }

    // ---------- Arithmetic core ----------

    private Fact fact(OperationType op, int tier) {
        return switch(op) {
            case ADD -> {
                int a = inRange(gameConfig.getMinNumber(), gameConfig.getMaxNumber(), tier);
                int b = inRange(gameConfig.getMinNumber(), gameConfig.getMaxNumber(), tier);
                yield new Fact(a, b, op, a + b);
            }
            case SUBTRACT -> {
                // Stellt sicher dass das Ergebnis positiv ist
                int x = inRange(gameConfig.getMinNumber(), gameConfig.getMaxNumber(), tier);
                int y = inRange(gameConfig.getMinNumber(), gameConfig.getMaxNumber(), tier);
                yield new Fact(Math.max(x, y), Math.min(x, y), op, Math.abs(x - y));
            }
            case MULTIPLY -> {
                int a = inRange(gameConfig.getMultiplicationMin(), gameConfig.getMultiplicationMax(), tier);
                int b = inRange(gameConfig.getMultiplicationMin(), gameConfig.getMultiplicationMax(), tier);
                yield new Fact(a, b, op, a * b);
            }
            case DIVIDE -> {
                // Erst das Ergebnis wählen, dann rückwärts rechnen (Division geht immer auf)
                int answer = inRange(gameConfig.getDivisionMin(), gameConfig.getDivisionMax(), tier);
                int divisor = inRange(gameConfig.getDivisionMin(), gameConfig.getDivisionMax(), tier);
                yield new Fact(answer * divisor, divisor, op, answer);
            }
        };
    }

    /**
     * A number from [min, max] that grows with the tier: tier 0 uses the lowest third of the range,
     * the last tier the whole range. After the warm-up, trivial 1s are skipped where the range allows.
     */
    private int inRange(int min, int max, int tier) {
        int lo = tier > 0 && max >= 2 ? Math.max(min, 2) : min;
        int hi = min + (int) Math.ceil((max - min) * (tier + 1) / (double) TIERS);
        hi = Math.max(lo, Math.min(max, hi));
        return random().nextInt(lo, hi + 1);
    }

    /** A wrong result a player might believe: off by one or two, off by ten, or digits swapped */
    private int plausibleWrong(int result) {
        List<Integer> candidates = new ArrayList<>(List.of(result + 1, result + 2, result - 1, result - 2, result + 10));
        if(result >= 10) {
            candidates.add(result - 10);
            String digits = String.valueOf(result);
            String swapped = new StringBuilder(digits).reverse().toString();
            if(!swapped.equals(digits) && !swapped.startsWith("0")) {
                candidates.add(Integer.parseInt(swapped));
            }
        }
        candidates.removeIf(c -> c < 0 || c == result);
        return candidates.get(random().nextInt(candidates.size()));
    }

    private static OperationType randomOperation() {
        OperationType[] types = OperationType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    private static String symbol(OperationType op) {
        return switch(op) {
            case ADD -> "+";
            case SUBTRACT -> "-";
            case MULTIPLY -> "×";
            case DIVIDE -> "÷";
        };
    }

    private static String decimalText(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static ThreadLocalRandom random() {
        return ThreadLocalRandom.current();
    }
}
