# macroengine:experimental/particle_trail/toggle
# Toggles a per-player particle trail (spawns particles behind the
# player as they move). Gated behind flags.experimental.particle_trail.
# Tick loop hook lives in experimental/particle_trail/tick.mcfunction.
#
# Usage:  function macroengine:experimental/particle_trail/toggle
# Caller: any player (self-toggle, no admin tag needed)

execute unless data storage macroengine:engine flags.experimental{particle_trail:1b} run tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"experimental/particle_trail is disabled.","color":"red"}]
execute unless data storage macroengine:engine flags.experimental{particle_trail:1b} run return 0

execute if entity @s[tag=macroengine.experimental.trail] run tag @s remove macroengine.experimental.trail
execute if entity @s[tag=macroengine.experimental.trail] run return 0
execute unless entity @s[tag=macroengine.experimental.trail] run tag @s add macroengine.experimental.trail

execute if entity @s[tag=macroengine.experimental.trail] run tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"particle trail → ","color":"gray"},{"text":"on","color":"green"}]
execute unless entity @s[tag=macroengine.experimental.trail] run tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"particle trail → ","color":"gray"},{"text":"off","color":"red"}]
