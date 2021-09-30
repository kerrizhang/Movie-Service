package edu.uci.ics.kerriz.service.movies.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenreModel {

    @JsonProperty
    public Integer genre_id;

    @JsonProperty
    public String name;
}