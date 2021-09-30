package edu.uci.ics.kerriz.service.idm.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PrivilegeRequestModel {

    @JsonProperty(value="email", required = true)
    private String email;

    @JsonProperty(value="plevel", required=true)
    private int plevel;

    @JsonCreator
    public PrivilegeRequestModel(@JsonProperty(value = "email", required = true) String email,
                             @JsonProperty(value = "plevel", required = true) int plevel) {
        this.email = email;
        this.plevel = plevel;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    @JsonProperty("plevel")
    public int getPlevel() {
        return plevel;
    }


}
