package gb.eathanpage.chestlogs;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
public class Utils {
    public static Date parseTimestamp(String timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy");
        try {
            return dateFormat.parse(timestamp);
        } catch (ParseException e) {
            e.printStackTrace();
            return new Date();
        }
    }
}
