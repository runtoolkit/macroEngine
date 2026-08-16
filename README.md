# MacroEngine Fabric — 1.21.1 / Yarn

Working runtime port of the macroEngine datapack core.

## Smoke test

```
/macroengine status
/macroengine run say hello
/macroengine queue add 40 say queued
/macroengine schedule repeat t 20 say pulse
/macroengine fiber spawn blink 5 say fiber-step
/macroengine batch add say a
/macroengine batch add say b
/macroengine batch flush
/macroengine input summon_cbm
/macroengine region add spawn -5 60 -5 5 80 5
/macroengine event on ON_REGION_ENTER e say entered
/macroengine wand register boom particle explosion ~ ~1 ~
/macroengine toggle set feature_x true
```

## Modules

tick · queue · schedule · fiber · batch · event · cmd · input(CBM) · region · wand · perm · interaction · bossbar · title · item · toggle · gamerule

## Build

```bash
./gradlew build
```
