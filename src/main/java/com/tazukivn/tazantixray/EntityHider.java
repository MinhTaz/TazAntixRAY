package com.tazukivn.tazantixray;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/**
 * Handles hiding entities in underground areas for anti-xray protection
 */
public class EntityHider implements Listener {
    
    private final Plugin plugin;
    private final Set<UUID> hiddenEntitiesForPlayer = ConcurrentHashMap.newKeySet();
    private boolean entityHidingEnabled = true;
    private int hideBelow = 16;
    private int protectionLevel = 31;
    
    public EntityHider(Plugin plugin) {
        this.plugin = plugin;
        loadSettings();

        // Register this as an event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Load settings from config
     */
    public void loadSettings() {
        entityHidingEnabled = plugin.getConfig().getBoolean("performance.entities.hide-entities", true);
        hideBelow = plugin.getConfig().getInt("antixray.hide-below-y", 16);
        protectionLevel = plugin.getConfig().getInt("antixray.protection-y-level", 31);
    }
    
    /**
     * Check if entity hiding is enabled
     */
    public boolean isEntityHidingEnabled() {
        return entityHidingEnabled;
    }
    
    /**
     * Check if entities should be hidden for a player at their current location
     */
    public boolean shouldHideEntities(Player player) {
        if (!entityHidingEnabled) {
            return false;
        }
        
        Location loc = player.getLocation();
        double playerY = loc.getY();
        
        // Hide entities when player is above protection level
        // and entities are below hideBelow level
        return playerY > protectionLevel;
    }
    
    /**
     * Check if a specific entity should be hidden from a player
     */
    public boolean shouldHideEntity(Player player, Entity entity) {
        if (!shouldHideEntities(player)) {
            return false;
        }
        
        Location entityLoc = entity.getLocation();
        double entityY = entityLoc.getY();
        
        // Hide entities that are below the hide-below-y level
        return entityY <= hideBelow;
    }
    
    /**
     * Hide entities for a player in their view area
     */
    public void hideEntitiesForPlayer(Player player) {
        if (!shouldHideEntities(player)) {
            return;
        }

        // Use platform-compatible scheduler for proper timing
        PlatformCompatibility.runTask(plugin, () -> {
            Location playerLoc = player.getLocation();
            int viewDistance = getViewDistance(player);

            // Get entities in player's view area
            List<Entity> nearbyEntities = getNearbyEntities(player, viewDistance);

            for (Entity entity : nearbyEntities) {
                if (shouldHideEntity(player, entity)) {
                    hideEntityFromPlayer(player, entity);
                }
            }
        });
    }
    
    /**
     * Show all hidden entities for a player
     */
    public void showAllEntitiesForPlayer(Player player) {
        // Use platform-compatible scheduler for proper timing
        PlatformCompatibility.runTask(plugin, () -> {
            Location playerLoc = player.getLocation();
            int viewDistance = getViewDistance(player);

            // Get entities in player's view area
            List<Entity> nearbyEntities = getNearbyEntities(player, viewDistance);

            for (Entity entity : nearbyEntities) {
                showEntityToPlayer(player, entity);
            }
        });
    }
    
    /**
     * Update entity visibility for a player based on their current state
     */
    public void updateEntityVisibility(Player player) {
        if (!entityHidingEnabled) {
            return;
        }
        
        boolean shouldHide = shouldHideEntities(player);
        
        if (shouldHide) {
            hideEntitiesForPlayer(player);
        } else {
            showAllEntitiesForPlayer(player);
        }
    }
    
    /**
     * Hide a specific entity from a player
     */
    private void hideEntityFromPlayer(Player player, Entity entity) {
        try {
            // Check if entity is already hidden to avoid duplicate calls
            String key = player.getUniqueId() + ":" + entity.getUniqueId();
            UUID trackingId = UUID.nameUUIDFromBytes(key.getBytes());

            if (!hiddenEntitiesForPlayer.contains(trackingId)) {
                // Use Bukkit's built-in method to hide entity
                player.hideEntity(plugin, entity);

                // Track hidden entity
                hiddenEntitiesForPlayer.add(trackingId);

                plugin.getLogger().fine("Hidden entity " + entity.getType() + " at Y=" +
                    String.format("%.1f", entity.getLocation().getY()) + " from player " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hide entity " + entity.getType() + " from player " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Show a specific entity to a player
     */
    private void showEntityToPlayer(Player player, Entity entity) {
        try {
            // Check if entity was actually hidden before showing
            String key = player.getUniqueId() + ":" + entity.getUniqueId();
            UUID trackingId = UUID.nameUUIDFromBytes(key.getBytes());

            if (hiddenEntitiesForPlayer.contains(trackingId)) {
                // Use Bukkit's built-in method to show entity
                player.showEntity(plugin, entity);

                // Remove from tracking
                hiddenEntitiesForPlayer.remove(trackingId);

                plugin.getLogger().fine("Showed entity " + entity.getType() + " at Y=" +
                    String.format("%.1f", entity.getLocation().getY()) + " to player " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to show entity " + entity.getType() + " to player " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Get nearby entities for a player
     */
    private List<Entity> getNearbyEntities(Player player, int viewDistance) {
        List<Entity> entities = new ArrayList<>();
        
        try {
            Location center = player.getLocation();
            double radius = viewDistance * 16.0; // Convert chunks to blocks
            
            // Get entities in the area
            entities.addAll(center.getWorld().getNearbyEntities(center, radius, radius, radius));
            
            // Filter out players (we don't want to hide other players)
            entities.removeIf(entity -> entity instanceof Player);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get nearby entities: " + e.getMessage());
        }
        
        return entities;
    }
    
    /**
     * Get view distance for a player
     */
    private int getViewDistance(Player player) {
        try {
            // Try to get player's view distance
            return player.getClientViewDistance();
        } catch (Exception e) {
            // Fallback to server view distance
            return Bukkit.getViewDistance();
        }
    }
    
    /**
     * Clean up hidden entities for a player when they leave
     */
    public void cleanupPlayer(UUID playerId) {
        // Remove all tracking for this player
        hiddenEntitiesForPlayer.removeIf(uuid -> {
            String uuidStr = uuid.toString();
            return uuidStr.startsWith(playerId.toString());
        });
    }
    
    /**
     * Get statistics about hidden entities
     */
    public String getStatistics() {
        return "Hidden entity mappings: " + hiddenEntitiesForPlayer.size();
    }
    
    /**
     * Check if entity hiding is enabled in config
     */
    public boolean isConfigEnabled() {
        return plugin.getConfig().getBoolean("performance.entities.hide-entities", true) &&
               plugin.getConfig().getBoolean("performance.underground-protection.enabled", true);
    }
    
    /**
     * Refresh entity visibility for all online players
     */
    public void refreshAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateEntityVisibility(player);
        }
    }
    
    /**
     * Handle player movement - update entity visibility if needed
     */
    public void handlePlayerMovement(Player player, Location from, Location to) {
        if (!entityHidingEnabled) {
            return;
        }
        
        double fromY = from.getY();
        double toY = to.getY();
        
        // Check if player crossed the protection threshold
        boolean wasAboveProtection = fromY > protectionLevel;
        boolean isAboveProtection = toY > protectionLevel;
        
        if (wasAboveProtection != isAboveProtection) {
            // Player crossed threshold, update entity visibility
            updateEntityVisibility(player);
        }
    }
    
    /**
     * Handle entity spawn events to immediately hide entities if needed
     */
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!entityHidingEnabled) {
            return;
        }

        Entity entity = event.getEntity();
        Location entityLoc = entity.getLocation();

        // Only process entities below hide level
        if (entityLoc.getY() > hideBelow) {
            return;
        }

        // Hide entity from all players who should not see it
        PlatformCompatibility.runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (shouldHideEntity(player, entity)) {
                    hideEntityFromPlayer(player, entity);
                }
            }
        }, 1L); // Small delay to ensure entity is fully spawned
    }

    /**
     * Periodic cleanup of stale data
     */
    public void performPeriodicCleanup() {
        // Remove mappings for offline players
        Set<UUID> onlinePlayerIds = ConcurrentHashMap.newKeySet();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayerIds.add(player.getUniqueId());
        }

        hiddenEntitiesForPlayer.removeIf(uuid -> {
            String uuidStr = uuid.toString();
            return onlinePlayerIds.stream().noneMatch(playerId ->
                uuidStr.startsWith(playerId.toString()));
        });
    }
}
