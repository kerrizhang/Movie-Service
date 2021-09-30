package edu.uci.ics.kerriz.service.movies.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GetMovieResponseModel {

        @JsonProperty(value = "resultCode", required = true)
        private int resultCode;

        @JsonProperty(value = "message", required = true)
        private String message;

        @JsonProperty(value = "movie", required = true)
        private MovieModel movie;

        @JsonCreator
        public GetMovieResponseModel(@JsonProperty(value = "resultCode", required = true) int resultCode,
                                   @JsonProperty(value = "message", required = true) String message,
                                   @JsonProperty(value = "movie", required = true) MovieModel movie) {
            this.resultCode = resultCode;
            this.message = message;
            this.movie = movie;
        }

        @JsonProperty("resultCode")
        public int getResultCode(){
            return resultCode;
        }

        @JsonProperty("message")
        public String getMessage(){
            return message;
        }

        @JsonProperty("movie")
        public MovieModel getMovie() {
            return movie;
        }


}
