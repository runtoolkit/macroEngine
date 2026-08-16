# macroengine:setup/show_menu [1_20_5 OVERLAY — INTERNAL]
# Replaces the main pack's (1.21.6+) macroengine:setup_screen dialog.
# Since the dialog system doesn't exist in this Minecraft version, this
# uses tellraw + clickable clickEvent/run_command links instead.
tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"━━━ Setup ━━━","color":"aqua"}]
tellraw @s ["",{"text":" ▶ ","color":"#555555"},{"text":"Add Admin","color":"green","clickEvent":{"action":"run_command","value":"/function macroengine:setup/admin/add_self"},"hoverEvent":{"action":"show_text","value":"Makes yourself an admin (op required)"}}]
tellraw @s ["",{"text":" ▶ ","color":"#555555"},{"text":"Remove Admin","color":"red","clickEvent":{"action":"run_command","value":"/function macroengine:setup/admin/remove_self"}}]
tellraw @s ["",{"text":" ▶ ","color":"#555555"},{"text":"Admin List","color":"yellow","clickEvent":{"action":"run_command","value":"/function macroengine:debug/tools/admin/list"}}]
tellraw @s ["",{"text":" ▶ ","color":"#555555"},{"text":"Force Reload","color":"gold","clickEvent":{"action":"run_command","value":"/function macroengine:core/internal/load/force"},"hoverEvent":{"action":"show_text","value":"Requires confirmation"}}]
tellraw @s ["",{"text":"━━━━━━━━━━━━━━━","color":"#555555"}]
