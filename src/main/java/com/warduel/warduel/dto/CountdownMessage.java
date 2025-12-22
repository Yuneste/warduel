package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;

/**
 * CountdownMessage - Server sends countdown before game starts
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("COUNTDOWN")
public class CountdownMessage extends BaseMessage {

    private int countdown; // 3, 2, 1
    private String message; // "Game starting in..."

    public CountdownMessage() {
        super();
        setType("COUNTDOWN");
    }

    public CountdownMessage(int countdown, String message) {
        this();
        this.countdown = countdown;
        this.message = message;
    }
}
