package edu.uci.ics.kerriz.service.idm.models;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RegisterResponseModel {
    @JsonProperty
    int resultCode;

    @JsonProperty
    String message;

    public RegisterResponseModel(int resultCode, String message){
        this.resultCode = resultCode;
        this.message = message;
    }
}
