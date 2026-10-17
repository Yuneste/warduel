package com.warduel.warduel.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * ChallengeResponseMessage - The host accepts or declines a challenge
 * Example: {"type":"CHALLENGE_RESPONSE","accept":true}
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ChallengeResponseMessage extends BaseMessage {

    private boolean accept;

    public ChallengeResponseMessage() {
        super();
        setType("CHALLENGE_RESPONSE");
    }
}
