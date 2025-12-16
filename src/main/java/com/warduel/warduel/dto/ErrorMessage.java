package com.warduel.warduel.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonTypeName("ERROR")
public class ErrorMessage extends BaseMessage {
    private String errorMessage;

    public ErrorMessage(String errorMessage) {
        super();
        setType("ERROR");
        this.errorMessage = errorMessage;
    }

    public ErrorMessage() {
        super();
        setType("ERROR");
    }
}