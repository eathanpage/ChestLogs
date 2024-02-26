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
        getLogger().info("Logging to console is currently set to: " + logToConsole);
        // Initialize Gson
        gson = new GsonBuilder().setPrettyPrinting().create();
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
        appendToLogFile(jsonLogEntry, x, y, z, dimension); // Pass the coordinates to create separate log files for each chest
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

    private void appendToLogFile(String logEntry, int x, int y, int z, String dimension) {
        try {
            // Create the "containers" directory if it doesn't exist
            File containersDirectory = new File(getDataFolder(), "containers");
            if (!containersDirectory.exists()) {
                containersDirectory.mkdir();
            }

            File dimensionDirectory = new File(getDataFolder() + "/containers", dimension.toLowerCase());
            if (!dimensionDirectory.exists()) {
                dimensionDirectory.mkdir();
            }

            File chestLogFile = new File(containersDirectory, dimension.toLowerCase() + "/" + x + "_" + y + "_" + z + ".json");

            // Read the existing contents of the file
            StringBuilder fileContents = new StringBuilder();
            if (chestLogFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(chestLogFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        fileContents.append(line);
                    }
                }
            }

            // Remove the closing square bracket if it exists
            if (fileContents.length() > 0 && fileContents.charAt(fileContents.length() - 1) == ']') {
                fileContents.deleteCharAt(fileContents.length() - 1);
            }

            // Append the new log entry
            if (fileContents.length() > 0) {
                fileContents.append(",").append(logEntry);
            } else {
                fileContents.append("[").append(logEntry);
            }

            // Add the closing square bracket
            fileContents.append("]");

            // Write the updated contents back to the file
            try (FileWriter writer = new FileWriter(chestLogFile);
                 BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
                bufferedWriter.write(fileContents.toString());
            }
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
        File containersDirectory = new File(getDataFolder(), "containers");
        File dimensionDirectory = new File(containersDirectory, dimension);
        File chestLogFile = new File(dimensionDirectory, x + "_" + y + "_" + z + ".json");

        // Check if the file exists before attempting to read it
        if (!chestLogFile.exists()) {
            return entries; // Return an empty list if the file doesn't exist
        }

        try (BufferedReader br = new BufferedReader(new FileReader(chestLogFile))) {
            StringBuilder fileContents = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                fileContents.append(line);
            }

            // Parse the entire file contents as a JSON array
            JsonArray jsonArray = JsonParser.parseString(fileContents.toString()).getAsJsonArray();
            Gson gson = new Gson();

            // Iterate over the JSON array and deserialize LogEntry objects
            for (JsonElement element : jsonArray) {
                LogEntry logEntry = gson.fromJson(element, LogEntry.class);
                // Check if the log entry matches the provided coordinates and dimension (case-insensitive)
                if (logEntry != null && logEntry.x() == x && logEntry.y() == y && logEntry.z() == z && logEntry.dimension().equalsIgnoreCase(dimension)) {
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
