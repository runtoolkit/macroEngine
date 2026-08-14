# runtoolkit global entry point — load
# Dispatches to the macroengine subsystem's own load chain.
function macroengine_load:main

execute unless data storage macroengine:engine global.loaded run return 0
execute if data storage macroengine:engine global{loaded:0b} run return 0
execute if data storage macroengine:engine global{loaded:1b} run return 1