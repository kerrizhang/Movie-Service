package edu.uci.ics.kerriz.service.billing.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IDMPrivilegeRequestModel {

    @JsonProperty(value = "email", required = true)
    private String email;

    @JsonProperty(value = "plevel", required = true)
    private Integer plevel;

    @JsonCreator
    public IDMPrivilegeRequestModel(@JsonProperty(value = "email", required = true) String email,
                               @JsonProperty(value = "plevel", required = true) Integer plevel) {
        this.email = email;
        this.plevel = plevel;
    }

    @JsonProperty("email")
    public String getResultCode(){
        return email;
    }

    @JsonProperty("plevel")
    public Integer getMessage(){
        return plevel;
    }
}

