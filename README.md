<a href> <img src="https://cdn.discordapp.com/attachments/1264376591203700799/1399353137608786021/2025-07-28_16.41.40.png?ex=6888b0aa&is=68875f2a&hm=0e50b631edbca638005ec8266a22d3aaceb09eb92a8a7079ab53861985e638f8&"> </a>

# TazAntixRAY - Like DonutSmp

An advanced Anti-XRay plugin for Minecraft with full Folia support and region-based threading optimization. **Developed by TazukiVN**

## Features

- ✅ **Folia Compatible**: Fully supports Folia's region-based threading
- ✅ **Advanced Anti X-Ray**: Configurable Y-level based block hiding
- ✅ **Multi-Language Support**: English, Vietnamese, and more
- ✅ **Advanced Configuration**: Comprehensive config with performance tuning
- ✅ **World Management**: Per-world activation with easy commands
- ✅ **Performance Optimized**: Region-aware processing for maximum efficiency
- ✅ **PacketEvents Integration**: Efficient packet-level modifications
- ✅ **Smart Caching**: Intelligent cooldown system prevents lag
- ✅ **Developer Friendly**: Extensive debug mode and logging

## Requirements

- **Java 21** or higher
- **Folia** server (Paper fork with region-based threading)
- **PacketEvents** plugin (dependency)

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

## Configuration

Edit `plugins/TazAntixRAY/config.yml`:

```yaml
# General Settings
settings:
  language: "en"  # Language: en, vi, etc.
  debug-mode: false
  refresh-cooldown-seconds: 3

# World Configuration
worlds:
  whitelist:
    - "world"
    - "mining_world"

# Anti-XRay Settings
antixray:
  trigger-y-level: 31.0    # Hide blocks when player above this Y
  hide-below-y: 16         # Hide blocks at or below this Y
  transition:
    stop-hiding-y: 30.0    # Stop hiding when player below this Y
    smooth-transition: true

# Performance Settings
performance:
  folia-optimizations: true
  region-aware-processing: true
  max-chunks-per-tick: 50
```

## Commands

| Command | Aliases | Permission | Description |
|---------|---------|------------|-------------|
| `/tazantixray` | `/tar`, `/antixray` | `tazantixray.admin` | Main plugin command |
| `/tardebug` | `/tazantixraydebug` | `tazantixray.debug` | Toggle debug mode |
| `/tarreload` | `/tazantixrayreload` | `tazantixray.reload` | Reload configuration |
| `/tarworld list` | `/tazantixrayworld list` | `tazantixray.world` | List whitelisted worlds |
| `/tarworld add <world>` | `/tazantixrayworld add` | `tazantixray.world` | Add world to whitelist |
| `/tarworld remove <world>` | `/tazantixrayworld remove` | `tazantixray.world` | Remove world from whitelist |

### Command Examples
```
/tazantixray debug          # Toggle debug mode
/tazantixray reload         # Reload config
/tazantixray world list     # List worlds
/tarworld add mining_world  # Add world
/tarworld remove old_world  # Remove world
```

## How It Works

1. **Player Height Detection**: Monitors player Y-coordinate
2. **Dynamic Hiding**: When player is above Y=31, blocks below Y=16 are hidden
3. **Region-Aware**: Uses Folia's region scheduler for thread-safe operations
4. **Packet Modification**: Intercepts chunk and block change packets
5. **Smart Refresh**: Efficiently updates player view when transitioning between states

## Folia-Specific Features

- **Region Scheduler**: Uses `RegionScheduler` for location-based tasks
- **Global Scheduler**: Uses `GlobalRegionScheduler` for global operations
- **Cross-Region Safety**: Handles chunk operations across different regions
- **Thread-Safe**: All operations are designed for Folia's threading model

## Performance Notes

- Minimal impact on server performance
- Efficient packet-level modifications
- Smart cooldown system prevents spam
- Region-aware chunk refreshing

## Troubleshooting

### Common Issues

1. **Plugin not working**: Ensure PacketEvents is installed and loaded
2. **Chunks not updating**: Check if world is in whitelist
3. **Performance issues**: Adjust refresh cooldown in config
4. **Folia compatibility**: Ensure you're running Folia, not Paper/Spigot

### Debug Mode

Enable debug mode with `/ylevelhiderdebug` to see detailed logs.

## Known Issues (Fixed in Folia Edition)

- ✅ **Chunk visibility bug**: Fixed with improved region-aware chunk handling
- ✅ **Threading issues**: Resolved with Folia's region-based threading
- ✅ **Cross-region operations**: Now properly handled with RegionScheduler

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
