import authorization.JWToken;
import objects.extra.RafDB;
import objects.query.QueryView;
import objects.query.WhereMap;
import objects.query.dashboard.DashboardInfo;
import objects.tables.TableUsers;
import objects.tables.TableUsersNotice;
import objects.tables.TableUsersProfiles;
import objects.tables.stock.TableStore;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;

import static objects.extra.RafDB.*;

@Path("dashboard")
public class Dashboard {

    private static JWToken jwtToken;

    static {
        try {
            jwtToken = new JWToken();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /** Get info for Dashboard section
     * parameters for filter get from uri parameter
     * */
    @GET
    public Response getDashboard(
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            String body){
        try {
            QueryView query = DashboardInfo.getAnalytic(uriInfo);
            return Responses.view(headers, query, uriInfo);

        } catch (SQLException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }


    /** Get information dashboard about employers
     * employer data get from uri parameters
     * */
    @GET
    @Path("/employers")
    public Response getDashboardEmployers(
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            String body){
        try {
            QueryView query = DashboardInfo.getAnalitycEmployer(uriInfo);
            return Responses.view(headers, query, uriInfo);

        } catch (SQLException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Get information dashboard about products */
    @GET
    @Path("/products")
    public Response getDashboardProducts(
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            String body){
        try {
            QueryView query = DashboardInfo.getAnalitycProductions(uriInfo);
            return Responses.view(headers, query, uriInfo);

        } catch (SQLException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Get notice in dashboard
     * parameter get from token jwt
     * */
    @GET
    @Path("/notice")
    public Response getDashboardNotice(
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            String body){
        try {

            // get token from header
            String token = jwtToken.getHeaderToken(headers);
            System.out.println(" token: " + token);


            // get user id from token
            Integer user_id = jwtToken.getId(token);
            System.out.println(" user_id: " + user_id);

            // get notice about user id
            QueryView query = new QueryView(users_notice(), TableUsersNotice.ID,
                    TableUsersNotice.MESSAGE,
                    TableUsersNotice.SECTION,
                    TableUsersNotice.TYPE,
                    TableUsersNotice.BUTIK_ID,
                    TableUsersNotice.READ,
                    TableUsersNotice.DATE,
                    TableUsersNotice.USER_ID,
                    TableUsersNotice.ACTION,
                    TableUsersNotice.AUTHOR_ID,
                    TableUsersNotice.AUTHOR,
                    TableUsersNotice.DOC_ID,
                    TableUsersNotice.DOC_TYPE)
                    .asVar("p")

                    // total count of rows
                    .extra("total_count", "count_row.count")

                    // get number of document
                    .extra("doc_number", "coalesce( p.doc_number, (select number from raf_product_move where id = p.doc_id) )")

                    // get magazine name
                    .extra("magazine", new QueryView(store(), TableStore.NAME)
                            .where(new WhereMap(TableStore.ID,
                                    new QueryView(users(), TableUsers.SUBSCRIBE)
                                            .where(new WhereMap(TableUsers.ID, "p",TableUsersNotice.USER_ID)))))

                    // get all rows count for pagination
                    .lateral("count_row",new QueryView(users_notice()).where(new WhereMap(TableUsersNotice.USER_ID, user_id)).countOnly())

                    .where(new WhereMap(TableUsersNotice.USER_ID, user_id))

                    // desc order about date
                    .order(TableUsersNotice.DATE.getName() + "_");

            return Responses.view(headers, query, uriInfo);

        } catch (SQLException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Edit notice status from dashboard section
     * (viewed, delete etc)
     * */
    @POST
    @Path("/notice")
    public Response editDashboardNotice(
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            String body){
        try {

            // get token from header
            String token = jwtToken.getHeaderToken(headers);

            // get user id from token
            Integer user_id = jwtToken.getId(token);

            JSONObject notice = new JSONObject(body);
            if (notice.has("id") && notice.has("read") && notice.get("read") instanceof Boolean){

                JSONObject pure_notice = new JSONObject();
                pure_notice.put("id", notice.getInt("id"));
                pure_notice.put("read", notice.getBoolean("read"));

                RafDB.edit("public", pure_notice, users_notice(), false);
                return services.Responses.getResponse(200, "notice edited");
            }else{
                return services.Responses.getResponse(400, "notice no have need parameters");
            }

        } catch (SQLException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return services.Responses.getResponse(400, e.getMessage());
        }
    }
}
