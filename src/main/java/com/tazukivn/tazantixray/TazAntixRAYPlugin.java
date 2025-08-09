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
    private WrappedBlockState replacementBlockState;
    private int replacementBlockGlobalId = 0;
    private String replacementBlockType = "air";
    private boolean debugMode = false;
    private int refreshCooldownMillis = 3000;
    private Set<String> whitelistedWorlds = new HashSet<>();

    // Performance settings
    private boolean limitedAreaEnabled = false;
    private int limitedAreaChunkRadius = 3;
    private boolean instantProtectionEnabled = true;
    private int instantLoadRadius = 15;
    private int preLoadDistance = 10;
    private boolean forceImmediateRefresh = true;
    private boolean undergroundProtectionEnabled = true;

    // Language support
    private FileConfiguration langConfig;
    private String currentLanguage = "en";

    // Platform-specific optimizers
    private FoliaOptimizer foliaOptimizer;
    private PaperOptimizer paperOptimizer;

    // Geyser/Floodgate support
    private GeyserFloodgateSupport geyserFloodgateSupport;

    // Entity hiding support
    private EntityHider entityHider;

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

        // Load debug mode from config
        this.debugMode = config.getBoolean("settings.debug-mode", false);

        // Load other settings
        double protectionY = config.getDouble("antixray.protection-y-level", 31.0);
        int hideBelow = config.getInt("antixray.hide-below-y", 16);
        int transitionZone = config.getInt("antixray.transition.transition-zone-size", 5);

        // Load block replacement type
        this.replacementBlockType = config.getString("performance.replacement.block-type", "air");
        if (!this.replacementBlockType.startsWith("minecraft:")) {
            this.replacementBlockType = "minecraft:" + this.replacementBlockType;
        }

        // Load performance settings
        this.limitedAreaEnabled = config.getBoolean("performance.limited-area.enabled", false);
        this.limitedAreaChunkRadius = config.getInt("performance.limited-area.chunk-radius", 3);
        this.instantProtectionEnabled = config.getBoolean("performance.instant-protection.enabled", true);
        this.instantLoadRadius = config.getInt("performance.instant-protection.instant-load-radius", 15);
        this.preLoadDistance = config.getInt("performance.instant-protection.pre-load-distance", 10);
        this.forceImmediateRefresh = config.getBoolean("performance.instant-protection.force-immediate-refresh", true);
        this.undergroundProtectionEnabled = config.getBoolean("performance.underground-protection.enabled", true);

        debugLog("Anti-XRay settings - Protection Y: " + protectionY + ", Hide below Y: " + hideBelow + ", Transition zone: " + transitionZone + ", Replacement block: " + this.replacementBlockType);
        debugLog("Performance settings - Limited area: " + limitedAreaEnabled + ", Instant protection: " + instantProtectionEnabled + ", Underground protection: " + undergroundProtectionEnabled);

        // Reload entity hider settings
        if (entityHider != null) {
            entityHider.loadSettings();
        }

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

        // Initialize PacketEvents with comprehensive error handling
        try {
            PacketEventsAPI packetEventsAPI = PacketEvents.getAPI();
            if (packetEventsAPI == null) {
                getLogger().severe("=== PACKETEVENTS ERROR ===");
                getLogger().severe("PacketEvents API is null!");
                getLogger().severe("Make sure PacketEvents plugin is installed and loaded BEFORE TazAntixRAY.");
                getLogger().severe("Download from: https://github.com/retrooper/packetevents/releases");
                getLogger().severe("========================");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            getLogger().info("PacketEvents found. Version: " + packetEventsAPI.getVersion());
            getLogger().info("Server: " + getServer().getVersion());
            getLogger().info("Java: " + System.getProperty("java.version"));

            packetEventsAPI.load();
            if (!packetEventsAPI.isLoaded()) {
                getLogger().severe("=== PACKETEVENTS LOAD ERROR ===");
                getLogger().severe("PacketEvents failed to load!");
                getLogger().severe("This may be due to version incompatibility:");
                getLogger().severe("- Server version: " + getServer().getVersion());
                getLogger().severe("- PacketEvents version: " + packetEventsAPI.getVersion());
                getLogger().severe("- Java version: " + System.getProperty("java.version"));
                getLogger().severe("Try updating PacketEvents to the latest version.");
                getLogger().severe("==============================");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            getLogger().info("PacketEvents loaded successfully!");

        } catch (NoClassDefFoundError e) {
            getLogger().severe("=== PACKETEVENTS NOT FOUND ===");
            getLogger().severe("PacketEvents plugin is not installed!");
            getLogger().severe("Download PackketEvents from:");
            getLogger().severe("https://github.com/retrooper/packetevents/releases");
            getLogger().severe("Install it in your plugins folder and restart.");
            getLogger().severe("=============================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (Exception e) {
            getLogger().severe("=== PACKETEVENTS INITIALIZATION ERROR ===");
            getLogger().severe("Failed to initialize PackketEvents: " + e.getMessage());
            getLogger().severe("Error type: " + e.getClass().getSimpleName());
            getLogger().severe("This usually indicates a compatibility issue.");
            getLogger().severe("Try updating both PacketEvents and your server.");
            getLogger().severe("========================================");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onEnable() {
        // Display beautiful startup banner with colors
        String[] banner = MessageFormatter.createStartupBanner("1.2.1",
            PlatformCompatibility.isFolia() ? "Folia" : "Paper/Spigot");
        for (String line : banner) {
            if (line.trim().isEmpty()) {
                getServer().getConsoleSender().sendMessage("");
            } else {
                getServer().getConsoleSender().sendMessage(MessageFormatter.format(line));
            }
        }

        // Initialize platform-specific optimizers
        if (PlatformCompatibility.isFolia() && FoliaOptimizer.shouldUseFoliaOptimizations()) {
            foliaOptimizer = new FoliaOptimizer(this);
        } else if (PaperOptimizer.shouldUsePaperOptimizations()) {
            paperOptimizer = new PaperOptimizer(this);
        }

        // Initialize Geyser/Floodgate support
        geyserFloodgateSupport = new GeyserFloodgateSupport(this);

        // Initialize Entity Hider
        entityHider = new EntityHider(this);

        loadConfigValues();

        final PacketEventsAPI packetEventsAPI = PacketEvents.getAPI();
        if (packetEventsAPI == null || !packetEventsAPI.isLoaded()) {
            getLogger().severe(getMessage("error.packetevents-not-available"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        debugLog("PacketEvents API confirmed available and loaded in onEnable.");

        try {
            replacementBlockState = WrappedBlockState.getByString(replacementBlockType);
            if (replacementBlockState == null) {
                throw new IllegalStateException("WrappedBlockState.getByString(\"" + replacementBlockType + "\") returned null.");
            }
            replacementBlockGlobalId = replacementBlockState.getGlobalId();
            debugLog("Replacement block state initialized successfully. Type: " + replacementBlockType + ", Global ID: " + replacementBlockGlobalId);
        } catch (Exception e) {
            getLogger().warning("Failed to initialize replacement block state (" + replacementBlockType + "): " + e.getMessage());

            // Try common fallback blocks in order
            String[] fallbackBlocks = {"minecraft:air", "minecraft:stone", "minecraft:dirt"};
            boolean fallbackSuccess = false;

            for (String fallbackBlock : fallbackBlocks) {
                try {
                    replacementBlockState = WrappedBlockState.getByString(fallbackBlock);
                    if (replacementBlockState != null) {
                        replacementBlockGlobalId = replacementBlockState.getGlobalId();
                        this.replacementBlockType = fallbackBlock;
                        getLogger().info("Successfully fell back to " + fallbackBlock + " for block replacement");
                        fallbackSuccess = true;
                        break;
                    }
                } catch (Exception e2) {
                    // Continue to next fallback
                }
            }

            if (!fallbackSuccess) {
                getLogger().severe("Failed to initialize any fallback block state. This may indicate a PacketEvents compatibility issue.");
                replacementBlockState = null;
            }
        }

        if (replacementBlockState == null) {
            getLogger().severe("Could not initialize any replacement block state. Plugin will be disabled.");
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
        try {
            if (this.getCommand("tazantixray") != null) {
                this.getCommand("tazantixray").setExecutor(this);
                this.getCommand("tazantixray").setTabCompleter(this);
            }
            if (this.getCommand("tardebug") != null) {
                this.getCommand("tardebug").setExecutor(this);
            }
            if (this.getCommand("tarreload") != null) {
                this.getCommand("tarreload").setExecutor(this);
            }
            if (this.getCommand("tarworld") != null) {
                this.getCommand("tarworld").setExecutor(this);
                this.getCommand("tarworld").setTabCompleter(this);
            }
            debugLog("Commands registered successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
        }

        // Use platform-compatible scheduler for global tasks
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


    }

    @Override
    public void onDisable() {
        infoLog("onDisable() called.");

        // Display beautiful shutdown banner with colors
        String[] banner = MessageFormatter.createShutdownBanner();
        for (String line : banner) {
            if (line.trim().isEmpty()) {
                getServer().getConsoleSender().sendMessage("");
            } else {
                getServer().getConsoleSender().sendMessage(MessageFormatter.format(line));
            }
        }

        if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded()) {
            PacketEvents.getAPI().terminate();
            debugLog("PacketEvents API terminated.");
        }

        // Shutdown optimizers
        if (foliaOptimizer != null) {
            getLogger().info("Shutting down Folia optimizer");
        }
        if (paperOptimizer != null) {
            paperOptimizer.shutdown();
            getLogger().info("Shutting down Paper optimizer");
        }

        playerHiddenState.clear();
        getLogger().info("TazAntixRAY v1.2.1 has been disabled.");
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
            MessageFormatter.sendError(sender, MessageFormatter.createPermissionDenied());
            return true;
        }

        if (args.length == 0) {
            MessageFormatter.sendInfo(sender, "Usage: /tazantixray <debug|reload|world|stats|entities> [args]");
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
            case "stats":
                return handleStatsCommand(sender);
            case "entities":
                return handleEntitiesCommand(sender);
            case "test":
                return handleTestCommand(sender, subArgs);
            default:
                MessageFormatter.sendError(sender, "Unknown subcommand: " + subCommand);
                MessageFormatter.sendInfo(sender, "Available commands: debug, reload, world, stats, entities, test");
                return true;
        }
    }

    private boolean handleDebugCommand(CommandSender sender) {
        if (!sender.hasPermission("tazantixray.admin")) {
            MessageFormatter.sendError(sender, MessageFormatter.createPermissionDenied());
            return true;
        }
        debugMode = !debugMode;
        getConfig().set("settings.debug-mode", debugMode);
        saveConfig();

        String status = debugMode ? "§aENABLED" : "§7DISABLED";
        MessageFormatter.sendSuccess(sender, "Debug mode " + status);
        getLogger().info("Debug mode " + (debugMode ? "ENABLED" : "DISABLED") + " by " + sender.getName());
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("tazantixray.admin")) {
            MessageFormatter.sendError(sender, MessageFormatter.createPermissionDenied());
            return true;
        }
        reloadConfig();
        loadConfigValues();
        MessageFormatter.sendSuccess(sender, MessageFormatter.createReloadSuccess());
        MessageFormatter.sendInfo(sender, "Active worlds: §b" + whitelistedWorlds.toString());
        getLogger().info("Configuration reloaded by " + sender.getName() + ". Active worlds: " + whitelistedWorlds.toString());

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
        if (!sender.hasPermission("tazantixray.admin")) {
            MessageFormatter.sendError(sender, MessageFormatter.createPermissionDenied());
            return true;
        }

        if (args.length < 1) {
            MessageFormatter.sendInfo(sender, "Usage: /tarworld <add|remove|list> [world]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("list")) {
            if (whitelistedWorlds.isEmpty()) {
                MessageFormatter.sendInfo(sender, "No worlds are currently whitelisted for anti-xray protection.");
            } else {
                MessageFormatter.sendSuccess(sender, "Whitelisted worlds: " + String.join(", ", whitelistedWorlds));
            }
            return true;
        }

        if (args.length < 2) {
            MessageFormatter.sendError(sender, "Please specify a world name.");
            MessageFormatter.sendInfo(sender, "Usage: /tarworld <add|remove> <world>");
            return true;
        }

        String worldName = args[1];
        World targetWorld = Bukkit.getWorld(worldName);

        if (targetWorld == null && subCommand.equals("add")) {
            MessageFormatter.sendError(sender, MessageFormatter.createPlayerNotFound(worldName).replace("Player", "World"));
            return true;
        }

        if (subCommand.equals("add")) {
            if (whitelistedWorlds.add(worldName)) {
                getConfig().set("worlds.whitelist", new ArrayList<>(whitelistedWorlds));
                saveConfig();
                MessageFormatter.sendSuccess(sender, "World '" + worldName + "' added to anti-xray protection.");
                getLogger().info("World '" + worldName + "' added to whitelist by " + sender.getName());

                // Update players in this world
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getName().equals(worldName)) {
                        handlePlayerInitialState(p, true);
                    }
                }
            } else {
                MessageFormatter.sendWarning(sender, "World '" + worldName + "' is already in the whitelist.");
            }
        } else if (subCommand.equals("remove")) {
            if (whitelistedWorlds.remove(worldName)) {
                getConfig().set("worlds.whitelist", new ArrayList<>(whitelistedWorlds));
                saveConfig();
                MessageFormatter.sendSuccess(sender, "World '" + worldName + "' removed from anti-xray protection.");
                getLogger().info("World '" + worldName + "' removed from whitelist by " + sender.getName());

                // Update players in this world
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getName().equals(worldName)) {
                        playerHiddenState.remove(p.getUniqueId());
                        refreshFullView(p);
                        debugLog("Reset hidden state and refreshed chunks for " + p.getName() + " in now non-whitelisted world " + worldName);
                    }
                }
            } else {
                MessageFormatter.sendWarning(sender, "World '" + worldName + "' is not in the whitelist.");
            }
        } else {
            MessageFormatter.sendError(sender, "Unknown subcommand: " + subCommand);
            MessageFormatter.sendInfo(sender, "Available commands: add, remove, list");
        }
        return true;
    }

    private boolean handleStatsCommand(CommandSender sender) {
        if (!sender.hasPermission("tazantixray.admin")) {
            MessageFormatter.sendError(sender, MessageFormatter.createPermissionDenied());
            return true;
        }

        MessageFormatter.send(sender, "§3§l=== TazAntixRAY Statistics ===");
        MessageFormatter.send(sender, "§ePlugin Version: §a1.2.1");
        MessageFormatter.send(sender, "§ePlatform: §a" + (PlatformCompatibility.isFolia() ? "Folia" : "Paper/Spigot"));
        MessageFormatter.send(sender, "§eActive Players: §a" + playerHiddenState.size());
        MessageFormatter.send(sender, "§eActive Worlds: §a" + whitelistedWorlds.size());
        MessageFormatter.send(sender, "§eDebug Mode: §a" + (debugMode ? "ENABLED" : "DISABLED"));

        // Geyser/Floodgate stats
        if (geyserFloodgateSupport != null) {
            MessageFormatter.send(sender, "§eGeyser/Floodgate: §a" + geyserFloodgateSupport.getSupportStatus());
        }

        // Entity hiding stats
        if (entityHider != null) {
            MessageFormatter.send(sender, "§eEntity Hiding: §a" + (entityHider.isEntityHidingEnabled() ? "ENABLED" : "DISABLED"));
            MessageFormatter.send(sender, "§eEntity Stats: §a" + entityHider.getStatistics());
        }

        if (foliaOptimizer != null) {
            MessageFormatter.send(sender, "§eFolia Optimizer: §a" + foliaOptimizer.getOptimizationStats());
        }
        if (paperOptimizer != null) {
            MessageFormatter.send(sender, "§ePaper Optimizer: §a" + paperOptimizer.getOptimizationStats());
        }

        MessageFormatter.send(sender, "§3§l========================");
        return true;
    }

    private boolean handleEntitiesCommand(CommandSender sender) {
        if (!sender.hasPermission("tazantixray.admin")) {
            MessageFormatter.sendError(sender, MessageFormatter.createPermissionDenied());
            return true;
        }

        if (entityHider == null) {
            MessageFormatter.sendError(sender, "Entity hiding system not initialized!");
            return true;
        }

        // Toggle entity hiding
        boolean currentState = getConfig().getBoolean("performance.entities.hide-entities", true);
        boolean newState = !currentState;

        getConfig().set("performance.entities.hide-entities", newState);
        saveConfig();

        // Reload settings
        entityHider.loadSettings();

        // Update all players
        if (newState) {
            entityHider.refreshAllPlayers();
            MessageFormatter.sendSuccess(sender, "Entity hiding §aENABLED");
        } else {
            entityHider.refreshAllPlayers();
            MessageFormatter.sendSuccess(sender, "Entity hiding §cDISABLED");
        }

        getLogger().info("Entity hiding " + (newState ? "ENABLED" : "DISABLED") + " by " + sender.getName());
        return true;
    }



    private boolean handleTestCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tazantixray.admin")) {
            MessageFormatter.sendError(sender, MessageFormatter.createPermissionDenied());
            return true;
        }

        if (!(sender instanceof Player)) {
            MessageFormatter.sendError(sender, "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            MessageFormatter.sendInfo(sender, "Test commands:");
            MessageFormatter.sendInfo(sender, "§e/tazantixray test block §7- Test current block replacement");
            MessageFormatter.sendInfo(sender, "§e/tazantixray test state §7- Show your current anti-xray state");
            MessageFormatter.sendInfo(sender, "§e/tazantixray test refresh §7- Force refresh your view");
            return true;
        }

        String testType = args[0].toLowerCase();

        switch (testType) {
            case "block":
                MessageFormatter.sendInfo(sender, "§3=== Block Replacement Test ===");
                MessageFormatter.sendInfo(sender, "§eCurrent replacement block: §b" + replacementBlockType);
                MessageFormatter.sendInfo(sender, "§eGlobal ID: §b" + replacementBlockGlobalId);
                MessageFormatter.sendInfo(sender, "§eBlock state valid: §b" + (replacementBlockState != null));
                if (replacementBlockState != null) {
                    MessageFormatter.sendInfo(sender, "§eBlock type: §b" + replacementBlockState.getType().getName());
                }
                break;

            case "state":
                boolean isHidden = playerHiddenState.getOrDefault(player.getUniqueId(), false);
                double currentY = player.getLocation().getY();
                double protectionY = getConfig().getDouble("antixray.protection-y-level", 31.0);
                boolean worldWhitelisted = isWorldWhitelisted(player.getWorld().getName());

                MessageFormatter.sendInfo(sender, "§3=== Anti-XRay State Test ===");
                MessageFormatter.sendInfo(sender, "§eYour Y level: §b" + String.format("%.1f", currentY));
                MessageFormatter.sendInfo(sender, "§eProtection Y level: §b" + protectionY);
                MessageFormatter.sendInfo(sender, "§eWorld whitelisted: §b" + worldWhitelisted);
                MessageFormatter.sendInfo(sender, "§eAnti-XRay active: §b" + isHidden);
                MessageFormatter.sendInfo(sender, "§eUnderground protection: §b" + undergroundProtectionEnabled);
                break;

            case "refresh":
                if (isWorldWhitelisted(player.getWorld().getName())) {
                    refreshFullView(player);
                    MessageFormatter.sendSuccess(sender, "View refreshed! Move between Y levels to test.");
                } else {
                    MessageFormatter.sendWarning(sender, "Your world is not whitelisted for anti-xray.");
                }
                break;

            default:
                MessageFormatter.sendError(sender, "Unknown test type: " + testType);
                MessageFormatter.sendInfo(sender, "Available tests: block, state, refresh");
                break;
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
                return StringUtil.copyPartialMatches(args[0], Arrays.asList("debug", "reload", "world", "stats", "entities", "test"), new ArrayList<>());
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("world")) {
                    return StringUtil.copyPartialMatches(args[1], Arrays.asList("list", "add", "remove"), new ArrayList<>());
                } else if (args[0].equalsIgnoreCase("test")) {
                    return StringUtil.copyPartialMatches(args[1], Arrays.asList("block", "state", "refresh"), new ArrayList<>());
                }
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
        double protectionY = getConfig().getDouble("antixray.protection-y-level", 31.0);
        boolean initialStateIsHidden = undergroundProtectionEnabled && currentY >= protectionY;
        playerHiddenState.put(player.getUniqueId(), initialStateIsHidden);
        debugLog("Player " + player.getName() + " at Y=" + String.format("%.2f", currentY) + ". Initial hidden state: " + initialStateIsHidden);

        // Update entity visibility
        if (entityHider != null) {
            entityHider.updateEntityVisibility(player);
        }

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
        if (!PlatformCompatibility.isOwnedByCurrentRegion(player.getLocation())) {
            final int finalRadius = radiusChunks;
            debugLog("Not on correct region thread. Scheduling performRefresh for " + player.getName() + " with radius " + finalRadius);
            PlatformCompatibility.runTask(this, player.getLocation(), () -> performRefresh(player, finalRadius));
            return;
        }

        // Perform chunk refresh using platform-specific optimizations
        refreshChunksForPlayerOptimized(player, radiusChunks);
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
                        PlatformCompatibility.runTask(this, chunkLoc, () -> {
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
     * Optimized chunk refresh using platform-specific optimizers
     */
    private void refreshChunksForPlayerOptimized(Player player, int radiusChunks) {
        // Apply limited area settings if enabled
        int finalRadius = radiusChunks;
        if (limitedAreaEnabled) {
            finalRadius = Math.min(radiusChunks, limitedAreaChunkRadius);
            debugLog("Limited area enabled. Reduced radius from " + radiusChunks + " to " + finalRadius + " for " + player.getName());
        }

        // Optimize radius for Bedrock players
        if (geyserFloodgateSupport != null && geyserFloodgateSupport.isBedrockPlayer(player)) {
            finalRadius = geyserFloodgateSupport.getOptimizedChunkRadius(player, finalRadius);
            debugLog("Optimized chunk radius for Bedrock player " + player.getName() + ": " + finalRadius);
        }

        // Apply instant protection if enabled
        if (instantProtectionEnabled && forceImmediateRefresh) {
            double playerY = player.getLocation().getY();
            double protectionY = getConfig().getDouble("antixray.protection-y-level", 31.0);
            int hideBelow = getConfig().getInt("antixray.hide-below-y", 16);

            // If player is approaching the protection zone, use instant load radius
            if (playerY <= protectionY + preLoadDistance && playerY > hideBelow) {
                finalRadius = Math.max(finalRadius, instantLoadRadius);
                debugLog("Instant protection triggered for " + player.getName() + ". Using radius: " + finalRadius);
            }
        }

        if (foliaOptimizer != null) {
            // Use Folia-specific optimizations
            foliaOptimizer.refreshChunksOptimized(player, finalRadius);
        } else if (paperOptimizer != null) {
            // Use Paper/Spigot-specific optimizations
            paperOptimizer.refreshChunksOptimized(player, finalRadius);
        } else {
            // Fallback to original method
            refreshChunksForPlayer(player, finalRadius);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        infoLog("onPlayerJoin CALLED for: " + player.getName() + " in world " + player.getWorld().getName());

        // Check if this is a Bedrock player
        if (geyserFloodgateSupport != null && geyserFloodgateSupport.isBedrockPlayer(player)) {
            debugLog("Bedrock player joined: " + player.getName());
        }

        if (isWorldWhitelisted(player.getWorld().getName())) {
            handlePlayerInitialState(player, false);
        } else {
            debugLog("Player " + player.getName() + " joined non-whitelisted world. No initial state handling.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        infoLog("onPlayerQuit CALLED for: " + player.getName());

        // Cleanup player data
        refreshCooldowns.remove(playerId);
        playerHiddenState.remove(playerId);

        // Cleanup optimizer data
        if (foliaOptimizer != null) {
            foliaOptimizer.cleanupPlayer(playerId);
        }
        if (paperOptimizer != null) {
            paperOptimizer.cleanupPlayer(playerId);
        }

        // Cleanup Geyser/Floodgate data
        if (geyserFloodgateSupport != null) {
            geyserFloodgateSupport.cleanupPlayer(playerId);
        }

        // Cleanup Entity Hider data
        if (entityHider != null) {
            entityHider.cleanupPlayer(playerId);
        }
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
                // Schedule the refresh for after the teleport is complete using platform-compatible scheduler
                PlatformCompatibility.runTask(this, to, () -> {
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
            // Schedule a new teleport for the next tick using platform-compatible scheduler
            PlatformCompatibility.runTask(this, to, () -> {
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
        double protectionY = getConfig().getDouble("antixray.protection-y-level", 31.0);

        boolean oldStateIsHidden = this.playerHiddenState.getOrDefault(playerUUID, currentY >= protectionY);
        boolean newStateIsHidden = undergroundProtectionEnabled && currentY >= protectionY;

        debugLog(String.format("PlayerMove Details: %s, FromY: %.2f (BlockY:%d), ToY: %.2f (BlockY:%d), OldStateHidden: %b, NewStateHidden: %b",
                player.getName(), from.getY(), from.getBlockY(), to.getY(), to.getBlockY(), oldStateIsHidden, newStateIsHidden));

        if (newStateIsHidden != oldStateIsHidden) {
            // Always update the state immediately so the packet listener has the correct information.
            this.playerHiddenState.put(playerUUID, newStateIsHidden);

            // Update entity visibility
            if (entityHider != null) {
                entityHider.handlePlayerMovement(player, from, to);
            }

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
        return replacementBlockState;
    }

    public int getAirStateGlobalId() {
        return replacementBlockGlobalId;
    }

    public WrappedBlockState getReplacementBlockState() {
        return replacementBlockState;
    }

    public int getReplacementBlockGlobalId() {
        return replacementBlockGlobalId;
    }

    public String getReplacementBlockType() {
        return replacementBlockType;
    }



    public GeyserFloodgateSupport getGeyserFloodgateSupport() {
        return geyserFloodgateSupport;
    }

    public boolean isDebugMode() {
        return debugMode;
    }
}
