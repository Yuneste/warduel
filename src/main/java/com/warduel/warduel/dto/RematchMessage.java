package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

/**
 * RematchMessage - Für Rematch-Anfragen
 * Client → Server: Spieler will Rematch
 * Server → Client: Status der Rematch-Anfrage
 */

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("REMATCH")
public class RematchMessage extends BaseMessage {

    private boolean requestRematch;
    private boolean opponentAccepted; // wie lässt sich dies entscheiden? -> im service Layer behandelt (Spiellogik)
    private String statusMessage;

    public RematchMessage() {
        super();
        setType("REMATCH");
    }

    public RematchMessage(boolean requestRematch, boolean opponentAccepted, String statusMessage) {
        this();
        this.requestRematch = requestRematch;
        this.opponentAccepted = opponentAccepted;
        this.statusMessage = statusMessage;
    }
}
