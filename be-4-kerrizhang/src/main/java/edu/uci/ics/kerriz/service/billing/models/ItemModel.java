package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ItemModel {

    @JsonProperty
    public String email;

    @JsonProperty
    public Float unit_price;

    @JsonProperty
    public Float discount;

    @JsonProperty
    public Integer quantity;

    @JsonProperty
    public String movie_id;

    @JsonProperty
    public String movie_title;

    @JsonProperty
    public String backdrop_path;

    @JsonProperty
    public String poster_path;

}
