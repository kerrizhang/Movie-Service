package edu.uci.ics.kerriz.service.basic.resources;

import edu.uci.ics.kerriz.service.basic.logger.ServiceLogger;
import edu.uci.ics.kerriz.service.basic.models.ReverseResponseModel;
import edu.uci.ics.kerriz.service.basic.models.ReverseRequestModel;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("basic") // Outer path
public class BasicReverse {

    @Path("reverse/{word}") // This function's path
    @GET // Type of request
    // Note that get requests do not "consume"
    @Produces(MediaType.APPLICATION_JSON)
    public Response exampleGet(@Context HttpHeaders headers, @PathParam("word") String word) {
        ReverseRequestModel requestModel = new ReverseRequestModel(word);
        ReverseResponseModel responseModel;

        ServiceLogger.LOGGER.info("String successfully received");
        ServiceLogger.LOGGER.info("Request: " + word);

        String input = requestModel.getWord();
        if (input.isEmpty()){
            responseModel = new ReverseResponseModel(11, "String is empty", null);
        }
        else if (input.matches("^[a-zA-Z0-9_ ]*$") ){
            String reversed = "";
            for (int i=input.length()-1; i>=0; --i) {
                reversed += input.charAt(i);
            }

            ServiceLogger.LOGGER.info("String reversed");
            responseModel = new ReverseResponseModel(10, "String successfully reversed.", reversed);

        }
        else {
            responseModel = new ReverseResponseModel(12, "String contains invalid characters.", null);
        }
      return Response.status(Response.Status.OK).entity(responseModel).build();
    }

}
