import authorization.JWToken;
import objects.extra.RafDB;
import objects.query.QueryView;
import objects.query.WhereMap;
import objects.tables.TableUsers;
import objects.tables.TableUsersNotice;
import objects.tables.TableUsersProfiles;
import objects.tables.stock.TableStore;
import org.json.JSONObject;
import services.WebSockets.socketNotice;

import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;

import static objects.extra.RafDB.store;
import static objects.extra.RafDB.users_notice;
import static objects.extra.RafDB.users_profiles;

/** class for work with notice */
public class Noticer {

    private static JWToken jwtToken;

    static {
        try {
            jwtToken = new JWToken();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    /** send notice to user info from http header
     * @param headers http header
     * @param info json of message
     * @param type type (insert, update, delete, open (shift), close (shift))
     * @param section section (stock, payments etc)
     * @param doc_type type doc
     */
    public static void sendNotice(HttpHeaders headers, JSONObject info, String type, String section, String doc_type, String number){
        try {
            // get token from header
            String token = jwtToken.getHeaderToken(headers);


            // get info from token
            Integer butik_id = jwtToken.getButik(token);
            Integer author_id = jwtToken.getId(token);

            // send notice
            sendNotice(butik_id, author_id, type, info, section, doc_type, number);

        } catch (SQLException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /** send notice to user
     * @param butik_id magazine id
     * @param author_id id of user who do action
     * @param info json of message
     * @param type type (insert, update, delete, open (shift), close (shift))
     * @param section section (stock, payments etc)
     * @param doc_type type doc
     */
    public static void sendNotice(Integer butik_id, Integer author_id, String type, JSONObject info, String section, String doc_type, String number){

        // create thread for send and create notice
        Runnable task = () -> {
            // get thread name
            String threadName = Thread.currentThread().getName();
            System.out.println("send notice thread : " + threadName);

            JSONObject message = new JSONObject();

            // get butik info
            ResultSet butik  = QueryView.select(
                    new QueryView(store(), TableStore.NAME)
                            .where(new WhereMap(TableStore.ID, butik_id)));

            // add info and type to message
            try {
                if (butik != null){
                    butik.first();
                    switch (type) {
                        case "open": { // if type of open shift
                            String message_str = " Открыта смена в \"" + butik.getString("name") + "\"";
                            message.put("message", message_str);
                            message.put("info", info);
                            break;
                        }case "close": { // if type of close shift
                            String message_str = " Закрыта смена в \"" + butik.getString("name") + "\"";
                            message.put("message", message_str);
                            message.put("info", info);
                            break;
                        }case "insert": { // if type of insert row
                            String message_str = " Создан документ в \"" + butik.getString("name") + "\"";
                            message.put("message", message_str);
                            message.put("info", info);
                            break;
                        }case "update": { // if type of edit row
                            String message_str = " Отредактирован документ в \"" + butik.getString("name") + "\"";
                            message.put("message", message_str);
                            message.put("info", info);
                            break;
                        }case "delete": { // if type of delete roe
                            String message_str = " Удален документ в \"" + butik.getString("name") + "\"";
                            message.put("message", message_str);
                            message.put("info", info);
                            break;
                        }
                        default:
                            message.put("info", info);
                            message.put("message", info.has("message") ? info.get("message") : null);
                            break;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // get info about author
            ResultSet author = QueryView.select(
                    new QueryView(users_profiles(), TableUsersProfiles.NICKNAME)
                            .where(new WhereMap(TableUsersProfiles.USER_ID, author_id)));
            String name = null;
            if (author != null){
                try {
                    if(author.first()){
                        name = author.getString(TableUsersProfiles.NICKNAME.getName());
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            // get info about users registered in this butik
            ResultSet users = QueryView.select(
                    new QueryView(RafDB.users(), TableUsers.ID)
                            .where(new WhereMap(TableUsers.SUBSCRIBE, butik_id)));

            try {
                if (users != null){
                    while (users.next()){
                        Integer user_id = users.getInt("id");

                        // generate notice
                        JSONObject mm = new JSONObject();
                        mm.put(TableUsersNotice.MESSAGE.getName(), message);
                        mm.put(TableUsersNotice.SECTION.getName(), section);
                        mm.put(TableUsersNotice.BUTIK_ID.getName(), butik_id);
                        mm.put(TableUsersNotice.TYPE.getName(), "notice");
                        mm.put(TableUsersNotice.AUTHOR_ID.getName(), author_id);
                        mm.put(TableUsersNotice.AUTHOR.getName(), name);
                        mm.put(TableUsersNotice.USER_ID.getName(), user_id);
                        mm.put(TableUsersNotice.ACTION.getName(), type);
                        mm.put(TableUsersNotice.DOC_ID.getName(), info.has("id") ? info.get("id") : null);
                        mm.put(TableUsersNotice.DOC_TYPE.getName(), doc_type);

                        // write notice to database
                        RafDB.edit("public", mm, users_notice());

                        // send notice to users in websocket
                        socketNotice.sendNotice(String.valueOf(user_id),mm);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }
}
