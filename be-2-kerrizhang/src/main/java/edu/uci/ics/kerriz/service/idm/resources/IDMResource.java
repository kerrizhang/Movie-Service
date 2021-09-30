package edu.uci.ics.kerriz.service.idm.resources;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uci.ics.kerriz.service.idm.IDMService;
import edu.uci.ics.kerriz.service.idm.logger.ServiceLogger;
import edu.uci.ics.kerriz.service.idm.models.*;
import edu.uci.ics.kerriz.service.idm.security.Crypto;
import edu.uci.ics.kerriz.service.idm.security.Session;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

@Path("")
public class IDMResource {

    public final int ACTIVE_STATUS = 1;
    public final int CLOSED_STATUS = 2;
    public final int EXPIRED_STATUS = 3;
    public final int REVOKED_STATUS = 4;

    public final int PLEVEL_ROOT = 1;
    public final int PLEVEL_ADMIN = 2;
    public final int PLEVEL_EMPLOYEE = 3;
    public final int PLEVEL_SERVICE = 4;
    public final int PLEVEL_USER = 5;


    @POST
    @Path("register")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(@Context HttpHeaders headers, String jsonText) {
        RegisterRequestModel requestModel;
        RegisterResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestModel = mapper.readValue(jsonText, RegisterRequestModel.class);
            String email = requestModel.getEmail();
            char[] password = requestModel.getPassword();
            if (password == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new RegisterResponseModel(-12, "Password has invalid length. <=16"))
                        .build();
            }
            if (email == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new RegisterResponseModel(-10, "Email address has invalid length."))
                        .build();
            }
        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new RegisterResponseModel(resultCode, "JSON Parse Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(responseModel).build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new RegisterResponseModel(resultCode, "JSON Mapping Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(responseModel).build();
            } else {
                resultCode = -1;
                responseModel = new RegisterResponseModel(resultCode, "Internal Server Error.");
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseModel).build();
            }
        }


        // Validate input
        String email = requestModel.getEmail();
        char[] password = requestModel.getPassword();

        if(password.length > 16 || password.length == 0){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new RegisterResponseModel(-12, "Password has invalid length. <=16"))
                    .build();
        }
        if(password.length < 7){
            return Response.status(Response.Status.OK)
                    .entity(new RegisterResponseModel(12, "Password does not meet length requirements. >=7"))
                    .build();
        }
        if(email.length() > 50 || email.length() < 6){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new RegisterResponseModel(-10, "Email address has invalid length."))
                    .build();
        }
        if (!email.matches("[a-zA-Z\\.-_0-9]+@[a-zA-Z\\.-_0-9]+\\.[a-zA-Z]+")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new RegisterResponseModel(-11, "Email address has invalid format."))
                    .build();
        }


        int alphnum_count = 0;
        boolean upper = false;
        boolean lower = false;
        boolean numeric = false;
        for (int i = 0; i<password.length; i++){
            if (Character.isLetter(password[i])){
                alphnum_count++;
                if(Character.isUpperCase(password[i])){
                    upper = true;
                }
                else if(Character.isLowerCase(password[i])){
                    lower = true;
                }
            }
            else if (Character.isDigit(password[i])) {
                alphnum_count++;
                numeric = true;
            }
        }
        if(alphnum_count < 7 || upper == false || lower == false || numeric == false){
            return Response.status(Response.Status.OK)
                    .entity(new RegisterResponseModel(13, "Password does not meet character requirements."))
                    .build();
        }

        try {
            String login_query = "INSERT INTO user (email, status, plevel, salt, pword) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement login_stmt = IDMService.getCon().prepareStatement(login_query);
            String queryEmail = "SELECT email FROM user";
            ArrayList<String> login_emails = new ArrayList<>();
            PreparedStatement login_emailStmt = IDMService.getCon().prepareStatement(queryEmail);
            ResultSet rs = login_emailStmt.executeQuery();
            while (rs.next()){
                login_emails.add(rs.getString("email"));
            }
            for (String e : login_emails){
                if (e.equals(email)){
                    return Response.status(Response.Status.OK)
                            .entity(new RegisterResponseModel(16, "Email already in use."))
                            .build();
                }
            }
            byte[] salt = Crypto.genSalt();
            byte[] hashedPass = Crypto.hashPassword(password, salt, Crypto.ITERATIONS, Crypto.KEY_LENGTH);
            login_stmt.setString(1, email);
            login_stmt.setInt(2, ACTIVE_STATUS);
            login_stmt.setInt(3, PLEVEL_USER);
            login_stmt.setBytes(4, salt);
            login_stmt.setBytes(5, hashedPass);
            login_stmt.execute();
            return Response.status(Response.Status.OK)
                    .entity(new RegisterResponseModel(110, "User registered successfully."))
                    .build();
        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("Adding user failed");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }


    @POST
    @Path("login")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(@Context HttpHeaders headers, String jsonText) {
        LoginRequestModel requestModel;
        LoginResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestModel = mapper.readValue(jsonText, LoginRequestModel.class);
            String email = requestModel.getEmail();
            char[] password = requestModel.getPassword();
            if (password == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new LoginResponseModel(-12, "Password has invalid length. <=16", null))
                        .build();
            }
            if (email == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new LoginResponseModel(-10, "Email address has invalid length.", null))
                        .build();
            }
        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                responseModel = new LoginResponseModel(resultCode, "JSON Parse Exception.", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(responseModel).build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                responseModel = new LoginResponseModel(resultCode, "JSON Mapping Exception.", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(responseModel).build();
            } else {
                resultCode = -1;
                responseModel = new LoginResponseModel(resultCode, "Internal Server Error.", null);
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseModel).build();
            }
        }

        String email = requestModel.getEmail();
        char[] password = requestModel.getPassword();

        if(password.length > 16 || password.length == 0){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new LoginResponseModel(-12, "Password has invalid length. <=16", null))
                    .build();
        }
        if(password.length < 7){
            return Response.status(Response.Status.OK)
                    .entity(new LoginResponseModel(12, "Password does not meet length requirements. >=7", null))
                    .build();
        }
        if(email.length() > 50 || email.length() < 6){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new LoginResponseModel(-10, "Email address has invalid length.", null))
                    .build();
        }
        if (!email.matches("[a-zA-Z\\.-_0-9]+@[a-zA-Z\\.-_0-9]+\\.[a-zA-Z]+")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new LoginResponseModel(-11, "Email address has invalid format.", null))
                    .build();
        }

        try {
            ArrayList<String> emails = new ArrayList<String>();
            String emailQuery = "SELECT email FROM user";
            PreparedStatement login_emailStmt = IDMService.getCon().prepareStatement(emailQuery);
            ResultSet email_rs = login_emailStmt.executeQuery();
            while(email_rs.next()) {
                emails.add(email_rs.getString("email"));
            }
            if(!emails.contains(email)) {
                return Response.status(Response.Status.OK)
                        .entity(new LoginResponseModel(14, "User not found.", null))
                        .build();
            }
            byte[] storedSalt = new byte[0];
            byte[] storedHashPass = new byte[0];
            String query = "SELECT salt, pword FROM user WHERE email = ?";
            PreparedStatement stmt = IDMService.getCon().prepareStatement(query);
            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();
            while(rs.next()) {
                storedSalt = rs.getBytes("salt");
                storedHashPass = rs.getBytes("pword");
            }

            byte[] hash_password = Crypto.hashPassword(password, storedSalt, Crypto.ITERATIONS, Crypto.KEY_LENGTH);
            boolean password_equal = (Arrays.equals(storedHashPass,hash_password));

            if(password_equal == true) {
                String login_updateQuery = "UPDATE session SET status = 4 WHERE email = ? AND status = 1";
                PreparedStatement login_updateStmt = IDMService.getCon().prepareStatement(login_updateQuery);
                login_updateStmt.setString(1, email);
                login_updateStmt.execute();
                Session s = Session.createSession(email);
                String insert_session = "INSERT INTO session (session_id, email, status, time_created, last_used, expr_time) VALUES (?, ?, ?, ?, ?, ?)";
                PreparedStatement insert_session_stmt = IDMService.getCon().prepareStatement(insert_session);
                insert_session_stmt.setString(1, s.getSessionID().toString());
                insert_session_stmt.setString(2, s.getEmail());
                insert_session_stmt.setInt(3, ACTIVE_STATUS);
                insert_session_stmt.setTimestamp(4, s.getTimeCreated());
                insert_session_stmt.setTimestamp(5, s.getLastUsed());
                insert_session_stmt.setTimestamp(6, s.getExprTime());
                insert_session_stmt.execute();
                return Response.status(Response.Status.OK)
                        .entity(new LoginResponseModel(120, "User logged in successfully.", s.getSessionID().toString()))
                        .build();
            } else {
                return Response.status(Response.Status.OK)
                        .entity(new LoginResponseModel(11, "Passwords do not match.", null))
                        .build();
            }

        } catch(SQLException e) {
            ServiceLogger.LOGGER.warning("Logging on failed");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @Path("session")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response session(@Context HttpHeaders headers, String jsonText) {
        SessionRequestModel ses_requestModel;
        SessionResponseModel ses_responseModel;
        ObjectMapper ses_mapper = new ObjectMapper();

        try {
            ses_requestModel = ses_mapper.readValue(jsonText, SessionRequestModel.class);
            String email = ses_requestModel.getEmail();
            if (email == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new SessionResponseModel(-10, "Email address has invalid length.", null))
                        .build();
            }
        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                ses_responseModel = new SessionResponseModel(resultCode, "JSON Parse Exception.", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(ses_responseModel).build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                ses_responseModel = new SessionResponseModel(resultCode, "JSON Mapping Exception.", null);
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(ses_responseModel).build();
            } else {
                resultCode = -1;
                ses_responseModel = new SessionResponseModel(resultCode, "Internal Server Error.", null);
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ses_responseModel).build();
            }
        }


        // Validate input
        String email = ses_requestModel.getEmail();
        String session_id = ses_requestModel.getSessionId();

        if(session_id.length() != 128){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SessionResponseModel(-13, "Token has invalid length.", null))
                    .build();
        }
        if(email.length() > 50 || email.length() < 6){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SessionResponseModel(-10, "Email address has invalid length.", null))
                    .build();
        }
        if (!email.matches("[a-zA-Z\\.-_0-9]+@[a-zA-Z\\.-_0-9]+\\.[a-zA-Z]+")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SessionResponseModel(-11, "Email address has invalid format.", null))
                    .build();
        }

        try {
            // case 14: user not found
            ArrayList<String> ses_emails = new ArrayList<>();
            String emailQuery = "SELECT email FROM session";
            PreparedStatement ses_emailStmt = IDMService.getCon().prepareStatement(emailQuery);
            ResultSet email_rs = ses_emailStmt.executeQuery();
            while(email_rs.next()) {
                ses_emails.add(email_rs.getString("email"));
            }
            if(!ses_emails.contains(email)) {
                // Case 14: User not found.
                return Response.status(Response.Status.OK)
                        .entity(new SessionResponseModel(14, "User not found.", null))
                        .build();
            }

            int storedStatus = 0;
            Timestamp last_use = new Timestamp(System.currentTimeMillis());
            Timestamp expire_time = new Timestamp(System.currentTimeMillis());
            Timestamp current_time = new Timestamp(System.currentTimeMillis());

            String query = "SELECT status, time_created, last_used, expr_time FROM session WHERE email = ? AND session_id = ?";
            PreparedStatement stmt = IDMService.getCon().prepareStatement(query);
            stmt.setString(1, email);
            stmt.setString(2, session_id);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) {
                storedStatus = rs.getInt("status");
                last_use = rs.getTimestamp("last_used");
                expire_time = rs.getTimestamp("expr_time");
            }

            if(storedStatus == 0) {
                return Response.status(Response.Status.OK)
                        .entity(new SessionResponseModel(134, "Session not found.", null))
                        .build();
            }
            else if (storedStatus == ACTIVE_STATUS) {
                // Check if expired
                if (!current_time.before(expire_time)) {
                    // Expired
                    String updateQuery = "UPDATE session SET status = 3 WHERE email = ? AND session_id = ?";
                    PreparedStatement updateStmt = IDMService.getCon().prepareStatement(updateQuery);
                    updateStmt.setString(1, email);
                    updateStmt.setString(2, session_id);
                    updateStmt.execute();
                    return Response.status(Response.Status.OK)
                            .entity(new SessionResponseModel(131, "Session is expired.", null))
                            .build();
                }
                // Check if timed out
                else if (current_time.getTime() - last_use.getTime() > Session.SESSION_TIMEOUT) {
                    String updateQuery = "UPDATE session SET status = 4 WHERE email = ? AND session_id = ?";
                    PreparedStatement updateStmt = IDMService.getCon().prepareStatement(updateQuery);
                    updateStmt.setString(1, email);
                    updateStmt.setString(2, session_id);
                    updateStmt.execute();
                    return Response.status(Response.Status.OK)
                            .entity(new SessionResponseModel(133, "Session is revoked.", null))
                            .build();
                }
                // Check if expires before timeout
                else if (expire_time.getTime() - current_time.getTime() < Session.SESSION_TIMEOUT) {
                    String updateQuery = "UPDATE session SET status = 4 WHERE email = ? AND session_id = ?";
                    PreparedStatement updateStmt = IDMService.getCon().prepareStatement(updateQuery);
                    updateStmt.setString(1, email);
                    updateStmt.setString(2, session_id);
                    updateStmt.execute();
                    //
                    Session newSession = Session.createSession(email);
                    String addSession = "INSERT INTO session (session_id, email, status, time_created, last_used, expr_time) VALUES (?, ?, ?, ?, ?, ?)";
                    PreparedStatement addSessionStmt = IDMService.getCon().prepareStatement(addSession);
                    addSessionStmt.setString(1, newSession.getSessionID().toString());
                    addSessionStmt.setString(2, newSession.getEmail());
                    addSessionStmt.setInt(3, ACTIVE_STATUS);
                    addSessionStmt.setTimestamp(4, newSession.getTimeCreated());
                    addSessionStmt.setTimestamp(5, newSession.getLastUsed());
                    addSessionStmt.setTimestamp(6, newSession.getExprTime());
                    addSessionStmt.execute();

                    return Response.status(Response.Status.OK)
                            .entity(new SessionResponseModel(133, "Session is revoked.", null))
                            .build();


                } else {
                    // Update lastUsed
                    String priv_updateQuery = "UPDATE session SET last_used = ? WHERE email = ? AND session_id = ?";
                    PreparedStatement priv_updateStmt = IDMService.getCon().prepareStatement(priv_updateQuery);
                    priv_updateStmt.setTimestamp(1, current_time);
                    priv_updateStmt.setString(2, email);
                    priv_updateStmt.setString(3, session_id);
                    priv_updateStmt.execute();

                    // case 130
                    return Response.status(Response.Status.OK)
                            .entity(new SessionResponseModel(130, "Session is active.", session_id))
                            .build();
                }
            }
            else if (storedStatus == EXPIRED_STATUS){
                // case 131
                return Response.status(Response.Status.OK)
                        .entity(new SessionResponseModel(131, "Session is expired.", null))
                        .build();
            }
            else if (storedStatus == CLOSED_STATUS){
                // case 132
                return Response.status(Response.Status.OK)
                        .entity(new SessionResponseModel(132, "Session is closed.", null))
                        .build();
            }
            else if (storedStatus == REVOKED_STATUS){
                // case 133
                return Response.status(Response.Status.OK)
                        .entity(new SessionResponseModel(133, "Session is revoked.", null))
                        .build();
            }
            else{
                // case 134
                return Response.status(Response.Status.OK)
                        .entity(new SessionResponseModel(134, "Session not found.", null))
                        .build();
            }


        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("Verify Session Failed");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }


    @POST
    @Path("privilege")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response privilege(@Context HttpHeaders headers, String jsonText) {
        PrivilegeRequestModel p_requestModel;
        PrivilegeResponseModel p_responseModel;
        ObjectMapper p_mapper = new ObjectMapper();

        try {
            p_requestModel = p_mapper.readValue(jsonText, PrivilegeRequestModel.class);
            String email = p_requestModel.getEmail();
            if (email == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new PrivilegeResponseModel(-10, "Email address has invalid length."))
                        .build();
            }
        } catch (IOException e) {
            int resultCode;
            e.printStackTrace();
            if (e instanceof JsonParseException) {
                resultCode = -3;
                p_responseModel = new PrivilegeResponseModel(resultCode, "JSON Parse Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(p_responseModel).build();
            } else if (e instanceof JsonMappingException) {
                resultCode = -2;
                p_responseModel = new PrivilegeResponseModel(resultCode, "JSON Mapping Exception.");
                ServiceLogger.LOGGER.warning("Unable to map JSON to POJO");
                return Response.status(Response.Status.BAD_REQUEST).entity(p_responseModel).build();
            } else {
                resultCode = -1;
                p_responseModel = new PrivilegeResponseModel(resultCode, "Internal Server Error.");
                ServiceLogger.LOGGER.severe("Internal Server Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(p_responseModel).build();
            }
        }


        // Validate input
        String email = p_requestModel.getEmail();
        int plevel = p_requestModel.getPlevel();
        if (plevel < 1 || plevel > 5){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new PrivilegeResponseModel(-14, "Privilege level out of valid range."))
                    .build();
        }

        if(email.length() > 50 || email.length() < 6){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new PrivilegeResponseModel(-10, "Email address has invalid length."))
                    .build();
        }
        if (!email.matches("[a-zA-Z\\.-_0-9]+@[a-zA-Z\\.-_0-9]+\\.[a-zA-Z]+")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new PrivilegeResponseModel(-11, "Email address has invalid format."))
                    .build();
        }

        try {
            // case 14: user not found
            ArrayList<String> p_emails = new ArrayList<>();
            String p_emailQuery = "SELECT email FROM user";
            // TODO from user or session?
            PreparedStatement p_emailStmt = IDMService.getCon().prepareStatement(p_emailQuery);
            ResultSet email_rs = p_emailStmt.executeQuery();
            while(email_rs.next()) {
                p_emails.add(email_rs.getString("email"));
            }
            if(!p_emails.contains(email)) {
                // Case 14: User not found.
                return Response.status(Response.Status.OK)
                        .entity(new PrivilegeResponseModel(14, "User not found."))
                        .build();
            }

            int stored_plevel = 0;
            String p_query = "SELECT plevel FROM user WHERE email = ?";
            PreparedStatement p_stmt = IDMService.getCon().prepareStatement(p_query);
            p_stmt.setString(1, email);
            ResultSet p_rs = p_stmt.executeQuery();
            while(p_rs.next()) {
                stored_plevel = p_rs.getInt("plevel");
            }

            if (plevel <= stored_plevel){
                // users have sufficient privilege level
                return Response.status(Response.Status.OK)
                        .entity(new PrivilegeResponseModel(140, "User has sufficient privilege level."))
                        .build();
            }
            else{
                return Response.status(Response.Status.OK)
                        .entity(new PrivilegeResponseModel(141, "User has insufficient privilege level."))
                        .build();
            }



        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("Verify Session Failed");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

}