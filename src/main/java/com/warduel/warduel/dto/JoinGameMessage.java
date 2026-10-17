package com.warduel.warduel.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * JoinGameMessage - Wird vom Client nach dem Verbinden gesendet, um einem Spiel beizutreten
 * Examples:
 *   {"type":"JOIN_GAME","playerName":"Yuko"}                      public matchmaking
 *   {"type":"JOIN_GAME","playerName":"Yuko","createRoom":true}    private game, answered with ROOM_CREATED
 *   {"type":"JOIN_GAME","playerName":"Kai","roomCode":"K7QM2X"}   join a friend's private game
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class JoinGameMessage extends BaseMessage {

    private String playerName;
    private boolean createRoom;
    private String roomCode;
    private String challengeType; // private games only: CLASSIC, TRUE_FALSE, MISSING_NUMBER, COMPARE; empty = random
    private String playerToken;   // random device token for the leaderboard (public games only)

    @Override
    public String toString() {
        // never log the token
        return "JoinGameMessage(playerName=" + playerName + ", createRoom=" + createRoom + ", roomCode=" + roomCode
                + ", challengeType=" + challengeType + ")";
    }

    public JoinGameMessage() {
        super();
        setType("JOIN_GAME");
    }
}
