package edu.uci.ics.kerriz.service.movies.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdmPrivilegeResponseModel {
    @JsonProperty
    private Integer resultCode;

    @JsonProperty
    private String message;

    @JsonCreator
    public IdmPrivilegeResponseModel(@JsonProperty(value = "resultCode", required = true) Integer resultCode,
                                     @JsonProperty(value = "message", required = true)String message){
        this.resultCode = resultCode;
        this.message = message;
    }

    public Integer getResultCode() {return resultCode;}

    public String getMessage() {return message;}

}
