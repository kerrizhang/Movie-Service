package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RetrieveRequestModel {

    @JsonProperty(value = "email", required = true)
    public String email;

}
