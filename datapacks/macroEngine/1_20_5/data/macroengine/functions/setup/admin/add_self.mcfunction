# macroengine:setup/admin/add_self [1_20_5 OVERLAY — INTERNAL]
# SECURITY: only server operators (permission_level 2+) can use this.
execute if entity @s[level=2..] run tag @s add macroengine.admin
execute if entity @s[level=2..] run tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"✔ ","color":"green"},"Added as admin."]
execute unless entity @s[level=2..] run tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"✘ ","color":"red"},"This action requires server operator (op)."]
