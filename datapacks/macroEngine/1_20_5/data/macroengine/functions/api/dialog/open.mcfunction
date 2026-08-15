# macroengine:api/dialog/open [1.20.5-1.20.6 overlay stub]
# The `dialog` command does not exist before 1.21.6 — this overlay has no
# real dialog implementation. Fail loudly instead of "Unknown function".
tellraw @s ["",{"text":"[MACROENGINE] ","color":"#00AAAA","bold":true},{"text":"Dialogs require 1.21.6+. This server is running the 1.20.5 compatibility overlay, so api/dialog/* is unavailable.","color":"red"}]
