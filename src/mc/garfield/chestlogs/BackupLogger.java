package mc.garfield.chestlogs;

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
            logsFolder.mkdirs(); // Create the "logs" folder if it doesn't exist
        }
        File logFile = new File(logsFolder, fileName);
        try {
            fileWriter = new FileWriter(logFile, true); // Append mode
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void log(String playerName, String dimension, int x, int y, int z) {
        checkDate(); // Check if a new log file needs to be created
        String currentTime = new SimpleDateFormat("HH:mm").format(new Date());
        String logEntry = "[" + currentTime + "] - " + playerName + " - " + x + ", " + y + ", " + z + " - " + dimension + "\n";
        try {
            fileWriter.write(logEntry);
            fileWriter.flush(); // Ensure the entry is written immediately
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkDate() {
        String newDate = getCurrentDate();
        if (!newDate.equals(currentDate)) {
            // If the date has changed, create a new log file
            currentDate = newDate;
            close(); // Close the current file writer
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
