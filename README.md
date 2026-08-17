# MacroEngine Fabric — 1.21.1 / Yarn
Working runtime port of the macroEngine datapack core.
## Smoke test
```
/macroengine status
/macroengine run say hello
/macroengine queue add 40 say queued
/macroengine schedule repeat t 20 say pulse
/macroengine fiber spawn blink 5 say fiber-step
/macroengine batch add say a
/macroengine batch add say b
/macroengine batch flush
/macroengine input summon_cbm
/macroengine region add spawn -5 60 -5 5 80 5
/macroengine event on ON_REGION_ENTER e say entered
/macroengine wand register boom particle explosion ~ ~1 ~
/macroengine toggle set feature_x true
```
## Modules
tick · queue · schedule · fiber · batch · event · cmd · input(CBM) · region · wand · perm · interaction · bossbar · title · item · toggle · gamerule
## Build
```bash
./gradlew build
```

## Packs (legacy reference)
`packs/macroEngine-Datapack` (v6.1.0) and `packs/macroEngine-Resourcepack` (v6.1.0) — the original datapack/resource pack pair this Fabric port is derived from, kept under `packs/` for reference.

- **macroEngine-Datapack**: full `macroengine` namespace — `core/` (internal engine: tick dispatch, queue, schedule, fiber, batch, cooldowns, state, string/math/nbt/uuid systems, hooks for ~40 player interaction events, click detection, config, gate/load pipeline), `api/` (public-facing cmd/dialog/gamerule/interaction/perm/wand/title/toggle/color functions — several hundred `.mcfunction` files), `systems/` (log, rate_limit, geo region-watch, flag, math, hook dispatch), `player/` (per-player var storage, inventory helpers, teams), `events/` (custom event fire/register/queue), `input/` (command block minecart capture, writable book capture, dialog capture — used for structured text input from players), advancements/predicates/loot_tables used as internal hooks (not vanilla progression), and a `setup`/`disable`/admin dialog menu. Also carries a small `runtoolkit` namespace (`killswitch`, `registry`, `diagnostics`) shared across runtoolkit datapacks.
- **macroEngine-Resourcepack**: companion pack — 7 custom `.ogg` sounds (`load_success`, `ui_confirm`, `ui_error`, `ui_freeze`, `ui_unfreeze`, `perm_granted`, `perm_denied`) registered under the `macroengine` namespace, plus `en_us`/`de_de` lang overrides. No custom fonts, textures, or models present despite the mcmeta description mentioning "GUI & dialog rendering" — it's audio/lang only.

> **Inconsistency to verify:** the datapack's `pack.mcmeta` displays "26.2" in its in-game description text but sets `min_format`/`max_format` to `107` (flat, not `[107,1]`) — plain 107 corresponds to 26.1, and 26.2 should use `[107,1]`. Either the description or the pack_format is stale; confirm which is correct before shipping v6.1.0.

> **Status:** these two packs are the pre-Fabric implementation. Whether they're still deployed anywhere or are purely historical reference now that logic is being ported to the mod isn't stated in the files themselves — clarify in the repo if relevant.
