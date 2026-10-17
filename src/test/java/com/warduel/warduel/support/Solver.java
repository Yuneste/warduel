package com.warduel.warduel.support;

import java.util.List;
import java.util.Map;

/**
 * Independent answer key for every challenge type, used to check the generator and to let
 * scripted test players answer correctly. Deliberately shares no code with the generator.
 */
public final class Solver {

    private static final Map<Character, Integer> SUPERSCRIPT = Map.of('²', 2, '³', 3, '⁴', 4, '⁵', 5);

    private Solver() {
    }

    /** The correct int answer for a question as the client sees it */
    public static int answer(String format, String questionText, List<String> options) {
        return switch(format) {
            case "NUMBER" -> questionText.contains("?") ? missing(questionText) : (int) Math.round(value(questionText));
            case "TRUE_FALSE" -> isTrue(questionText) ? 1 : 0;
            case "CHOICE" -> value(options.get(0)) > value(options.get(1)) ? 0 : 1;
            default -> throw new IllegalArgumentException(format);
        };
    }

    /** "7 × 8 = 54" */
    public static boolean isTrue(String statement) {
        String[] sides = statement.split(" = ");
        return Math.round(value(sides[0])) == Long.parseLong(sides[1]);
    }

    /** "? × 8 = 56" or "56 ÷ ? = 8" */
    public static int missing(String equation) {
        String[] sides = equation.split(" = ");
        String[] t = sides[0].split(" ");
        int result = Integer.parseInt(sides[1]);
        boolean first = t[0].equals("?");
        int known = Integer.parseInt(first ? t[2] : t[0]);
        return switch(t[1]) {
            case "+" -> result - known;
            case "-" -> first ? result + known : known - result;
            case "×" -> result / known;
            case "÷" -> first ? result * known : known / result;
            default -> throw new IllegalArgumentException(equation);
        };
    }

    /** Value of "6 × 7", "3/4", "0.7", "2³", "√81" or a plain number */
    public static double value(String text) {
        String s = text.trim();
        String[] t = s.split(" ");
        if(t.length == 3) {
            double a = Double.parseDouble(t[0]);
            double b = Double.parseDouble(t[2]);
            return switch(t[1]) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "×" -> a * b;
                case "÷" -> a / b;
                default -> throw new IllegalArgumentException(text);
            };
        }
        if(s.startsWith("√")) {
            return Math.sqrt(Double.parseDouble(s.substring(1)));
        }
        if(s.contains("/")) {
            String[] f = s.split("/");
            return Double.parseDouble(f[0]) / Double.parseDouble(f[1]);
        }
        char last = s.charAt(s.length() - 1);
        if(SUPERSCRIPT.containsKey(last)) {
            return Math.pow(Double.parseDouble(s.substring(0, s.length() - 1)), SUPERSCRIPT.get(last));
        }
        return Double.parseDouble(s);
    }
}
