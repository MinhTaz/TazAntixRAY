package com.tazukivn.tazantixray;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Platform compatibility layer for Folia vs Spigot/Paper
 * Automatically detects the platform and uses appropriate scheduling methods
 */
public class PlatformCompatibility {
    
    private static Boolean isFolia = null;
    private static Boolean hasRegionScheduler = null;
    private static Boolean hasGlobalRegionScheduler = null;
    
    /**
     * Detect if the server is running Folia
     */
    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                // Try to access Folia-specific classes
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (ClassNotFoundException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }
    
    /**
     * Check if RegionScheduler is available
     */
    public static boolean hasRegionScheduler() {
        if (hasRegionScheduler == null) {
            try {
                Bukkit.class.getMethod("getRegionScheduler");
                hasRegionScheduler = true;
            } catch (NoSuchMethodException e) {
                hasRegionScheduler = false;
            }
        }
        return hasRegionScheduler;
    }
    
    /**
     * Check if GlobalRegionScheduler is available
     */
    public static boolean hasGlobalRegionScheduler() {
        if (hasGlobalRegionScheduler == null) {
            try {
                Bukkit.class.getMethod("getGlobalRegionScheduler");
                hasGlobalRegionScheduler = true;
            } catch (NoSuchMethodException e) {
                hasGlobalRegionScheduler = false;
            }
        }
        return hasGlobalRegionScheduler;
    }
    
    /**
     * Check if a location is owned by the current region (Folia only)
     */
    public static boolean isOwnedByCurrentRegion(Location location) {
        if (!isFolia()) {
            return true; // On Spigot/Paper, everything is on the main thread
        }
        
        try {
            Method method = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            return (Boolean) method.invoke(null, location);
        } catch (Exception e) {
            return true; // Fallback to true if method not available
        }
    }
    
    /**
     * Schedule a task on the appropriate scheduler
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (hasGlobalRegionScheduler()) {
            try {
                Method method = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object scheduler = method.invoke(null);
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Runnable.class);
                runMethod.invoke(scheduler, plugin, task);
                return;
            } catch (Exception e) {
                // Fall back to Bukkit scheduler
            }
        }
        
        // Fallback to traditional Bukkit scheduler
        Bukkit.getScheduler().runTask(plugin, task);
    }
    
    /**
     * Schedule a task at a specific location (Folia) or globally (Spigot/Paper)
     */
    public static void runTaskAtLocation(Plugin plugin, Location location, Runnable task) {
        if (hasRegionScheduler() && location != null) {
            try {
                Method method = Bukkit.class.getMethod("getRegionScheduler");
                Object scheduler = method.invoke(null);
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Location.class, Runnable.class);
                runMethod.invoke(scheduler, plugin, location, task);
                return;
            } catch (Exception e) {
                // Fall back to global task
            }
        }
        
        // Fallback to global task
        runTask(plugin, task);
    }
    
    /**
     * Schedule a delayed task
     */
    public static BukkitTask runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (hasGlobalRegionScheduler()) {
            try {
                Method method = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object scheduler = method.invoke(null);
                Method runLaterMethod = scheduler.getClass().getMethod("runDelayed", Plugin.class, Runnable.class, long.class);
                Object scheduledTask = runLaterMethod.invoke(scheduler, plugin, task, delayTicks);
                // Return a dummy BukkitTask for compatibility
                return new DummyBukkitTask();
            } catch (Exception e) {
                // Fall back to Bukkit scheduler
            }
        }
        
        // Fallback to traditional Bukkit scheduler
        return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
    
    /**
     * Schedule a delayed task at a specific location
     */
    public static void runTaskLaterAtLocation(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (hasRegionScheduler() && location != null) {
            try {
                Method method = Bukkit.class.getMethod("getRegionScheduler");
                Object scheduler = method.invoke(null);
                Method runLaterMethod = scheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class, Runnable.class, long.class);
                runLaterMethod.invoke(scheduler, plugin, location, task, delayTicks);
                return;
            } catch (Exception e) {
                // Fall back to global task
            }
        }
        
        // Fallback to global delayed task
        runTaskLater(plugin, task, delayTicks);
    }
    
    /**
     * Get platform information string
     */
    public static String getPlatformInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Platform: ");
        
        if (isFolia()) {
            info.append("Folia");
        } else {
            info.append("Spigot/Paper");
        }
        
        info.append(" | RegionScheduler: ").append(hasRegionScheduler() ? "Available" : "Not Available");
        info.append(" | GlobalRegionScheduler: ").append(hasGlobalRegionScheduler() ? "Available" : "Not Available");
        
        return info.toString();
    }
    
    /**
     * Dummy BukkitTask implementation for compatibility
     */
    private static class DummyBukkitTask implements BukkitTask {
        @Override
        public int getTaskId() { return -1; }
        
        @Override
        public Plugin getOwner() { return null; }
        
        @Override
        public boolean isSync() { return true; }
        
        @Override
        public boolean isCancelled() { return false; }
        
        @Override
        public void cancel() { }
    }
}
