# 🛡️ TazAntixRAY

[![Version](https://img.shields.io/badge/version-1.2.0-blue.svg)](https://github.com/MinhTaz/TazAntixRAY)
[![Platform](https://img.shields.io/badge/platform-Folia%20%7C%20Paper%20%7C%20Spigot-green.svg)](https://github.com/MinhTaz/TazAntixRAY)
[![Bedrock](https://img.shields.io/badge/bedrock-supported-purple.svg)](https://github.com/MinhTaz/TazAntixRAY)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://github.com/MinhTaz/TazAntixRAY)
[![License](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)

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
- **Block Replacement**: Replace hidden blocks with air, deepslate, or stone
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

### Basic Setup
```yaml
# Enable anti-xray for specific worlds
worlds:
  whitelist:
    - "world"
    - "mining_world"

# Basic anti-xray settings
antixray:
  trigger-y-level: 31.0    # Hide blocks when player is above this Y
  hide-below-y: 16         # Hide all blocks at or below this Y
```

### NEW: Instant Protection Settings
```yaml
# Instant protection - eliminates base visibility
performance:
  instant-protection:
    enabled: true
    instant-load-radius: 15        # Load 15 chunks instantly
    pre-load-distance: 10          # Start protection 10 blocks early
    force-immediate-refresh: true  # No delays, instant activation

# Bedrock Edition support
compatibility:
  bedrock-support:
    enabled: true
    geyser-compatibility: true
    floodgate-compatibility: true
    # Auto-detect custom Floodgate prefixes
    floodgate-prefixes: []         # Leave empty for auto-detection
    auto-detect-floodgate-config: true
    detection-methods:
      use-floodgate-api: true      # Use Floodgate API if available
      use-geyser-api: true         # Use Geyser API if available
      uuid-pattern-detection: true # Fallback UUID pattern detection
```

### Advanced Features
```yaml
# Limited area hiding (recommended for better base security)
antixray:
  limited-area:
    enabled: true
    chunk-radius: 3
    apply-only-near-player: true

  # Block replacement options
  replacement:
    block-type: "deepslate"      # air, deepslate, stone

  # Entity hiding
  entities:
    # Hide all entities in hidden areas (true = hide, false = show)
    hide-entities: true

  # Complete underground protection
  underground-protection:
    # Hide everything below Y16 when player is above Y31
    # This includes: blocks, entities, chests, etc.
    enabled: true
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

### Command Examples
```bash
# Show plugin information
/tazantixray info

# Check if a player is using Bedrock Edition
/tazantixray checkplayer Steve

# List whitelisted worlds
/tarworld list

# Add a world to whitelist
/tarworld add mining_world

# Remove a world from whitelist
/tarworld remove old_world

# Toggle debug mode
/tardebug

# Reload configuration
/tarreload
```

## 🔒 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `tazantixray.admin` | `op` | Access to all admin commands |
| `tazantixray.debug` | `op` | Toggle debug mode |
| `tazantixray.world` | `op` | Manage world whitelist |
| `tazantixray.reload` | `op` | Reload configuration |

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

### 🎮 **New Commands**
- `/tazantixray info` - Display comprehensive plugin information
- `/tazantixray checkplayer <player>` - Check if a player is using Bedrock Edition
- Enhanced debugging capabilities with detailed player information

### 🚀 **Performance Improvements**
- **Instant chunk loading**: Large area protection without delays
- **Bedrock-optimized**: Smaller chunk radius for mobile players
- **Smart caching**: Improved memory management and player detection caching
- **Platform-aware optimization**: Better performance across all server types

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
