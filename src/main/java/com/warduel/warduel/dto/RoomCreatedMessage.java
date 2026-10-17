package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * RoomCreatedMessage - Server confirms a private game; the client turns the code into a share link
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("ROOM_CREATED")
public class RoomCreatedMessage extends BaseMessage {

    private String roomCode;

    public RoomCreatedMessage() {
        super();
        setType("ROOM_CREATED");
    }

    public RoomCreatedMessage(String roomCode) {
        this();
        this.roomCode = roomCode;
    }
}
