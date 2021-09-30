package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InsertResponseModel {

    @JsonProperty(value = "resultCode", required = true)
    public Integer resultCode;

    @JsonProperty(value = "message", required = true)
    public String message;

    @JsonCreator
    public InsertResponseModel(@JsonProperty(value = "resultCode", required = true) int resultCode,
                          @JsonProperty(value = "message", required = true) String message) {
        this.resultCode = resultCode;
        this.message = message;
    }
}
