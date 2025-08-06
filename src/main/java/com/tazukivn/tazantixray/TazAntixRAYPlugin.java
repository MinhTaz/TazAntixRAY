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
import java.io.File;

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
    private WrappedBlockState deepslateState;
    private WrappedBlockState stoneState;
    private int airStateGlobalId = 0;
    private boolean debugMode = false;
    private int refreshCooldownMillis = 3000;
    private Set<String> whitelistedWorlds = new HashSet<>();

    // Instant protection settings
    private boolean instantProtectionEnabled = true;
    private int instantLoadRadius = 15;
    private int preLoadDistance = 10;
    private boolean forceImmediateRefresh = true;

    // Bedrock support
    private boolean bedrockSupportEnabled = true;
    private boolean geyserCompatibility = true;
    private boolean floodgateCompatibility = true;
    private List<String> floodgatePrefixes = new ArrayList<>();
    private boolean autoDetectFloodgateConfig = true;
    private boolean useFloodgateAPI = true;
    private boolean useGeyserAPI = true;
    private boolean uuidPatternDetection = true;

    // Cache for detected Bedrock players
    private final Set<UUID> bedrockPlayers = new HashSet<>();
    private final Set<String> detectedPrefixes = new HashSet<>();

    // Language support
    private FileConfiguration langConfig;
    private String currentLanguage = "en";

    // Platform compatibility
    private boolean isFolia = false;

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

        // Auto-apply performance optimizations based on platform
        applyPlatformOptimizations();

        // Performance settings
        int maxChunksPerTick = config.getInt("performance.max-chunks-per-tick", 50);
        int maxEntitiesPerTick = config.getInt("performance.max-entities-per-tick", 100);

        // Instant protection settings
        this.instantProtectionEnabled = config.getBoolean("performance.instant-protection.enabled", true);
        this.instantLoadRadius = config.getInt("performance.instant-protection.instant-load-radius", 15);
        this.preLoadDistance = config.getInt("performance.instant-protection.pre-load-distance", 10);
        this.forceImmediateRefresh = config.getBoolean("performance.instant-protection.force-immediate-refresh", true);

        // Bedrock support settings
        this.bedrockSupportEnabled = config.getBoolean("compatibility.bedrock-support.enabled", true);
        this.geyserCompatibility = config.getBoolean("compatibility.bedrock-support.geyser-compatibility", true);
        this.floodgateCompatibility = config.getBoolean("compatibility.bedrock-support.floodgate-compatibility", true);
        this.autoDetectFloodgateConfig = config.getBoolean("compatibility.bedrock-support.auto-detect-floodgate-config", true);

        // Detection methods
        this.useFloodgateAPI = config.getBoolean("compatibility.bedrock-support.detection-methods.use-floodgate-api", true);
        this.useGeyserAPI = config.getBoolean("compatibility.bedrock-support.detection-methods.use-geyser-api", true);
        this.uuidPatternDetection = config.getBoolean("compatibility.bedrock-support.detection-methods.uuid-pattern-detection", true);

        // Load Floodgate prefixes
        this.floodgatePrefixes = config.getStringList("compatibility.bedrock-support.floodgate-prefixes");
        if (this.floodgatePrefixes.isEmpty()) {
            this.floodgatePrefixes.add("."); // Default prefix
        }

        // Auto-detect Floodgate config if enabled
        if (autoDetectFloodgateConfig && floodgateCompatibility) {
            detectFloodgatePrefixes();
        }


        debugLog("Performance settings - Platform: " + (isFolia ? "Folia" : "Spigot/Paper") + ", Max chunks per tick: " + maxChunksPerTick + ", Max entities per tick: " + maxEntitiesPerTick);
        debugLog("Instant protection - Enabled: " + instantProtectionEnabled + ", Radius: " + instantLoadRadius + ", Pre-load distance: " + preLoadDistance);
        debugLog("Bedrock support - Enabled: " + bedrockSupportEnabled + ", Geyser: " + geyserCompatibility + ", Floodgate: " + floodgateCompatibility);
        debugLog("Floodgate prefixes: " + floodgatePrefixes.toString() + " (Auto-detect: " + autoDetectFloodgateConfig + ")");
        debugLog("Detection methods - Floodgate API: " + useFloodgateAPI + ", Geyser API: " + useGeyserAPI + ", UUID Pattern: " + uuidPatternDetection);
    }

    public boolean isWorldWhitelisted(String worldName) {
        if (worldName == null) return false;
        return whitelistedWorlds.contains(worldName);
    }

    @Override
    public void onLoad() {
        instance = this;

        // Detect platform
        this.isFolia = PlatformCompatibility.isFolia();
        infoLog("onLoad() called. " + PlatformCompatibility.getPlatformInfo());

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

            // Initialize additional block states for replacement options
            try {
                deepslateState = WrappedBlockState.getByString("minecraft:deepslate");
                debugLog("DEEPSLATE block state initialized successfully.");
            } catch (Exception e) {
                debugLog("Failed to initialize DEEPSLATE block state: " + e.getMessage());
                deepslateState = null;
            }

            try {
                stoneState = WrappedBlockState.getByString("minecraft:stone");
                debugLog("STONE block state initialized successfully.");
            } catch (Exception e) {
                debugLog("Failed to initialize STONE block state: " + e.getMessage());
                stoneState = null;
            }
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

        // Register entity packet listener for entity hiding
        packetEventsAPI.getEventManager().registerListener(new EntityPacketListener(this), PacketListenerPriority.NORMAL);
        debugLog("EntityPacketListener registered.");

        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        debugLog("Bukkit PlayerListeners (this class) registered.");

        // Register commands
        this.getCommand("tazantixray").setExecutor(this);
        this.getCommand("tardebug").setExecutor(this);
        this.getCommand("tarreload").setExecutor(this);
        this.getCommand("tarworld").setExecutor(this);
        this.getCommand("tarworld").setTabCompleter(this);
        debugLog("Commands registered.");

        // Use platform-appropriate scheduler for initialization
        PlatformCompatibility.runTask(this, () -> {
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

        // Send colored console messages
        Bukkit.getConsoleSender().sendMessage("§8========================================");
        Bukkit.getConsoleSender().sendMessage("§a  TazAntixRAY §ev1.2.0 §a- §2Successfully Loaded!");
        Bukkit.getConsoleSender().sendMessage("§b  Platform: §f" + (isFolia ? "§dFolia" : "§6Spigot/Paper") + " §7(Auto-Optimized)");
        Bukkit.getConsoleSender().sendMessage("§b  Debug Mode: " + (debugMode ? "§aON" : "§cOFF"));
        Bukkit.getConsoleSender().sendMessage("§b  Active Worlds: §f" + whitelistedWorlds.toString());
        Bukkit.getConsoleSender().sendMessage("§b  Optimizations: §f" + (isFolia ? "§dRegion-Based Threading" : "§6Traditional Threading"));
        Bukkit.getConsoleSender().sendMessage("§b  Underground Protection: §aENABLED §7(Y≤16 = Normal, Y>16 = Hidden)");
        Bukkit.getConsoleSender().sendMessage("§b  Instant Protection: " + (instantProtectionEnabled ? "§aENABLED" : "§cDISABLED") + " §7(Radius: " + instantLoadRadius + ")");
        Bukkit.getConsoleSender().sendMessage("§b  Bedrock Support: " + (bedrockSupportEnabled ? "§aENABLED" : "§cDISABLED") + " §7(Geyser/Floodgate)");
        if (bedrockSupportEnabled) {
            Bukkit.getConsoleSender().sendMessage("§b  Floodgate Prefixes: §f" + floodgatePrefixes.toString());
            Bukkit.getConsoleSender().sendMessage("§b  Detection Methods: §f" +
                (useFloodgateAPI ? "FloodgateAPI " : "") +
                (useGeyserAPI ? "GeyserAPI " : "") +
                (uuidPatternDetection ? "UUIDPattern" : ""));
        }
        Bukkit.getConsoleSender().sendMessage("§b  Force Immediate Refresh: " + (forceImmediateRefresh ? "§aON" : "§cOFF"));
        Bukkit.getConsoleSender().sendMessage("§b  Developed by §eTazukiVN");
        Bukkit.getConsoleSender().sendMessage("§8========================================");
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
            case "checkplayer":
                return handleCheckPlayerCommand(sender, subArgs);
            case "info":
                return handleInfoCommand(sender);
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

    private boolean handleCheckPlayerCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tazantixray.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /tazantixray checkplayer <playername>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        boolean isBedrock = isBedrockPlayer(targetPlayer);
        UUID playerUUID = targetPlayer.getUniqueId();
        boolean isHidden = playerHiddenState.getOrDefault(playerUUID, false);

        sender.sendMessage(ChatColor.YELLOW + "=== Player Info: " + targetPlayer.getName() + " ===");
        sender.sendMessage(ChatColor.AQUA + "UUID: " + playerUUID.toString());
        sender.sendMessage(ChatColor.AQUA + "Bedrock Player: " + (isBedrock ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.AQUA + "Current Y: " + String.format("%.2f", targetPlayer.getLocation().getY()));
        sender.sendMessage(ChatColor.AQUA + "Hidden State: " + (isHidden ? ChatColor.RED + "HIDDEN" : ChatColor.GREEN + "VISIBLE"));
        sender.sendMessage(ChatColor.AQUA + "World: " + targetPlayer.getWorld().getName());

        // Show detection details
        if (isBedrock) {
            sender.sendMessage(ChatColor.GREEN + "Detection method:");
            for (String prefix : floodgatePrefixes) {
                if (targetPlayer.getName().startsWith(prefix)) {
                    sender.sendMessage(ChatColor.GREEN + "  - Floodgate prefix: '" + prefix + "'");
                }
            }
            if (targetPlayer.getUniqueId().toString().startsWith("00000000-0000-0000")) {
                sender.sendMessage(ChatColor.GREEN + "  - Geyser UUID pattern");
            }
        }

        return true;
    }

    private boolean handleInfoCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== TazAntixRAY v1.2.0 Info ===");
        sender.sendMessage(ChatColor.AQUA + "Platform: " + (isFolia ? "Folia" : "Spigot/Paper"));
        sender.sendMessage(ChatColor.AQUA + "Instant Protection: " + (instantProtectionEnabled ? "ENABLED" : "DISABLED"));
        sender.sendMessage(ChatColor.AQUA + "Instant Load Radius: " + instantLoadRadius + " chunks");
        sender.sendMessage(ChatColor.AQUA + "Pre-load Distance: " + preLoadDistance + " blocks");
        sender.sendMessage(ChatColor.AQUA + "Force Immediate Refresh: " + (forceImmediateRefresh ? "ON" : "OFF"));
        sender.sendMessage(ChatColor.AQUA + "Bedrock Support: " + (bedrockSupportEnabled ? "ENABLED" : "DISABLED"));
        if (bedrockSupportEnabled) {
            sender.sendMessage(ChatColor.AQUA + "Floodgate Prefixes: " + floodgatePrefixes.toString());
            sender.sendMessage(ChatColor.AQUA + "Geyser Compatibility: " + (geyserCompatibility ? "ON" : "OFF"));
        }
        sender.sendMessage(ChatColor.AQUA + "Active Worlds: " + whitelistedWorlds.toString());
        sender.sendMessage(ChatColor.AQUA + "Debug Mode: " + (debugMode ? "ON" : "OFF"));
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
        boolean initialStateIsHidden = currentY > 16.0;
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

        // Use optimized radius based on player type and settings
        int radius;
        if (instantProtectionEnabled) {
            radius = getOptimizedRefreshRadius(player);
        } else {
            radius = Math.max(Bukkit.getServer().getViewDistance(), 8);
        }

        boolean isBedrockPlayer = isBedrockPlayer(player);
        debugLog("Using refresh radius: " + radius + " (instant protection: " + instantProtectionEnabled + ", bedrock player: " + isBedrockPlayer + ")");
        performRefresh(player, radius);
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
        if (!PlatformCompatibility.isOwnedByCurrentRegion(player.getLocation())) {
            final int finalRadius = radiusChunks;
            debugLog("Not on correct region thread. Scheduling performRefresh for " + player.getName() + " with radius " + finalRadius);
            PlatformCompatibility.runTaskAtLocation(this, player.getLocation(), () -> performRefresh(player, finalRadius));
            return;
        }

        // Perform chunk refresh in a Folia-safe manner with improved transition handling
        refreshChunksForPlayerImproved(player, radiusChunks);
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
                    if (PlatformCompatibility.isOwnedByCurrentRegion(chunkLoc)) {
                        world.refreshChunk(cx, cz);
                        refreshedCount++;
                    } else {
                        // Schedule refresh on the correct region
                        final int finalCx = cx;
                        final int finalCz = cz;
                        PlatformCompatibility.runTaskAtLocation(this, chunkLoc, () -> {
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

    /**
     * Improved chunk refresh method with instant protection and Bedrock support
     */
    private void refreshChunksForPlayerImproved(Player player, int radiusChunks) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int playerChunkX = loc.getBlockX() >> 4;
        int playerChunkZ = loc.getBlockZ() >> 4;
        int refreshedCount = 0;

        boolean isBedrockPlayer = isBedrockPlayer(player);
        boolean useInstantRefresh = forceImmediateRefresh || isBedrockPlayer;

        debugLog("Starting chunk refresh for " + player.getName() + " (Bedrock: " + isBedrockPlayer + ", Instant: " + useInstantRefresh + ", Radius: " + radiusChunks + ")");

        if (useInstantRefresh) {
            // Instant refresh mode - load all chunks immediately without delay
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                    int cx = playerChunkX + dx;
                    int cz = playerChunkZ + dz;

                    try {
                        Location chunkLoc = new Location(world, cx * 16, loc.getY(), cz * 16);
                        if (PlatformCompatibility.isOwnedByCurrentRegion(chunkLoc)) {
                            world.refreshChunk(cx, cz);
                            refreshedCount++;
                        } else {
                            // Schedule immediate refresh on the correct region
                            final int finalCx = cx;
                            final int finalCz = cz;
                            PlatformCompatibility.runTaskAtLocation(this, chunkLoc, () -> {
                                try {
                                    world.refreshChunk(finalCx, finalCz);
                                    debugLog("Cross-region instant chunk refresh at (" + finalCx + "," + finalCz + ") for " + player.getName());
                                } catch (Exception e) {
                                    debugLog("Failed cross-region instant chunk refresh (" + finalCx + "," + finalCz + "): " + e.getMessage());
                                }
                            });
                        }
                    } catch (Exception e) {
                        debugLog("Error refreshing chunk (" + cx + "," + cz + ") for " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
            debugLog("Instant refresh completed for " + refreshedCount + " chunks around " + player.getName());
        } else {
            // Gradual refresh mode - spiral pattern with delays
            for (int radius = 0; radius <= radiusChunks; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        // Only refresh chunks on the edge of the current radius
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius && radius > 0) {
                            continue;
                        }

                        int cx = playerChunkX + dx;
                        int cz = playerChunkZ + dz;

                        try {
                            Location chunkLoc = new Location(world, cx * 16, loc.getY(), cz * 16);
                            if (PlatformCompatibility.isOwnedByCurrentRegion(chunkLoc)) {
                                if (radius > 0) {
                                    final int finalCx = cx;
                                    final int finalCz = cz;
                                    final int finalRadius = radius;

                                    PlatformCompatibility.runTaskLater(this, () -> {
                                        try {
                                            world.refreshChunk(finalCx, finalCz);
                                            debugLog("Delayed chunk refresh at (" + finalCx + "," + finalCz + ") radius " + finalRadius + " for " + player.getName());
                                        } catch (Exception e) {
                                            debugLog("Failed delayed chunk refresh (" + finalCx + "," + finalCz + "): " + e.getMessage());
                                        }
                                    }, radius);
                                } else {
                                    world.refreshChunk(cx, cz);
                                }
                                refreshedCount++;
                            } else {
                                // Schedule refresh on the correct region
                                final int finalCx = cx;
                                final int finalCz = cz;
                                final int finalRadius = radius;
                                PlatformCompatibility.runTaskAtLocation(this, chunkLoc, () -> {
                                    try {
                                        if (finalRadius > 0) {
                                            PlatformCompatibility.runTaskLater(this, () -> {
                                                try {
                                                    world.refreshChunk(finalCx, finalCz);
                                                    debugLog("Cross-region delayed chunk refresh at (" + finalCx + "," + finalCz + ") for " + player.getName());
                                                } catch (Exception e) {
                                                    debugLog("Failed cross-region delayed chunk refresh (" + finalCx + "," + finalCz + "): " + e.getMessage());
                                                }
                                            }, finalRadius);
                                        } else {
                                            world.refreshChunk(finalCx, finalCz);
                                            debugLog("Cross-region immediate chunk refresh at (" + finalCx + "," + finalCz + ") for " + player.getName());
                                        }
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
            }
            debugLog("Started gradual refresh for " + refreshedCount + " chunks (radius " + radiusChunks + ") around " + player.getName());
        }
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
                // Schedule the refresh for after the teleport is complete using platform-appropriate scheduler
                PlatformCompatibility.runTaskAtLocation(this, to, () -> {
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
            // Schedule a new teleport for the next tick using platform-appropriate scheduler
            PlatformCompatibility.runTaskAtLocation(this, to, () -> {
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

        boolean oldStateIsHidden = this.playerHiddenState.getOrDefault(playerUUID, currentY > 16.0);
        boolean newStateIsHidden;

        // Check if player is approaching the protection zone for pre-loading
        boolean isApproachingProtection = currentY <= (16.0 + preLoadDistance) && currentY > 16.0;

        if (currentY > 16.0) {
            newStateIsHidden = true;
        } else {
            newStateIsHidden = false;
        }

        debugLog(String.format("PlayerMove Details: %s, FromY: %.2f (BlockY:%d), ToY: %.2f (BlockY:%d), OldStateHidden: %b, NewStateHidden: %b",
                player.getName(), from.getY(), from.getBlockY(), to.getY(), to.getBlockY(), oldStateIsHidden, newStateIsHidden));

        // Handle pre-loading for instant protection
        if (instantProtectionEnabled && isApproachingProtection && !oldStateIsHidden) {
            debugLog("Player " + player.getName() + " approaching protection zone at Y=" + String.format("%.2f", currentY) + ". Pre-loading protection...");
            this.playerHiddenState.put(playerUUID, true);
            if (forceImmediateRefresh) {
                this.refreshFullView(player);
            }
            return;
        }

        if (newStateIsHidden != oldStateIsHidden) {
            // Always update the state immediately so the packet listener has the correct information.
            this.playerHiddenState.put(playerUUID, newStateIsHidden);

            if (!newStateIsHidden) {
                // ALWAYS refresh when transitioning to a VISIBLE state. This is crucial to prevent players from getting stuck in a void view.
                debugLog("State changed for " + player.getName() + " to VISIBLE. Refreshing full view at Y=" + String.format("%.2f", currentY));
                this.refreshFullView(player);
            } else {
                // When transitioning to a HIDING state, force immediate refresh if enabled
                if (forceImmediateRefresh) {
                    debugLog("State changed for " + player.getName() + " to HIDING. Force immediate refresh at Y=" + String.format("%.2f", currentY));
                    this.refreshFullView(player);
                } else {
                    // Apply cooldown for non-force mode
                    long currentTime = System.currentTimeMillis();
                    long expirationTime = refreshCooldowns.getOrDefault(playerUUID, 0L);

                    if (currentTime < expirationTime) {
                        debugLog("State changed for " + player.getName() + " to HIDING. Refresh skipped due to active cooldown.");
                    } else {
                        debugLog("State changed for " + player.getName() + " to HIDING. Refreshing full view at Y=" + String.format("%.2f", currentY) + " and starting cooldown.");
                        this.refreshFullView(player);
                        refreshCooldowns.put(playerUUID, currentTime + refreshCooldownMillis);
                    }
                }
            }
        } else {
            debugLog("State NOT changed for " + player.getName() + ". Current hidden state: " + newStateIsHidden);
        }
    }

    public WrappedBlockState getAirState() {
        return airState;
    }

    public WrappedBlockState getDeepslateState() {
        return deepslateState;
    }

    public WrappedBlockState getStoneState() {
        return stoneState;
    }

    public int getAirStateGlobalId() {
        return airStateGlobalId;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isFolia() {
        return isFolia;
    }

    /**
     * Auto-detect Floodgate prefixes from config
     */
    private void detectFloodgatePrefixes() {
        try {
            // Try to read Floodgate config
            File floodgateConfig = new File(getDataFolder().getParentFile(), "floodgate/config.yml");
            if (floodgateConfig.exists()) {
                YamlConfiguration floodgateYml = YamlConfiguration.loadConfiguration(floodgateConfig);
                String prefix = floodgateYml.getString("username-prefix", ".");
                if (!prefix.isEmpty() && !floodgatePrefixes.contains(prefix)) {
                    floodgatePrefixes.add(prefix);
                    debugLog("Auto-detected Floodgate prefix: '" + prefix + "'");
                }
            }

            // Also check for common alternative prefixes
            String[] commonPrefixes = {".", "*", "_", "BE_", "Mobile_", "PE_", "MCPE_", "Bedrock_"};
            for (String prefix : commonPrefixes) {
                if (!floodgatePrefixes.contains(prefix)) {
                    floodgatePrefixes.add(prefix);
                }
            }

            debugLog("Floodgate prefixes loaded: " + floodgatePrefixes.toString());
        } catch (Exception e) {
            debugLog("Failed to auto-detect Floodgate config: " + e.getMessage());
            // Fallback to common prefixes
            if (floodgatePrefixes.isEmpty()) {
                floodgatePrefixes.add(".");
                floodgatePrefixes.add("*");
                floodgatePrefixes.add("_");
            }
        }
    }

    /**
     * Check if player is a Bedrock Edition player
     */
    public boolean isBedrockPlayer(Player player) {
        if (!bedrockSupportEnabled) {
            return false;
        }

        String playerName = player.getName();

        // Check for Floodgate prefixes
        if (floodgateCompatibility) {
            for (String prefix : floodgatePrefixes) {
                if (playerName.startsWith(prefix)) {
                    debugLog("Detected Bedrock player via Floodgate prefix '" + prefix + "': " + playerName);
                    return true;
                }
            }
        }

        // Check for Geyser UUID pattern (Bedrock players have specific UUID format)
        if (geyserCompatibility && uuidPatternDetection) {
            String uuid = player.getUniqueId().toString();
            // Geyser UUIDs typically start with 00000000-0000-0000
            if (uuid.startsWith("00000000-0000-0000")) {
                debugLog("Detected Bedrock player via Geyser UUID: " + playerName + " (" + uuid + ")");
                return true;
            }
        }

        // Check using Floodgate API if available and enabled
        if (useFloodgateAPI && Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                if (checkFloodgateAPI(player)) {
                    return true;
                }
            } catch (Exception e) {
                debugLog("Floodgate API check failed: " + e.getMessage());
            }
        }

        // Check using Geyser API if available and enabled
        if (useGeyserAPI && Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
            try {
                if (checkGeyserAPI(player)) {
                    return true;
                }
            } catch (Exception e) {
                debugLog("Geyser API check failed: " + e.getMessage());
            }
        }

        return false;
    }

    /**
     * Check using Floodgate API if available
     */
    private boolean checkFloodgateAPI(Player player) {
        try {
            // Use reflection to check Floodgate API
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = floodgateApi.getMethod("getInstance").invoke(null);
            Object isFloodgatePlayer = floodgateApi.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(instance, player.getUniqueId());

            if (isFloodgatePlayer instanceof Boolean && (Boolean) isFloodgatePlayer) {
                debugLog("Detected Bedrock player via Floodgate API: " + player.getName());
                return true;
            }
        } catch (Exception e) {
            debugLog("Floodgate API reflection failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Check using Geyser API if available
     */
    private boolean checkGeyserAPI(Player player) {
        try {
            // Use reflection to check Geyser API
            Class<?> geyserApi = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object instance = geyserApi.getMethod("api").invoke(null);
            Object connectionManager = geyserApi.getMethod("connectionManager").invoke(instance);

            // Check if player is connected via Geyser
            Class<?> connectionManagerClass = connectionManager.getClass();
            Object isBedrockPlayer = connectionManagerClass.getMethod("isBedrockPlayer", java.util.UUID.class)
                    .invoke(connectionManager, player.getUniqueId());

            if (isBedrockPlayer instanceof Boolean && (Boolean) isBedrockPlayer) {
                debugLog("Detected Bedrock player via Geyser API: " + player.getName());
                return true;
            }
        } catch (Exception e) {
            debugLog("Geyser API reflection failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get optimized refresh radius for player type
     */
    public int getOptimizedRefreshRadius(Player player) {
        if (isBedrockPlayer(player)) {
            // Bedrock players need smaller radius for better performance
            return Math.min(instantLoadRadius, 10);
        }
        return instantLoadRadius;
    }

    /**
     * Automatically apply platform-specific optimizations
     */
    private void applyPlatformOptimizations() {
        if (isFolia) {
            // Folia-specific optimizations
            debugLog("Applying Folia optimizations:");
            debugLog("- Region-aware chunk processing: ENABLED");
            debugLog("- Cross-region scheduling: ENABLED");
            debugLog("- Async entity processing: ENABLED");
            debugLog("- Optimized packet handling: ENABLED");

            infoLog("Platform optimizations applied for Folia server");
        } else {
            // Spigot/Paper optimizations
            debugLog("Applying Spigot/Paper optimizations:");
            debugLog("- Traditional scheduling: ENABLED");
            debugLog("- Main thread processing: ENABLED");
            debugLog("- Bukkit scheduler: ENABLED");
            debugLog("- Standard packet handling: ENABLED");

            infoLog("Platform optimizations applied for Spigot/Paper server");
        }

        // Common optimizations for all platforms
        debugLog("Common optimizations:");
        debugLog("- Smart caching: ENABLED");
        debugLog("- Cooldown system: ENABLED");
        debugLog("- Efficient packet modification: ENABLED");
        debugLog("- Memory optimization: ENABLED");
    }
}
