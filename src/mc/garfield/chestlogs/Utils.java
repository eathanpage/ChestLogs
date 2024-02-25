package mc.garfield.chestlogs;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
public class Utils {
    public static Date parseTimestamp(String timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy");
        try {
            return dateFormat.parse(timestamp);
        } catch (ParseException e) {
            e.printStackTrace();
            return new Date(); // Return current date in case of parsing error
        }
    }
}
