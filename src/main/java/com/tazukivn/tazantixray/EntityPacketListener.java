package com.tazukivn.tazantixray;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Packet listener for hiding entities in hidden areas
 */
public class EntityPacketListener implements PacketListener {
    
    private final TazAntixRAYPlugin plugin;
    
    public EntityPacketListener(TazAntixRAYPlugin plugin) {
        this.plugin = plugin;
    }
    
    private void listenerDebugLog(String message) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[TazAntixRAY DEBUG][EntityListener] " + message);
        }
    }
    
    @Override
    public void onPacketSend(PacketSendEvent event) {
        User user = event.getUser();
        if (user == null) {
            return;
        }
        
        UUID userUUID = user.getUUID();
        if (userUUID == null) {
            return;
        }
        
        Player player = Bukkit.getPlayer(userUUID);
        if (player == null || !player.isOnline()) {
            return;
        }
        
        // Check if player's world is whitelisted
        if (!plugin.isWorldWhitelisted(player.getWorld().getName())) {
            return;
        }
        
        // Check if entity hiding is enabled and player should have entities hidden
        if (!AntiXrayUtils.shouldHideEntitiesForPlayer(plugin, player)) {
            return;
        }
        
        // Handle different entity-related packets
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            handleSpawnEntityPacket(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            handleEntityMetadataPacket(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
            handleEntityTeleportPacket(event, player);
        }
    }
    
    private void handleSpawnEntityPacket(PacketSendEvent event, Player player) {
        try {
            WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);

            // Get entity position using the correct method names
            double entityX = wrapper.getPosition().getX();
            double entityY = wrapper.getPosition().getY();
            double entityZ = wrapper.getPosition().getZ();

            // Check if entity is in a hidden area
            if (shouldHideEntityAtLocation(player, (int) entityX, (int) entityY, (int) entityZ)) {
                // Get entity type from the wrapper
                EntityType entityType = getEntityTypeFromWrapper(wrapper);

                if (entityType != null && AntiXrayUtils.shouldHideEntity(entityType)) {
                    listenerDebugLog("SPAWN_ENTITY: Hiding entity " + entityType.name() + " at (" +
                            (int) entityX + "," + (int) entityY + "," + (int) entityZ + ") for player " + player.getName());

                    // Cancel the packet to hide the entity
                    event.setCancelled(true);
                }
            }
        } catch (Exception e) {
            listenerDebugLog("Error handling SPAWN_ENTITY packet: " + e.getMessage());
        }
    }
    
    private void handleEntityMetadataPacket(PacketSendEvent event, Player player) {
        try {
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            
            // For metadata packets, we need to check if the entity should be hidden
            // This is more complex as we need to track entity positions
            // For now, we'll skip this to avoid complexity, but it could be implemented
            // by maintaining an entity position cache
            
        } catch (Exception e) {
            listenerDebugLog("Error handling ENTITY_METADATA packet: " + e.getMessage());
        }
    }
    
    private void handleEntityTeleportPacket(PacketSendEvent event, Player player) {
        try {
            WrapperPlayServerEntityTeleport wrapper = new WrapperPlayServerEntityTeleport(event);

            // Get entity position using the correct method names
            double entityX = wrapper.getPosition().getX();
            double entityY = wrapper.getPosition().getY();
            double entityZ = wrapper.getPosition().getZ();

            // Check if entity is moving to a hidden area
            if (shouldHideEntityAtLocation(player, (int) entityX, (int) entityY, (int) entityZ)) {
                listenerDebugLog("ENTITY_TELEPORT: Hiding entity teleport to (" +
                        (int) entityX + "," + (int) entityY + "," + (int) entityZ + ") for player " + player.getName());

                // Cancel the packet to hide the entity movement
                event.setCancelled(true);
            }
        } catch (Exception e) {
            listenerDebugLog("Error handling ENTITY_TELEPORT packet: " + e.getMessage());
        }
    }
    
    private boolean shouldHideEntityAtLocation(Player player, int entityX, int entityY, int entityZ) {
        // Simple check: hide all entities below Y16 when player is above Y31
        return AntiXrayUtils.shouldHideBlockSimple(plugin, player, entityY);
    }
    

    
    private EntityType getEntityTypeFromWrapper(WrapperPlayServerSpawnEntity wrapper) {
        try {
            // For now, we'll disable entity type detection due to API complexity
            // This feature can be enhanced in future versions with proper PacketEvents integration
            listenerDebugLog("Entity type detection temporarily disabled - hiding all entities in hidden areas");
            return EntityType.ARMOR_STAND; // Default to a common entity type for hiding
        } catch (Exception e) {
            listenerDebugLog("Failed to get entity type from spawn packet: " + e.getMessage());
        }

        return null;
    }
}
