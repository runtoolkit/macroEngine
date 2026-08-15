# macroengine:backport/detect [1.20.5-1.20.6]
# Overlay override: sets mc_version to this overlay's pack_format instead of modern default

scoreboard players set #macroengine.mc_version macroengine.meta 41

# Call the overlay-specific backport/load
function macroengine:backport/load
