package edu.uci.ics.kerriz.service.movies.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GetPersonResponseModel {
    @JsonProperty(value = "resultCode", required = true)
    private int resultCode;

    @JsonProperty(value = "message", required = true)
    private String message;

    @JsonProperty(value = "person", required = true)
    private PersonModel person;

    @JsonCreator
    public GetPersonResponseModel(@JsonProperty(value = "resultCode", required = true) int resultCode,
                                 @JsonProperty(value = "message", required = true) String message,
                                 @JsonProperty(value = "person", required = true) PersonModel person) {
        this.resultCode = resultCode;
        this.message = message;
        this.person = person;
    }

    @JsonProperty("resultCode")
    public int getResultCode(){
        return resultCode;
    }

    @JsonProperty("message")
    public String getMessage(){
        return message;
    }

    @JsonProperty("person")
    public PersonModel getPerson() {
        return person;
    }

}
