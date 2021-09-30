package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderPlaceRequestModel {

    @JsonProperty(value="email", required = true)
    private String email;


    @JsonCreator
    public OrderPlaceRequestModel(@JsonProperty(value = "email", required = true) String email) {
        this.email = email;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

}
