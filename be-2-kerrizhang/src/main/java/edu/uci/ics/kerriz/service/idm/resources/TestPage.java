package edu.uci.ics.kerriz.service.idm.resources;

import edu.uci.ics.kerriz.service.idm.logger.ServiceLogger;
import edu.uci.ics.kerriz.service.idm.IDMService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import edu.uci.ics.kerriz.service.idm.IDMService;
import edu.uci.ics.kerriz.service.idm.configs.ConfigsModel;
import edu.uci.ics.kerriz.service.idm.configs.ServiceConfigs;
import edu.uci.ics.kerriz.service.idm.logger.ServiceLogger;
//import edu.uci.ics.kerriz.service.idm.models.IDMPrivilegeRequest;
//import edu.uci.ics.kerriz.service.idm.models.IDMPrivilegeResponse;
import edu.uci.ics.kerriz.service.idm.security.Session;
import org.glassfish.grizzly.Result;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.internal.util.ExceptionUtils;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Path("") // Outer path
public class TestPage {
    @Path("hello") // This function's path
    @GET // Type of request
    @Produces(MediaType.APPLICATION_JSON)
    public Response helloWorld() {
        System.err.println("Hello world!");
        ServiceLogger.LOGGER.info("Hello!");
        return Response.status(Response.Status.OK).build();
    }

    @GET
    @Path("list-users")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list_users() {
        ArrayList<String> users = new ArrayList<>();
        try {
            String query_string = "SELECT email FROM user";
            ServiceLogger.LOGGER.info("Listing all users");
            PreparedStatement stmt = IDMService.getCon().prepareStatement(query_string);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) {
                users.add(rs.getString("email"));
            }
            return Response.status(Response.Status.OK).entity(users).build();
        } catch(SQLException e) {
            ServiceLogger.LOGGER.warning("List user query failed");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

    }
}