package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * ChallengeMessage - Server tells the creator of an invite link that someone wants to play
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("CHALLENGE")
public class ChallengeMessage extends BaseMessage {

    private String fromName;
    private int timeoutSeconds;

    public ChallengeMessage() {
        super();
        setType("CHALLENGE");
    }

    public ChallengeMessage(String fromName, int timeoutSeconds) {
        this();
        this.fromName = fromName;
        this.timeoutSeconds = timeoutSeconds;
    }
}
