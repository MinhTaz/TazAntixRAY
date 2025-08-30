# 🛡️ TazAntixRAY

[![Version](https://img.shields.io/badge/version-1.2.0-blue.svg)](https://github.com/MinhTaz/TazAntixRAY)
[![Platform](https://img.shields.io/badge/platform-Folia%20%7C%20Paper%20%7C%20Spigot-green.svg)](https://github.com/MinhTaz/TazAntixRAY)
[![Bedrock](https://img.shields.io/badge/bedrock-supported-purple.svg)](https://github.com/MinhTaz/TazAntixRAY)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://github.com/MinhTaz/TazAntixRAY)
[![License](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)


***A fork https://github.com/omdumrotat/flicexyzantixray***
**🚀 Advanced Anti-XRay plugin with Instant Protection, Bedrock Support & Multi-Platform Compatibility**

TazAntixRAY is a next-generation anti-cheat plugin designed to prevent X-ray hacking with **instant protection** that eliminates base visibility. Features intelligent block hiding, entity concealment, Bedrock Edition support, and seamless compatibility across Folia, Paper, and Spigot platforms.

## ✨ Features

### ⚡ **NEW: Instant Protection System**
- **🚀 Zero-Delay Loading**: Instantly loads 15+ chunks when players approach Y16
- **🛡️ Pre-Loading**: Starts protection 10 blocks before reaching underground
- **👁️ No Base Visibility**: Players can't see bases before plugin activates
- **🎯 Force Immediate Refresh**: Eliminates any chance of seeing hidden structures

### 📱 **NEW: Bedrock Edition Support**
- **🔧 Geyser Compatibility**: Full support for Geyser-connected players
- **🌊 Floodgate Integration**: Auto-detects custom prefixes (not just ".")
- **🤖 Smart Detection**: Multiple detection methods (API, UUID patterns, prefixes)
- **⚡ Optimized Performance**: Smaller chunk radius for mobile players

### 🎯 **Core Anti-XRay Protection**
- **Smart Block Hiding**: Dynamically hides blocks below configurable Y-levels
- **Transition Zones**: Smooth transitions between hidden and visible areas
- **Performance Optimized**: Minimal server impact with intelligent caching

### 🌍 **Multi-Platform Support**
- **Folia Compatible**: Full support for region-based threading
- **Paper/Spigot Support**: Traditional server compatibility
- **Auto-Optimization**: Automatically detects platform and applies optimal settings

### 🎨 **Advanced Hiding Features**
- **Complete Underground Protection**: Hide everything below Y16 from X-ray and freecam
- **Limited Area Hiding**: Hide only small areas (3x3 chunks) around players
- **Entity Hiding**: Conceal all entities in hidden areas
- **Smart Detection**: Normal view when actually mining underground

### 🔧 **Smart Configuration**
- **Per-World Settings**: Enable/disable per world
- **Flexible Y-Levels**: Customizable trigger and hiding thresholds
- **Performance Tuning**: Adjustable chunk processing limits
- **Language Support**: English and Vietnamese translations

## 📦 Requirements

- **Java 21** or higher
- **Folia/Paper/Spigot** server (1.20.6+)
- **PacketEvents** plugin (required dependency)

### Optional Dependencies (for enhanced features)
- **Geyser-Spigot**: For Bedrock Edition player support
- **Floodgate**: For Bedrock Edition authentication
- **ViaVersion**: For cross-version compatibility
- **ProtocolLib**: For advanced packet handling
- **PlaceholderAPI**: For placeholder support

## 📦 Installation

1. **Download** the latest release from [Releases](https://github.com/MinhTaz/TazAntixRAY/releases)
2. **Install PacketEvents** dependency (if not already installed)
3. **Optional**: Install Geyser-Spigot and Floodgate for Bedrock support
4. **Place** `TazAntixRAY-1.2.0.jar` in your `plugins/` folder
5. **Restart** your server
6. **Configure** the plugin in `plugins/TazAntixRAY/config.yml`

## 🔧 Building from Source

### Prerequisites
- Java Development Kit (JDK) 21
- Maven 3.6+
- Internet connection (for dependencies)

### Build Commands
```bash
git clone https://github.com/MinhTaz/TazAntixRAY.git
cd TazAntixRAY
mvn clean package
```

The compiled JAR will be in the `target/` directory.

## ⚙️ Configuration

Edit `plugins/TazAntixRAY/config.yml`:

```yaml
# ========================================
# GENERAL SETTINGS
# ========================================
settings:
  language: "en"                    # Language: en, vi, etc.
  debug-mode: false                 # Enable debug logging
  refresh-cooldown-seconds: 3       # Cooldown between refreshes

# ========================================
# WORLD CONFIGURATION
# ========================================
worlds:
  whitelist:
    - "world"
    - "mining_world"
    # Add your worlds here

# ========================================
# ANTI-XRAY SETTINGS
# ========================================
antixray:
  protection-y-level: 31.0          # Hide blocks when player above this Y
  hide-below-y: 16                  # Hide blocks at or below this Y
  transition:
    smooth-transition: true         # Enable smooth transitions
    transition-zone-size: 5         # Transition zone size

# ========================================
# PERFORMANCE SETTINGS
# ========================================
performance:
  max-chunks-per-tick: 50           # Max chunks processed per tick
  max-entities-per-tick: 100        # Max entities processed per tick

  # Instant protection - load large area immediately
  instant-protection:
    enabled: true
    instant-load-radius: 15         # Chunks to load instantly
    pre-load-distance: 10           # Pre-load when this close to Y16
    force-immediate-refresh: true

  # Limited area mode for performance
  limited-area:
    enabled: false
    chunk-radius: 3                 # Limit effect to this radius

  # Block replacement settings
  replacement:
    block-type: "air"               # Block to replace hidden blocks with

  # Entity hiding settings
  entities:
    hide-entities: true             # Hide entities in protected areas

  # Underground protection
  underground-protection:
    enabled: true                   # Enable underground protection
```

## 🎮 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/tazantixray` | `tazantixray.admin` | Main plugin command |
| `/tazantixray info` | `tazantixray.admin` | Show plugin information and settings |
| `/tazantixray checkplayer <player>` | `tazantixray.admin` | Check if player is Bedrock Edition |
| `/tardebug` | `tazantixray.debug` | Toggle debug mode |
| `/tarreload` | `tazantixray.admin` | Reload configuration |
| `/tarworld <list\|add\|remove> [world]` | `tazantixray.admin` | Manage world whitelist |

## 🎮 Commands

### Main Commands
| Command | Aliases | Permission | Description |
|---------|---------|------------|-------------|
| `/tazantixray` | `/tar`, `/antixray` | `tazantixray.admin` | Main plugin command |
| `/tardebug` | `/tazantixraydebug` | `tazantixray.admin` | Toggle debug mode |
| `/tarreload` | `/tazantixrayreload` | `tazantixray.admin` | Reload configuration |
| `/tarworld` | `/tazantixrayworld` | `tazantixray.admin` | World management |

### Subcommands
```bash
# Main command subcommands
/tazantixray debug          # Toggle debug mode
/tazantixray reload         # Reload configuration
/tazantixray stats          # Show plugin statistics
/tazantixray entities       # Toggle entity hiding
/tazantixray test <type>    # Testing tools

# World management
/tazantixray world list     # List whitelisted worlds
/tazantixray world add <world>    # Add world to whitelist
/tazantixray world remove <world> # Remove world from whitelist

# Quick aliases
/tardebug                   # Quick debug toggle
/tarreload                  # Quick reload
/tarworld list              # Quick world list
/tarworld add <world>       # Quick add world
/tarworld remove <world>    # Quick remove world

# Testing commands
/tazantixray test block     # Test current block replacement
/tazantixray test state     # Show your anti-xray state
/tazantixray test refresh   # Force refresh your view
```

## 🚀 Performance

### Auto-Optimizations
- **Platform Detection**: Automatically detects Folia vs Spigot/Paper
- **Smart Threading**: Uses region-based threading on Folia, traditional on Spigot/Paper
- **Adaptive Scheduling**: Chooses optimal scheduler for each platform
- **Memory Optimization**: Automatic memory management and caching

### Performance Settings
```yaml
# Only these settings need manual configuration
performance:
  max-chunks-per-tick: 50      # Adjust based on server performance
  max-entities-per-tick: 100   # Limit entity processing
```

**Note**: All other optimizations are applied automatically based on your server platform!

## 🌐 Multi-Language Support

TazAntixRAY supports multiple languages:
- **English** (`en`) - Default
- **Vietnamese** (`vi`) - Tiếng Việt

Set your language in `config.yml`:
```yaml
settings:
  language: "en"  # or "vi"
```

## 📊 Compatibility

| Platform | Version | Status |
|----------|---------|--------|
| **Folia** | 1.20.6+ | ✅ Full Support |
| **Paper** | 1.20.6+ | ✅ Full Support |
| **Spigot** | 1.20.6+ | ✅ Full Support |
| **Bedrock Edition** | Any | ✅ Full Support (via Geyser/Floodgate) |

### Bedrock Edition Support
- ✅ **Geyser**: Auto-detects Geyser-connected players
- ✅ **Floodgate**: Supports any custom prefix configuration
- ✅ **Performance Optimized**: Smaller chunk radius for mobile devices
- ✅ **API Integration**: Uses Floodgate/Geyser APIs when available

## Troubleshooting

### Common Issues

1. **Plugin not working**: Ensure PacketEvents is installed and loaded
2. **Chunks not updating**: Check if world is in whitelist
3. **Performance issues**: Adjust refresh cooldown in config
4. **Folia compatibility**: Ensure you're running Folia, not Paper/Spigot


## Known Issues (Fixed in Folia Edition)

- ✅ **Chunk visibility bug**: Fixed with improved region-aware chunk handling
- ✅ **Threading issues**: Resolved with Folia's region-based threading
- ✅ **Cross-region operations**: Now properly handled with RegionScheduler

## 🆕 What's New in v1.2.0 - Big Update!

### ⚡ **Instant Protection System**
- **Zero-delay activation**: Plugin now loads 15+ chunks instantly when players approach Y16
- **Pre-loading mechanism**: Starts protection 10 blocks before reaching underground areas
- **Force immediate refresh**: Eliminates any possibility of seeing hidden bases
- **No more base visibility**: Players can no longer see bases before plugin activates

### 📱 **Bedrock Edition Support**
- **Full Geyser compatibility**: Auto-detects players connected via Geyser
- **Smart Floodgate integration**: Auto-detects custom prefixes (not limited to ".")
- **Multiple detection methods**: API integration, UUID patterns, and prefix detection
- **Performance optimization**: Optimized chunk loading for mobile devices

### 🔧 **Enhanced Configuration**
- **Flexible Floodgate prefixes**: Supports any custom prefix configuration
- **Auto-detection system**: Automatically reads Floodgate config files
- **Advanced detection methods**: Multiple fallback detection systems
- **Improved compatibility**: Enhanced softdepend for better plugin integration


### 🚀 **Performance Improvements**
- **Instant chunk loading**: Large area protection without delays
- **Bedrock-optimized**: Smaller chunk radius for mobile players
- **Smart caching**: Improved memory management and player detection caching
- **Platform-aware optimization**: Better performance across all server types
## 🆕 What's New in v1.2.1

### Major Changes
- ✅ **Fix Multi-Platform Support**: Now works on Folia is working
- ✅ **Enhanced Entity Hiding**: Improved entity management with event-based hiding
- ✅ **Clean Startup Messages**: Professional loading without spam
- ✅ **Advanced Commands**: Comprehensive command system with testing tools
- ✅ **Real-time Config**: Live configuration updates
- ✅ **Better Performance**: Platform-specific optimizations

### Architecture Improvements
- ✅ **Platform Detection**: Automatic detection and optimization for each server type
- ✅ **Modular Design**: Separate optimizers for Folia, Paper, and Spigot
- ✅ **Enhanced Error Handling**: Better error messages and fallback systems
- ✅ **Improved Compatibility**: Works with more server configurations

### Fixed Issues
- ✅ **Chunk visibility bug**: Fixed with improved region-aware chunk handling
- ✅ **Threading issues**: Resolved with platform-specific threading
- ✅ **Cross-region operations**: Properly handled with RegionScheduler
- ✅ **Entity leakage**: Entities now properly hidden with event-based system
- ✅ **Command issues**: All commands now work correctly with proper permissions

## License
## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Languages Supported

- 🇺🇸 **English** (en) - Default
- 🇻🇳 **Tiếng Việt** (vi) - Vietnamese
- 🌍 **More languages coming soon!**

To change language, edit `language: "vi"` in config.yml


## Compiling

1. Clone the repository
2. Import to your IDE of choice
3. Build with Maven: `mvn clean package`
4. JAR file will be in `target/` directory
