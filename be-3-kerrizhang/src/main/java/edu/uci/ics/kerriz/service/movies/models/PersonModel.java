package edu.uci.ics.kerriz.service.movies.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonModel {

    @JsonProperty(required = true)
    public Integer person_id;

    @JsonProperty(required = true)
    public String name;

    @JsonProperty
    public String gender;

    @JsonProperty
    public String birthday;

    @JsonProperty
    public String biography;

    @JsonProperty
    public String birthplace;

    @JsonProperty
    public Float popularity;

    @JsonProperty
    public String profile_path;
}