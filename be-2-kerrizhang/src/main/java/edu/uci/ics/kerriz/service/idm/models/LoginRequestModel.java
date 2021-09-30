package edu.uci.ics.kerriz.service.idm.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginRequestModel {

    @JsonProperty(value="email", required = true)
    private String email;

    @JsonProperty(value="password", required=true)
    private char[] password;

    @JsonCreator
    public LoginRequestModel(@JsonProperty(value = "email", required = true) String email,
                           @JsonProperty(value = "password", required = true) char[] password) {
        this.email = email;
        this.password = password;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    @JsonProperty("password")
    public char[] getPassword() {
        return password;
    }


}