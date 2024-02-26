package gb.eathanpage.chestlogs;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupLogger {

    private String currentDate;
    private FileWriter fileWriter;

    public BackupLogger() {
        currentDate = getCurrentDate();
        createNewLogFile();
    }

    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(new Date());
    }

    private void createNewLogFile() {
        String fileName = "backup_log_" + currentDate + ".txt";
        File logsFolder = new File(Main.getPlugin(Main.class).getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        File logFile = new File(logsFolder, fileName);
        try {
            fileWriter = new FileWriter(logFile, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void log(String playerName, String dimension, int x, int y, int z) {
        checkDate();
        String currentTime = new SimpleDateFormat("HH:mm").format(new Date());
        String logEntry = "[" + currentTime + "] - " + playerName + " - " + x + ", " + y + ", " + z + " - " + dimension + "\n";
        try {
            fileWriter.write(logEntry);
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkDate() {
        String newDate = getCurrentDate();
        if (!newDate.equals(currentDate)) {
            currentDate = newDate;
            close();
            createNewLogFile();
        }
    }

    public void close() {
        if (fileWriter != null) {
            try {
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
