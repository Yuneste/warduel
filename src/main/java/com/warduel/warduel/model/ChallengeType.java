package com.warduel.warduel.model;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The kinds of problems a duel can be played with. Both players always get the same type.
 */
public enum ChallengeType {
    CLASSIC,        // 7 × 8 = ?
    TRUE_FALSE,     // 7 × 8 = 54 -> true or false
    MISSING_NUMBER, // ? × 8 = 56
    COMPARE;        // 3/4 or 0.7 -> pick the bigger one

    public static ChallengeType random() {
        ChallengeType[] all = values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }

    /**
     * @return the type, or {@code null} for unknown or missing values ("random")
     */
    public static ChallengeType parse(String value) {
        if(value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch(IllegalArgumentException e) {
            return null;
        }
    }
}
