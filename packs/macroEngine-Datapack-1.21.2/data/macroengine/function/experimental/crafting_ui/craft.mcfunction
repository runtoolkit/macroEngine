# macroengine:experimental/crafting_ui/craft [MACRO]
# Attempts to craft the given recipe id. Looks up the recipe definition
# from experimental/crafting_ui/recipes.mcfunction (storage-based table),
# checks the caller has every required ingredient in sufficient count,
# clears them, and gives the result. All-or-nothing — nothing is
# consumed if any ingredient is missing.
#
# Usage:  function macroengine:experimental/crafting_ui/craft {recipe:"example"}
# Caller: any player

execute unless data storage macroengine:engine flags.experimental{crafting_ui:1b} run return 0

function macroengine:experimental/crafting_ui/recipes

$execute unless data storage macroengine:engine _crafting_ui.recipes.$(recipe) run tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"unknown recipe: ","color":"red"},{"text":"$(recipe)","color":"aqua"}]
$execute unless data storage macroengine:engine _crafting_ui.recipes.$(recipe) run return 0

$data modify storage macroengine:engine _crafting_ui.active set from storage macroengine:engine _crafting_ui.recipes.$(recipe)

# Check every ingredient is present in sufficient count (all-or-nothing).
data modify storage macroengine:engine _crafting_ui.ok set value 1b
function macroengine:core/internal/experimental/crafting_ui/check_ingredients with storage macroengine:engine _crafting_ui.active

execute unless data storage macroengine:engine _crafting_ui{ok:1b} run tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"missing ingredients.","color":"red"}]
execute unless data storage macroengine:engine _crafting_ui{ok:1b} run data remove storage macroengine:engine _crafting_ui
execute unless data storage macroengine:engine _crafting_ui{ok:1b} run return 0

# All present — consume then give.
function macroengine:core/internal/experimental/crafting_ui/consume_ingredients with storage macroengine:engine _crafting_ui.active
function macroengine:core/internal/experimental/crafting_ui/give_result with storage macroengine:engine _crafting_ui.active.result

tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"crafted!","color":"green"}]
data remove storage macroengine:engine _crafting_ui
