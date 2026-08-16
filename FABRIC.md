# MacroEngine Fabric

Full Java port of the [macroEngine](https://github.com/runtoolkit/macroEngine) datapack core.

| | |
|--|--|
| **Minecraft** | 1.21.1 |
| **Mappings** | Yarn `1.21.1+build.3` |
| **Loader** | Fabric 0.16.9 |
| **API** | Fabric API `0.116.15+1.21.1` |
| **Java** | 21 |

## Build

```bash
./gradlew build
```

Output: `build/libs/macroengine-6.1.0.jar`

## Install

1. Install Fabric Loader for 1.21.1  
2. Put `macroengine-6.1.0.jar` in `mods/`  
3. Put Fabric API in `mods/`  
4. Start server/client — engine auto-boots (no `/function setup`)

## Commands (op level 2)

```
/macroengine status|pause|resume|version
/macroengine channel list|enable <id>|disable <id>|setrate <id> <n>
/macroengine dialog open|submit <text>
/macroengine input last|validate <int|float|bool|tag> <value>
/macroengine perm grant|revoke|check|admin <player> [perm]
/macroengine wand list|register <id>
```

## Architecture

Tick channels run on `ServerTickEvents.END_SERVER_TICK`.  
No datapack `#minecraft:tick` / storage `loaded` guard required.

MIT © runtoolkit
