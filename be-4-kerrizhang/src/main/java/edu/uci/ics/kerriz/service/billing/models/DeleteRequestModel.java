package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteRequestModel {

    @JsonProperty(value = "email", required = true)
    public String email;

    @JsonProperty(value = "movie_id", required = true)
    public String movie_id;

}
