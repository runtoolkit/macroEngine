# macroengine:experimental/crafting_ui/show
# Prints a clickable "custom crafting" shortcut menu — each button gives
# the result item if the caller has the listed ingredients, consuming
# them. This is NOT a real crafting-table GUI (data packs can't add
# custom recipe UIs to the survival crafting grid) — it's a chat-menu
# shortcut for datapack-defined recipes that don't fit the vanilla
# 3x3 grid. Gated behind flags.experimental.crafting_ui.
#
# BACKPORT NOTE (1.21.2): same constraint as setup/open_screen.mcfunction —
# no `dialog show` / custom container screen available without a resource
# pack GUI + click-handling entity, out of scope for a first experimental
# pass. Chat-menu clickables instead.
#
# Usage:  function macroengine:experimental/crafting_ui/show
# Caller: any player

execute unless data storage macroengine:engine flags.experimental{crafting_ui:1b} run tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"experimental/crafting_ui is disabled.","color":"red"}]
execute unless data storage macroengine:engine flags.experimental{crafting_ui:1b} run return 0

tellraw @s ["",{"text":"═══════ ","color":"dark_gray"},{"text":"macroEngine — Custom Recipes","color":"aqua","bold":true},{"text":" ═══════","color":"dark_gray"}]
tellraw @s ["",{"text":"Recipes not shown in the vanilla grid. Click to attempt a craft (consumes ingredients if you have them).","color":"gray","italic":true}]
tellraw @s ["",{"text":"[Craft] ","color":"green","bold":true,"clickEvent":{"action":"run_command","value":"/function macroengine:experimental/crafting_ui/craft {recipe:\"example\"}"},"hoverEvent":{"action":"show_text","value":"See experimental/crafting_ui/recipes.mcfunction to define real recipes"}},{"text":"example — placeholder recipe","color":"white"}]
tellraw @s ["",{"text":"═════════════════════════════════════","color":"dark_gray"}]
