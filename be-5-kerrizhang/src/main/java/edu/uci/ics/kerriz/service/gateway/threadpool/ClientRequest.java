package edu.uci.ics.kerriz.service.gateway.threadpool;

import edu.uci.ics.kerriz.service.gateway.transaction.TransactionGenerator;

public class ClientRequest
{
    /* User Information */
    public String email;
    public String session_id;
    public String transaction_id;

    /* Target Service and Endpoint */
    public String URI;
    public String endpoint;
    public HTTPMethod method;

    /*
     * So before when we wanted to get the request body
     * we would grab it as a String (String jsonText).
     *
     * The Gateway however does not need to see the body
     * but simply needs to pass it. So we save ourselves some
     * time and overhead by grabbing the request as a byte array
     * (byte[] jsonBytes).
     *
     * This way we can just act as a
     * messenger and just pass along the bytes to the target
     * service and it will do the rest.
     *
     * for example:
     *
     * where we used to do this:
     *
     *     @Path("hello")
     *     ...ect
     *     public Response hello(String jsonString) {
     *         ...ect
     *     }
     *
     * do:
     *
     *     @Path("hello")
     *     ...ect
     *     public Response hello(byte[] jsonBytes) {
     *         ...ect
     *     }
     *
     */
    public byte[] requestBytes;

    public ClientRequest()
    {
//        this.method = method;
//        this.URI = URI;
//        this.endpoint = endpoint;
//        this.requestBytes = requestBytes;
//        this.email = email;
//        this.session_id = session_id;
//        this.transaction_id = transaction_id;
        // ^ used if everything private
    }
    public ClientRequest(String URI, String endpoint, HTTPMethod method,
                         String email, String session_id, String transaction_id)
    {
        this.method = method;
        this.URI = URI;
        this.endpoint = endpoint;
        this.email = email;
        this.session_id = session_id;
        this.transaction_id = transaction_id;

    }
}
