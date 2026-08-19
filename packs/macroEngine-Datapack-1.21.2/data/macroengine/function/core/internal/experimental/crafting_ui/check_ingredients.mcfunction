# macroengine:core/internal/experimental/crafting_ui/check_ingredients [MACRO]
# Called `with storage macroengine:engine _crafting_ui.active` — receives
# the recipe compound (ingredients, result) as macro context. Recursively
# checks each ingredient is present in sufficient count via
# `clear @s <item> 0` (count-only, does not remove — see
# player/inv/count_item.mcfunction for the same trick used elsewhere).
# Sets _crafting_ui.ok to 0b on the first missing ingredient found.

execute unless data storage macroengine:engine _crafting_ui.active.ingredients[0] run return 0

data modify storage macroengine:engine _cui_ing set from storage macroengine:engine _crafting_ui.active.ingredients[0]
data remove storage macroengine:engine _crafting_ui.active.ingredients[0]

function macroengine:core/internal/experimental/crafting_ui/check_one_ingredient with storage macroengine:engine _cui_ing
data remove storage macroengine:engine _cui_ing

execute if data storage macroengine:engine _crafting_ui.active.ingredients[0] run function macroengine:core/internal/experimental/crafting_ui/check_ingredients with storage macroengine:engine _crafting_ui.active
