package edu.uci.ics.kerriz.service.movies.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PeopleSearchResponseModel {

    @JsonProperty(value = "resultCode", required = true)
    private int resultCode;

    @JsonProperty(value = "message", required = true)
    private String message;

    @JsonProperty(value = "people", required = true)
    private PersonModel[] people;

    @JsonCreator
    public PeopleSearchResponseModel(@JsonProperty(value = "resultCode", required = true) int resultCode,
                                @JsonProperty(value = "message", required = true) String message,
                                @JsonProperty(value = "people", required = true) PersonModel[] people) {
        this.resultCode = resultCode;
        this.message = message;
        this.people = people;
    }

    @JsonProperty("resultCode")
    public int getResultCode(){
        return resultCode;
    }

    @JsonProperty("message")
    public String getMessage(){
        return message;
    }

    @JsonProperty("people")
    public PersonModel[] getPeople() {
        return people;
    }
}
