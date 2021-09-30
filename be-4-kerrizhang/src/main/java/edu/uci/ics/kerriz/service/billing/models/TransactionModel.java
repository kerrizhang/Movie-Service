package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TransactionModel {

    @JsonProperty
    public String capture_id;

    @JsonProperty
    public String state;

    @JsonProperty
    public AmountModel amount;

    @JsonProperty
    public TransactionFeeModel transaction_fee;

    @JsonProperty
    public String create_time;

    @JsonProperty
    public String update_time;

    @JsonProperty
    public RetrieveItemsModel[] items;


}
