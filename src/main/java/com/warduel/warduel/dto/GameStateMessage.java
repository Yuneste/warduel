package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.GameSession.GameStatus;
import lombok.*;

/**
 * GameStateMessage - Server informiert Client über den Spielstatus
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("GAME_STATE")
public class GameStateMessage extends BaseMessage {

    private GameStatus gameStatus;
    private String yourName;
    private String opponentName;
    private Long remainingSeconds; // Nachricht an den Spieler (z.B "Verbleibende Zeit in Sekunden")

    public GameStateMessage() {
        super();
        setType("GAME_STATE");  // ← NEU
    }

    public GameStateMessage(
            GameSession.GameStatus gameStatus,
            String yourName,
            String opponentName,
            Long remainingSeconds
    ) {
        this();  // Ruft Konstruktor oben auf
        this.gameStatus = gameStatus;
        this.yourName = yourName;
        this.opponentName = opponentName;
        this.remainingSeconds = remainingSeconds;
    }
}
