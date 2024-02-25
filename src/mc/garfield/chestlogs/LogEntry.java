package mc.garfield.chestlogs;

public record LogEntry(String timestamp, String dimension, int x, int y, int z, String playerName) {
    // Getter method for timestamp
    public String timestamp() {
        return this.timestamp;
    }
}
