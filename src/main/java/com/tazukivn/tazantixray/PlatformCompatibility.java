package com.tazukivn.tazantixray;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Platform compatibility layer for handling different server implementations
 * Supports both Folia (region-based threading) and Paper/Spigot (traditional threading)
 */
public class PlatformCompatibility {
    
    private static Boolean isFolia = null;
    private static Boolean hasGlobalRegionScheduler = null;
    private static Boolean hasRegionScheduler = null;
    
    /**
     * Detect if the server is running Folia
     */
    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (ClassNotFoundException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }
    
    /**
     * Check if GlobalRegionScheduler is available
     */
    public static boolean hasGlobalRegionScheduler() {
        if (hasGlobalRegionScheduler == null) {
            try {
                Method method = Bukkit.class.getMethod("getGlobalRegionScheduler");
                hasGlobalRegionScheduler = method != null;
            } catch (NoSuchMethodException e) {
                hasGlobalRegionScheduler = false;
            }
        }
        return hasGlobalRegionScheduler;
    }
    
    /**
     * Check if RegionScheduler is available
     */
    public static boolean hasRegionScheduler() {
        if (hasRegionScheduler == null) {
            try {
                Method method = Bukkit.class.getMethod("getRegionScheduler");
                hasRegionScheduler = method != null;
            } catch (NoSuchMethodException e) {
                hasRegionScheduler = false;
            }
        }
        return hasRegionScheduler;
    }
    
    /**
     * Run a task on the appropriate scheduler
     * For Folia: Uses GlobalRegionScheduler
     * For Paper/Spigot: Uses BukkitScheduler
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (isFolia() && hasGlobalRegionScheduler()) {
            try {
                // Use Folia's GlobalRegionScheduler
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runMethod = globalScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                runMethod.invoke(globalScheduler, plugin, (Consumer<Object>) scheduledTask -> task.run());
            } catch (Exception e) {
                // Fallback to Bukkit scheduler if Folia method fails
                plugin.getLogger().warning("Failed to use Folia GlobalRegionScheduler, falling back to Bukkit scheduler: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            // Use traditional Bukkit scheduler for Paper/Spigot
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Run a task later on the appropriate scheduler
     */
    public static BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        if (isFolia() && hasGlobalRegionScheduler()) {
            try {
                // Use Folia's GlobalRegionScheduler with delay
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runDelayedMethod = globalScheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                Object scheduledTask = runDelayedMethod.invoke(globalScheduler, plugin, (Consumer<Object>) st -> task.run(), delay);
                
                // Return a dummy BukkitTask for compatibility
                return new FoliaTaskWrapper(scheduledTask);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to use Folia GlobalRegionScheduler for delayed task, falling back to Bukkit scheduler: " + e.getMessage());
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            }
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }
    
    /**
     * Run a task at a specific location (region-aware for Folia)
     */
    public static void runTask(Plugin plugin, Location location, Runnable task) {
        if (isFolia() && hasRegionScheduler()) {
            try {
                // Use Folia's RegionScheduler
                Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
                Method runMethod = regionScheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
                runMethod.invoke(regionScheduler, plugin, location, (Consumer<Object>) scheduledTask -> task.run());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to use Folia RegionScheduler, falling back to Bukkit scheduler: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            // For Paper/Spigot, just run the task normally
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Check if current thread owns the region for the given location
     * For Folia: Uses Bukkit.isOwnedByCurrentRegion()
     * For Paper/Spigot: Always returns true (single-threaded)
     */
    public static boolean isOwnedByCurrentRegion(Location location) {
        if (isFolia()) {
            try {
                Method method = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
                return (Boolean) method.invoke(null, location);
            } catch (Exception e) {
                // If method doesn't exist or fails, assume we own the region
                return true;
            }
        } else {
            // Paper/Spigot is single-threaded, so we always "own" the region
            return true;
        }
    }
    
    /**
     * Get platform information for debugging
     */
    public static String getPlatformInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Platform: ").append(isFolia() ? "Folia" : "Paper/Spigot");
        info.append(", GlobalRegionScheduler: ").append(hasGlobalRegionScheduler());
        info.append(", RegionScheduler: ").append(hasRegionScheduler());
        info.append(", Server Version: ").append(Bukkit.getVersion());
        return info.toString();
    }
    
    /**
     * Wrapper class for Folia tasks to provide BukkitTask compatibility
     */
    private static class FoliaTaskWrapper implements BukkitTask {
        private final Object foliaTask;
        
        public FoliaTaskWrapper(Object foliaTask) {
            this.foliaTask = foliaTask;
        }
        
        @Override
        public int getTaskId() {
            return -1; // Folia tasks don't have traditional IDs
        }
        
        @Override
        public Plugin getOwner() {
            return null; // Not easily accessible from Folia task
        }
        
        @Override
        public boolean isSync() {
            return true; // Folia tasks are always sync within their region
        }
        
        @Override
        public boolean isCancelled() {
            try {
                Method method = foliaTask.getClass().getMethod("isCancelled");
                return (Boolean) method.invoke(foliaTask);
            } catch (Exception e) {
                return false;
            }
        }
        
        @Override
        public void cancel() {
            try {
                Method method = foliaTask.getClass().getMethod("cancel");
                method.invoke(foliaTask);
            } catch (Exception e) {
                // Ignore if cancel method doesn't exist
            }
        }
    }
}
