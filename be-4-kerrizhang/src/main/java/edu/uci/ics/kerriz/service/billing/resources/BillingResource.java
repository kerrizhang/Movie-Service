package edu.uci.ics.kerriz.service.billing.resources;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import edu.uci.ics.kerriz.service.billing.BillingService;
import edu.uci.ics.kerriz.service.billing.configs.IdmConfigs;
import edu.uci.ics.kerriz.service.billing.configs.MoviesConfigs;
import edu.uci.ics.kerriz.service.billing.logger.ServiceLogger;
import edu.uci.ics.kerriz.service.billing.models.*;
import org.glassfish.jersey.jackson.JacksonFeature;

import com.braintreepayments.http.serializer.Json;
import com.braintreepayments.http.HttpResponse;
import com.braintreepayments.http.exceptions.HttpException;
import com.paypal.orders.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;


@Path("")
public class BillingResource {
    private Response send_privilege_request(String endpoint, IDMPrivilegeRequestModel request_payload) throws Exception{
        ServiceLogger.LOGGER.info(String.format("Preparing remove request to do IDM endpoint %s", endpoint));
        Client client = ClientBuilder.newClient();
        client.register(JacksonFeature.class);

        IdmConfigs configs = BillingService.getIdmConfigs();
        String destination_uri = configs.getScheme() + configs.getHostName() + ":" + configs.getPort() + configs.getPath();
        WebTarget destination = client.target(destination_uri).path(endpoint);
        Invocation.Builder requester = destination.request(MediaType.APPLICATION_JSON);

        ServiceLogger.LOGGER.info("Sending remote request.");
        ServiceLogger.LOGGER.info(endpoint);
        Response response = requester.post(Entity.entity(request_payload, MediaType.APPLICATION_JSON));

        if(response.getStatus() != 200 || !response.hasEntity()) {
            throw new Exception(); // TODO make more specific
        }
        return response;
    }

    @POST
    @Path("cart/insert")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response insert(@Context HttpHeaders headers, String jsonText) {
        InsertRequestModel requestModel;
        InsertResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestModel = mapper.readValue(jsonText, InsertRequestModel.class);
            String email  = requestModel.email;
            String movie_id  = requestModel.movie_id;
            Integer quantity  = requestModel.quantity;

            if (quantity <= 0) {
                responseModel = new InsertResponseModel(33, "Quantity has invalid value.");
                return Response.status(Response.Status.OK)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new InsertResponseModel(resultCode, "JSON Parse Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new InsertResponseModel(resultCode, "JSON Mapping Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                resultCode = -1;
                responseModel = new InsertResponseModel(resultCode, "Internal Server Error.");
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }

        String email  = requestModel.email;
        String movie_id  = requestModel.movie_id;
        Integer quantity  = requestModel.quantity;

        IDMPrivilegeRequestModel privilegeRequest = new IDMPrivilegeRequestModel(email, 4);
        try {
            Response response = send_privilege_request(BillingService.getIdmConfigs().getPrivilegePath(),
                    privilegeRequest);
            IDMPrivilegeResponseModel response_payload = response.readEntity(IDMPrivilegeResponseModel.class);
            Integer resultCode = response_payload.getResultCode();

            if (resultCode == 141 || resultCode == 140) {
                ServiceLogger.LOGGER.warning("User found");
            }
            else if (resultCode == 14) {
                ServiceLogger.LOGGER.warning("User not found");
                return Response.status(Response.Status.OK)
                        .entity(new InsertResponseModel(14, "User not found."))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
            else {
                ServiceLogger.LOGGER.warning("IDM Response Invalid");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new InsertResponseModel(-1, "Internal server error."))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch(Exception e) {
            ServiceLogger.LOGGER.info("IDM Request Error");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new InsertResponseModel(-1, "Internal server error."))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }


        try {
            Integer count = 0;
            String movieQuery = "SELECT COUNT(*) FROM movie WHERE movie_id = ?";
            PreparedStatement movieStmt = BillingService.getCon().prepareStatement(movieQuery);
            movieStmt.setString(1, movie_id);
            ResultSet rs = movieStmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt("COUNT(*)");
                if (count == 0) {
                    ServiceLogger.LOGGER.info("Movie does not exist");
                    return Response.status(Response.Status.OK)
                            .entity(new InsertResponseModel(3150, "Shopping cart operation failed."))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }
                else {
                    ServiceLogger.LOGGER.info("Movie exists");
                }
            }

            String query = "INSERT INTO cart (email, movie_id, quantity) VALUES (?, ?, ?)";
            PreparedStatement stmt = BillingService.getCon().prepareStatement(query);
            stmt.setString(1, email);
            stmt.setString(2, movie_id);
            stmt.setInt(3, quantity);
            stmt.execute();

            return Response.status(Response.Status.OK)
                    .entity(new InsertResponseModel(3100, "Shopping cart item inserted successfully."))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();

        } catch (SQLException e) {
            if (e instanceof SQLIntegrityConstraintViolationException) {
                // Duplicate entry
                ServiceLogger.LOGGER.warning("Duplicate insertion");
                return Response.status(Response.Status.OK)
                        .entity(new InsertResponseModel(311, "Duplicate insertion."))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                // Other SQL Exception
                ServiceLogger.LOGGER.warning("SQL Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new InsertResponseModel(-1, "Internal Server Error."))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }
    }


    @POST
    @Path("cart/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders headers, String jsonText) {
        InsertRequestModel requestModel;
        InsertResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestModel = mapper.readValue(jsonText, InsertRequestModel.class);
            String email  = requestModel.email;
            String movie_id  = requestModel.movie_id;
            Integer quantity  = requestModel.quantity;

            if (quantity <= 0) {
                responseModel = new InsertResponseModel(33, "Quantity has invalid value.");
                return Response.status(Response.Status.OK)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new InsertResponseModel(resultCode, "JSON Parse Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new InsertResponseModel(resultCode, "JSON Mapping Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                resultCode = -1;
                responseModel = new InsertResponseModel(resultCode, "Internal Server Error.");
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }

        String email  = requestModel.email;
        String movie_id  = requestModel.movie_id;
        Integer quantity  = requestModel.quantity;

        try {
            Integer count = 0;
            String movieQuery = "SELECT COUNT(*) FROM cart WHERE email = ? AND movie_id = ?";
            PreparedStatement movieStmt = BillingService.getCon().prepareStatement(movieQuery);
            movieStmt.setString(1, email);
            movieStmt.setString(2, movie_id);
            ResultSet rs = movieStmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt("COUNT(*)");
                if (count == 0) {
                    ServiceLogger.LOGGER.info("Movie does not exist");
                    return Response.status(Response.Status.OK)
                            .entity(new InsertResponseModel(312, "Shopping cart item does not exist."))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }
                else {
                    ServiceLogger.LOGGER.info("Movie exists");
                }
            }

            String query = "UPDATE cart SET quantity = ? WHERE email = ? AND movie_id = ?";
            PreparedStatement stmt = BillingService.getCon().prepareStatement(query);
            stmt.setInt(1, quantity);
            stmt.setString(2, email);
            stmt.setString(3, movie_id);
            stmt.execute();

            return Response.status(Response.Status.OK)
                    .entity(new InsertResponseModel(3110, "Shopping cart item updated successfully."))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("SQL Error");
            return Response.status(Response.Status.OK)
                    .entity(new InsertResponseModel(3150, "Shopping cart operation failed."))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }
    }


    @POST
    @Path("cart/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders headers, String jsonText) {
        DeleteRequestModel requestModel;
        InsertResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();


        try {
            requestModel = mapper.readValue(jsonText, DeleteRequestModel.class);
            String email  = requestModel.email;
            String movie_id  = requestModel.movie_id;

        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new InsertResponseModel(resultCode, "JSON Parse Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new InsertResponseModel(resultCode, "JSON Mapping Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                resultCode = -1;
                responseModel = new InsertResponseModel(resultCode, "Internal Server Error.");
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }

        String email  = requestModel.email;
        String movie_id  = requestModel.movie_id;

        try {
            Integer count = 0;
            String movieQuery = "SELECT COUNT(*) FROM cart WHERE email = ? AND movie_id = ?";
            PreparedStatement movieStmt = BillingService.getCon().prepareStatement(movieQuery);
            movieStmt.setString(1, email);
            movieStmt.setString(2, movie_id);
            ResultSet rs = movieStmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt("COUNT(*)");
                if (count == 0) {
                    ServiceLogger.LOGGER.info("Movie does not exist");
                    return Response.status(Response.Status.OK)
                            .entity(new InsertResponseModel(312, "Shopping cart item does not exist."))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }
                else {
                    ServiceLogger.LOGGER.info("Movie exists");
                }
            }

            String query = "DELETE FROM cart WHERE email = ? AND movie_id = ?";
            PreparedStatement stmt = BillingService.getCon().prepareStatement(query);
            stmt.setString(1, email);
            stmt.setString(2, movie_id);
            stmt.execute();

            return Response.status(Response.Status.OK)
                    .entity(new InsertResponseModel(3120, "Shopping cart item deleted successfully."))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("SQL Error");
            return Response.status(Response.Status.OK)
                    .entity(new InsertResponseModel(3150, "Shopping cart operation failed."))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }
    }

    private Response send_thumbnail_request(String endpoint, ThumbnailRequestModel request_payload) throws Exception{
        ServiceLogger.LOGGER.info(String.format("Preparing remove request to do IDM endpoint %s", endpoint));
        Client client = ClientBuilder.newClient();
        client.register(JacksonFeature.class);

        MoviesConfigs configs = BillingService.getMoviesConfigs();
        String destination_uri = configs.getScheme() + configs.getHostName() + ":" + configs.getPort() + configs.getPath();
        WebTarget destination = client.target(destination_uri).path(endpoint);
        Invocation.Builder requester = destination.request(MediaType.APPLICATION_JSON);

        ServiceLogger.LOGGER.info("Sending remote request.");
        ServiceLogger.LOGGER.info(endpoint);
        Response response = requester.post(Entity.entity(request_payload, MediaType.APPLICATION_JSON));

        if(response.getStatus() != 200 || !response.hasEntity()) {
            throw new Exception(); // TODO make more specific
        }
        return response;
    }


    @POST
    @Path("cart/retrieve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response retrieve(@Context HttpHeaders headers, String jsonText) {
        RetrieveRequestModel requestModel;
        RetrieveResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();


        try {
            requestModel = mapper.readValue(jsonText, RetrieveRequestModel.class);
            String email  = requestModel.email;

        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new RetrieveResponseModel(resultCode, "JSON Parse Exception.", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new RetrieveResponseModel(resultCode, "JSON Mapping Exception.", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                resultCode = -1;
                responseModel = new RetrieveResponseModel(resultCode, "Internal Server Error.", null);
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }

        String email  = requestModel.email;
        ArrayList<String> movie_ids = new ArrayList<>();
        ArrayList<Integer> quantities = new ArrayList<>();

        try {
            // TODO check if shopping cart item exists
            String movieQuery = "SELECT movie_id, quantity FROM cart WHERE email = ?";
            PreparedStatement movieStmt = BillingService.getCon().prepareStatement(movieQuery);
            movieStmt.setString(1, email);
            ResultSet rs = movieStmt.executeQuery();
            while (rs.next()) {
                String movie_id = rs.getString("movie_id");
                movie_ids.add(movie_id);
                Integer quant = rs.getInt("quantity");
                quantities.add(quant);
            }

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("SQL Error");
            return Response.status(Response.Status.OK)
                    .entity(new RetrieveResponseModel(3150, "Shopping cart operation failed.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }

        if (movie_ids.size() == 0){
            return Response.status(Response.Status.OK)
                    .entity(new RetrieveResponseModel(312, "Shopping cart item does not exist.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }
        String[] movieIds = new String[movie_ids.size()];
        for (int i=0; i<movie_ids.size(); ++i) {
            movieIds[i] = movie_ids.get(i);
        }

        ThumbnailRequestModel thumbnailRequest = new ThumbnailRequestModel();
        thumbnailRequest.movie_ids = movieIds;

        ThumbnailModel[] thumbnails;

        try {
            Response response = send_thumbnail_request(BillingService.getMoviesConfigs().getThumbnailPath(),
                    thumbnailRequest);
            ThumbnailResponseModel response_payload = response.readEntity(ThumbnailResponseModel.class);
            Integer resultCode = response_payload.resultCode;
            thumbnails = response_payload.thumbnails;
            ServiceLogger.LOGGER.info(String.format("Remote replied with code %d.", resultCode));

            if (resultCode == -1) {
                ServiceLogger.LOGGER.warning("Thumbnail error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new RetrieveResponseModel(-1, "Internal server error.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch(Exception e) {
            ServiceLogger.LOGGER.info("Error");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new RetrieveResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }

        ItemModel[] items = new ItemModel[movieIds.length];

        for (int i=0; i<movieIds.length; ++i) {
            try {
                ItemModel item = new ItemModel();
                item.email = email;

                String priceQuery = "SELECT unit_price, discount FROM movie_price WHERE movie_id = ?";
                PreparedStatement priceStmt = BillingService.getCon().prepareStatement(priceQuery);
                priceStmt.setString(1, movieIds[i]);
                ResultSet rs = priceStmt.executeQuery();
                while (rs.next()) {
                    item.unit_price = rs.getFloat("unit_price");
                    item.discount = rs.getFloat("discount");
                }

                item.quantity = quantities.get(i);
                item.movie_id = movieIds[i];
                item.movie_title = thumbnails[i].title;
                item.backdrop_path = thumbnails[i].backdrop_path;
                item.poster_path = thumbnails[i].poster_path;

                items[i] = item;

            } catch (SQLException e) {
                ServiceLogger.LOGGER.warning("SQL Error");
                return Response.status(Response.Status.OK)
                        .entity(new RetrieveResponseModel(3150, "Shopping cart operation failed.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }




        return Response.status(Response.Status.OK)
                .entity(new RetrieveResponseModel(3130, "Shopping cart retrieved successfully.", items))
                .header("email", headers.getHeaderString("email"))
                .header("session_id", headers.getHeaderString("session_id"))
                .header("transaction_id", headers.getHeaderString("transaction_id"))
                .build();
    }


    @POST
    @Path("cart/clear")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response clear(@Context HttpHeaders headers, String jsonText) {
        OrderPlaceRequestModel requestModel;
        InsertResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();


        try {
            requestModel = mapper.readValue(jsonText, OrderPlaceRequestModel.class);
            String email  = requestModel.getEmail();

        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new InsertResponseModel(resultCode, "JSON Parse Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new InsertResponseModel(resultCode, "JSON Mapping Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                resultCode = -1;
                responseModel = new InsertResponseModel(resultCode, "Internal Server Error.");
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }

        String email  = requestModel.getEmail();

        try {
            Integer count = 0;
            String movieQuery = "SELECT COUNT(*) FROM cart WHERE email = ?";
            PreparedStatement movieStmt = BillingService.getCon().prepareStatement(movieQuery);
            movieStmt.setString(1, email);
            ResultSet rs = movieStmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt("COUNT(*)");
                if (count == 0) {
                    ServiceLogger.LOGGER.info("Movie does not exist");
                    return Response.status(Response.Status.OK)
                            .entity(new InsertResponseModel(312, "Shopping cart item does not exist."))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }
                else {
                    ServiceLogger.LOGGER.info("Movie exists");
                }
            }

            String query = "DELETE FROM cart WHERE email = ?";
            PreparedStatement stmt = BillingService.getCon().prepareStatement(query);
            stmt.setString(1, email);
            stmt.execute();

            return Response.status(Response.Status.OK)
                    .entity(new InsertResponseModel(3140, "Shopping cart cleared successfully."))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("SQL Error");
            return Response.status(Response.Status.OK)
                    .entity(new InsertResponseModel(3150, "Shopping cart operation failed."))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }
    }


    @POST
    @Path("order/place")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response place(@Context HttpHeaders headers, String jsonText) throws SQLException {
        OrderPlaceRequestModel requestModel;
        OrderPlaceResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestModel = mapper.readValue(jsonText, OrderPlaceRequestModel.class);
            String email  = requestModel.getEmail();

        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new OrderPlaceResponseModel(resultCode, "JSON Parse Exception.", null, null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new OrderPlaceResponseModel(resultCode, "JSON Mapping Exception.", null, null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                resultCode = -1;
                responseModel = new OrderPlaceResponseModel(resultCode, "Internal Server Error.", null, null);
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }
        String email  = requestModel.getEmail();

        // Before PayPal callback
        try {
            // insert new sale
            PreparedStatement sales_query = BillingService.getCon().prepareStatement(
                    "INSERT INTO sale (email, movie_id, quantity, sale_date)"+
                    " SELECT ?, cart.movie_id, quantity, NOW() FROM cart"+
                    " WHERE email = ?;",
                    Statement.RETURN_GENERATED_KEYS);

            sales_query.setString(1, email);
            sales_query.setString(2, email);
            ServiceLogger.LOGGER.info(""+ sales_query);
            int n_inserted = sales_query.executeUpdate();
            if(n_inserted == 0){
                return Response.status(Response.Status.OK)
                        .entity(new OrderPlaceResponseModel(312, "Shopping cart item does not exist.", null, null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
            // to get sale_ids
            ResultSet keys_rs = sales_query.getGeneratedKeys();
            ArrayList<Integer> keys = new ArrayList<>();
            int n_keys = 0;
            while (keys_rs.next()){
                keys.add(keys_rs.getInt(1));
                n_keys++;
            }
            // Get sum
            String sale_id_list = "";
            for (int i = 0; i < n_keys; i++){
                sale_id_list += keys.get(i);
                if (i != n_keys-1){
                    sale_id_list += " ,";
                }
            }

            String saleSumQuery = "SELECT SUM(unit_price*quantity*(1-discount))"+
                    " FROM sale"+
                    " JOIN movie_price ON sale.movie_id = movie_price.movie_id"+
                    " WHERE sale.sale_id IN (" + sale_id_list + ");";

            PreparedStatement saleSumStmt = BillingService.getCon().prepareStatement(saleSumQuery);

            ServiceLogger.LOGGER.info(""+ saleSumQuery);
            ResultSet rs = saleSumStmt.executeQuery();
            double sum = 0.0;
            if (rs.next()) {
                sum = rs.getFloat("SUM(unit_price*quantity*(1-discount))");
            }

            // first callback
            ServiceLogger.LOGGER.info("sum: "+ sum);
            String orderid;
            PayPalOrderClient client = new PayPalOrderClient();
            System.out.println("---------- Creating order -----------");
            boolean debug = true;

            //Construct a request object and set desired parameters
            //Here orderscreaterequest creates a post request to v2/checkout/orders

            OrderRequest orderRequest = new OrderRequest();

            //MUST use this method instead of intent to create capture.
            orderRequest.checkoutPaymentIntent("CAPTURE");
            ServiceLogger.LOGGER.info("CAPTURE");

            //Create application context with return url upon payer completion.
            ApplicationContext applicationContext = new ApplicationContext().returnUrl("http://localhost:12345/api/billing/order/complete");

            orderRequest.applicationContext(applicationContext);

            List<PurchaseUnitRequest> purchaseUnits = new ArrayList<>();
//            String s1 = String.format("%.2f", sum);
            BigDecimal bd = new BigDecimal(sum).setScale(2, RoundingMode.HALF_UP);
            String s1 = String.format("%.2f", bd);
            purchaseUnits
                    .add(new PurchaseUnitRequest().amountWithBreakdown(new AmountWithBreakdown().currencyCode("USD").value(s1)));
//                    .add(new PurchaseUnitRequest().amountWithBreakdown(new AmountWithBreakdown().currencyCode("USD").value("1000.00")));

            orderRequest.purchaseUnits(purchaseUnits);
            OrdersCreateRequest request = new OrdersCreateRequest().requestBody(orderRequest);
            ServiceLogger.LOGGER.info(" BEFORE API CALL");
            String approve_url = "";
            String token_ret = "";
            try {
                // Call API with your client and get a response for your call
                HttpResponse<Order> response = client.client.execute(request);
                ServiceLogger.LOGGER.info("AFTER API CALL");


                // If call returns body in response, you can get the de-serialized version by
                // calling result() on the response

                if (debug) {
                    if (response.statusCode() == 201) {
                        System.out.println("Status Code: " + response.statusCode());
                        System.out.println("Status: " + response.result().status());
                        System.out.println("Order ID: " + response.result().id());

                        token_ret = response.result().id();


                        Dictionary<String, String> links_dict = new Hashtable<>();

                        response.result().links().forEach(link -> links_dict.put(link.rel(), link.href()));
                        approve_url = links_dict.get("approve");

                        response.result().links().forEach(link -> System.out.println(link.rel() + " => " + link.method() + ":" + link.href()));
                        // Set transaction token as order_id
                        String transactionSaleIds = "";
                        for (int i = 0; i < n_keys; i++){
                            transactionSaleIds += "(" + keys.get(i) + ", " + response.result().id() + ")";
                            if (i != n_keys-1){
                                transactionSaleIds += " ,";
                            }
                        }

                        String query = "REPLACE INTO transaction(sale_id, token) VALUES ";
                        for (int i = 0; i < n_keys; i++){
                            query += "(" + keys.get(i) + ", '" + response.result().id() + "')";
                            if (i != n_keys-1){
                                query += " ,";
                            }
                        }
                        ServiceLogger.LOGGER.info("transaction QUERY: "+ query);
                        PreparedStatement stmt = BillingService.getCon().prepareStatement(query);
                        ServiceLogger.LOGGER.info(""+ stmt);
                        stmt.execute();

                    }
                }

                orderid = response.result().id();
                ServiceLogger.LOGGER.info("ORDER ID: "+ orderid);
            } catch (IOException ioe) {
                System.err.println("*******COULD NOT CREATE ORDER*******");
                if (ioe instanceof HttpException) {
                    // Something went wrong server-side
                    HttpException he = (HttpException) ioe;
                    System.out.println(he.getMessage());
                    he.headers().forEach(x -> System.out.println(x + " :" + he.headers().header(x)));
                } else {


                    // Something went wrong client-side
                }
                return Response.status(Response.Status.OK)
                        .entity(new OrderPlaceResponseModel(342, "Order creation failed.", null, null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }


            return Response.status(Response.Status.OK)
                    .entity(new OrderPlaceResponseModel(3400, "Order placed successfully.", approve_url, token_ret))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("SQL Error");
            return Response.status(Response.Status.OK)
                    .entity(new OrderPlaceResponseModel(342, "Order creation failed.", null, null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }
        
    }


    @POST
    @Path("order/retrieve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response order_retrieve(@Context HttpHeaders headers, String jsonText) throws SQLException, IOException {
        OrderPlaceRequestModel requestModel;
        OrderRetrieveResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestModel = mapper.readValue(jsonText, OrderPlaceRequestModel.class);

        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new OrderRetrieveResponseModel(resultCode, "JSON Parse Exception.", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new OrderRetrieveResponseModel(resultCode, "JSON Mapping Exception.", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                resultCode = -1;
                responseModel = new OrderRetrieveResponseModel(resultCode, "Internal Server Error.", null);
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseModel)
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }
        String email = requestModel.getEmail();

        try{
            String order_query = "SELECT DISTINCT transaction.token, transaction.capture_id " +
                    "FROM sale " +
                    "JOIN transaction ON sale.sale_id = transaction.sale_id " +
                    "WHERE sale.email = ?;";
            PreparedStatement order_stmt = BillingService.getCon().prepareStatement(order_query);
            order_stmt.setString(1, email);
            ServiceLogger.LOGGER.info(""+ order_stmt);
            ResultSet rs = order_stmt.executeQuery();

            ArrayList<TransactionModel> transactions = new ArrayList<>();

            while(rs.next()) {
                TransactionModel transaction = new TransactionModel();
                AmountModel amount = new AmountModel();
                TransactionFeeModel trans_fee = new TransactionFeeModel();
                String orderId;
                orderId = rs.getString("token");
                transaction.capture_id = rs.getString("capture_id");

                try{
                    ServiceLogger.LOGGER.info(orderId);
                    PayPalOrderClient orderClient = new PayPalOrderClient();
                    OrdersGetRequest request = new OrdersGetRequest(orderId);
                    HttpResponse<Order> response = orderClient.client.execute(request);
                    Order order = response.result();
                    ServiceLogger.LOGGER.info("Order ID: " + order.id());
                    transaction.state = order.status();
                    transaction.create_time = order.createTime();
                    transaction.update_time = order.updateTime();
                    System.out.println("Full response body:" + (new Json().serialize(response.result())));
                    String jsonString = new Json().serialize(response.result());
                    JSONObject jsonObject = new JSONObject(jsonString);
                    JSONArray purchaseUnits = jsonObject.getJSONArray("purchase_units");
                    JSONObject payments = new JSONObject(purchaseUnits.getJSONObject(0).getString("payments"));
                    JSONArray captures = payments.getJSONArray("captures");
                    JSONObject sellerBreakdown = new JSONObject(captures.getJSONObject(0).getString("seller_receivable_breakdown"));
                    JSONObject paypal_fee = sellerBreakdown.getJSONObject("paypal_fee");
                    JSONObject gross_amount = sellerBreakdown.getJSONObject("gross_amount");

                    trans_fee.value = paypal_fee.getString("value");
                    trans_fee.currency = paypal_fee.getString("currency_code");
                    amount.total = gross_amount.getString("value");
                    amount.currency = gross_amount.getString("currency_code");


                } catch(IOException e){
                    ServiceLogger.LOGGER.info("Could not get Order");
                    return Response.status(Response.Status.OK)
                            .entity(new OrderRetrieveResponseModel(313, "Order history does not exist.", null))
                            .build();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try{
                    String items_query = "SELECT sale.movie_id, sale.email, sale.quantity, movie_price.unit_price, movie_price.discount, sale.sale_date " +
                        "FROM sale " +
                        "JOIN transaction ON sale.sale_id = transaction.sale_id " +
                        "JOIN movie_price ON sale.movie_id = movie_price.movie_id " +
                        "WHERE transaction.capture_id = ?;";
                    PreparedStatement items_stmt = BillingService.getCon().prepareStatement(items_query);
                    items_stmt.setString(1, rs.getString("capture_id"));
                    ServiceLogger.LOGGER.info(""+ items_stmt);
                    ResultSet items_rs = items_stmt.executeQuery();
                    ArrayList<RetrieveItemsModel> items_list = new ArrayList<>();
                    while(items_rs.next()){
                        RetrieveItemsModel items = new RetrieveItemsModel();
                        items.email = items_rs.getString("email");
                        items.movie_id = items_rs.getString("movie_id");
                        items.quantity = items_rs.getInt("quantity");
                        items.unit_price = items_rs.getFloat("unit_price");
                        items.discount = items_rs.getFloat("discount");
                        items.sale_date = items_rs.getString("sale_date");
                        items_list.add(items);
                        RetrieveItemsModel[] items_result = new RetrieveItemsModel[items_list.size()];
                        for (int i = 0; i < items_list.size(); i++) {
                            items_result[i] = items_list.get(i);
                            ServiceLogger.LOGGER.info(""+ items_result[i]);
                        }
                        transaction.items = items_result;
                    }
                    transaction.amount = amount;
                    transaction.transaction_fee = trans_fee;

                } catch (SQLException e) {
                    ServiceLogger.LOGGER.info("Items SQL Query Error");
                    return Response.status(Response.Status.OK)
                            .entity(new OrderRetrieveResponseModel(313, "Order history does not exist.", null))
                            .build();
                }
                transactions.add(transaction);

            }
            TransactionModel[] trans_result = new TransactionModel[transactions.size()];
            for (int i = 0; i < transactions.size(); i++) {
                trans_result[i] = transactions.get(i);
            }
            return Response.status(Response.Status.OK)
                    .entity(new OrderRetrieveResponseModel(3410, "Orders retrieved successfully.", trans_result))
                    .build();

        } catch (SQLException e) {
            return Response.status(Response.Status.OK)
                    .entity(new OrderRetrieveResponseModel(313, "Order history does not exist.", null))
                    .build();
        }



    }
    @GET
    @Path("order/complete")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@QueryParam("token") String token,
                           @QueryParam("payer_id") String payer_id) {
        ServiceLogger.LOGGER.info("Received a order complete request.");
        InsertResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();


        PayPalOrderClient orderClient = new PayPalOrderClient();
        String orderID = token;

        Order order = null;
        OrdersCaptureRequest request = new OrdersCaptureRequest(orderID);

        try {

            // Call API with your client and get a response for your call
            HttpResponse<Order> response = orderClient.client.execute(request);

            // If call returns body in response, you can get the de-serialized version by
            // calling result() on the response
            order = response.result();


            System.out.println("Capture ID: " + order.purchaseUnits().get(0).payments().captures().get(0).id());

            String query = "UPDATE transaction SET capture_id = ? WHERE token = ?;";
            PreparedStatement stmt = BillingService.getCon().prepareStatement(query);
            stmt.setString(1, order.purchaseUnits().get(0).payments().captures().get(0).id());
            stmt.setString(2, token);
            ServiceLogger.LOGGER.info(""+stmt);
            stmt.execute();

            order.purchaseUnits().get(0).payments().captures().get(0).links()
                    .forEach(link -> System.out.println(link.rel() + " => " + link.method() + ":" + link.href()));

            String sale_id_query = "SELECT transaction.sale_id FROM transaction WHERE token = ?";
            PreparedStatement sale_id_stmt = BillingService.getCon().prepareStatement(sale_id_query);
            sale_id_stmt.setString(1, token);
            ServiceLogger.LOGGER.info("" + sale_id_stmt);
            ResultSet sale_id_rs = sale_id_stmt.executeQuery();
            ArrayList<String> sale_id_list = new ArrayList<>();
            while(sale_id_rs.next()){
                sale_id_list.add(sale_id_rs.getString("sale_id"));
            }
            String sale_id_string = "(";
            for (int i = 0; i < sale_id_list.size(); i++){
                sale_id_string += sale_id_list.get(i);
                if (i != sale_id_list.size() -1){
                    sale_id_string += " ,";
                }
                else{
                    sale_id_string += ")";
                }
            }
            ServiceLogger.LOGGER.info("SALE ID STRING: " + sale_id_string);

            String delete_query = "DELETE FROM cart WHERE email = (SELECT DISTINCT sale.email FROM sale WHERE sale.sale_id IN "+
                    sale_id_string + ");";
            PreparedStatement delete_stmt = BillingService.getCon().prepareStatement(delete_query);
            ServiceLogger.LOGGER.info("" + delete_stmt);
            delete_stmt.execute();
        } catch (IOException | SQLException ioe) {
            if (ioe instanceof HttpException) {
                // Something went wrong server-side

                HttpException he = (HttpException) ioe;
                System.out.println(he.getMessage());
                he.headers().forEach(x -> System.out.println(x + " :" + he.headers().header(x)));
                return Response.status(Response.Status.OK)
                        .entity(new InsertResponseModel(3421, "Token not found."))
                        .build();


            }
            else  if (ioe instanceof JsonParseException){
                return Response.status(Response.Status.OK)
                        .entity(new InsertResponseModel(-1, "Internal Server Error."))
                        .build();
            }
            else {
                // Something went wrong client-side
                return Response.status(Response.Status.OK)
                        .entity(new InsertResponseModel(3422, "Order can not be completed."))
                        .build();
            }
        }

        return Response.status(Response.Status.OK)
                .entity(new InsertResponseModel(3420, "Order is completed successfully."))
                .build();
    }
}

class PayPalOrderClient
{
    private final String clientId = "ASPCN2Rr76wkSaopoA1nkM6Dlryy1uXpO9SCaSwt8he6-oE1ABpt3reNkN_DUuVU2KjAl7LvkdhErbkF";
    private final String clientSecret = "EI1VkHdC_GDXw-tpn9YzTlnpNeuoxvrvoMyY4yfTCRbPNyROzgWIjTTQ5zcKLE0BpQ3Wv5ymU6mqkhxe";

    {
        System.out.println(new String(Base64.getEncoder().encode((clientId + ":" + clientSecret).getBytes())));
    }

    public PayPalEnvironment environment = new PayPalEnvironment.Sandbox(clientId, clientSecret);
    //Create client for environment
    public PayPalHttpClient client = new PayPalHttpClient(environment);
}
