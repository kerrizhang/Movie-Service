package edu.uci.ics.kerriz.service.basic.models;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// Example response model
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseModel{

    @JsonProperty(value = "resultCode", required = true)
    private int resultCode;

    @JsonProperty(value = "message", required = true)
    private String message;

    @JsonProperty(value = "value")
    private Integer sum;

    @JsonCreator
    public ResponseModel(@JsonProperty(value = "resultCode", required = true) int resultCode,
                                @JsonProperty(value = "message", required = true) String message,
                                @JsonProperty(value = "value") Integer sum) {
        this.resultCode = resultCode;
        this.message = message;
        this.sum = sum;
    }

    @JsonProperty("resultCode")
    public int getResultCode(){
        return resultCode;
    }

    @JsonProperty("message")
    public String getMessage(){
        return message;
    }

    @JsonProperty("value")
    public Integer getSum() {
        return sum;
    }
}
