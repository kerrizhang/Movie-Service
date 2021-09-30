package edu.uci.ics.kerriz.service.basic.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Example request model
public class ReverseRequestModel{

    @JsonProperty(value = "word", required = true)
    private String word;


    @JsonCreator
    public ReverseRequestModel(@JsonProperty(value = "word", required = true) String word) {
        this.word = word;
    }

    @JsonProperty("word")
    public String getWord() {
        return word;
    }

}
