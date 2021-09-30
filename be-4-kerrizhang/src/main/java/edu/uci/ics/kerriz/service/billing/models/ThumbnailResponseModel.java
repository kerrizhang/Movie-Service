package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ThumbnailResponseModel {

    @JsonProperty
    public Integer resultCode;

    @JsonProperty
    public String message;

    @JsonProperty
    public ThumbnailModel[] thumbnails;

}
