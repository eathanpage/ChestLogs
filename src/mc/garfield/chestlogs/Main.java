package mc.garfield.chestlogs;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import org.bukkit.block.Block;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.util.*;
public class Main extends JavaPlugin implements Listener {

    private boolean logToConsole;
    private int retentionDays;
    private File logFile;
    private Gson gson;
    private BackupLogger backupLogger;
    @Override
    public void onEnable() {

        getLogger().info("ChestLogs is now enabled");
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        backupLogger = new BackupLogger();

        // Load configuration
        saveDefaultConfig();
        reloadConfig();
        logToConsole = getConfig().getBoolean("log-to-console", true);
        getLogger().info("Logging to console is currently set to :" + logToConsole);
        retentionDays = getConfig().getInt("retentionDays", 7);
        getLogger().info("Log retention is currently set to " + retentionDays + " days");

        // Initialize Gson
        gson = new GsonBuilder().setPrettyPrinting().create();

        // Initialize log file
        logFile = new File(getDataFolder(), "log.json");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Schedule deletion of old log entries
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::deleteOldEntries, 0, 72000); // Runs every hour (20 ticks/second * 3600 seconds)
    }

    @Override
    public void onDisable() {
        // Close the BackupLogger when the plugin is disabled
        getLogger().warning("ChestLogs is now disabled");
        backupLogger.close();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock != null && (clickedBlock.getState() instanceof Chest || clickedBlock.getState() instanceof Barrel)) {
            logChestInteraction(player, clickedBlock);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String[] args = message.split(" ");

        // Check if the message starts with "/chestlog" and has enough arguments
        if (args.length >= 4 && args[0].equalsIgnoreCase("/chestlog")) {
            try {
                int x = Integer.parseInt(args[1]);
                int y = Integer.parseInt(args[2]);
                int z = Integer.parseInt(args[3]);
                String dimension = ""; // Default dimension is empty

                // Check if the dimension parameter is provided
                if (args.length >= 5) {
                    dimension = args[4];
                }

                displayLogEntries(event.getPlayer(), x, y, z, dimension);
                event.setCancelled(true);
            } catch (NumberFormatException e) {
                event.getPlayer().sendMessage("Invalid coordinates.");
            }
        }
    }
    private void logChestInteraction(Player player, Block block) {
        String playerName = player.getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        String dimension = getDimension(block.getWorld().getName());
        String timestamp = new Date().toString();

        // Create log entry
        LogEntry logEntry = new LogEntry(timestamp, dimension, x, y, z, playerName);

        // Convert log entry to JSON string
        String jsonLogEntry = gson.toJson(logEntry);

        // Append log entry to log file
        appendToLogFile(jsonLogEntry);
        backupLogger.log(playerName, dimension, x, y, z);

        // Log to console if enabled
        if (logToConsole) {
            getLogger().info(playerName + " opened a container at " + x + ", " + y + ", " + z + " in the " + dimension);
        }
    }

    private String getDimension(String worldName) {
        if (worldName.endsWith("_nether")) {
            return "Nether";
        } else if (worldName.endsWith("_the_end")) {
            return "End";
        } else {
            return "Overworld";
        }
    }

    private void appendToLogFile(String logEntry) {
        try (FileReader fileReader = new FileReader(logFile);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            StringBuilder fileContents = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                fileContents.append(line);
            }

            // Check if the file is empty or if it contains an empty array
            boolean isEmpty = fileContents.toString().trim().isEmpty();
            boolean isArrayEmpty = fileContents.toString().trim().equals("[]");

            // Modify the file contents based on its current state
            if (isEmpty) {
                // If the file is empty, write an opening square bracket and append the new entry
                try (FileWriter fileWriter = new FileWriter(logFile);
                     BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
                    bufferedWriter.write("[" + logEntry + "]");
                }
            } else if (isArrayEmpty) {
                // If the file contains an empty array, remove the closing bracket and append the new entry
                fileContents.setLength(fileContents.length() - 1);
                try (FileWriter fileWriter = new FileWriter(logFile);
                     BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
                    bufferedWriter.write(fileContents.toString() + logEntry + "]");
                }
            } else {
                // If the file contains a non-empty array, remove the closing bracket, append the new entry, and write the closing bracket
                fileContents.setLength(fileContents.length() - 1);
                try (FileWriter fileWriter = new FileWriter(logFile);
                     BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
                    bufferedWriter.write(fileContents.toString() + "," + logEntry + "]");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void deleteOldEntries() {
        // Get current time
        long currentTime = System.currentTimeMillis();

        // Calculate time threshold (in milliseconds)
        long retentionTime = retentionDays * 24 * 60 * 60 * 1000;

        // Iterate through log file and delete old entries
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "rw")) {
            long length = raf.length();
            if (length == 0) return; // No entries to delete
            long pos = length - 1;
            boolean newline = false;
            do {
                raf.seek(pos);
                byte c = raf.readByte();
                if (c == '\n' && newline) {
                    String line = raf.readLine();
                    String[] parts = line.split(",");
                    String timestampStr = parts[0].substring(14, parts[0].length() - 1);
                    long entryTime = Date.parse(timestampStr);
                    if (currentTime - entryTime > retentionTime) {
                        raf.setLength(pos);
                    }
                }
                newline = c == '\n';
                pos--;
            } while (pos >= 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void displayLogEntries(Player player, int x, int y, int z, String dimension) {
        // Set dimension to "Overworld" if it's null or empty
        if (dimension == null || dimension.isEmpty()) {
            dimension = "Overworld";
        }

        List<LogEntry> logEntries = loadLogEntries(x, y, z, dimension.toLowerCase()); // Convert dimension to lowercase
        if (logEntries.isEmpty()) {
            player.sendMessage("No log entries found for the specified coordinates in dimension " + dimension + ".");
        } else {
            // Sort the log entries based on timestamp in ascending order
            logEntries.sort(Comparator.comparing(entry -> Utils.parseTimestamp(entry.timestamp())));
            Collections.reverse(logEntries);

            // Display the top 5 log entries in reverse order (from newest to oldest)
            int count = Math.min(logEntries.size(), 5);
            player.sendMessage("Last 5 log entries for chest at (" + x + ", " + y + ", " + z + ") in dimension " + dimension + ":");
            for (int i = count - 1; i >= 0; i--) {
                LogEntry logEntry = logEntries.get(i);
                if (logEntry != null) { // Ensure the logEntry is not null
                    player.sendMessage(formatLogEntry(logEntry));
                }
            }
        }
    }


    private List<LogEntry> loadLogEntries(int x, int y, int z, String dimension) {
        List<LogEntry> entries = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            JsonParser parser = new JsonParser();
            JsonArray jsonArray = parser.parse(br).getAsJsonArray();

            Gson gson = new Gson();

            for (JsonElement element : jsonArray) {
                LogEntry logEntry = gson.fromJson(element, LogEntry.class);
                // Check if the log entry matches the provided coordinates and dimension (case-insensitive)
                if (logEntry != null && logEntry.x() == x && logEntry.y() == y && logEntry.z() == z && logEntry.dimension().toLowerCase().equals(dimension)) {
                    entries.add(logEntry);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return entries;
    }




    private String formatLogEntry(LogEntry entry) {
        long timestamp = Date.parse(entry.timestamp());
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - timestamp;
        long hours = elapsedTime / (60 * 60 * 1000);
        long minutes = (elapsedTime % (60 * 60 * 1000)) / (60 * 1000);
        return entry.playerName() + " - " + hours + " hours " + minutes + " minutes ago - " + entry.dimension();
    }


}
