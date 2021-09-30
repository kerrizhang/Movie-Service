package edu.uci.ics.kerriz.service.movies.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uci.ics.kerriz.service.movies.MoviesService;
import edu.uci.ics.kerriz.service.movies.configs.IdmConfigs;
import edu.uci.ics.kerriz.service.movies.logger.ServiceLogger;
import edu.uci.ics.kerriz.service.movies.models.*;
import org.glassfish.jersey.jackson.JacksonFeature;

import javax.ws.rs.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.Service;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Path("")
public class MoviesResource {
    private Response send_idm_request(String endpoint, IdmPrivilegeRequestModel request_payload) throws Exception{
        ServiceLogger.LOGGER.info(String.format("Preparing remove request to do IDM endpoint %s", endpoint));
        Client client = ClientBuilder.newClient();
        client.register(JacksonFeature.class);

        IdmConfigs configs = MoviesService.getIdmConfigs();
        String destination_uri = configs.getScheme() + configs.getHostName() + ":" + configs.getPort() + configs.getPath();
        WebTarget destination = client.target(destination_uri).path(endpoint);
        Invocation.Builder requester = destination.request(MediaType.APPLICATION_JSON);

        ServiceLogger.LOGGER.info("Sending remote request.");
        ServiceLogger.LOGGER.info(endpoint);
        Response response = requester.post(Entity.entity(request_payload, MediaType.APPLICATION_JSON));
        ServiceLogger.LOGGER.info("IDM Request status: " + response.getStatus());
        if(response.getStatus() != 200 || !response.hasEntity()) {
            throw new Exception();
        }
        return response;
    }

    @GET
    @Path("search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@Context HttpHeaders headers,
                           @QueryParam("title") String title,
                           @QueryParam("year") Integer year,
                           @QueryParam("director") String director,
                           @QueryParam("genre") String genre,
                           @QueryParam("hidden") Boolean hidden,
                           @DefaultValue("10") @QueryParam("limit") Integer limit,
                           @DefaultValue("0") @QueryParam("offset") Integer offset,
                           @DefaultValue("title") @QueryParam("orderby") String orderby,
                           @DefaultValue("asc") @QueryParam("direction") String direction) {
        ServiceLogger.LOGGER.info("Received a search request.");

        if (limit != null && limit != 10 && limit != 25 && limit != 50 && limit != 100){
            limit = 10;
        }
        if (orderby != null && !orderby.equals("title") && !orderby.equals("rating") && !orderby.equals("year")){
            orderby = "title";
        }
        if (direction != null && !direction.equals("asc") && !direction.equals("desc")) {
            direction = "asc";
        }
        if (offset < 0 || offset%limit != 0) {
            offset = 0;
        }


        String email = headers.getHeaderString("email");
        IdmPrivilegeRequestModel privilegeRequest = new IdmPrivilegeRequestModel(email, 4);
        Response response;
        Integer resultCode = 0;

        // To reset hidden value with param
        int hidden_flag;
        if (hidden == null) {
            hidden_flag = -1;
        }
        else if (hidden == true) {
            hidden_flag = 1;
        }
        else {
            hidden_flag = 0;
        }

        try {
            response = send_idm_request(MoviesService.getIdmConfigs().getPrivilegePath(),
                    privilegeRequest);
            IdmPrivilegeResponseModel response_payload = response.readEntity(IdmPrivilegeResponseModel.class);
            resultCode = response_payload.getResultCode();
            ServiceLogger.LOGGER.info(String.format("Remote replied with code %d.", resultCode));
            if (resultCode == 141) {
                hidden = null;
            }
            else if (resultCode == 140) {
                hidden = false;
            }
            else {
                ServiceLogger.LOGGER.warning("Result code other than 140 or 141");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch(Exception e) {
            ServiceLogger.LOGGER.info("Error");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }


        try {
            String movie_query = "SELECT * FROM movie "
             + "JOIN person ON person.person_id = movie.director_id ";
            if (genre != null) {
                movie_query += "JOIN genre_in_movie ON movie.movie_id = genre_in_movie.movie_id " +
                        "JOIN genre ON genre.genre_id = genre_in_movie.genre_id ";
            }
            movie_query += "WHERE ";

            ArrayList<String> query_list = new ArrayList<>();
            if (title != null) {query_list.add(String.format("title LIKE '%%%s%%'", title));}
            if (year != null) {query_list.add(String.format("year = %s", year));}
            if (director != null) {query_list.add(String.format("person.name LIKE '%%%s%%'", director));}
            if (genre != null) {query_list.add(String.format("genre.name = '%s'", genre));}
            if (hidden != null) {query_list.add(String.format("hidden = '%s'", hidden));}

            String limit_query = String.format(" LIMIT %s", limit);
            String offset_query = String.format(" OFFSET %s", offset);

            String order_query = String.format(" ORDER BY %s %s", orderby, direction);

            if (orderby.equals("title"))
                order_query = String.format("ORDER BY title %s, rating DESC ", direction);
            else if (orderby.equals("rating"))
                order_query = String.format("ORDER BY rating %s, title ASC ", direction);
            else if (orderby.equals("year"))
                order_query = String.format("ORDER BY year %s, rating DESC ", direction);

            String where_query = query_list.get(0);
            for (int i = 1; i < query_list.size(); i++) {
                where_query += " AND " + query_list.get(i);
            }

            movie_query += where_query;
            movie_query += order_query;
            movie_query += limit_query;
            movie_query += offset_query;

            PreparedStatement movie_stmt = MoviesService.getCon().prepareStatement(movie_query);
            ResultSet rs = movie_stmt.executeQuery();

            ArrayList<MovieModel> movies = new ArrayList<>();

            while(rs.next()) {
                MovieModel movie = new MovieModel();
                movie.movie_id = rs.getString("movie_id");
                movie.title = rs.getString("title");
                movie.year = rs.getInt("year");
                movie.director = rs.getString("person.name");
                movie.rating = rs.getFloat("rating");
                movie.backdrop_path = rs.getString("backdrop_path");
                movie.poster_path = rs.getString("poster_path");
                if (resultCode == 140){
                    movie.hidden = rs.getBoolean("hidden");
                }
                else{
                    movie.hidden = null;
                }
                movies.add(movie);
            }

            if (resultCode == 140) {
                if (hidden_flag == -1) {
                    hidden = null;
                }
                else if (hidden_flag == 1) {
                    hidden = true;
                }
                else {
                    hidden = false;
                }
            }
            if (movies.size() == 0) {
                return Response.status(Response.Status.OK)
                        .entity(new MoviesResponseModel(211, "No movies found with search parameters.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                MovieModel[] movie_result = new MovieModel[movies.size()];
                for (int i = 0; i < movies.size(); i++) {
                    movie_result[i] = movies.get(i);
                }
                return Response.status(Response.Status.OK)
                        .entity(new MoviesResponseModel(210, "Found movie(s) with search parameters.", movie_result))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("Unable to query SQL");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }

    }

    @GET
    @Path("browse/{phrase}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response browse(@Context HttpHeaders headers,
                           @PathParam("phrase") String phrase,
                           @DefaultValue("10") @QueryParam("limit") Integer limit,
                           @DefaultValue("0") @QueryParam("offset") Integer offset,
                           @DefaultValue("title") @QueryParam("orderby") String orderby,
                           @DefaultValue("asc") @QueryParam("direction") String direction) {

        ServiceLogger.LOGGER.info(headers.getHeaderString("email"));
        ServiceLogger.LOGGER.info(headers.getHeaderString("session_id"));
        ServiceLogger.LOGGER.info(headers.getHeaderString("transaction_id"));

        if (limit != null && limit != 10 && limit != 25 && limit != 50 && limit != 100){
            limit = 10;
        }
        if (orderby != null && !orderby.equals("title") && !orderby.equals("rating") && !orderby.equals("year")){
            orderby = "title";
        }
        if (direction != null && !direction.equals("asc") && !direction.equals("desc")) {
            direction = "asc";
        }
        if (offset < 0 || offset%limit != 0) {
            offset = 0;
        }

        String email = headers.getHeaderString("email");
        IdmPrivilegeRequestModel privilegeRequest = new IdmPrivilegeRequestModel(email, 4);
        Response response;
        Integer resultCode = 0;

        try {
            response = send_idm_request(MoviesService.getIdmConfigs().getPrivilegePath(),
                    privilegeRequest);
            IdmPrivilegeResponseModel response_payload = response.readEntity(IdmPrivilegeResponseModel.class);
            resultCode = response_payload.getResultCode();
            ServiceLogger.LOGGER.info(String.format("Remote replied with code %d.", resultCode));
            if (resultCode == 141) {
                // plevel <= 4

            }
            else if (resultCode == 140) {
                // plevel = 5 - Cannot see hidden movies

            }
            else {
                ServiceLogger.LOGGER.warning("Result code other than 140 or 141");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch(Exception e) {
            ServiceLogger.LOGGER.info("Error");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }


        try {
            String movie_query = "SELECT * FROM movie "
                    + "JOIN keyword_in_movie ON movie.movie_id = keyword_in_movie.movie_id " +
                    "JOIN keyword ON keyword.keyword_id = keyword_in_movie.keyword_id "+
                    "JOIN person ON person.person_id = movie.director_id ";
            movie_query += "WHERE ";

            // how many phrases there are

            List<String> phrase_list = new ArrayList<String>(Arrays.asList(phrase.split(",")));

            if (phrase != null) {
                movie_query += String.format("keyword.name IN (", phrase);
                for(int i =0; i< phrase_list.size(); i++){
                    movie_query += "'"+phrase_list.get(i)+"'";
                    if (i != phrase_list.size()-1){
                        movie_query +=",";
                    }
                    else{
                        movie_query += ")";
                    }
                }
                movie_query += " GROUP BY movie.title, movie.year, movie.rating";
                movie_query += (" HAVING COUNT(*) = " + phrase_list.size());
            }

            String limit_query = String.format(" LIMIT %s", limit);
            String offset_query = String.format(" OFFSET %s", offset);
            String orderby_query = String.format(" ORDER BY %s %s", orderby, direction);

            movie_query += orderby_query;
            movie_query += limit_query;
            movie_query += offset_query;
            ServiceLogger.LOGGER.info(movie_query);
            PreparedStatement movie_stmt = MoviesService.getCon().prepareStatement(movie_query);
            ResultSet rs = movie_stmt.executeQuery();
            ArrayList<MovieModel> movies = new ArrayList<>();

            while(rs.next()) {
                MovieModel movie = new MovieModel();
                movie.movie_id = rs.getString("movie_id");
                movie.title = rs.getString("title");
                movie.year = rs.getInt("year");
                movie.director = rs.getString("person.name");
                movie.rating = rs.getFloat("rating");
                movie.backdrop_path = rs.getString("backdrop_path");
                movie.poster_path = rs.getString("poster_path");
                if (resultCode == 140){
                    movie.hidden = null;
                }
                else{
                    movie.hidden = rs.getBoolean("hidden");
                }
                movies.add(movie);
            }

            if (movies.size() == 0) {
                return Response.status(Response.Status.OK)
                        .entity(new MoviesResponseModel(211, "No movies found with search parameters.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                MovieModel[] movies_result = new MovieModel[movies.size()];
                for (int i=0; i<movies.size(); ++i) {
                    movies_result[i] = movies.get(i);
                }
                return Response.status(Response.Status.OK)
                        .entity(new MoviesResponseModel(210, "Found movie(s) with search parameters.", movies_result))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("Unable to query SQL");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }

    }


    @GET
    @Path("get/{movie_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get_movie(@Context HttpHeaders headers,
                        @PathParam("movie_id") String movie_id) {

        ServiceLogger.LOGGER.info(headers.getHeaderString("email"));
        ServiceLogger.LOGGER.info(headers.getHeaderString("session_id"));
        ServiceLogger.LOGGER.info(headers.getHeaderString("transaction_id"));

        String email = headers.getHeaderString("email");
        IdmPrivilegeRequestModel privilegeRequest = new IdmPrivilegeRequestModel(email, 4);
        Response response;
        Integer resultCode = 0;

        try {
            response = send_idm_request(MoviesService.getIdmConfigs().getPrivilegePath(),
                    privilegeRequest);
            IdmPrivilegeResponseModel response_payload = response.readEntity(IdmPrivilegeResponseModel.class);
            resultCode = response_payload.getResultCode();
            ServiceLogger.LOGGER.info(String.format("Remote replied with code %d.", resultCode));
            if (resultCode == 141) {
                // plevel <= 4

            }
            else if (resultCode == 140) {
                // plevel = 5 - Cannot see hidden movies

            }
            else {
                ServiceLogger.LOGGER.warning("Result code other than 140 or 141");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new GetMovieResponseModel(-1, "Internal server error.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch(Exception e) {
            ServiceLogger.LOGGER.info("Error");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new GetMovieResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }


        try {
            String movie_query = "SELECT DISTINCT movie.*, genre.genre_id, genre.name, person.person_id, person.name FROM movie "
           +"JOIN keyword_in_movie ON movie.movie_id = keyword_in_movie.movie_id"
           + " JOIN keyword ON keyword.keyword_id = keyword_in_movie.keyword_id"
           + " JOIN person ON person.person_id = movie.director_id"
           + " JOIN genre_in_movie ON movie.movie_id = genre_in_movie.movie_id"
            + " JOIN genre ON genre.genre_id = genre_in_movie.genre_id";

            movie_query += " WHERE movie.movie_id = ";

            // how many phrases there are

            movie_query += String.format("'%s'", movie_id);

            ServiceLogger.LOGGER.info(movie_query);
            PreparedStatement movie_stmt = MoviesService.getCon().prepareStatement(movie_query);
            ResultSet rs = movie_stmt.executeQuery();

            MovieModel movie = new MovieModel();

            ArrayList<GenreModel> genres = new ArrayList<>();
            ArrayList<PersonModel> people = new ArrayList<>();
            String people_query = "SELECT * " +
                    "FROM person " +
                    "JOIN person_in_movie on person.person_id = person_in_movie.person_id " +
                    "JOIN movie ON movie.movie_id = person_in_movie.movie_id " +
                    "WHERE movie.movie_id = ";
                people_query += String.format("'%s'", movie_id);
            ServiceLogger.LOGGER.info(people_query);
            PreparedStatement people_stmt = MoviesService.getCon().prepareStatement(people_query);
            ResultSet people_rs = people_stmt.executeQuery();
            while(people_rs.next()){
                PersonModel person = new PersonModel();
                person.person_id = people_rs.getInt("person.person_id");
                person.name = people_rs.getString("person.name");
                people.add(person);
                PersonModel[] person_result = new PersonModel[people.size()];
                for (int i=0; i<people.size(); ++i) {
                    person_result[i] = people.get(i);
                }
                movie.people = person_result;
            }


            while(rs.next()) {
                GenreModel genre = new GenreModel();

                movie.movie_id = rs.getString("movie_id");
                movie.title = rs.getString("title");
                movie.year = rs.getInt("year");
                movie.director = rs.getString("person.name");
                movie.rating = rs.getFloat("rating");
                movie.num_votes = rs.getInt("num_votes");
                movie.budget = rs.getInt("budget");
                movie.revenue = rs.getLong("revenue");
                movie.overview = rs.getString("overview");
                movie.backdrop_path = rs.getString("backdrop_path");
                movie.poster_path = rs.getString("poster_path");
                if (resultCode == 141){
                    movie.hidden = null;
                }
                else{
                    movie.hidden = rs.getBoolean("hidden");
                }
                genre.genre_id = rs.getInt("genre_id");
                genre.name = rs.getString("genre.name");

                genres.add(genre);


                GenreModel[] genre_result = new GenreModel[genres.size()];
                for (int i=0; i<genres.size(); ++i) {
                    genre_result[i] = genres.get(i);
                }

                movie.genres = genre_result;

            }

            if (movie.title == null) {
                return Response.status(Response.Status.OK)
                        .entity(new GetMovieResponseModel(211, "No movies found with search parameters.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                return Response.status(Response.Status.OK)
                        .entity(new GetMovieResponseModel(210, "Found movie(s) with search parameters.", movie))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("Unable to query SQL");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new GetMovieResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }

    }

    @POST
    @Path("thumbnail")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response thumbnail(@Context HttpHeaders headers, String jsonText) {
        ThumbnailRequestModel requestModel;
        ThumbnailResponseModel responseModel;
        ObjectMapper mapper = new ObjectMapper();


        try {
            requestModel = mapper.readValue(jsonText, ThumbnailRequestModel.class);
            String[] movies = requestModel.getMovie_ids();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ThumbnailResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }


        // do stuff
        String[] movies = requestModel.getMovie_ids();
        ArrayList<ThumbnailModel> thumbnails = new ArrayList<>();

        for (String movie : movies) {
            ServiceLogger.LOGGER.info("Movie id: " + movie);

            String title = "";
            String backdrop_path = "";
            String poster_path = "";

            try {
                // Get title, backdrop_path, poster_path
                String query = "SELECT title, backdrop_path, poster_path FROM movie WHERE movie_id = ?";
                PreparedStatement stmt = MoviesService.getCon().prepareStatement(query);
                stmt.setString(1, movie);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    title = rs.getString("title");
                    backdrop_path = rs.getString("backdrop_path");
                    poster_path = rs.getString("poster_path");
                }

            } catch (SQLException e) {
                ServiceLogger.LOGGER.warning("SQL Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ThumbnailResponseModel(-1, "Internal server error.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

            // Add to thumbnails if movie_id exists
            if (!title.equals("")) {
                ThumbnailModel thumbnail = new ThumbnailModel();
                thumbnail.movie_id = movie;
                thumbnail.title = title;
                thumbnail.backdrop_path = backdrop_path;
                thumbnail.poster_path = poster_path;
                thumbnails.add(thumbnail);
            }

        }

        if (thumbnails.size() == 0) {
            return Response.status(Response.Status.OK)
                    .entity(new ThumbnailResponseModel(211, "No movies found with search parameters.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();

        }

        // Return response object with thumbnails
        ThumbnailModel[] thumbnailList = new ThumbnailModel[thumbnails.size()];
        for (int i=0; i<thumbnails.size(); ++i) {
            thumbnailList[i] = thumbnails.get(i);
        }


        return Response.status(Response.Status.OK)
                .entity(new ThumbnailResponseModel(210, "Found movies with search parameters.", thumbnailList))
                .header("email", headers.getHeaderString("email"))
                .header("session_id", headers.getHeaderString("session_id"))
                .header("transaction_id", headers.getHeaderString("transaction_id"))
                .build();

    }


    @GET
    @Path("people")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@Context HttpHeaders headers,
                           @QueryParam("name") String name,
                           @DefaultValue("10") @QueryParam("limit") Integer limit,
                           @DefaultValue("0") @QueryParam("offset") Integer offset,
                           @DefaultValue("title") @QueryParam("orderby") String orderby,
                           @DefaultValue("asc") @QueryParam("direction") String direction) {
        ServiceLogger.LOGGER.info("Received a people request.");

        if (limit != null && limit != 10 && limit != 25 && limit != 50 && limit != 100 ||
                orderby != null && !orderby.equals("title") && !orderby.equals("rating") && !orderby.equals("year") ||
                direction != null && !direction.equals("asc") && !direction.equals("desc")) {
            ServiceLogger.LOGGER.info("Request was bad.");
            limit = 10;
            orderby = "title";
            direction = "asc";
        }
        if (offset < 0 || offset%limit != 0) {
            ServiceLogger.LOGGER.info("Invalid offset.");
            offset = 0;
        }

        String email = headers.getHeaderString("email");
        IdmPrivilegeRequestModel privilegeRequest = new IdmPrivilegeRequestModel(email, 4);
        Response response;
        Integer resultCode = 0;


        try {
            response = send_idm_request(MoviesService.getIdmConfigs().getPrivilegePath(),
                    privilegeRequest);
            IdmPrivilegeResponseModel response_payload = response.readEntity(IdmPrivilegeResponseModel.class);
            resultCode = response_payload.getResultCode();
            ServiceLogger.LOGGER.info(String.format("Remote replied with code %d.", resultCode));
            if (resultCode == 141) {
//                hidden = null;
            }
            else if (resultCode == 140) {
//                hidden = false;
            }
            else {
                ServiceLogger.LOGGER.warning("Result code other than 140 or 141");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch(Exception e) {
            ServiceLogger.LOGGER.info("Error");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }


        try {
            String people_query = "SELECT * FROM movie "
//                    + "JOIN person ON person.person_id = movie.director_id "
                    + "JOIN person_in_movie ON person_in_movie.movie_id = movie.movie_id " +
                    "JOIN person ON person.person_id = person_in_movie.person_id " +
                    "WHERE person.name = ";

            people_query += String.format("'%s'", name);

            String limit_query = String.format(" LIMIT %s", limit);
            String offset_query = String.format(" OFFSET %s", offset);
            String order_query = String.format(" ORDER BY %s %s", orderby, direction);

            people_query += order_query;
            people_query += limit_query;
            people_query += offset_query;

            ServiceLogger.LOGGER.info(people_query);
            PreparedStatement people_stmt = MoviesService.getCon().prepareStatement(people_query);
            ResultSet people_rs = people_stmt.executeQuery();

            ArrayList<MovieModel> movies = new ArrayList<>();

            while(people_rs.next()) {
                MovieModel movie = new MovieModel();
                movie.movie_id = people_rs.getString("movie_id");
                movie.title = people_rs.getString("title");
                movie.year = people_rs.getInt("year");
                String director_query = "SELECT DISTINCT name FROM person"+
                " JOIN movie ON person.person_id = movie.director_id"+
                " JOIN person_in_movie ON person_in_movie.movie_id = movie.movie_id" +
                 " WHERE movie.movie_id = ";
                director_query += String.format("'%s'", people_rs.getString("movie_id"));
                PreparedStatement director_stmt = MoviesService.getCon().prepareStatement(director_query);
                ResultSet director_rs = director_stmt.executeQuery();
                while(director_rs.next()){
                    movie.director = director_rs.getString("name");
                }
                movie.rating = people_rs.getFloat("rating");
                movie.backdrop_path = people_rs.getString("backdrop_path");
                movie.poster_path = people_rs.getString("poster_path");
                if (resultCode == 141){
                    movie.hidden = null;
                }
                else{
                    movie.hidden = people_rs.getBoolean("hidden");
                }
                movies.add(movie);
            }
            if (movies.size() == 0) {
                return Response.status(Response.Status.OK)
                        .entity(new MoviesResponseModel(211, "No movies found with search parameters.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                MovieModel[] movie_result = new MovieModel[movies.size()];
                for (int i = 0; i < movies.size(); i++) {
                    return Response.status(Response.Status.OK)
                            .entity(new MoviesResponseModel(210, "Found movie(s) with search parameters.", movie_result))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }

            }

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("Unable to query SQL");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MoviesResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }
        //TODO after changes made
        return null;
    }

    @GET
    @Path("people/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response peopleSearch(@Context HttpHeaders headers,
                                 @QueryParam("name") String name,
                                 @QueryParam("birthday") String birthday,
                                 @QueryParam("movie_title") String movie_title,
                                 @DefaultValue("10") @QueryParam("limit") Integer limit,
                                 @DefaultValue("0") @QueryParam("offset") Integer offset,
                                 @DefaultValue("name") @QueryParam("orderby") String orderby,
                                 @DefaultValue("asc") @QueryParam("direction") String direction) {

        if (limit != null && limit != 10 && limit != 25 && limit != 50 && limit != 100){
            limit = 10;
        }
        if(orderby != null && !orderby.equals("name") && !orderby.equals("birthday") && !orderby.equals("popularity") ){
            orderby = "name";
        }
        if (direction != null && !direction.equals("asc") && !direction.equals("desc")) {
            direction = "asc";
        }
        if (offset < 0 || offset%limit != 0) {
            ServiceLogger.LOGGER.info("Invalid offset.");
            offset = 0;
        }

        ArrayList<PersonModel> people = new ArrayList<>();
        String query = "";

        if (movie_title != null) {
            query = "SELECT DISTINCT person.person_id, name, birthday, popularity, profile_path FROM person " +
                    "JOIN person_in_movie p ON p.person_id = person.person_id " +
                    "JOIN movie m ON p.movie_id = m.movie_id " +
                    String.format("WHERE m.title LIKE '%%%s%%' ", movie_title);
            if (name != null) {
                query += String.format("AND person.name LIKE '%%%s%%' ", name);
            }
            if (birthday != null) {
                query += String.format("AND person.birthday = '%s' ", birthday);
            }


            if (orderby.equals("name"))
                query += String.format("ORDER BY person.name %s, person.popularity DESC ", direction);
            else if (orderby.equals("birthday"))
                query += String.format("ORDER BY person.birthday %s, person.popularity DESC ", direction);
            else if (orderby.equals("popularity"))
                query += String.format("ORDER BY person.popularity %s, person.name ASC ", direction);
            query += String.format("LIMIT %s ", limit);
            query += String.format("OFFSET %s", offset);

            ServiceLogger.LOGGER.info(query);

            try {
                PreparedStatement stmt = MoviesService.getCon().prepareStatement(query);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    PersonModel p = new PersonModel();
                    p.person_id = rs.getInt("person_id");
                    p.name = rs.getString("name");
                    p.birthday = rs.getString("birthday");
                    p.popularity = rs.getFloat("popularity");
                    p.profile_path = rs.getString("profile_path");
                    people.add(p);
                }

                if (people.size() == 0) {
                    return Response.status(Response.Status.OK)
                            .entity(new PeopleSearchResponseModel(213, "No people found with search parameters.", null))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }
                else {
                    PersonModel[] peopleList = new PersonModel[people.size()];
                    for (int i=0; i<people.size(); ++i) {
                        peopleList[i] = people.get(i);
                    }
                    return Response.status(Response.Status.OK)
                            .entity(new PeopleSearchResponseModel(212, "Found people with search parameters.", peopleList))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }

            } catch (SQLException e) {
                ServiceLogger.LOGGER.warning("SQL Error");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new PeopleSearchResponseModel(-1, "Internal server error.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }
        else {
            query = "SELECT DISTINCT person_id, name, birthday, popularity, profile_path FROM person ";
            if (name != null) {
                query += String.format("WHERE name LIKE '%%%s%%' ", name);
                if (birthday != null) {
                    query += "AND ";
                    query += String.format("birthday = '%s' ", birthday);
                }
            }
            else if (birthday != null) {
                query += String.format("WHERE birthday = '%s' ", birthday);
            }

            query += String.format("ORDER BY %s %s ", orderby, direction);
            query += String.format("LIMIT %s ", limit);
            query += String.format("OFFSET %s", offset);

            ServiceLogger.LOGGER.info(query);

            try {
                PreparedStatement stmt = MoviesService.getCon().prepareStatement(query);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    PersonModel p = new PersonModel();
                    p.person_id = rs.getInt("person_id");
                    p.name = rs.getString("name");
                    p.birthday = rs.getString("birthday");
                    p.popularity = rs.getFloat("popularity");
                    p.profile_path = rs.getString("profile_path");
                    people.add(p);
                }

                if (people.size() == 0) {
                    return Response.status(Response.Status.OK)
                            .entity(new PeopleSearchResponseModel(213, "No people found with search parameters.", null))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }
                else {
                    PersonModel[] peopleList = new PersonModel[people.size()];
                    for (int i=0; i<people.size(); ++i) {
                        peopleList[i] = people.get(i);
                    }
                    return Response.status(Response.Status.OK)
                            .entity(new PeopleSearchResponseModel(212, "Found people with search parameters.", peopleList))
                            .header("email", headers.getHeaderString("email"))
                            .header("session_id", headers.getHeaderString("session_id"))
                            .header("transaction_id", headers.getHeaderString("transaction_id"))
                            .build();
                }

            } catch (SQLException e) {
                ServiceLogger.LOGGER.warning("SQL Error2");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new PeopleSearchResponseModel(-1, "Internal server error.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }
        }

    }

    @GET
    @Path("people/get/{person_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get_person(@Context HttpHeaders headers,
                              @PathParam("person_id") Integer person_id) {

        String email = headers.getHeaderString("email");

        try {
            String people_query = "SELECT * FROM person " +
                    "JOIN gender ON person.gender_id = gender.gender_id " +
                    "WHERE person_id = ";

            people_query += String.format("'%s'", person_id);

            ServiceLogger.LOGGER.info(people_query);
            PersonModel person = new PersonModel();
            PreparedStatement people_stmt = MoviesService.getCon().prepareStatement(people_query);
            ResultSet get_people_rs = people_stmt.executeQuery();


            while(get_people_rs.next()) {
                person.person_id = person_id;
                person.name = get_people_rs.getString("name");
                person.gender = get_people_rs.getString("gender.gender_name");
                person.birthday = get_people_rs.getString("birthday");
                person.biography = get_people_rs.getString("biography");
                person.birthplace = get_people_rs.getString("birthplace");
                person.popularity = get_people_rs.getFloat("popularity");
                person.profile_path = get_people_rs.getString("profile_path");
            }

            if (person.name == null) {
                return Response.status(Response.Status.OK)
                        .entity(new GetPersonResponseModel(213, "No people found with search parameters.", null))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            } else {
                return Response.status(Response.Status.OK)
                        .entity(new GetPersonResponseModel(212, "Found people with search parameters.", person))
                        .header("email", headers.getHeaderString("email"))
                        .header("session_id", headers.getHeaderString("session_id"))
                        .header("transaction_id", headers.getHeaderString("transaction_id"))
                        .build();
            }

        } catch (SQLException e) {
            ServiceLogger.LOGGER.warning("Unable to query SQL");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new GetPersonResponseModel(-1, "Internal server error.", null))
                    .header("email", headers.getHeaderString("email"))
                    .header("session_id", headers.getHeaderString("session_id"))
                    .header("transaction_id", headers.getHeaderString("transaction_id"))
                    .build();
        }

    }

}
