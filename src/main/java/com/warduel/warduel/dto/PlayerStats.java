package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.warduel.warduel.model.AnswerRecord;
import com.warduel.warduel.model.Player;

import java.util.List;

/**
 * Post-game numbers for one player. {@code answers} is only filled in for the player themself.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerStats(int answered, int correct, Long averageMillis, Long fastestMillis, List<AnswerRecord> answers) {

    public static PlayerStats of(Player player, boolean withAnswers) {
        if(player == null) {
            return new PlayerStats(0, 0, null, null, withAnswers ? List.of() : null);
        }
        List<AnswerRecord> log = List.copyOf(player.getAnswerLog());
        int correct = (int) log.stream().filter(AnswerRecord::correct).count();
        Long average = log.isEmpty() ? null : Math.round(log.stream().mapToLong(AnswerRecord::millis).average().orElse(0));
        Long fastest = log.stream().filter(AnswerRecord::correct).mapToLong(AnswerRecord::millis).min().stream().boxed().findFirst().orElse(null);
        return new PlayerStats(log.size(), correct, average, fastest, withAnswers ? log : null);
    }
}
