package com.tazukivn.tazantixray;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/**
 * Message formatter for beautiful and consistent plugin messages
 * Handles color codes properly for both console and players
 */
public class MessageFormatter {
    
    // Color scheme
    private static final String PRIMARY = "§3";      // Dark Aqua
    private static final String SECONDARY = "§b";    // Aqua  
    private static final String ACCENT = "§e";       // Yellow
    private static final String SUCCESS = "§a";      // Green
    private static final String WARNING = "§6";      // Gold
    private static final String ERROR = "§c";        // Red
    private static final String INFO = "§7";         // Gray
    private static final String RESET = "§r";        // Reset
    private static final String BOLD = "§l";         // Bold
    
    // Plugin branding
    private static final String PLUGIN_NAME = PRIMARY + BOLD + "TazAntixRAY" + RESET;
    private static final String PREFIX = "[" + PLUGIN_NAME + PRIMARY + "]" + RESET + " ";
    private static final String CONSOLE_PREFIX = "[TazAntixRAY] ";
    
    /**
     * Format a message for display
     */
    public static String format(String message) {
        return translateColorCodes(message);
    }
    
    /**
     * Send a formatted message to a command sender
     */
    public static void send(CommandSender sender, String message) {
        if (sender instanceof ConsoleCommandSender) {
            // For console, use plain text without color codes
            sender.sendMessage(CONSOLE_PREFIX + stripColorCodes(message));
        } else {
            // For players, use full color formatting
            sender.sendMessage(PREFIX + translateColorCodes(message));
        }
    }
    
    /**
     * Send an info message
     */
    public static void sendInfo(CommandSender sender, String message) {
        send(sender, INFO + message);
    }
    
    /**
     * Send a success message
     */
    public static void sendSuccess(CommandSender sender, String message) {
        send(sender, SUCCESS + message);
    }
    
    /**
     * Send a warning message
     */
    public static void sendWarning(CommandSender sender, String message) {
        send(sender, WARNING + message);
    }
    
    /**
     * Send an error message
     */
    public static void sendError(CommandSender sender, String message) {
        send(sender, ERROR + message);
    }
    
    /**
     * Create simple and clean startup messages
     */
    public static String[] createStartupBanner(String version, String platform) {
        return new String[] {
            "",
            SUCCESS + "TazAntixRAY " + ACCENT + "v" + version + SUCCESS + " - Advanced Anti-XRay Protection",
            INFO + "Platform: " + SECONDARY + platform + INFO + " | Minecraft: " + SECONDARY + "1.20-1.21.8+",
            SUCCESS + "Multi-platform support: " + SECONDARY + "Folia, Paper, Spigot, Geyser",
            ACCENT + "GitHub: " + SECONDARY + "https://github.com/MinhTaz/TazAntixRAY",
            SUCCESS + "Protection Status: " + ACCENT + "ACTIVE",
            ""
        };
    }
    
    /**
     * Create simple and clean shutdown messages
     */
    public static String[] createShutdownBanner() {
        return new String[] {
            "",
            WARNING + "TazAntixRAY " + INFO + "- Shutting down...",
            INFO + "All anti-xray protections have been safely disabled.",
            SUCCESS + "Thank you for using TazAntixRAY!",
            ""
        };
    }
    
    /**
     * Create loading messages with colors
     */
    public static String createLoadingMessage(String component, String status) {
        String statusColor = status.equals("LOADING") ? ACCENT :
                           status.equals("SUCCESS") ? SUCCESS :
                           status.equals("FAILED") ? ERROR : INFO;
        return ACCENT + "  ⚡ " + INFO + component + ": " + statusColor + status;
    }

    /**
     * Create platform detection message
     */
    public static String createPlatformMessage(String platformInfo) {
        return ACCENT + "Platform Detection: " + SUCCESS + platformInfo;
    }
    
    /**
     * Create world activation message
     */
    public static String createWorldMessage(String worlds) {
        if (worlds.isEmpty() || worlds.equals("[]")) {
            return WARNING + "No worlds configured for anti-xray protection";
        }
        return SUCCESS + "Active in worlds: " + SECONDARY + worlds;
    }
    
    /**
     * Create debug mode message
     */
    public static String createDebugMessage(boolean enabled) {
        String status = enabled ? SUCCESS + "ENABLED" : INFO + "DISABLED";
        return ACCENT + "Debug Mode: " + status;
    }
    
    /**
     * Translate color codes from & to §
     */
    private static String translateColorCodes(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Strip all color codes from a message
     */
    public static String stripColorCodes(String message) {
        return ChatColor.stripColor(translateColorCodes(message));
    }
    
    /**
     * Create a separator line
     */
    public static String createSeparator() {
        return PRIMARY + "════════════════════════════════════════";
    }
    
    /**
     * Create a command usage message
     */
    public static String createUsage(String command, String usage, String description) {
        return ACCENT + "/" + command + " " + INFO + usage + "\n" + 
               "  " + SECONDARY + description;
    }
    
    /**
     * Create a permission denied message
     */
    public static String createPermissionDenied() {
        return ERROR + "You don't have permission to use this command!";
    }
    
    /**
     * Create a reload success message
     */
    public static String createReloadSuccess() {
        return SUCCESS + "Configuration reloaded successfully!";
    }
    
    /**
     * Create a player not found message
     */
    public static String createPlayerNotFound(String playerName) {
        return ERROR + "Player '" + WARNING + playerName + ERROR + "' not found!";
    }
}
