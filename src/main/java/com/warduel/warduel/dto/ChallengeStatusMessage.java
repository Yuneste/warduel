package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * ChallengeStatusMessage - How a challenge is going.
 *
 * To the challenger: SENT (waiting for the host), DECLINED, TIMED_OUT, BUSY (someone else is
 * already challenging), HOST_LEFT. To the host: CANCELLED (the challenger left), TIMED_OUT.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("CHALLENGE_STATUS")
public class ChallengeStatusMessage extends BaseMessage {

    public enum Status { SENT, DECLINED, TIMED_OUT, BUSY, HOST_LEFT, CANCELLED }

    private Status status;
    private String name;         // the other person's nickname
    private Integer timeoutSeconds;

    public ChallengeStatusMessage() {
        super();
        setType("CHALLENGE_STATUS");
    }

    public ChallengeStatusMessage(Status status, String name, Integer timeoutSeconds) {
        this();
        this.status = status;
        this.name = name;
        this.timeoutSeconds = timeoutSeconds;
    }
}
