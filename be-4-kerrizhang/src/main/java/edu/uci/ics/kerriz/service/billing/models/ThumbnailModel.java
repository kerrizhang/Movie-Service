package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ThumbnailModel {

    @JsonProperty(value = "movie_id", required = true)
    public String movie_id;

    @JsonProperty(value = "title", required = true)
    public String title;

    @JsonProperty(value = "backdrop_path")
    public String backdrop_path;

    @JsonProperty(value = "poster_path")
    public String poster_path;

}
