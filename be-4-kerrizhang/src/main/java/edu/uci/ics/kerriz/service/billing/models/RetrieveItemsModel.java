package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RetrieveItemsModel {
    @JsonProperty
    public String email;

    @JsonProperty
    public String movie_id;

    @JsonProperty
    public Integer quantity;

    @JsonProperty
    public Float unit_price;

    @JsonProperty
    public Float discount;

    @JsonProperty
    public String sale_date;

}
