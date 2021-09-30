package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AmountModel {
    @JsonProperty
    public String total;

    @JsonProperty
    public String currency;
}
