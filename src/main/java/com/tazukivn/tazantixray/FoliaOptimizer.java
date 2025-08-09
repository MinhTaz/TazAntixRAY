package com.tazukivn.tazantixray;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * Folia-specific optimizations for region-based threading
 * Handles chunk operations efficiently across different regions
 */
public class FoliaOptimizer {
    
    private final Plugin plugin;
    private final Map<String, Queue<ChunkRefreshTask>> regionQueues = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegionSwitch = new ConcurrentHashMap<>();
    private static final long REGION_SWITCH_COOLDOWN = 100; // ms
    
    public FoliaOptimizer(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Optimized chunk refresh for Folia
     * Batches chunk operations by region to minimize cross-region calls
     */
    public void refreshChunksOptimized(Player player, int radiusChunks) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        
        int playerChunkX = loc.getBlockX() >> 4;
        int playerChunkZ = loc.getBlockZ() >> 4;
        
        // Check if player recently switched regions to avoid spam
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastSwitch = lastRegionSwitch.get(playerId);
        if (lastSwitch != null && (currentTime - lastSwitch) < REGION_SWITCH_COOLDOWN) {
            return; // Skip refresh if too recent
        }
        
        // Group chunks by region ownership
        Map<String, Queue<ChunkRefreshTask>> regionTasks = new ConcurrentHashMap<>();
        
        for (int cx = playerChunkX - radiusChunks; cx <= playerChunkX + radiusChunks; cx++) {
            for (int cz = playerChunkZ - radiusChunks; cz <= playerChunkZ + radiusChunks; cz++) {
                Location chunkLoc = new Location(world, cx * 16, loc.getY(), cz * 16);
                String regionKey = getRegionKey(chunkLoc);
                
                regionTasks.computeIfAbsent(regionKey, k -> new ConcurrentLinkedQueue<>())
                          .offer(new ChunkRefreshTask(world, cx, cz, chunkLoc));
            }
        }
        
        // Execute tasks grouped by region
        for (Map.Entry<String, Queue<ChunkRefreshTask>> entry : regionTasks.entrySet()) {
            Queue<ChunkRefreshTask> tasks = entry.getValue();
            if (!tasks.isEmpty()) {
                ChunkRefreshTask firstTask = tasks.peek();
                if (firstTask != null) {
                    scheduleRegionTasks(firstTask.location, tasks);
                }
            }
        }
        
        lastRegionSwitch.put(playerId, currentTime);
    }
    
    /**
     * Schedule chunk refresh tasks for a specific region
     */
    private void scheduleRegionTasks(Location regionLocation, Queue<ChunkRefreshTask> tasks) {
        PlatformCompatibility.runTask(plugin, regionLocation, () -> {
            int processed = 0;
            final int maxBatchSize = 25; // Limit batch size to prevent lag
            
            while (!tasks.isEmpty() && processed < maxBatchSize) {
                ChunkRefreshTask task = tasks.poll();
                if (task != null) {
                    try {
                        if (PlatformCompatibility.isOwnedByCurrentRegion(task.location)) {
                            task.world.refreshChunk(task.chunkX, task.chunkZ);
                            processed++;
                        } else {
                            // Re-queue if region ownership changed
                            tasks.offer(task);
                            break;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to refresh chunk at " + 
                            task.chunkX + "," + task.chunkZ + ": " + e.getMessage());
                    }
                }
            }
            
            // If there are remaining tasks, schedule them for next tick
            if (!tasks.isEmpty()) {
                PlatformCompatibility.runTaskLater(plugin, () -> 
                    scheduleRegionTasks(regionLocation, tasks), 1L);
            }
        });
    }
    
    /**
     * Get a region key for grouping chunks
     */
    private String getRegionKey(Location location) {
        // Simple region key based on chunk coordinates
        // Folia uses 512x512 block regions (32x32 chunks)
        int regionX = (location.getBlockX() >> 9); // Divide by 512
        int regionZ = (location.getBlockZ() >> 9); // Divide by 512
        return location.getWorld().getName() + ":" + regionX + ":" + regionZ;
    }
    
    /**
     * Optimized player state handling for Folia
     */
    public void handlePlayerStateOptimized(Player player, boolean newHiddenState, Runnable callback) {
        Location playerLoc = player.getLocation();
        
        // Check if we're on the correct region thread
        if (!PlatformCompatibility.isOwnedByCurrentRegion(playerLoc)) {
            // Schedule on correct region
            PlatformCompatibility.runTask(plugin, playerLoc, () -> {
                handlePlayerStateOptimized(player, newHiddenState, callback);
            });
            return;
        }
        
        // Execute state change on correct region
        try {
            callback.run();
        } catch (Exception e) {
            plugin.getLogger().warning("Error handling player state for " + 
                player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Optimized teleport handling for Folia
     */
    public void handleTeleportOptimized(Player player, Location from, Location to, Runnable teleportAction) {
        // Check if teleport crosses regions
        boolean sameRegion = getRegionKey(from).equals(getRegionKey(to));
        
        if (sameRegion) {
            // Same region, execute immediately if we own it
            if (PlatformCompatibility.isOwnedByCurrentRegion(to)) {
                teleportAction.run();
            } else {
                PlatformCompatibility.runTask(plugin, to, teleportAction);
            }
        } else {
            // Cross-region teleport, add small delay to ensure proper handling
            PlatformCompatibility.runTaskLater(plugin, () -> {
                PlatformCompatibility.runTask(plugin, to, teleportAction);
            }, 1L);
        }
    }
    
    /**
     * Clean up optimization data for a player
     */
    public void cleanupPlayer(UUID playerId) {
        lastRegionSwitch.remove(playerId);
    }
    
    /**
     * Get optimization statistics
     */
    public String getOptimizationStats() {
        return String.format("Region queues: %d, Tracked players: %d", 
            regionQueues.size(), lastRegionSwitch.size());
    }
    
    /**
     * Task for chunk refresh operations
     */
    private static class ChunkRefreshTask {
        final World world;
        final int chunkX;
        final int chunkZ;
        final Location location;
        
        ChunkRefreshTask(World world, int chunkX, int chunkZ, Location location) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.location = location;
        }
    }
    
    /**
     * Check if Folia optimizations should be used
     */
    public static boolean shouldUseFoliaOptimizations() {
        return PlatformCompatibility.isFolia() && 
               PlatformCompatibility.hasRegionScheduler() && 
               PlatformCompatibility.hasGlobalRegionScheduler();
    }
    
    /**
     * Periodic cleanup of optimization data
     */
    public void performPeriodicCleanup() {
        long currentTime = System.currentTimeMillis();
        long cleanupThreshold = currentTime - (5 * 60 * 1000); // 5 minutes
        
        lastRegionSwitch.entrySet().removeIf(entry -> entry.getValue() < cleanupThreshold);
        
        // Clear empty region queues
        regionQueues.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
