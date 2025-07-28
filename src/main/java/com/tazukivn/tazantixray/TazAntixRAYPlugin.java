package com.tazukivn.tazantixray;

// PacketEvents v2.x imports - using com.github.retrooper.packetevents
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;

public class TazAntixRAYPlugin extends JavaPlugin implements org.bukkit.event.Listener, CommandExecutor, TabCompleter {

    public final Map<UUID, Boolean> playerHiddenState = new ConcurrentHashMap<>();
    private final Map<UUID, Long> refreshCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> internallyTeleporting = ConcurrentHashMap.newKeySet();
    private static TazAntixRAYPlugin instance;
    private WrappedBlockState airState;
    private int airStateGlobalId = 0;
    private boolean debugMode = false;
    private int refreshCooldownMillis = 3000;
    private Set<String> whitelistedWorlds = new HashSet<>();
    
    // Language support
    private FileConfiguration langConfig;
    private String currentLanguage = "en";

    public static TazAntixRAYPlugin getInstance() {
        return instance;
    }

    private void debugLog(String message) {
        if (debugMode) {
            getLogger().info("[TazAntixRAY DEBUG] " + message);
        }
    }

    private void infoLog(String message) {
        getLogger().info("[TazAntixRAY] " + message);
    }

    /**
     * Get localized message from language file
     */
    public String getMessage(String key, Object... args) {
        if (langConfig == null) {
            return key; // Fallback to key if lang not loaded
        }
        String message = langConfig.getString("messages." + key, key);
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                message = message.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Load language configuration
     */
    private void loadLanguageConfig() {
        String language = getConfig().getString("settings.language", "en");
        this.currentLanguage = language;
        
        File langFile = new File(getDataFolder(), "lang/" + language + ".yml");
        
        // Create lang directory if it doesn't exist
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        // Copy default language files if they don't exist
        if (!langFile.exists()) {
            saveResource("lang/" + language + ".yml", false);
        }
        
        // Load language config
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        // Load defaults from jar
        InputStream defConfigStream = getResource("lang/" + language + ".yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
            langConfig.setDefaults(defConfig);
        }
        
        debugLog("Loaded language: " + language);
    }

    private void loadConfigValues() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration config = getConfig();

        // Load language first
        loadLanguageConfig();

        // Load world whitelist from new config structure
        List<String> worldsFromConfig = config.getStringList("worlds.whitelist");
        if (worldsFromConfig == null || worldsFromConfig.isEmpty()) {
            worldsFromConfig = new ArrayList<>();
            // Try old config format for backward compatibility
            List<String> oldFormat = config.getStringList("whitelisted-worlds");
            if (oldFormat != null && !oldFormat.isEmpty()) {
                worldsFromConfig = oldFormat;
                // Migrate to new format
                config.set("worlds.whitelist", worldsFromConfig);
                config.set("whitelisted-worlds", null);
                saveConfig();
                infoLog("Migrated world whitelist to new config format.");
            } else {
                getLogger().warning(getMessage("config.worlds-not-found"));
            }
        }
        this.whitelistedWorlds = new HashSet<>(worldsFromConfig);
        debugLog("Loaded whitelisted worlds: " + this.whitelistedWorlds);

        // Load settings from new config structure
        int cooldownSeconds = config.getInt("settings.refresh-cooldown-seconds", 3);
        if (!config.contains("settings.refresh-cooldown-seconds")) {
            config.set("settings.refresh-cooldown-seconds", 3);
            saveConfig();
        }
        this.refreshCooldownMillis = cooldownSeconds * 1000;
        infoLog(getMessage("config.cooldown-set", cooldownSeconds, this.refreshCooldownMillis));

        // Load debug mode from config
        this.debugMode = config.getBoolean("settings.debug-mode", false);

        // Load other settings
        double triggerY = config.getDouble("antixray.trigger-y-level", 31.0);
        int hideBelow = config.getInt("antixray.hide-below-y", 16);
        double stopHiding = config.getDouble("antixray.transition.stop-hiding-y", 30.0);

        debugLog("Anti-XRay settings - Trigger Y: " + triggerY + ", Hide below Y: " + hideBelow + ", Stop hiding Y: " + stopHiding);

        // Performance settings
        boolean foliaOptimizations = config.getBoolean("performance.folia-optimizations", true);
        boolean regionAware = config.getBoolean("performance.region-aware-processing", true);
        int maxChunksPerTick = config.getInt("performance.max-chunks-per-tick", 50);

        debugLog("Performance settings - Folia optimizations: " + foliaOptimizations + ", Region aware: " + regionAware + ", Max chunks per tick: " + maxChunksPerTick);
    }

    public boolean isWorldWhitelisted(String worldName) {
        if (worldName == null) return false;
        return whitelistedWorlds.contains(worldName);
    }

    @Override
    public void onLoad() {
        instance = this;
        infoLog("onLoad() called.");
        PacketEventsAPI packetEventsAPI = PacketEvents.getAPI();
        if (packetEventsAPI == null) {
            getLogger().severe(getMessage("error.packetevents-null"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        packetEventsAPI.load();
        if (!packetEventsAPI.isLoaded()) {
            getLogger().severe(getMessage("error.packetevents-load-failed"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        infoLog(getMessage("info.packetevents-loaded"));
    }

    @Override
    public void onEnable() {
        infoLog("onEnable() called.");
        loadConfigValues();

        final PacketEventsAPI packetEventsAPI = PacketEvents.getAPI();
        if (packetEventsAPI == null || !packetEventsAPI.isLoaded()) {
            getLogger().severe(getMessage("error.packetevents-not-available"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        debugLog("PacketEvents API confirmed available and loaded in onEnable.");

        try {
            airState = WrappedBlockState.getByString("minecraft:air");
            if (airState == null) {
                throw new IllegalStateException("WrappedBlockState.getByString(\"minecraft:air\") returned null.");
            }
            airStateGlobalId = airState.getGlobalId();
            debugLog("AIR block state initialized successfully. Global ID: " + airStateGlobalId);
        } catch (Exception e) {
            getLogger().severe(getMessage("error.air-state-failed", e.getMessage()));
            airState = null;
        }

        if (airState == null) {
            getLogger().severe(getMessage("error.air-state-null"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        packetEventsAPI.getSettings().checkForUpdates(true);
        debugLog("PacketEvents settings configured.");

        packetEventsAPI.getEventManager().registerListener(new ChunkPacketListenerPE(this), PacketListenerPriority.NORMAL);
        debugLog("ChunkPacketListenerPE registered.");
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        debugLog("Bukkit PlayerListeners (this class) registered.");

        // Register commands
        this.getCommand("tazantixray").setExecutor(this);
        this.getCommand("tardebug").setExecutor(this);
        this.getCommand("tarreload").setExecutor(this);
        this.getCommand("tarworld").setExecutor(this);
        this.getCommand("tarworld").setTabCompleter(this);
        debugLog("Commands registered.");

        // Use Folia's GlobalRegionScheduler for global tasks
        Bukkit.getGlobalRegionScheduler().run(this, (task) -> {
            if (this.isEnabled() && packetEventsAPI.isLoaded()) {
                packetEventsAPI.init();
                infoLog(getMessage("info.packetevents-init"));
            } else if (!this.isEnabled()){
                getLogger().warning(getMessage("warning.plugin-disabled-before-init"));
            } else {
                getLogger().warning(getMessage("warning.packetevents-not-loaded-init"));
            }
        });

        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                debugLog("Processing online player in onEnable: " + player.getName());
                if (isWorldWhitelisted(player.getWorld().getName())) {
                    debugLog("Handling initial state for already online player in whitelisted world: " + player.getName());
                    handlePlayerInitialState(player, false);
                } else {
                    debugLog("Skipping initial state for " + player.getName() + " - world '" + player.getWorld().getName() + "' not whitelisted.");
                }
            }
        } catch (Exception e) {
            getLogger().severe(getMessage("error.online-player-loop", e.getMessage()));
            e.printStackTrace();
        }

        getLogger().info(getMessage("info.plugin-enabled", (debugMode ? "ON" : "OFF")));
        getLogger().info(getMessage("info.active-worlds", whitelistedWorlds.toString()));
    }

    @Override
    public void onDisable() {
        infoLog("onDisable() called.");
        if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded()) {
            PacketEvents.getAPI().terminate();
            debugLog("PacketEvents API terminated.");
        }
        playerHiddenState.clear();
        getLogger().info(getMessage("info.plugin-disabled"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "tazantixray":
                return handleMainCommand(sender, args);
            case "tardebug":
                return handleDebugCommand(sender);
            case "tarreload":
                return handleReloadCommand(sender);
            case "tarworld":
                return handleWorldCommand(sender, args);
        }
        return false;
    }

    private boolean handleMainCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tazantixray.admin")) {
            sender.sendMessage(getMessage("error.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(getMessage("command.main.usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "debug":
                return handleDebugCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "world":
                return handleWorldCommand(sender, subArgs);
            default:
                sender.sendMessage(getMessage("command.main.unknown-subcommand", subCommand));
                return true;
        }
    }

    private boolean handleDebugCommand(CommandSender sender) {
        if (!sender.hasPermission("tazantixray.debug")) {
            sender.sendMessage(getMessage("error.no-permission"));
            return true;
        }
        debugMode = !debugMode;
        getConfig().set("settings.debug-mode", debugMode);
        saveConfig();

        String status = debugMode ? getMessage("status.on") : getMessage("status.off");
        sender.sendMessage(getMessage("command.debug.toggled", status));
        getLogger().info(getMessage("log.debug-toggled", (debugMode ? "ON" : "OFF"), sender.getName()));
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("tazantixray.reload")) {
            sender.sendMessage(getMessage("error.no-permission"));
            return true;
        }
        loadConfigValues();
        sender.sendMessage(getMessage("command.reload.success", whitelistedWorlds.toString()));
        getLogger().info(getMessage("log.config-reloaded", sender.getName(), whitelistedWorlds.toString()));

        // Update all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isWorldWhitelisted(p.getWorld().getName())) {
                handlePlayerInitialState(p, true);
            } else {
                if (playerHiddenState.remove(p.getUniqueId()) != null) {
                    refreshFullView(p);
                }
            }
        }
        return true;
    }

    private boolean handleWorldCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tazantixray.world")) {
            sender.sendMessage(getMessage("error.no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(getMessage("command.world.usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("list")) {
            sender.sendMessage(getMessage("command.world.list", String.join(", ", whitelistedWorlds)));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(getMessage("command.world.usage-with-world"));
            return true;
        }

        String worldName = args[1];
        World targetWorld = Bukkit.getWorld(worldName);

        if (targetWorld == null && subCommand.equals("add")) {
            sender.sendMessage(getMessage("error.world-not-found", worldName));
            return true;
        }

        if (subCommand.equals("add")) {
            if (whitelistedWorlds.add(worldName)) {
                getConfig().set("worlds.whitelist", new ArrayList<>(whitelistedWorlds));
                saveConfig();
                sender.sendMessage(getMessage("command.world.added", worldName));
                getLogger().info(getMessage("log.world-added", worldName, sender.getName()));

                // Update players in this world
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getName().equals(worldName)) {
                        handlePlayerInitialState(p, true);
                    }
                }
            } else {
                sender.sendMessage(getMessage("command.world.already-added", worldName));
            }
        } else if (subCommand.equals("remove")) {
            if (whitelistedWorlds.remove(worldName)) {
                getConfig().set("worlds.whitelist", new ArrayList<>(whitelistedWorlds));
                saveConfig();
                sender.sendMessage(getMessage("command.world.removed", worldName));
                getLogger().info(getMessage("log.world-removed", worldName, sender.getName()));

                // Update players in this world
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getName().equals(worldName)) {
                        playerHiddenState.remove(p.getUniqueId());
                        refreshFullView(p);
                        debugLog("Reset hidden state and refreshed chunks for " + p.getName() + " in now non-whitelisted world " + worldName);
                    }
                }
            } else {
                sender.sendMessage(getMessage("command.world.not-in-list", worldName));
            }
        } else {
            sender.sendMessage(getMessage("command.world.unknown-subcommand"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tarworld")) {
            if (args.length == 1) {
                return StringUtil.copyPartialMatches(args[0], Arrays.asList("list", "add", "remove"), new ArrayList<>());
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
                List<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                if (args[0].equalsIgnoreCase("remove")) {
                    List<String> removableWorlds = new ArrayList<>(whitelistedWorlds);
                    return StringUtil.copyPartialMatches(args[1], removableWorlds, new ArrayList<>());
                }
                return StringUtil.copyPartialMatches(args[1], worldNames, new ArrayList<>());
            }
        } else if (command.getName().equalsIgnoreCase("tazantixray")) {
            if (args.length == 1) {
                return StringUtil.copyPartialMatches(args[0], Arrays.asList("debug", "reload", "world"), new ArrayList<>());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("world")) {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList("list", "add", "remove"), new ArrayList<>());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("world") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                List<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                if (args[1].equalsIgnoreCase("remove")) {
                    List<String> removableWorlds = new ArrayList<>(whitelistedWorlds);
                    return StringUtil.copyPartialMatches(args[2], removableWorlds, new ArrayList<>());
                }
                return StringUtil.copyPartialMatches(args[2], worldNames, new ArrayList<>());
            }
        }
        return Collections.emptyList();
    }

    public void handlePlayerInitialState(Player player, boolean immediateRefresh) {
        if (!isWorldWhitelisted(player.getWorld().getName())) {
            debugLog("handlePlayerInitialState for " + player.getName() + " skipped, world " + player.getWorld().getName() + " not whitelisted.");
            boolean wasPresent = playerHiddenState.remove(player.getUniqueId()) != null;
            if (wasPresent && immediateRefresh) {
                debugLog("Player " + player.getName() + " moved to non-whitelisted world, had state, refreshing immediately with full view.");
                refreshFullView(player);
            }
            return;
        }
        debugLog("handlePlayerInitialState for " + player.getName() + " in whitelisted world " + player.getWorld().getName() + (immediateRefresh ? " (immediate refresh)" : " (delayed refresh allowed)"));
        double currentY = player.getLocation().getY();
        boolean initialStateIsHidden = currentY >= 31.0;
        playerHiddenState.put(player.getUniqueId(), initialStateIsHidden);
        debugLog("Player " + player.getName() + " at Y=" + String.format("%.2f", currentY) + ". Initial hidden state: " + initialStateIsHidden);

        if (initialStateIsHidden) {
            if (immediateRefresh) {
                debugLog("Initial state is hidden for " + player.getName() + ". Refreshing full view immediately.");
                refreshFullView(player);
            } else {
                debugLog("Initial state is hidden for " + player.getName() + ". Relying on packet listener for new/refreshed chunks.");
            }
        } else {
            if (immediateRefresh) {
                debugLog("Player " + player.getName() + " new state is NOT hidden. Refreshing full view immediately to ensure normal view.");
                refreshFullView(player);
            }
        }
    }

    public void refreshFullView(Player player) {
        debugLog("refreshFullView called for " + player.getName() + " in world " + player.getWorld().getName());
        performRefresh(player, Bukkit.getServer().getViewDistance());
    }

    private void performRefresh(Player player, int radiusChunks) {
        debugLog("performRefresh executing for " + player.getName() + " with radius " + radiusChunks);
        if (!player.isOnline()) {
            debugLog("Player " + player.getName() + " is offline in performRefresh. Skipping.");
            return;
        }
        if (!isWorldWhitelisted(player.getWorld().getName())) {
            debugLog("performRefresh skipped for " + player.getName() + ", world " + player.getWorld().getName() + " not whitelisted.");
            return;
        }

        // For Folia, we need to ensure we're on the correct region thread
        if (!Bukkit.isOwnedByCurrentRegion(player.getLocation())) {
            final int finalRadius = radiusChunks;
            debugLog("Not on correct region thread. Scheduling performRefresh for " + player.getName() + " with radius " + finalRadius);
            Bukkit.getRegionScheduler().run(this, player.getLocation(), (task) -> performRefresh(player, finalRadius));
            return;
        }

        // Perform chunk refresh in a Folia-safe manner
        refreshChunksForPlayer(player, radiusChunks);
    }

    /**
     * Folia-safe chunk refresh method that handles region-based threading
     */
    private void refreshChunksForPlayer(Player player, int radiusChunks) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int playerChunkX = loc.getBlockX() >> 4;
        int playerChunkZ = loc.getBlockZ() >> 4;
        int refreshedCount = 0;

        // In Folia, we need to be careful about chunk operations across regions
        for (int cx = playerChunkX - radiusChunks; cx <= playerChunkX + radiusChunks; cx++) {
            for (int cz = playerChunkZ - radiusChunks; cz <= playerChunkZ + radiusChunks; cz++) {
                try {
                    // Check if the chunk location is owned by current region before refreshing
                    Location chunkLoc = new Location(world, cx * 16, loc.getY(), cz * 16);
                    if (Bukkit.isOwnedByCurrentRegion(chunkLoc)) {
                        world.refreshChunk(cx, cz);
                        refreshedCount++;
                    } else {
                        // Schedule refresh on the correct region
                        final int finalCx = cx;
                        final int finalCz = cz;
                        Bukkit.getRegionScheduler().run(this, chunkLoc, (task) -> {
                            try {
                                world.refreshChunk(finalCx, finalCz);
                                debugLog("Cross-region chunk refresh at (" + finalCx + "," + finalCz + ") for " + player.getName());
                            } catch (Exception e) {
                                debugLog("Failed to refresh cross-region chunk (" + finalCx + "," + finalCz + "): " + e.getMessage());
                            }
                        });
                    }
                } catch (Exception e) {
                    debugLog("Error refreshing chunk (" + cx + "," + cz + ") for " + player.getName() + ": " + e.getMessage());
                }
            }
        }
        debugLog("Refreshed " + refreshedCount + " chunks (radius " + radiusChunks + ") around " + player.getName());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        infoLog("onPlayerJoin CALLED for: " + player.getName() + " in world " + player.getWorld().getName());
        if (isWorldWhitelisted(player.getWorld().getName())) {
            handlePlayerInitialState(player, false);
        } else {
            debugLog("Player " + player.getName() + " joined non-whitelisted world. No initial state handling.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        infoLog("onPlayerQuit CALLED for: " + player.getName());
        refreshCooldowns.remove(player.getUniqueId());
        playerHiddenState.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World fromWorld = event.getFrom();
        World toWorld = player.getWorld();

        infoLog("PlayerChangedWorldEvent for " + player.getName() + " from " + fromWorld.getName() + " to " + toWorld.getName());

        if (isWorldWhitelisted(toWorld.getName())) {
            debugLog("Player " + player.getName() + " entered whitelisted world " + toWorld.getName() + ". Handling initial state with immediate (full) refresh.");
            handlePlayerInitialState(player, true);
        } else {
            boolean wasHidden = playerHiddenState.remove(player.getUniqueId()) != null;
            if (wasHidden) {
                debugLog("Player " + player.getName() + " entered non-whitelisted world " + toWorld.getName() + ". State cleared, refreshing full view immediately.");
                refreshFullView(player);
            } else {
                debugLog("Player " + player.getName() + " entered non-whitelisted world " + toWorld.getName() + ". No prior hidden state to clear or already normal.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Fix for recursion: If this teleport was initiated by our own plugin, ignore it.
        if (internallyTeleporting.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        boolean toWorldIsWhitelisted = isWorldWhitelisted(to.getWorld().getName());
        boolean fromWorldIsWhitelisted = isWorldWhitelisted(event.getFrom().getWorld().getName());

        if (!toWorldIsWhitelisted) {
            // Handle teleporting OUT of a whitelisted world.
            if (fromWorldIsWhitelisted && playerHiddenState.remove(player.getUniqueId()) != null) {
                // Schedule the refresh for after the teleport is complete using Folia's RegionScheduler
                Bukkit.getRegionScheduler().run(this, to, (task) -> {
                    if (player.isOnline()) {
                        debugLog("Player " + player.getName() + " teleported out of a whitelisted world. Refreshing view.");
                        refreshFullView(player);
                    }
                });
            }
            return;
        }

        UUID playerUUID = player.getUniqueId();
        double destY = to.getY();

        boolean oldStateIsHidden = playerHiddenState.getOrDefault(playerUUID, destY >= 31.0);
        boolean newStateIsHidden = destY >= 31.0;

        if (oldStateIsHidden == newStateIsHidden) {
            return; // No state change, so no special handling is needed.
        }

        // This is the critical race condition: teleporting from a HIDING state to a NOT HIDING state where chunks may be unloaded.
        if (!newStateIsHidden) { // Transitioning TO a non-hiding state (Hiding -> Visible)
            debugLog("Intercepting teleport for " + player.getName() + " from HIDING to NOT HIDING state. Delaying by 1 tick to prevent void bug.");
            playerHiddenState.put(playerUUID, false); // Update state immediately
            event.setCancelled(true); // Cancel original event
            // Schedule a new teleport for the next tick using Folia's RegionScheduler
            Bukkit.getRegionScheduler().run(this, to, (task) -> {
                if (!player.isOnline()) return;
                internallyTeleporting.add(playerUUID);
                try {
                    player.teleport(to);
                } finally {
                    internallyTeleporting.remove(playerUUID);
                }
            });
        } else { // Transitioning TO a hiding state (Visible -> Hiding)
            debugLog("Player " + player.getName() + " teleporting to a HIDING state. Updating state immediately.");
            playerHiddenState.put(playerUUID, true);
            // Let the teleport proceed. The packet listener will now correctly hide the new chunks being sent.
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        debugLog("onPlayerMove CALLED for " + player.getName() + " in world " + player.getWorld().getName());

        if (!isWorldWhitelisted(player.getWorld().getName())) {
            if (playerHiddenState.containsKey(player.getUniqueId())) {
                boolean wasHidden = playerHiddenState.remove(player.getUniqueId()) != null;
                if (wasHidden) {
                    debugLog(player.getName() + " moved within/to non-whitelisted world " + player.getWorld().getName() + ". Resetting state and refreshing full view.");
                    refreshFullView(player);
                }
            }
            return;
        }

        debugLog("onPlayerMove in whitelisted world " + player.getWorld().getName() + " for " + player.getName());

        Location to = event.getTo();
        Location from = event.getFrom();

        if (to == null) {
            debugLog("onPlayerMove: 'to' location is null. Skipping.");
            return;
        }
        if (from.getBlockY() == to.getBlockY()) {
            return;
        }

        debugLog("onPlayerMove: Y-block CHANGED for " + player.getName());

        double currentY = to.getY();
        UUID playerUUID = player.getUniqueId();

        boolean oldStateIsHidden = this.playerHiddenState.getOrDefault(playerUUID, currentY >= 31.0);
        boolean newStateIsHidden;

        if (currentY >= 31.0) {
            newStateIsHidden = true;
        } else if (currentY <= 30.0) {
            newStateIsHidden = false;
        } else {
            newStateIsHidden = oldStateIsHidden;
        }

        debugLog(String.format("PlayerMove Details: %s, FromY: %.2f (BlockY:%d), ToY: %.2f (BlockY:%d), OldStateHidden: %b, NewStateHidden: %b",
                player.getName(), from.getY(), from.getBlockY(), to.getY(), to.getBlockY(), oldStateIsHidden, newStateIsHidden));

        if (newStateIsHidden != oldStateIsHidden) {
            // Always update the state immediately so the packet listener has the correct information.
            this.playerHiddenState.put(playerUUID, newStateIsHidden);

            if (!newStateIsHidden) {
                // ALWAYS refresh when transitioning to a VISIBLE state. This is crucial to prevent players from getting stuck in a void view.
                debugLog("State changed for " + player.getName() + " to VISIBLE. Refreshing full view at Y=" + String.format("%.2f", currentY));
                this.refreshFullView(player);
            } else {
                // When transitioning to a HIDING state, we can apply a cooldown to prevent lag from rapid movement.
                long currentTime = System.currentTimeMillis();
                long expirationTime = refreshCooldowns.getOrDefault(playerUUID, 0L);

                if (currentTime < expirationTime) {
                    // Cooldown is active, skip the refresh.
                    debugLog("State changed for " + player.getName() + " to HIDING. Refresh skipped due to active cooldown.");
                } else {
                    // Cooldown has expired, perform the refresh and start a new cooldown.
                    debugLog("State changed for " + player.getName() + " to HIDING. Refreshing full view at Y=" + String.format("%.2f", currentY) + " and starting cooldown.");
                    this.refreshFullView(player);
                    refreshCooldowns.put(playerUUID, currentTime + refreshCooldownMillis);
                }
            }
        } else {
            debugLog("State NOT changed for " + player.getName() + ". Current hidden state: " + newStateIsHidden);
        }
    }

    public WrappedBlockState getAirState() {
        return airState;
    }

    public int getAirStateGlobalId() {
        return airStateGlobalId;
    }

    public boolean isDebugMode() {
        return debugMode;
    }
}
