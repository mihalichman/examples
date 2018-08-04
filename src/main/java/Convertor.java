
import com.stylist.dbconnect.query.QueryView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Utility for converting ResultSets into some Output formats
 */
public class Convertor {

    private final static Logger logger = LoggerFactory.getLogger(Convertor.class);
    private final static String DATA = "data";

    /**
     * Convert a result set into a JSON Array
     * @return a JSONArray
     */
    public static JSONObject QueryToJson(String query) throws SQLException, IOException, ClassNotFoundException, JSONException {
        Statement statement = new pgDBConnect().pgconnect().createStatement();

        logger.info("query = {}", query);
        ResultSet result = statement.executeQuery(query);

        JSONObject response_j = new JSONObject();

        ResultSetMetaData md = result.getMetaData();
        int columns = md.getColumnCount();

        while (result.next()){
            for(int i=1; i<=columns; ++i){
                // System.out.println(result.getString(i));
                if (result.getString(i)!=null){
                    try{
                        response_j.put(DATA, new JSONArray(result.getString(i)));
                    }catch (JSONException e){
                        e.printStackTrace();
                        response_j.put("data", new JSONArray());
                        response_j.put("error", e.getMessage());
                    }
                }else {
                    response_j.put("data", new JSONArray());
                }
            }
        }

        result.close();
        statement.close();

        return response_j;
    }

    /**
     * Convert a QueryView into a JSON Array (with field "data")
     * @param query QueryView obj
     * @return a JSONArray
     */
    public static JSONObject QueryToJsonExtra(QueryView query) throws SQLException, IOException, ClassNotFoundException, JSONException {

        System.out.println("query : " + query.toString());

        ResultSet result;
        if (query.getIsJson()){
            result = pgDBConnect.stmtRead().executeQuery(query.toString());
        }else{
            query.json();
            result = pgDBConnect.stmtRead().executeQuery(query.toString());
        }

        JSONObject response_j = new JSONObject();
        JSONArray data_j = new JSONArray();

        if (result.first()){
            String res1 = result.getString(1);
            if (res1 != null){
                data_j = new JSONArray(res1);
            }
        }
        response_j.put("data",data_j);
        result.close();
        return response_j;
    }

    /**
     * Convert a JSON to Response
     * @return a Response
     */
    static ResponseEntity JSONtoResponse(JSONObject json) throws JSONException {
        JSONArray responseJ  = json.getJSONArray("data");
        if (responseJ.length()!=0){
            return new ResponseEntity<>(responseJ.toString(), HttpStatus.OK);
        }else if (responseJ.length() == 1){
            return new ResponseEntity<>(responseJ.getJSONObject(0).toString(), HttpStatus.OK);
        }else{
            return new ResponseEntity<>(responseJ.toString(), HttpStatus.NO_CONTENT);
        }
    }



    public static String arrToStrPureIntegerOnly(String str){
        return arrToStrPure(arrToStrPureIntegerOnlyArr(str));
    }

    /**
     * Convert String with delimeter coma to array with integer only
     * @param str String to convert
     * @return ArrayList
     * */

    private static ArrayList<String> arrToStrPureIntegerOnlyArr(String str){
        ArrayList<String> int_arr = new ArrayList<>();
        String[] array = str.split(",");
        for (String value: array){
            try {
                Integer inter = Integer.parseInt(value);
                int_arr.add(value);
            }catch (NumberFormatException e){
                e.getMessage();
            }
        }
        return int_arr;
    }

    public static ArrayList<Integer> strToArrIntOnly(String str){
        ArrayList<Integer> int_arr = new ArrayList<>();
        String[] array = str.split(",");
        for (String value: array){
            try {
                Integer inter = Integer.parseInt(value);
                int_arr.add(inter);
            }catch (NumberFormatException e){
                e.getMessage();
            }
        }
        return int_arr;
    }

    /**
     * Convert pg query to json response
     * @param query String query which need around to json
     * @return String with new query
     * */
    public static String PgJsonQuery(String query) throws SQLException, IOException, ClassNotFoundException, JSONException {
        query = " select array_to_json(array_agg(row_to_json(t))) as data" +
                " from(" +query+ ") t";
        return query;
    }

    /**
     * Convert pg query to json with extra field name
     * @param query String query which need around to json
     * @param var String extra name for json
     * @return String with new query
     * */
    public static String PgJsonQuery(String query, String var) throws SQLException, IOException, ClassNotFoundException, JSONException {
        query = " select array_to_json(array_agg(row_to_json(" + var + "))) as data" +
                " from(" +query+ ") " + var + " ";
        return query;
    }

    /**
     * coalesce around query for null result
     * @param query String
     * @return String
     */
    public static String PgCoalesceJSON(String query) throws SQLException, IOException, ClassNotFoundException, JSONException {
        query = " COALESCE(("+query+"),'[]') ";
        return query;
    }


    /**
     * Methods for generate response
     * @param entity object for body responce
     * @return ResponseEntity spring
     */
    public static ResponseEntity getResponseJSONArr(HttpStatus status, JSONArray entity) throws JSONException {
        return getResponse(status, entity.toString());
    }

    static ResponseEntity getResponse(HttpStatus status, JSONObject message){
        return getResponse(status, message.toString());
    }

    static ResponseEntity getResponse(HttpStatus status, String message) throws JSONException {

        JSONObject entity_return = new JSONObject();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
        headers.add("Access-Control-Allow-Credentials","true");

        entity_return.put("code",status.value());
        entity_return.put("message",message);

        return new ResponseEntity<>(entity_return.toString(), headers, status);

    }

    /**
     * Check object about JSON
     * */
    static Object checkJSON(String body){
        Object object = null;
        try{
            object = new JSONObject(body);
        }catch (JSONException e){
            logger.debug(" is not object : {} ", e.getMessage());
        }
        try {
            object = new JSONArray(body);
        } catch (JSONException e){
            logger.debug(" is not array: {}", e.getMessage());
        }
        return object;
    }

    /**
     * Convert a UnixTimestamp to String Date
     * @param timestamp Integer Unis timestamp
     * @return a String Date
     */
    public static String TimestampToDate(int timestamp){
        String strDate;
        DateFormat dateFormat;

        /* get now date */
        Date now_date = Calendar.getInstance().getTime();
        Calendar calNow = Calendar.getInstance();
        calNow.setTime(now_date);

        /* get date from timestamp*/
        Date time = new Date(timestamp*1000L);
        Calendar calIn = Calendar.getInstance();
        calIn.setTime(time);

        /* gate dates oparameters*/
        int now_y = calNow.get(Calendar.YEAR);
        int now_m = calNow.get(Calendar.MONTH);
        int now_d = calNow.get(Calendar.DAY_OF_MONTH);

        int time_y = calIn.get(Calendar.YEAR);
        int time_m = calIn.get(Calendar.MONTH);
        int time_d = calIn.get(Calendar.DAY_OF_MONTH);

        /* get string date format */
        if (now_y==time_y&&now_m==time_m){
            if (now_d==time_d){

                dateFormat= new SimpleDateFormat("сегодня в HH:mm");
            }else if (now_d-1==time_d){
                dateFormat= new SimpleDateFormat("вчера в HH:mm");
            }else{
                dateFormat= new SimpleDateFormat("dd.MM в HH:mm");
            }
        }else{
            dateFormat= new SimpleDateFormat("dd.MM в HH:mm");
        }
        strDate = dateFormat.format(time);
        return strDate;
    }



    /**
     * Convert a UnixTimestamp to String Date without time
     * @param timestamp Unix rimestamp
     * @return a String Date
     */
    public static String TimestampToDateOnlyDate(int timestamp){
        String strDate;

        Date now_date = Calendar.getInstance().getTime();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now_date);

        Date time=new Date(timestamp*1000L);
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(time);

        int now_y = cal.get(Calendar.YEAR);
        int now_m = cal.get(Calendar.MONTH);
        int now_d = cal.get(Calendar.DAY_OF_MONTH);

        int time_y = cal1.get(Calendar.YEAR);
        int time_m = cal1.get(Calendar.MONTH);
        int time_d = cal1.get(Calendar.DAY_OF_MONTH);

        DateFormat dateFormat;

        if (now_y==time_y&&now_m==time_m){
            if (now_d==time_d){

                dateFormat= new SimpleDateFormat("сегодня");
            }else if (now_d-1==time_d){
                dateFormat= new SimpleDateFormat("вчера");
            }else{
                dateFormat= new SimpleDateFormat("dd.MM");
            }
        }else{
            dateFormat= new SimpleDateFormat("dd.MM");
        }

        strDate = dateFormat.format(time);

        return strDate;
    }


    /**
     * Convert a UnixTimestamp to String Date time only
     * @param timestamp Unix rimestamp
     * @return a String Date
     */
    public static String TimestampToOnlyTime(int timestamp){
        String strDate;

        Date time=new Date(timestamp*1000L);
        DateFormat dateFormat = new SimpleDateFormat("HH:mm");
        strDate = dateFormat.format(time);

        return strDate;
    }
}
