import com.stylist.auth.service.JWTokenClient;
import com.stylist.config.AppConfig;
import com.stylist.dao.SocialDataDAO;
import com.stylist.dbconnect.Convertor;
import com.stylist.dbconnect.RafDB;
import com.stylist.dbconnect.Responses;
import com.stylist.dbconnect.WhereMap;
import com.stylist.dbconnect.query.QueryView;
import com.stylist.dbconnect.table.TableProfiles;
import com.stylist.dbconnect.table.TableUsers;
import com.stylist.model.SocialData;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

import static com.stylist.dbconnect.RafDB.stylistProfiles;
import static com.stylist.dbconnect.RafDB.stylistUsers;
import static com.stylist.dbconnect.RafDB.stylist_users;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

/** Class for profile work, contain methods for view, edit profile info*/
@RestController
public class ProfileController {

    private static Logger LOGGER = LoggerFactory.getLogger(ProfileController.class);

    private AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(AppConfig.class);

    private SocialDataDAO profileDAO = context.getBean(SocialDataDAO.class);

    @RequestMapping(value = "/profile", method = GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseBody
    public ResponseEntity getFeeds(HttpServletRequest request) throws SQLException, IOException, ClassNotFoundException {


        /* get user is from header token if present*/
        Integer user_id  = new JWTokenClient().getUserId(request);

        /* query by get all feed (will be criteria by user subscribe)*/
        QueryView queryFeeds = stylistUsers(
                TableUsers.ALL_COLUMNS)
                .asVar("usr")
                .extra("social", stylistProfiles(TableProfiles.ALL_COLUMNS)
                        .where(new WhereMap(TableProfiles.USER_ID, "usr", TableUsers.ID ))
                        .json())
                .where(TableUsers.ID, user_id);

        return Responses.view(request, queryFeeds);
    }


    @RequestMapping(value = "/profile",
            produces = "application/json",
            consumes = "application/json",
            method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity createProfile(
            @RequestBody String body,
            HttpServletResponse response,
            HttpServletRequest request) throws IOException, SQLException, ClassNotFoundException {

        /* get user id from request header token */
        Integer userId  = new JWTokenClient().getUserId(request);

        /* query ti find user by id*/
        QueryView getUserData = stylistUsers(TableUsers.ID)
                .where(TableUsers.ID, userId);

        JSONObject userData = Convertor.QueryToJsonExtra(getUserData);

        LOGGER.info("userData to edit :: {}", userData);

        Integer userDataId = userData
                .getJSONArray("data")
                .getJSONObject(0)
                .getInt(TableUsers.ID.getName());

        try{
            JSONObject body_j = new JSONObject(body);
            SocialData client = null;

            try {
                /* get client id from request header */
                Integer client_id  = new JWTokenClient().getId(request);
                if (client_id != null){
                     client = profileDAO.get(client_id);
                }
            } catch (SQLException | IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }

            if (body_j.has("email") && body_j.get("email") instanceof String){

                String email = body_j.getString("email");
                if (client != null){
                    client.setEmail(email);
                    LOGGER.info("client to edit :: {}", client);

                    /* update client info */
                    profileDAO.update(client.getId(),client);
                }
            }

            body_j.put(TableUsers.ID.getName(), userDataId);
            RafDB.editAndReturn(body_j, stylist_users);

            return new ResponseEntity<>(client != null ? client : "client not found", HttpStatus.OK);
        }catch (JSONException e){
            LOGGER.error("Profile edit error ", e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
