package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

/**
 * ForfeitMessage - Player forfeits the game
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("FORFEIT")
public class ForfeitMessage extends BaseMessage {

    public ForfeitMessage() {
        super();
        setType("FORFEIT");
    }
}
