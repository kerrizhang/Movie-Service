package edu.uci.ics.kerriz.service.basic.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Example request model
public class RequestModel{

    @JsonProperty(value = "num_x", required = true)
    private Integer x;
    @JsonProperty(value = "num_y", required = true)
    private Integer y;
    @JsonProperty(value = "num_z", required = true)
    private Integer z;

    @JsonCreator
    public RequestModel(@JsonProperty(value = "num_x", required = true) Integer x,
                               @JsonProperty(value = "num_y", required = true) Integer y,
                        @JsonProperty(value = "num_z", required = true) Integer z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @JsonProperty("num_x")
    public Integer getX() {
        return x;
    }

    @JsonProperty("num_y")
    public Integer getY() {
        return y;
    }

    @JsonProperty("num_z")
    public Integer getZ() {
        return z;
    }
}
