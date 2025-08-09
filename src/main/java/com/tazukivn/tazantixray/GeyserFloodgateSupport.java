package com.tazukivn.tazantixray;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Support for Geyser and Floodgate compatibility
 * Handles Bedrock Edition players through Geyser/Floodgate
 */
public class GeyserFloodgateSupport {
    
    private static boolean geyserAvailable = false;
    private static boolean floodgateAvailable = false;
    private static Object geyserApi = null;
    private static Object floodgateApi = null;
    private static Method isBedrockPlayerMethod = null;
    private static Method getBedrockPlayersMethod = null;
    private static Method floodgateIsBedrockMethod = null;
    
    private final Plugin plugin;
    private final Set<UUID> bedrockPlayers = ConcurrentHashMap.newKeySet();
    
    public GeyserFloodgateSupport(Plugin plugin) {
        this.plugin = plugin;
        initializeSupport();
    }
    
    /**
     * Initialize Geyser and Floodgate support
     */
    private void initializeSupport() {
        // Check for Geyser
        Plugin geyserPlugin = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if (geyserPlugin == null) {
            geyserPlugin = Bukkit.getPluginManager().getPlugin("Geyser");
        }
        
        if (geyserPlugin != null && geyserPlugin.isEnabled()) {
            try {
                // Try to get Geyser API
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Method getInstanceMethod = geyserApiClass.getMethod("api");
                geyserApi = getInstanceMethod.invoke(null);
                
                // Get isBedrockPlayer method
                isBedrockPlayerMethod = geyserApiClass.getMethod("isBedrockPlayer", UUID.class);
                
                geyserAvailable = true;
                plugin.getLogger().info("Geyser support automatically enabled");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to initialize Geyser support: " + e.getMessage());
            }
        }
        
        // Check for Floodgate
        Plugin floodgatePlugin = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgatePlugin != null && floodgatePlugin.isEnabled()) {
            try {
                // Try to get Floodgate API
                Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Method getInstanceMethod = floodgateApiClass.getMethod("getInstance");
                floodgateApi = getInstanceMethod.invoke(null);
                
                // Get isFloodgatePlayer method
                floodgateIsBedrockMethod = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
                
                floodgateAvailable = true;
                plugin.getLogger().info("Floodgate support automatically enabled");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to initialize Floodgate support: " + e.getMessage());
            }
        }
        
        if (!geyserAvailable && !floodgateAvailable) {
            plugin.getLogger().info("Geyser/Floodgate not detected - Bedrock player support disabled");
        }
    }
    
    /**
     * Check if a player is a Bedrock Edition player
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        
        UUID playerId = player.getUniqueId();
        
        // Check cache first
        if (bedrockPlayers.contains(playerId)) {
            return true;
        }
        
        boolean isBedrock = false;
        
        // Try Geyser first
        if (geyserAvailable && geyserApi != null && isBedrockPlayerMethod != null) {
            try {
                isBedrock = (Boolean) isBedrockPlayerMethod.invoke(geyserApi, playerId);
            } catch (Exception e) {
                // Ignore and try Floodgate
            }
        }
        
        // Try Floodgate if Geyser didn't work
        if (!isBedrock && floodgateAvailable && floodgateApi != null && floodgateIsBedrockMethod != null) {
            try {
                isBedrock = (Boolean) floodgateIsBedrockMethod.invoke(floodgateApi, playerId);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Cache result
        if (isBedrock) {
            bedrockPlayers.add(playerId);
        }
        
        return isBedrock;
    }
    
    /**
     * Check if a player is a Bedrock Edition player by UUID
     */
    public boolean isBedrockPlayer(UUID playerId) {
        if (playerId == null) return false;
        
        // Check cache first
        if (bedrockPlayers.contains(playerId)) {
            return true;
        }
        
        boolean isBedrock = false;
        
        // Try Geyser first
        if (geyserAvailable && geyserApi != null && isBedrockPlayerMethod != null) {
            try {
                isBedrock = (Boolean) isBedrockPlayerMethod.invoke(geyserApi, playerId);
            } catch (Exception e) {
                // Ignore and try Floodgate
            }
        }
        
        // Try Floodgate if Geyser didn't work
        if (!isBedrock && floodgateAvailable && floodgateApi != null && floodgateIsBedrockMethod != null) {
            try {
                isBedrock = (Boolean) floodgateIsBedrockMethod.invoke(floodgateApi, playerId);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Cache result
        if (isBedrock) {
            bedrockPlayers.add(playerId);
        }
        
        return isBedrock;
    }
    
    /**
     * Get special handling for Bedrock players
     * Bedrock players might need different anti-xray settings
     */
    public boolean shouldApplyBedrockOptimizations(Player player) {
        if (!isBedrockPlayer(player)) {
            return false;
        }
        
        // Bedrock players might have different rendering capabilities
        // You can customize this based on your needs
        return true;
    }
    
    /**
     * Get optimized chunk radius for Bedrock players
     * Bedrock players might need smaller radius due to different chunk loading
     */
    public int getOptimizedChunkRadius(Player player, int defaultRadius) {
        if (isBedrockPlayer(player)) {
            // Reduce radius for Bedrock players to improve performance
            return Math.max(1, defaultRadius - 1);
        }
        return defaultRadius;
    }
    
    /**
     * Check if Bedrock player needs special packet handling
     */
    public boolean needsSpecialPacketHandling(Player player) {
        return isBedrockPlayer(player);
    }
    
    /**
     * Clean up player data when they leave
     */
    public void cleanupPlayer(UUID playerId) {
        bedrockPlayers.remove(playerId);
    }
    
    /**
     * Get support status
     */
    public String getSupportStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Geyser: ").append(geyserAvailable ? "✓" : "✗");
        status.append(", Floodgate: ").append(floodgateAvailable ? "✓" : "✗");
        status.append(", Bedrock Players: ").append(bedrockPlayers.size());
        return status.toString();
    }
    
    /**
     * Check if any Geyser/Floodgate support is available
     */
    public boolean isSupported() {
        return geyserAvailable || floodgateAvailable;
    }
    
    /**
     * Get count of currently tracked Bedrock players
     */
    public int getBedrockPlayerCount() {
        return bedrockPlayers.size();
    }
    
    /**
     * Force refresh a player's Bedrock status
     */
    public void refreshPlayerStatus(Player player) {
        if (player != null) {
            bedrockPlayers.remove(player.getUniqueId());
            isBedrockPlayer(player); // This will re-check and cache
        }
    }
    
    /**
     * Periodic cleanup of offline players
     */
    public void performPeriodicCleanup() {
        bedrockPlayers.removeIf(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            return player == null || !player.isOnline();
        });
    }
}
