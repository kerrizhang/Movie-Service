package edu.uci.ics.kerriz.service.movies.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ThumbnailModel {

    @JsonProperty(required = true)
    public String movie_id;

    @JsonProperty(required = true)
    public String title;

    @JsonProperty
    public String backdrop_path;

    @JsonProperty
    public String poster_path;

}
