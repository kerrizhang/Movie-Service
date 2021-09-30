package edu.uci.ics.kerriz.service.idm.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PrivilegeResponseModel {
    @JsonProperty
    int resultCode;

    @JsonProperty
    String message;

    public PrivilegeResponseModel(int resultCode, String message){
        this.resultCode = resultCode;
        this.message = message;
    }
}
