package com.tazukivn.tazantixray;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Utility class for Anti-XRay functionality
 */
public class AntiXrayUtils {
    
    private static final Random RANDOM = new Random();
    
    // Default entity types to hide
    private static final Set<EntityType> DEFAULT_HIDDEN_ENTITIES = new HashSet<>(Arrays.asList(
        EntityType.ARMOR_STAND,
        EntityType.ITEM_FRAME,
        EntityType.GLOW_ITEM_FRAME,
        EntityType.PAINTING,
        EntityType.MINECART
        // Note: MINECART_CHEST, MINECART_FURNACE, MINECART_HOPPER are deprecated in newer versions
        // They are now handled as MINECART with different data
    ));
    
    /**
     * Check if a chunk is within the limited area around a player
     */
    public static boolean isChunkInLimitedArea(Player player, int chunkX, int chunkZ, int chunkRadius) {
        Location playerLoc = player.getLocation();
        int playerChunkX = playerLoc.getBlockX() >> 4;
        int playerChunkZ = playerLoc.getBlockZ() >> 4;
        
        int deltaX = Math.abs(chunkX - playerChunkX);
        int deltaZ = Math.abs(chunkZ - playerChunkZ);
        
        return deltaX <= chunkRadius && deltaZ <= chunkRadius;
    }
    
    /**
     * Check if a block position is within the limited area around a player
     */
    public static boolean isBlockInLimitedArea(Player player, int blockX, int blockZ, int chunkRadius) {
        Location playerLoc = player.getLocation();
        int playerChunkX = playerLoc.getBlockX() >> 4;
        int playerChunkZ = playerLoc.getBlockZ() >> 4;
        
        int blockChunkX = blockX >> 4;
        int blockChunkZ = blockZ >> 4;
        
        int deltaX = Math.abs(blockChunkX - playerChunkX);
        int deltaZ = Math.abs(blockChunkZ - playerChunkZ);
        
        return deltaX <= chunkRadius && deltaZ <= chunkRadius;
    }
    
    /**
     * Get replacement block state based on configuration
     */
    public static WrappedBlockState getReplacementBlock(TazAntixRAYPlugin plugin, WrappedBlockState airState) {
        String blockType = plugin.getConfig().getString("antixray.replacement.block-type", "air");

        if (blockType.equalsIgnoreCase("deepslate")) {
            try {
                return WrappedBlockState.getByString("minecraft:deepslate");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get deepslate block state, falling back to air");
                return airState;
            }
        }

        if (blockType.equalsIgnoreCase("stone")) {
            try {
                return WrappedBlockState.getByString("minecraft:stone");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get stone block state, falling back to air");
                return airState;
            }
        }

        // Default to air
        return airState;
    }
    
    /**
     * Check if an entity type should be hidden (simplified - hide all common entities)
     */
    public static boolean shouldHideEntity(EntityType entityType) {
        return DEFAULT_HIDDEN_ENTITIES.contains(entityType);
    }

    /**
     * Check if a player should have entities hidden based on their state
     */
    public static boolean shouldHideEntitiesForPlayer(TazAntixRAYPlugin plugin, Player player) {
        boolean hideEntities = plugin.getConfig().getBoolean("antixray.entities.hide-entities", true);
        if (!hideEntities) {
            return false;
        }

        // Check if player is in hiding state
        return plugin.playerHiddenState.getOrDefault(player.getUniqueId(), false);
    }
    
    /**
     * Check if limited area hiding is enabled
     */
    public static boolean isLimitedAreaEnabled(TazAntixRAYPlugin plugin) {
        return plugin.getConfig().getBoolean("antixray.limited-area.enabled", false);
    }
    
    /**
     * Get the chunk radius for limited area hiding
     */
    public static int getLimitedAreaChunkRadius(TazAntixRAYPlugin plugin) {
        return plugin.getConfig().getInt("antixray.limited-area.chunk-radius", 3);
    }
    
    /**
     * Check if limited area should only apply near the player
     */
    public static boolean shouldApplyLimitedAreaOnlyNearPlayer(TazAntixRAYPlugin plugin) {
        return plugin.getConfig().getBoolean("antixray.limited-area.apply-only-near-player", true);
    }
    
    /**
     * Calculate distance between two chunk coordinates
     */
    public static double getChunkDistance(int chunkX1, int chunkZ1, int chunkX2, int chunkZ2) {
        int deltaX = chunkX1 - chunkX2;
        int deltaZ = chunkZ1 - chunkZ2;
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }
    
    /**
     * Check if a block should be hidden based on all criteria
     */
    public static boolean shouldHideBlock(TazAntixRAYPlugin plugin, Player player, int worldX, int worldY, int worldZ) {
        // Check Y level
        int hideBelow = plugin.getConfig().getInt("antixray.hide-below-y", 16);
        if (worldY > hideBelow) {
            return false;
        }
        
        // Check if player should have blocks hidden
        boolean playerShouldHide = plugin.playerHiddenState.getOrDefault(player.getUniqueId(), false);
        if (!playerShouldHide) {
            return false;
        }
        
        // Check limited area if enabled
        if (isLimitedAreaEnabled(plugin)) {
            int chunkRadius = getLimitedAreaChunkRadius(plugin);
            boolean applyOnlyNearPlayer = shouldApplyLimitedAreaOnlyNearPlayer(plugin);
            
            if (applyOnlyNearPlayer) {
                return isBlockInLimitedArea(player, worldX, worldZ, chunkRadius);
            }
        }
        
        return true;
    }

    /**
     * Check if underground protection is enabled
     */
    public static boolean isUndergroundProtectionEnabled(TazAntixRAYPlugin plugin) {
        return plugin.getConfig().getBoolean("antixray.underground-protection.enabled", true);
    }

    /**
     * Simple check: should hide block if player is above ground and block is underground
     */
    public static boolean shouldHideBlockSimple(TazAntixRAYPlugin plugin, Player player, int worldY) {
        // Check if player should have blocks hidden (player above Y31)
        boolean playerShouldHide = plugin.playerHiddenState.getOrDefault(player.getUniqueId(), false);

        if (!playerShouldHide) {
            return false;
        }

        // Check if underground protection is enabled
        if (!isUndergroundProtectionEnabled(plugin)) {
            return false;
        }

        // Hide everything at or below Y16 when player is above Y31
        int hideBelow = plugin.getConfig().getInt("antixray.hide-below-y", 16);
        return worldY <= hideBelow;
    }
}
