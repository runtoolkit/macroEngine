import os
import subprocess
import json
import sys

def main():
    print("=== JMC Build Script ===")
    
    # 1. Ensure jmcfunction is installed
    try:
        import jmcfunction
        print("jmcfunction is already installed.")
    except ImportError:
        print("Installing jmcfunction...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "jmcfunction", "--pre"])
    
    # 2. Define JMC Config
    config_data = {
        "namespace": "macroengine_jmc",
        "description": "MacroEngine Datapack compiled with JMC",
        "pack_format": 112,
        "target": "main.jmc",
        "output": "./build/jmc_datapack"
    }
    
    with open("jmc_config.json", "w") as f:
        json.dump(config_data, f, indent=4)
    print("Created jmc_config.json")
    
    # 3. Create main.jmc with some elegant translated JMC code
    jmc_code = """// main.jmc — JMC version of macroEngine core mechanics
// Compiled using WingedSeal's JMC compiler.

// Global load commands (everything outside goes to load function)
tellraw @a ["",{"text":"[MacroEngine JMC] ","color":"#00AAAA","bold":true},{"text":"MacroEngine JMC version loaded successfully!","color":"green"}];

// Class representing command wrappers and administrative controls
class cmd {
    function op() {
        // Sandbox checks and execution
        $sandbox = data get storage macroengine:engine sandbox;
        if ($sandbox == 1) {
            data modify storage macroengine:engine _sandbox_cmd set value "op";
            execute store success score #gate_success me.tmp run function macroengine:core/internal/api/cmd/sandbox_gate;
            $success = #gate_success:me.tmp;
            if ($success == 0) {
                return 0;
            }
        } else {
            tellraw @a[tag=macroengine.debug] ["",{"text":"[MacroEngine] ","color":"#00AAAA","bold":true},{"text":"TIP ","color":"yellow","bold":true},{"text":"Sandbox mode recommended → ","color":"gray"}];
        }
        execute as @a[tag=macroengine.pending_op,limit=1] run op @s;
    }

    function deop() {
        $sandbox = data get storage macroengine:engine sandbox;
        if ($sandbox == 1) {
            data modify storage macroengine:engine _sandbox_cmd set value "deop";
            execute store success score #gate_success me.tmp run function macroengine:core/internal/api/cmd/sandbox_gate;
            $success = #gate_success:me.tmp;
            if ($success == 0) {
                return 0;
            }
        }
        execute as @a[tag=macroengine.pending_deop,limit=1] run deop @s;
    }
}

// Class for managing the confirmation gates
class gate {
    function request() {
        $pending = 1;
        $confirmed = 0;
        
        // Broadcast via tellraw — clickable buttons, no marker entity needed.
        Text.tellraw(@a, "&<yellow,bold>[MacroEngine GATE] Dangerous command pending — awaiting confirmation.");
        tellraw @a ["",{"text":"[MacroEngine GATE] ","color":"#555555"},{"text":"[Confirm]","color":"green","bold":true,"underlined":true,"click_event":{"action":"run_command","command":"/function macroengine_jmc:gate/confirm"}},{"text":"   ","color":"gray"},{"text":"[Cancel]","color":"red","bold":true,"underlined":true,"click_event":{"action":"run_command","command":"/function macroengine_jmc:gate/cancel"}}];
    }
    
    function confirm() {
        Text.tellraw(@a, "&<green,bold>[MacroEngine GATE] Dangerous command CONFIRMED.");
    }
    
    function cancel() {
        Text.tellraw(@a, "&<red,bold>[MacroEngine GATE] Request cancelled.");
    }
}

// Math and variable operations helper
class math {
    function calculate_stats() {
        $x = data get entity @s SelectedItem.Count;
        $y = 100;
        $random_int = Math.random($x, $y);
        Text.tellraw(@a, "Selected item count: &<$x>, random value: &<$random_int>");
    }
}
"""
    
    with open("main.jmc", "w") as f:
        f.write(jmc_code)
    print("Created main.jmc")
    
    # 4. Run the JMC compile
    print("Running JMC compile...")
    try:
        subprocess.check_call(["jmc", "compile"])
        print("JMC build completed successfully!")
        print("Output generated in: ./build/jmc_datapack")
    except subprocess.CalledProcessError as e:
        print(f"Error compiling JMC: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
