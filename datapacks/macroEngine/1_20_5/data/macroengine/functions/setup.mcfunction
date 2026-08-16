# macroengine:setup [1_20_5 OVERLAY — pack format 40-42]
# ============================================================================
# Manual (re)initialization entry point.
#
# IMPORTANT DIFFERENCE (vs. the main pack): this is the overlay version
# targeting Minecraft 1.20.3-1.20.5 (pack format 40-42). Minecraft's
# /dialog command and dialog-screen system were only added in 1.21.6 —
# they do NOT exist in this version. So the main pack's (format 107)
# macroengine:setup_screen dialog does NOT exist here and cannot; instead
# a classic tellraw + clickable command link (clickEvent/run_command) is
# used — the same 1.20.5-compatible pattern this project already uses for
# "force reload" in core/internal/load/main.mcfunction.
#
# This function replaces the automatic load flow that used to depend on
# the LanternMC "load" datapack's tag chain.
# ============================================================================

# 1) Start the core engine (scoreboard/storage/config/backport chain).
function macroengine:core/internal/load/main

# 2) Start the embedded StringLib port (macroengine:core/internal/string/*).
#    Note: split / to_lowercase / to_uppercase are broken — see the WARNING
#    comments in the relevant files. This preserves existing (upstream)
#    behavior.
function macroengine:core/internal/string/zprivate/load

# 3) Start the embedded PlayerAction port (macroengine:core/internal/player/*).
function macroengine:core/internal/player/enumerate
function macroengine:core/internal/player/resolve
function macroengine:core/internal/player/init

# 4) Announce this pack to runtoolkit's registry/list and diagnostics/status
#    tools (METADATA ONLY).
data modify storage runtoolkit:tmp _reg set value {name:"macroengine",version:610,load_fn:"macroengine:setup",tick_fn:"macroengine.main:macroengine/tick",disable_fn:"macroengine:disable"}
function runtoolkit:registry/register with storage runtoolkit:tmp _reg
data remove storage runtoolkit:tmp _reg

# 5) Show the management options — INSTEAD of a dialog, tellraw + clickable
#    command links (1.20.5-compatible). Admin add is guarded by an op
#    (permission_level 2+) check, see setup/admin/add_self.
execute if entity @s run function macroengine:setup/show_menu
