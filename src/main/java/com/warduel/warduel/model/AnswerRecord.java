package com.warduel.warduel.model;

/**
 * One answered question, kept for the post-game stats.
 *
 * @param number     question number, starting at 1
 * @param question   the question as shown to the player
 * @param answer     the player's answer as shown to them (e.g. "42", "True")
 * @param correct    whether it was right
 * @param millis     time from the question appearing to the answer arriving
 */
public record AnswerRecord(int number, String question, String answer, boolean correct, long millis) {
}
