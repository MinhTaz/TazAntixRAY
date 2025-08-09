<a href> <img src="https://cdn.discordapp.com/attachments/1264376591203700799/1399353137608786021/2025-07-28_16.41.40.png?ex=6888b0aa&is=68875f2a&hm=0e50b631edbca638005ec8266a22d3aaceb09eb92a8a7079ab53861985e638f8&"> </a>

# TazAntixRAY v1.2.1 - Advanced Anti-XRay Protection

An advanced Anti-XRay plugin for Minecraft with multi-platform support (Folia, Paper, Spigot) and intelligent underground protection. **Developed by TazukiVN**

## ✨ Key Features

### 🚀 **Multi-Platform Support**
- ✅ **Folia**: Full region-based threading support with optimizations
- ✅ **Paper**: Advanced optimizations for Paper servers
- ✅ **Spigot**: Compatible with vanilla Spigot servers
- ✅ **Geyser/Floodgate**: Automatic Bedrock player support

### 🛡️ **Advanced Anti-XRay Protection**
- ✅ **Underground Protection**: Hide everything below Y16 when player above Y31
- ✅ **Entity Hiding**: Hide entities in protected areas
- ✅ **Configurable Block Replacement**: Replace hidden blocks with air (configurable)
- ✅ **Instant Protection**: Large area protection when approaching underground
- ✅ **Limited Area Mode**: Optimize performance with area restrictions

### ⚙️ **Smart Configuration**
- ✅ **Multi-Language Support**: English, Vietnamese, and more
- ✅ **Per-World Settings**: Enable/disable per world
- ✅ **Performance Tuning**: Configurable chunk processing limits
- ✅ **Transition Zones**: Smooth transitions between protected/unprotected areas

### 🔧 **Developer & Admin Tools**
- ✅ **Comprehensive Commands**: Debug, reload, world management, testing
- ✅ **Real-time Testing**: Test commands for troubleshooting
- ✅ **Debug Mode**: Extensive logging for development
- ✅ **Clean Startup**: Professional plugin loading messages

## 📋 Requirements

- **Java 17+** (Java 21 recommended)
- **Server**: Folia, Paper, or Spigot (1.20-1.21.8+)
- **PacketEvents** plugin (dependency - auto-downloaded)

## Installation

1. Download the latest release JAR file in here https://github.com/MinhTaz/TazAntixRAY/releases/tag/Donutsmp
2. Place it in your server's `plugins/` folder
3. Ensure **PacketEvents** is installed
4. Restart your server
5. Configure worlds in `config.yml`

## Building from Source

### Prerequisites
- Java Development Kit (JDK) 21
- Maven 3.6+ or Gradle 7+
- Internet connection (for dependencies)

### Build with Maven
```bash
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

## 🔧 How It Works

### Core Mechanics
1. **Player Height Detection**: Continuously monitors player Y-coordinate
2. **Dynamic Protection**: When player above Y=31, everything below Y=16 is hidden
3. **Packet Interception**: Intercepts chunk data, block changes, and multi-block changes
4. **Entity Management**: Hides entities in protected underground areas
5. **Smart Refresh**: Efficient view updates when transitioning between states

### Platform-Specific Optimizations

#### 🚀 **Folia Support**
- **Region Scheduler**: Uses `RegionScheduler` for location-based tasks
- **Global Scheduler**: Uses `GlobalRegionScheduler` for global operations
- **Cross-Region Safety**: Handles chunk operations across different regions
- **Thread-Safe**: All operations designed for Folia's threading model

#### ⚡ **Paper Optimizations**
- **Async Chunk Loading**: Optimized chunk processing
- **Batch Operations**: Efficient bulk chunk refreshing
- **Memory Management**: Smart caching and cleanup

#### 🔧 **Spigot Compatibility**
- **Fallback Methods**: Compatible with vanilla Spigot API
- **Performance Tuning**: Optimized for single-threaded environments

#### 🌐 **Geyser/Floodgate Integration**
- **Bedrock Player Detection**: Automatic detection of Bedrock players
- **Optimized Chunk Radius**: Reduced chunk processing for mobile devices
- **Cross-Platform Compatibility**: Seamless Java/Bedrock experience

## 📈 Performance & Architecture

### Performance Optimizations
- **Minimal Server Impact**: Efficient packet-level modifications
- **Smart Cooldown System**: Prevents spam and reduces lag
- **Platform-Aware Processing**: Optimized for each server type
- **Intelligent Caching**: Memory-efficient state management
- **Batch Operations**: Bulk chunk processing for better performance

### Architecture Improvements (v1.2.1)
- **Multi-Platform Support**: Single plugin works on Folia, Paper, and Spigot
- **Modular Design**: Platform-specific optimizers for each server type
- **Clean Startup**: Professional loading messages without spam
- **Enhanced Commands**: Comprehensive command system with tab completion
- **Real-time Configuration**: Live config updates without restart
- **Advanced Testing**: Built-in testing tools for troubleshooting

## Troubleshooting

### Common Issues

1. **Plugin not working**: Ensure PacketEvents is installed and loaded
2. **Chunks not updating**: Check if world is in whitelist
3. **Performance issues**: Adjust refresh cooldown in config
4. **Folia compatibility**: Ensure you're running Folia, not Paper/Spigot

### Debug Mode

Enable debug mode with `/tazantixray debug` or `/tardebug` to see detailed logs.

## 🆕 What's New in v1.2.1

### Major Changes
- ✅ **Multi-Platform Support**: Now works on Folia, Paper, and Spigot
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
