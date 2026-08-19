# macroengine:core/internal/experimental/crafting_ui/check_one_ingredient [MACRO]
# INPUT (macro): $(item) -> item id string, $(count) -> required count
# Uses `clear @s <item> 0` (count-only probe, nothing removed) to read
# how many the caller holds, same trick as player/inv/count_item.mcfunction.

scoreboard players set $cui_have macroengine.tmp 0
$execute store result score $cui_have macroengine.tmp run clear @s $(item) 0
$execute unless score $cui_have macroengine.tmp matches $(count).. run data modify storage macroengine:engine _crafting_ui.ok set value 0b
