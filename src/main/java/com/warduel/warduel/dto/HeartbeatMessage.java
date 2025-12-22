package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * HeartbeatMessage - Client sends periodic heartbeat to keep connection alive
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("HEARTBEAT")
public class HeartbeatMessage extends BaseMessage {

    public HeartbeatMessage() {
        super();
        setType("HEARTBEAT");
    }
}
