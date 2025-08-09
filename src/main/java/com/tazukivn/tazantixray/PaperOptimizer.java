package com.tazukivn.tazantixray;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * Paper/Spigot-specific optimizations for traditional single-threaded model
 * Focuses on efficient batching and async operations where possible
 */
public class PaperOptimizer {
    
    private final Plugin plugin;
    private final Map<UUID, Long> lastRefresh = new ConcurrentHashMap<>();
    private final Queue<ChunkRefreshTask> refreshQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask refreshTask;
    private static final long REFRESH_COOLDOWN = 50; // ms
    private static final int MAX_CHUNKS_PER_TICK = 30;
    
    public PaperOptimizer(Plugin plugin) {
        this.plugin = plugin;
        startRefreshProcessor();
    }
    
    /**
     * Start the chunk refresh processor
     */
    private void startRefreshProcessor() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            processRefreshQueue();
        }, 1L, 1L); // Run every tick
    }
    
    /**
     * Optimized chunk refresh for Paper/Spigot
     * Uses batching and cooldowns to prevent lag
     */
    public void refreshChunksOptimized(Player player, int radiusChunks) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Check cooldown
        Long lastRefreshTime = lastRefresh.get(playerId);
        if (lastRefreshTime != null && (currentTime - lastRefreshTime) < REFRESH_COOLDOWN) {
            return; // Skip if too recent
        }
        
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        
        int playerChunkX = loc.getBlockX() >> 4;
        int playerChunkZ = loc.getBlockZ() >> 4;
        
        // Add chunks to refresh queue with priority for closer chunks
        for (int radius = 0; radius <= radiusChunks; radius++) {
            for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
                for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                    // Only add border chunks for each radius to avoid duplicates
                    if (Math.abs(cx - playerChunkX) == radius || Math.abs(cz - playerChunkZ) == radius) {
                        refreshQueue.offer(new ChunkRefreshTask(world, cx, cz, playerId, radius));
                    }
                }
            }
        }
        
        lastRefresh.put(playerId, currentTime);
    }
    
    /**
     * Process the chunk refresh queue
     */
    private void processRefreshQueue() {
        int processed = 0;
        
        while (!refreshQueue.isEmpty() && processed < MAX_CHUNKS_PER_TICK) {
            ChunkRefreshTask task = refreshQueue.poll();
            if (task != null) {
                try {
                    // Check if player is still online and in same world
                    Player player = Bukkit.getPlayer(task.playerId);
                    if (player != null && player.isOnline() && 
                        player.getWorld().equals(task.world)) {
                        
                        // Only refresh if chunk is loaded to avoid loading new chunks
                        if (task.world.isChunkLoaded(task.chunkX, task.chunkZ)) {
                            task.world.refreshChunk(task.chunkX, task.chunkZ);
                            processed++;
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to refresh chunk at " + 
                        task.chunkX + "," + task.chunkZ + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Optimized player state handling for Paper/Spigot
     */
    public void handlePlayerStateOptimized(Player player, boolean newHiddenState, Runnable callback) {
        // For Paper/Spigot, we can execute immediately on main thread
        if (Bukkit.isPrimaryThread()) {
            try {
                callback.run();
            } catch (Exception e) {
                plugin.getLogger().warning("Error handling player state for " + 
                    player.getName() + ": " + e.getMessage());
            }
        } else {
            // Schedule on main thread if called from async context
            Bukkit.getScheduler().runTask(plugin, callback);
        }
    }
    
    /**
     * Optimized teleport handling for Paper/Spigot
     */
    public void handleTeleportOptimized(Player player, Location from, Location to, Runnable teleportAction) {
        // For Paper/Spigot, add small delay to ensure chunk loading
        Bukkit.getScheduler().runTaskLater(plugin, teleportAction, 1L);
    }
    
    /**
     * Batch refresh for multiple players
     */
    public void batchRefreshForPlayers(Iterable<Player> players, int radiusChunks) {
        // Group players by world for efficient processing
        Map<World, Queue<Player>> worldGroups = new ConcurrentHashMap<>();
        
        for (Player player : players) {
            World world = player.getWorld();
            worldGroups.computeIfAbsent(world, k -> new ConcurrentLinkedQueue<>()).offer(player);
        }
        
        // Process each world group
        for (Map.Entry<World, Queue<Player>> entry : worldGroups.entrySet()) {
            World world = entry.getKey();
            Queue<Player> playersInWorld = entry.getValue();
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : playersInWorld) {
                    if (player.isOnline() && player.getWorld().equals(world)) {
                        refreshChunksOptimized(player, radiusChunks);
                    }
                }
            }, 1L);
        }
    }
    
    /**
     * Async chunk pre-loading for better performance
     */
    public void preloadChunksAsync(Player player, int radiusChunks) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        
        int playerChunkX = loc.getBlockX() >> 4;
        int playerChunkZ = loc.getBlockZ() >> 4;
        
        // Use async task for chunk loading checks
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (int cx = playerChunkX - radiusChunks; cx <= playerChunkX + radiusChunks; cx++) {
                for (int cz = playerChunkZ - radiusChunks; cz <= playerChunkZ + radiusChunks; cz++) {
                    final int finalCx = cx;
                    final int finalCz = cz;
                    
                    // Schedule chunk loading on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && player.getWorld().equals(world)) {
                            if (!world.isChunkLoaded(finalCx, finalCz)) {
                                world.loadChunk(finalCx, finalCz, false);
                            }
                        }
                    });
                }
            }
        });
    }
    
    /**
     * Clean up optimization data for a player
     */
    public void cleanupPlayer(UUID playerId) {
        lastRefresh.remove(playerId);
        // Remove player's tasks from queue
        refreshQueue.removeIf(task -> task.playerId.equals(playerId));
    }
    
    /**
     * Get optimization statistics
     */
    public String getOptimizationStats() {
        return String.format("Refresh queue size: %d, Tracked players: %d", 
            refreshQueue.size(), lastRefresh.size());
    }
    
    /**
     * Shutdown the optimizer
     */
    public void shutdown() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
        }
        refreshQueue.clear();
        lastRefresh.clear();
    }
    
    /**
     * Task for chunk refresh operations
     */
    private static class ChunkRefreshTask {
        final World world;
        final int chunkX;
        final int chunkZ;
        final UUID playerId;
        final int priority; // Lower number = higher priority
        
        ChunkRefreshTask(World world, int chunkX, int chunkZ, UUID playerId, int priority) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.playerId = playerId;
            this.priority = priority;
        }
    }
    
    /**
     * Check if Paper optimizations should be used
     */
    public static boolean shouldUsePaperOptimizations() {
        return !PlatformCompatibility.isFolia();
    }
    
    /**
     * Periodic cleanup of optimization data
     */
    public void performPeriodicCleanup() {
        long currentTime = System.currentTimeMillis();
        long cleanupThreshold = currentTime - (5 * 60 * 1000); // 5 minutes
        
        lastRefresh.entrySet().removeIf(entry -> entry.getValue() < cleanupThreshold);
        
        // Limit queue size to prevent memory issues
        while (refreshQueue.size() > 1000) {
            refreshQueue.poll();
        }
    }
    
    /**
     * Smart refresh that adapts to server load
     */
    public void smartRefresh(Player player, int radiusChunks) {
        // Check server TPS and adjust refresh rate accordingly
        double tps = getCurrentTPS();
        
        if (tps > 18.0) {
            // High TPS, can afford more aggressive refreshing
            refreshChunksOptimized(player, radiusChunks);
        } else if (tps > 15.0) {
            // Medium TPS, reduce refresh rate
            refreshChunksOptimized(player, Math.max(1, radiusChunks - 1));
        } else {
            // Low TPS, minimal refreshing
            refreshChunksOptimized(player, 1);
        }
    }
    
    /**
     * Get current server TPS (simplified)
     */
    private double getCurrentTPS() {
        try {
            // Try to get TPS from server if available
            Object server = Bukkit.getServer();
            if (server.getClass().getSimpleName().contains("CraftServer")) {
                // This is a simplified approach - in real implementation,
                // you might want to use reflection to get actual TPS
                return 20.0; // Default assumption
            }
        } catch (Exception e) {
            // Fallback
        }
        return 20.0;
    }
}
