package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RetrieveResponseModel {

    @JsonProperty(value = "resultCode", required = true)
    public Integer resultCode;

    @JsonProperty(value = "message", required = true)
    public String message;

    @JsonProperty(value = "items")
    public ItemModel[] items;

    @JsonCreator
    public RetrieveResponseModel(@JsonProperty(value = "resultCode", required = true) int resultCode,
                                 @JsonProperty(value = "message", required = true) String message,
                                 @JsonProperty(value = "items") ItemModel[] items) {
        this.resultCode = resultCode;
        this.message = message;
        this.items = items;
    }

}
