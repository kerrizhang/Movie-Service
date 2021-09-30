package edu.uci.ics.kerriz.service.gateway.threadpool;

import edu.uci.ics.kerriz.service.gateway.GatewayService;
import edu.uci.ics.kerriz.service.gateway.logger.ServiceLogger;
import edu.uci.ics.kerriz.service.gateway.transaction.TransactionGenerator;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.sql.Connection;
//import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Worker extends Thread {
    int id;
    ThreadPool threadPool;

    private Worker(int id, ThreadPool threadPool) {
        this.id = id;
        this.threadPool = threadPool;
    }

    public static Worker CreateWorker(int id, ThreadPool threadPool) {
        return new Worker(id, threadPool);
    }

    public void process(ClientRequest req) {
        // Process request by forwarding it to the appropriate endpoint, storing request into database
        // Prepare request to send
        Client client = ClientBuilder.newClient();
        WebTarget destination = client.target(req.URI);
        Invocation.Builder requester = destination.request();
        // TODO Set request headers - check if null?
        requester.header("email", req.email)
                .header("session_id", req.session_id)
                .header("transaction_id", req.transaction_id);
//        req = new ClientRequest(req.URI, req.endpoint, req.method, req.email, req.session_id, req.transaction_id);
        // TODO set request entity

        Entity<byte[]> entity = Entity.json(req.requestBytes);
//            // no more model -- Client Request - requestBytes (array of bytes)
            // login, user email, user password
//        entity = req.requestBytes;
//        String entity;
//        Entity.entity(entity, req.requestBytes);

        // TODO Send request

//        this.threadPool.putRequest(req);


        Response response;
        ServiceLogger.LOGGER.info(String.format("Sending %s request to %s, endpoint %s",
                req.method.toString(), req.URI, req.endpoint));
        // Read response
        try{
            if(entity != null){
                response = requester.method(req.method.toString(), entity);
            }else{
                response = requester.method(req.method.toString());
            }
        }catch(ProcessingException ex){
            ServiceLogger.LOGGER.severe(ex.toString());
            return;
        }

        int response_status = response.getStatus();
        String response_string = (response.hasEntity() ? response.readEntity(String.class): "");
        ServiceLogger.LOGGER.info(String.format("Request for transaction %s replied with status %s", req.transaction_id, response_status));

        // Insert response into table
        ServiceLogger.LOGGER.info(String.format("Inserting results of transaction %s into table", req.transaction_id));
        Connection con = null;
        ServiceLogger.LOGGER.info("set connection to null");
        try{
            // Get connection from Hikari
            con = GatewayService.getConnectionPoolManager().requestCon();
            ServiceLogger.LOGGER.info("Got connection from Hikari");
            // Store response into table (using insert statement)
            String query = "INSERT INTO responses (transaction_id, response, http_status) VALUES (?, ?, ?);";
            if(con != null){
                PreparedStatement stmt = con.prepareStatement(query);
                stmt.setString(1, req.transaction_id);
                stmt.setString(2, response_string);
                stmt.setInt(3, response_status);
                ServiceLogger.LOGGER.info("" + stmt);
                stmt.execute();
            }
            else{
                ServiceLogger.LOGGER.info("Connection is null");
            }



        }catch(SQLException e){
            ServiceLogger.LOGGER.severe(String.format("Error processing transaction %s cannot insert into table. " +
                    "SQL error: %s", req.transaction_id, e.toString()));
        } finally{
            GatewayService.getConnectionPoolManager().releaseCon(con);
        }
    }

    @Override
    public void run() {
        ServiceLogger.LOGGER.info(String.format("Worker thread %d started.", this.id));
        while (true) {
            // Get a request to process. If none available, block and wait.
            ClientRequest req = this.threadPool.takeRequest();
            ServiceLogger.LOGGER.info(String.format("Starting processing of request %s.", req.transaction_id));
            try{
                this.process(req);
            } catch(Exception e){
                ServiceLogger.LOGGER.severe(String.format("Uncaught exception occurred during processing request - Worker run."));
            }
        }
    }
}
