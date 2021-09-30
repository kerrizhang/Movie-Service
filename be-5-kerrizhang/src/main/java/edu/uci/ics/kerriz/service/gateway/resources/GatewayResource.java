package edu.uci.ics.kerriz.service.gateway.resources;

import edu.uci.ics.kerriz.service.gateway.GatewayService;
import edu.uci.ics.kerriz.service.gateway.configs.BillingConfigs;
import edu.uci.ics.kerriz.service.gateway.configs.IdmConfigs;
import edu.uci.ics.kerriz.service.gateway.configs.MoviesConfigs;
import edu.uci.ics.kerriz.service.gateway.logger.ServiceLogger;
import edu.uci.ics.kerriz.service.gateway.threadpool.ClientRequest;
import edu.uci.ics.kerriz.service.gateway.threadpool.HTTPMethod;
import edu.uci.ics.kerriz.service.gateway.transaction.TransactionGenerator;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


@Path("")
public class GatewayResource {
    private UriBuilder getServiceURI(String service, String endpoint){
        // TODO example for idm - not done?
        UriBuilder builder = null;
        if(service.equals("idm")){
            IdmConfigs configs = GatewayService.getIdmConfigs();
            builder = UriBuilder.fromUri(configs.getScheme() + configs.getHostName()+configs.getPath()).port(configs.getPort());
            switch(endpoint){
                case "login":
                    builder = builder.path(configs.getLoginPath());
                    break;
                case "register":
                    builder = builder.path(configs.getRegisterPath());
                    break;
                case "session":
                    builder = builder.path(configs.getSessionPath());
                    break;
                case "privilege":
                    builder = builder.path(configs.getPrivilegePath());
                    break;
                default:
                    return null;

            }
        }
        else if(service.equals("movies")){
            MoviesConfigs configs = GatewayService.getMoviesConfigs();
            builder = UriBuilder.fromUri(configs.getScheme() + configs.getHostName()+configs.getPath()).port(configs.getPort());
            switch(endpoint){
                case "search":
                    builder = builder.path(configs.getSearchPath());
                    break;
//                case "browse/":
//                    builder = builder.path(configs.getBrowsePath());
//                    break;
//                case "{get}/{.+}":
//                    builder = builder.path(configs.getGetPath());
//                    break;
                case "thumbnail":
                    builder = builder.path(configs.getThumbnailPath());
                    break;
                case "people":
                    builder = builder.path(configs.getPeoplePath());
                    break;
                case "people/search":
                    builder = builder.path(configs.getPeopleSearchPath());
                    break;
//                case "people/get/":
//                    builder = builder.path(configs.getPeopleGetPath());
//                    break;
                default:
                    if (endpoint.substring(0,4).equals("get/")){
                        builder = builder.path(configs.getGetPath());
                        break;
                    }
                    else if(endpoint.substring(0,7).equals("browse/")){
                        builder = builder.path(configs.getBrowsePath());
                        break;
                    }
                    else if(endpoint.substring(0,11).equals("people/get/")){
                        builder = builder.path(configs.getPeopleGetPath());
                        break;
                    }
                    return null;

            }
        }
        else if(service.equals("billing")){
            BillingConfigs configs = GatewayService.getBillingConfigs();
            builder = UriBuilder.fromUri(configs.getScheme() + configs.getHostName()+configs.getPath()).port(configs.getPort());
            switch(endpoint){
                case "cart/insert":
                    builder = builder.path(configs.getCartInsertPath());
                    break;
                case "cart/update":
                    builder = builder.path(configs.getCartUpdatePath());
                    break;
                case "cart/delete":
                    builder = builder.path(configs.getCartDeletePath());
                    break;
                case "cart/retrieve":
                    builder = builder.path(configs.getCartRetrievePath());
                    break;
                case "cart/clear":
                    builder = builder.path(configs.getCartClearPath());
                    break;
                case "order/place":
                    builder = builder.path(configs.getOrderPlacePath());
                    break;
                case "order/retrieve":
                    builder = builder.path(configs.getOrderRetrievePath());
                    break;
                case "order/complete":
                    builder = builder.path(configs.getOrderCompletePath());
                    break;
                default:
                    return null;

            }
        }
        if(builder == null){
            return null;
        }
        return builder;
    }

    // Enqueue the request
    // get request no args --> all in url
    private Response do_request(HTTPMethod method, String uri, String endpoint, HttpHeaders headers, byte[] args){
        // Create request object
        ClientRequest req = new ClientRequest();
        req.method = method;
        req.URI = uri;
        req.endpoint = endpoint;
        req.requestBytes = args;
        req.email = headers.getHeaderString("email");
        req.session_id = headers.getHeaderString("session_id");
        req.transaction_id = TransactionGenerator.generate();

        // Put request object into queue
        ServiceLogger.LOGGER.info(String.format("Put request %s into queue", req.transaction_id));
        GatewayService.getThreadPool().putRequest(req);

        // Send no content reply 204
        return Response.status(Response.Status.NO_CONTENT)
                .header("transaction_id", req.transaction_id)
                .header("request_delay", 1)
                .build();
    }

    @POST
    @Path("{service}/{endpoint:.+}")
    // any character = . + = one or more
    public Response request(@Context HttpHeaders headers,
                            @PathParam("service") String service,
                            @PathParam("endpoint") String endpoint,
                            byte[] args){
        String uri = getServiceURI(service, endpoint).build().toString();
        if(uri == null){
            ServiceLogger.LOGGER.severe(String.format("Unknown service "));
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return do_request(HTTPMethod.POST, uri, endpoint, headers, args);
    }

    @GET
    @Path("{service}/{endpoint:.+}")
    public Response request(@Context HttpHeaders headers,
                            @Context UriInfo uriinfo, // all parameters
                            @PathParam("service") String service,
                            @PathParam("endpoint") String endpoint,
                            byte[] args){
        UriBuilder uribuilder = getServiceURI(service, endpoint);
        if(uribuilder == null){
            ServiceLogger.LOGGER.severe(String.format("Unknown service "));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Add query parameters to URI
        MultivaluedMap<String, String> params = uriinfo.getQueryParameters();
        for(String k:params.keySet()){
            uribuilder = uribuilder.queryParam(k, params.get(k));
        }
        String uri = uribuilder.build().toString();
        return do_request(HTTPMethod.GET, uri, endpoint, headers, null);
    }

    @GET
    @Path("report")
    public Response report(@Context HttpHeaders headers){
        String transaction_id = headers.getHeaderString("transaction_id");
        if(transaction_id == null){
            ServiceLogger.LOGGER.severe(String.format("No transaction ID supplied with report request."));
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        Integer response_status = null;
        String response_string = null;
        ServiceLogger.LOGGER.info(String.format("Looking for transaction results"));
        Connection con = null;
        try{
            // Request connection from Hikari
            con = GatewayService.getConnectionPoolManager().requestCon();
            // SELECT transaction by ID from database
            String query = "SELECT * FROM responses WHERE transaction_id = ?;";
            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setString(1, transaction_id);
            ServiceLogger.LOGGER.info(""+stmt);
            ResultSet rs = stmt.executeQuery();

            // Put result into response_status, response_string
            while (rs.next()) {
                response_status = rs.getInt("http_status");
                response_string = rs.getString("response");
            }
            // Delete from database
            String delete_query = "DELETE FROM responses WHERE transaction_id = ?;";
            PreparedStatement delete_stmt = con.prepareStatement(delete_query);
            delete_stmt.setString(1, transaction_id);
            ServiceLogger.LOGGER.info(""+delete_stmt);
            delete_stmt.execute();

        }catch(SQLException e){
            ServiceLogger.LOGGER.info(String.format("SQL error for transaction ID"));
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }finally{
            if(con != null){
                GatewayService.getConnectionPoolManager().releaseCon(con);
            }
        }
        if(response_status != null){
            // not processed yet
            // TODO copy over the headers of the callback response
            return Response.status(response_status).entity(response_string).build();
        }
        return Response.status(Response.Status.NO_CONTENT)
                .header("message", "No result available yet.")
                .header("request_delay", 1)
                .header("transaction_id", transaction_id)
                .build();

    }



}
