package edu.uci.ics.kerriz.service.idm.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionResponseModel {
    @JsonProperty
    int resultCode;

    @JsonProperty
    String message;

    @JsonProperty
    String session_id;

    public SessionResponseModel(int resultCode, String message, String session_id){
        this.resultCode = resultCode;
        this.message = message;
        this.session_id = session_id;
    }
}
