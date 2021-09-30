package edu.uci.ics.kerriz.service.movies.models;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MoviesRequestModel {

    @JsonProperty(value="email", required = true)
    private String email;

    @JsonProperty(value="session_id", required = true)
    private String session_id;

    @JsonProperty(value="transaction_id", required = true)
    private String transaction_id;

    @JsonCreator
    public MoviesRequestModel(@JsonProperty(value = "email", required = true) String email,
                              @JsonProperty(value = "session_id", required = true) String session_id,
                              @JsonProperty(value = "transaction_id", required = true) String transaction_id) {
        this.email = email;
        this.session_id = session_id;
        this.transaction_id = transaction_id;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    @JsonProperty("session_id")
    public String getSession_id() {
        return session_id;
    }

    @JsonProperty("transaction_id")
    public String getTransaction_id() {
        return transaction_id;
    }
}
