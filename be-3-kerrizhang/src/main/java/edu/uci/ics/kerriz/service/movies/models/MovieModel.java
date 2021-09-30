package edu.uci.ics.kerriz.service.movies.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MovieModel {

    @JsonProperty(required = true)
    public String movie_id;

    @JsonProperty(required = true)
    public String title;

    @JsonProperty(required = true)
    public Integer year;

    @JsonProperty(required = true)
    public String director;

    @JsonProperty(required = true)
    public float rating;

    @JsonProperty(required = true)
    public Integer num_votes;

    @JsonProperty
    public Integer budget;

    @JsonProperty
    public Long revenue;

    @JsonProperty
    public String overview;

    @JsonProperty
    public String backdrop_path;

    @JsonProperty
    public String poster_path;

    @JsonProperty
    public Boolean hidden;

    @JsonProperty
    public GenreModel[] genres;

    @JsonProperty
    public PersonModel[] people;


}
