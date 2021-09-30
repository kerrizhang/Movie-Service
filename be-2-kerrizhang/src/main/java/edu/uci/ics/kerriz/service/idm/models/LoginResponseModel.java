package edu.uci.ics.kerriz.service.idm.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginResponseModel {

    @JsonProperty
    int resultCode;

    @JsonProperty
    String message;

    @JsonProperty
    String session_id;

    public LoginResponseModel(int resultCode, String message, String session_id) {
        this.resultCode = resultCode;
        this.message = message;
        this.session_id = session_id;
    }


}

