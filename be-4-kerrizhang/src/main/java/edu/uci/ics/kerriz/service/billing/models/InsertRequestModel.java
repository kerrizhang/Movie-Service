package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InsertRequestModel {

    @JsonProperty(value = "email", required = true)
    public String email;

    @JsonProperty(value = "movie_id", required = true)
    public String movie_id;

    @JsonProperty(value = "quantity", required = true)
    public Integer quantity;
}
