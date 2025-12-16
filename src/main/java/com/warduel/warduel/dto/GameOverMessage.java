package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

/**
 * GameOverMessage - Server informiert dass das Spiel vorbei ist
 * Zeigt Ergebnis und Gewinner
 */

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("GAME_OVER")
public class GameOverMessage extends BaseMessage {

    private int yourScore;
    private int opponentScore;
    private boolean youWon;
    private boolean draw; // Unentschieden
    private String winnerName;

    public GameOverMessage() {
        super();
        setType("GAME_OVER");
    }

    public GameOverMessage(int yourScore, int opponentScore, boolean youWon, boolean draw, String winnerName) {
        this();
        this.yourScore = yourScore;
        this.opponentScore = opponentScore;
        this.youWon = youWon;
        this.draw = draw;
        this.winnerName = winnerName;
    }
}
