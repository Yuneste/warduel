package com.warduel.warduel.dto;

import lombok.*;

/**
 * JoinGameMessage - Wird vom Client gesendet wenn er einem Spiel beitreten will
 * Beispiel JSON: {"type": "JOIN_GAME"}
 */

@Getter
@Setter
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class JoinGameMessage extends BaseMessage {

    // Optional: Gewünschter Anzeigename (falls Spieler selbst wählen darf)
    private String preferredName;

    // Optional: Gewünschte Game-ID (falls mehrere Sessions parallel laufen)
    private String gameId;

    public JoinGameMessage() {
        super();
        setType("JOIN_GAME");
    }



    // Vorerst keine zusätzlichen Felder
    // Später könnten wir hier z.B. einen gewünschten Spielernamen hinzufügen



}
