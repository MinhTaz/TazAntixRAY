package com.tazukivn.tazantixray;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ChunkPacketListenerPE implements PacketListener {
    private final TazAntixRAYPlugin plugin;

    public ChunkPacketListenerPE(TazAntixRAYPlugin plugin) {
        this.plugin = plugin;
    }

    private void listenerDebugLog(String message) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[TazAntixRAY DEBUG][PacketListener] " + message);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        listenerDebugLog("onPacketSend CALLED. PacketType: " + event.getPacketType().getName());

        User user = event.getUser();
        if (user == null) {
            listenerDebugLog("User object is null in onPacketSend. Skipping.");
            return;
        }

        UUID userUUID = user.getUUID();
        if (userUUID == null) {
            return;
        }

        Player player = Bukkit.getPlayer(userUUID);
        if (player == null || !player.isOnline()) {
            listenerDebugLog("Bukkit.getPlayer(uuid) returned null or player offline for packet type: " + event.getPacketType().getName());
            return;
        }

        // Add a null-check for the player's world to prevent errors during world change/login.
        World playerWorld = player.getWorld();
        if (playerWorld == null || !plugin.isWorldWhitelisted(playerWorld.getName())) {
            return;
        }

        listenerDebugLog("Processing packet for " + player.getName() + " in whitelisted world " + player.getWorld().getName() + ". PacketType: " + event.getPacketType().getName());

        // Handle CHUNK_DATA
        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            handleChunkDataPacket(event, player);
        }
        // Handle BLOCK_CHANGE
        else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            handleBlockChangePacket(event, player);
        }
        // Handle MULTI_BLOCK_CHANGE
        else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            handleMultiBlockChangePacket(event, player);
        }
    }

    private void handleChunkDataPacket(PacketSendEvent event, Player player) {
        listenerDebugLog("Intercepted CHUNK_DATA packet for " + player.getName());
        boolean shouldHide = plugin.playerHiddenState.getOrDefault(player.getUniqueId(), false);
        listenerDebugLog("Player: " + player.getName() + ", shouldHide: " + shouldHide + " (from playerHiddenState: " + plugin.playerHiddenState.get(player.getUniqueId()) + ")");

        if (shouldHide) {
            WrappedBlockState air = plugin.getAirState();
            if (air == null) {
                plugin.getLogger().warning("[TazAntixRAY][PacketListener] AIR block state is not available. Cannot modify chunk for " + player.getName());
                return;
            }
            listenerDebugLog("Proceeding to modify CHUNK_DATA for " + player.getName());

            WrapperPlayServerChunkData chunkDataWrapper = null;
            try {
                chunkDataWrapper = new WrapperPlayServerChunkData(event);
            } catch (Exception e) {
                plugin.getLogger().severe("[TazAntixRAY][PacketListener] Error creating WrapperPlayServerChunkData: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            Column column = null;
            BaseChunk[] chunkSections = null;

            try {
                column = chunkDataWrapper.getColumn();
                if (column == null) {
                    plugin.getLogger().warning("[TazAntixRAY][PacketListener] WrapperPlayServerChunkData.getColumn() returned null for player " + player.getName());
                    return;
                }
                chunkSections = column.getChunks();
                listenerDebugLog("Got column for " + player.getName() + " X:" + column.getX() + " Z:" + column.getZ() + " Sections:" + (chunkSections != null ? chunkSections.length : "null"));
            } catch (Exception e) {
                plugin.getLogger().severe("[TazAntixRAY][PacketListener] Error accessing Column or its data (X, Z, or Chunks): " + e.getMessage());
                e.printStackTrace();
                return;
            }

            if (chunkSections == null) {
                plugin.getLogger().warning("[TazAntixRAY][PacketListener] Retrieved chunkSections is null from Column object for player: " + player.getName());
                return;
            }

            World world = player.getWorld();
            if (world == null) {
                // This is a defensive check; we shouldn't get here if the check in onPacketSend is working, but it's safe to have.
                return;
            }
            int worldMinY = world.getMinHeight();
            boolean modified = false;

            // Check if limited area hiding is enabled
            boolean limitedAreaEnabled = AntiXrayUtils.isLimitedAreaEnabled(plugin);
            int chunkRadius = AntiXrayUtils.getLimitedAreaChunkRadius(plugin);
            boolean applyOnlyNearPlayer = AntiXrayUtils.shouldApplyLimitedAreaOnlyNearPlayer(plugin);

            // Get chunk coordinates
            int chunkX = column.getX();
            int chunkZ = column.getZ();

            for (int sectionIndex = 0; sectionIndex < chunkSections.length; sectionIndex++) {
                BaseChunk section = chunkSections[sectionIndex];
                if (section == null || section.isEmpty()) {
                    continue;
                }
                int sectionMinWorldY = worldMinY + (sectionIndex * 16);
                for (int yInSection = 0; yInSection < 16; yInSection++) {
                    int currentWorldY = sectionMinWorldY + yInSection;
                    if (currentWorldY <= 16) {
                        for (int relX = 0; relX < 16; relX++) {
                            for (int relZ = 0; relZ < 16; relZ++) {
                                // Calculate world coordinates
                                int worldX = (chunkX * 16) + relX;
                                int worldZ = (chunkZ * 16) + relZ;

                                try {
                                    WrappedBlockState currentState = section.get(relX, yInSection, relZ);
                                    if (currentState != null && !currentState.equals(air)) {
                                        // Simple check: hide everything below Y16 when player is above Y31
                                        boolean shouldHideThisBlock = AntiXrayUtils.shouldHideBlockSimple(plugin, player, currentWorldY);

                                        // Additional check for limited area if enabled
                                        if (limitedAreaEnabled && applyOnlyNearPlayer && shouldHideThisBlock) {
                                            boolean inLimitedArea = AntiXrayUtils.isChunkInLimitedArea(player, chunkX, chunkZ, chunkRadius);
                                            shouldHideThisBlock = shouldHideThisBlock && inLimitedArea;
                                        }

                                        if (shouldHideThisBlock) {
                                            // Get replacement block
                                            WrappedBlockState replacementBlock = AntiXrayUtils.getReplacementBlock(plugin, air);

                                            listenerDebugLog("CHUNK_DATA: Underground Protection - Hiding block at [" + relX + "," + yInSection + "," + relZ + "] in section " + sectionIndex +
                                                    " (world Y " + currentWorldY + ") from " + currentState.getType().getName() + " to " +
                                                    replacementBlock.getType().getName() + " for player " + player.getName());
                                            section.set(relX, yInSection, relZ, replacementBlock);
                                            modified = true;
                                        }
                                    }
                                } catch (Exception e) {
                                    listenerDebugLog("Error setting block in CHUNK_DATA section " + sectionIndex + " at (" + relX + "," + yInSection + "," + relZ + "): " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            }

            if (modified) {
                try {
                    chunkDataWrapper.setIgnoreOldData(true);
                    listenerDebugLog("Set ignoreOldData=true for CHUNK_DATA to " + player.getName());
                } catch (Exception e) {
                    plugin.getLogger().warning("[TazAntixRAY][PacketListener] Failed to set ignoreOldData on WrapperPlayServerChunkData: " + e.getMessage());
                }
                event.markForReEncode(true);
                listenerDebugLog("CHUNK_DATA for " + player.getName() + " was modified to hide blocks at Y<=16 and marked for re-encode.");
            } else {
                listenerDebugLog("CHUNK_DATA for " + player.getName() + " processed, but no blocks were modified (shouldHide=" + shouldHide + ").");
            }
        } else {
            listenerDebugLog("CHUNK_DATA for " + player.getName() + ", shouldHide is false. No modification.");
        }
    }

    private void handleBlockChangePacket(PacketSendEvent event, Player player) {
        listenerDebugLog("Intercepted BLOCK_CHANGE packet for " + player.getName());
        boolean shouldHide = plugin.playerHiddenState.getOrDefault(player.getUniqueId(), false);
        if (shouldHide) {
            WrappedBlockState air = plugin.getAirState();
            if (air == null) return;

            WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
            Vector3i blockPos = wrapper.getBlockPosition();

            if (blockPos != null) {
                WrappedBlockState currentState = wrapper.getBlockState();
                if (currentState != null && !currentState.equals(air)) {
                    // Simple check: hide everything below Y16 when player is above Y31
                    boolean shouldHideThisBlock = AntiXrayUtils.shouldHideBlockSimple(plugin, player, blockPos.getY());

                    if (shouldHideThisBlock) {
                        // Get replacement block
                        WrappedBlockState replacementBlock = AntiXrayUtils.getReplacementBlock(plugin, air);

                        listenerDebugLog("BLOCK_CHANGE: Underground Protection - Hiding block at " + blockPos.toString() + " from " + currentState.getType().getName() +
                                " to " + replacementBlock.getType().getName() + " for " + player.getName());
                        wrapper.setBlockState(replacementBlock);
                        event.markForReEncode(true);
                    }
                }
            }
        }
    }

    private void handleMultiBlockChangePacket(PacketSendEvent event, Player player) {
        listenerDebugLog("Intercepted MULTI_BLOCK_CHANGE packet for " + player.getName());
        boolean shouldHide = plugin.playerHiddenState.getOrDefault(player.getUniqueId(), false);
        if (shouldHide) {
            WrappedBlockState air = plugin.getAirState();
            if (air == null) return;

            WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
            boolean modifiedInPacket = false;

            WrapperPlayServerMultiBlockChange.EncodedBlock[] records = wrapper.getBlocks();
            if (records == null) {
                plugin.getLogger().warning("[TazAntixRAY][PacketListener] MULTI_BLOCK_CHANGE: Records (getBlocks) are null. Cannot process.");
                return;
            }

            listenerDebugLog("MULTI_BLOCK_CHANGE: Processing " + records.length + " records.");

            for (WrapperPlayServerMultiBlockChange.EncodedBlock record : records) {
                if (record == null) continue;

                int currentWorldY = record.getY();
                int currentBlockId = record.getBlockId();

                // Simple check: hide everything below Y16 when player is above Y31
                boolean shouldHideThisBlock = AntiXrayUtils.shouldHideBlockSimple(plugin, player, currentWorldY);

                if (shouldHideThisBlock) {
                    // Get replacement block
                    WrappedBlockState replacementBlock = AntiXrayUtils.getReplacementBlock(plugin, plugin.getAirState());
                    int replacementId = replacementBlock.getGlobalId();

                    if (currentBlockId != replacementId) {
                        listenerDebugLog("MULTI_BLOCK_CHANGE: Underground Protection - Hiding block at global ("+record.getX()+","+currentWorldY+","+record.getZ()+") from ID " + currentBlockId +
                                " to " + replacementBlock.getType().getName() + " (ID: " + replacementId + ") for " + player.getName());
                        try {
                            record.setBlockId(replacementId);
                            modifiedInPacket = true;
                        } catch (Exception e) {
                            listenerDebugLog("MULTI_BLOCK_CHANGE: Failed to setBlockId on EncodedBlock record. Error: " + e.getMessage());
                        }
                    }
                }
            }

            if (modifiedInPacket) {
                event.markForReEncode(true);
                listenerDebugLog("MULTI_BLOCK_CHANGE for " + player.getName() + " was modified and marked for re-encode.");
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // This method is required by the PacketListener interface.
    }
}
