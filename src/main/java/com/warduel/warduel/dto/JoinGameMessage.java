package com.warduel.warduel.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * JoinGameMessage - Wird vom Client nach dem Verbinden gesendet, um einem Spiel beizutreten
 * Examples:
 *   {"type":"JOIN_GAME","playerName":"Yuko"}                      public matchmaking
 *   {"type":"JOIN_GAME","playerName":"Yuko","createRoom":true}    private game, answered with ROOM_CREATED
 *   {"type":"JOIN_GAME","playerName":"Kai","roomCode":"K7QM2X"}   join a friend's private game
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class JoinGameMessage extends BaseMessage {

    private String playerName;
    private boolean createRoom;
    private String roomCode;

    public JoinGameMessage() {
        super();
        setType("JOIN_GAME");
    }
}
