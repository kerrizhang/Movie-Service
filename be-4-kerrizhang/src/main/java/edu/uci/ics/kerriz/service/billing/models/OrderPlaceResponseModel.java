package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderPlaceResponseModel {

    @JsonProperty
    int resultCode;

    @JsonProperty
    String message;

    @JsonProperty
    String approve_url;

    @JsonProperty
    public String token;

    public OrderPlaceResponseModel(int resultCode, String message, String approve_url, String token) {
        this.resultCode = resultCode;
        this.message = message;
        this.approve_url = approve_url;
        this.token = token;
    }

}
