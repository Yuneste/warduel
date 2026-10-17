package com.warduel.warduel.model;

import java.util.regex.Pattern;

/**
 * Cleans up player-chosen nicknames before they are shown to anyone.
 */
public final class PlayerNames {

    public static final int MAX_LENGTH = 16;

    // Letters and digits of any script, plus a few separators people use in names
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N} ._'-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PlayerNames() {
    }

    /**
     * @return the cleaned name, or {@code null} if nothing usable is left (the caller picks a default)
     */
    public static String sanitize(String raw) {
        if(raw == null) {
            return null;
        }
        String cleaned = WHITESPACE.matcher(raw).replaceAll(" ");
        cleaned = NOT_ALLOWED.matcher(cleaned).replaceAll("").trim();
        if(cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_LENGTH)).trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }
}
