package edu.uci.ics.kerriz.service.movies.models;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ThumbnailResponseModel {

    @JsonProperty(value = "resultCode", required = true)
    private int resultCode;

    @JsonProperty(value = "message", required = true)
    private String message;

    @JsonProperty(value = "thumbnails", required = true)
    private ThumbnailModel[] thumbnails;

    @JsonCreator
    public ThumbnailResponseModel(@JsonProperty(value = "resultCode", required = true) int resultCode,
                             @JsonProperty(value = "message", required = true) String message,
                             @JsonProperty(value = "thumbnails", required = true) ThumbnailModel[] thumbnails) {
        this.resultCode = resultCode;
        this.message = message;
        this.thumbnails = thumbnails;
    }

    @JsonProperty("resultCode")
    public int getResultCode(){
        return resultCode;
    }

    @JsonProperty("message")
    public String getMessage(){
        return message;
    }

    @JsonProperty("thumbnails")
    public ThumbnailModel[] getThumbnails() {
        return thumbnails;
    }
}
