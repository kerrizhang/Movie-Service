package edu.uci.ics.kerriz.service.basic.resources;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uci.ics.kerriz.service.basic.logger.ServiceLogger;
import edu.uci.ics.kerriz.service.basic.models.ResponseModel;
import edu.uci.ics.kerriz.service.basic.models.RequestModel;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;


@Path("basic") // Outer path
public class BasicMath {

    @Path("math") // This function's path
    @POST // Type of request
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    // Example endpoint to add two numbers
    public Response examplePost(@Context HttpHeaders headers, String jsonText) {
        RequestModel requestModel;
        ResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();

        // Validate model & map JSON to POJO
        try {
            requestModel = mapper.readValue(jsonText, RequestModel.class);
        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new ResponseModel(resultCode, "JSON Parse Exception", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(responseModel).build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new ResponseModel(resultCode, "JSON Mapping Exception", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(responseModel).build();
            } else {
                resultCode = -1;
                responseModel = new ResponseModel(resultCode, "Internal Server Error", null);
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseModel).build();
            }
        }

        ServiceLogger.LOGGER.info("Received post request");
        ServiceLogger.LOGGER.info("Request:\n" + jsonText);

        // Do work
        if ((0 < requestModel.getX() && requestModel.getX() < 100) && (0 < requestModel.getY() && requestModel.getY() < 100) && (-10 <= requestModel.getZ() && requestModel.getZ() <= 10)){
            Integer sum = requestModel.getX() * requestModel.getY() + requestModel.getZ();
            ServiceLogger.LOGGER.info("Calculated value");
            responseModel = new ResponseModel(20, "Calculation successful.", sum);
        }
        else{
            responseModel = new ResponseModel(21, "Data contains invalid integers.", null);
        }

        return Response.status(Response.Status.OK).entity(responseModel).build();
    }
}
