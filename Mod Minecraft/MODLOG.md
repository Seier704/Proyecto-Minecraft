# MODLOG - Dwarf NPC (Minecraft Java 26.3, Fabric)
- Route: Fabric loader 0.19.5 + Fabric API 0.161.0+26.3, Loom 1.18-SNAPSHOT. 26.x is unobfuscated, Java 25 required.
- Portable JDK 25 in tools\ (gradle.properties org.gradle.java.home). System Java is 1.8, untouched.
- Skin 64x64 (Modelos Npc\...gloin...png) is used as entity texture with the player mesh (wide arms; set slim=false in DwarfModel if arms look wrong).
- Village spawn: ServerEntityEvents.ENTITY_LOAD on adult villagers -> 34% chance, one dwarf per 64 blocks, tagged dwarfmod_checked.
- _src\ = decompiled MC sources (reference only, never ship).
- Run: set JAVA_HOME to tools\jdk-25*, cd dwarfmod, gradlew runClient. Jar: dwarfmod\build\libs\dwarfmod-1.0.0.jar
- Test: /summon dwarfmod:dwarf
- Spawn restricted to mountain (#is_mountain) + snowy biomes, 60% per village (structure-based, tag dwarfmod_checked_v3).

## Dwarf blacksmith (trading)
- DwarfEntity now extends AbstractVillager: own level (1-5) and xp (VillagerData thresholds), saved in NBT (DwarfLevel/DwarfXp/AnvilPos).
- Anvil: finds an anvil within 24x6 blocks, otherwise places one next to himself; setHomeTo(anvil, 8). Restock every 6000 ticks.
- Trades: L1 iron hoe/shovel, L2 iron axe/pickaxe + bronze armor (iron), L3 iron sword + steel armor (bronze piece + iron), L4 dwarven armor (steel piece + iron), L5 diamond tools (iron tool + diamonds, enchanted).
- Armor tiers in ModItems.TIERS (bronze, steel, dwarven); recolors via art/make_tiers.py. Crafting recipes removed.
- Status: compiled, NOT yet tested in game.
- Dwarven tools (ModItems.DWARVEN_TOOLS: 1850 dur, 8.6 speed, +3.5 dmg, diamond-level mining): sold at L5 for iron tool + diamonds. Icons via art/make_tools.py. Not tested in game.
- Trades v2: L2-3 iron pickaxe/shovel/axe come with Efficiency I, sword with random Sharpness/Smite/Bane I (rolled once per dwarf); L5 dwarven tools = matching iron tool + emeralds (4 shovel/hoe, 6 axe/pickaxe/sword).

## Roles + friendship (untested in game)
- DwarfEntity has role (0 blacksmith, 1 brewer, 2 miner; synced int, NBT DwarfRole), per-role workstation (anvil/barrel/blast furnace) and trades. Spawn assigns role = index.
- Drinks: ale/mead/stout/spirit (ModItems.brew, Consumable + ApplyStatusEffects). Icons: art/make_brews.py.
- Friendship per player (points -> 5 stages), price modifier +25%..-30%, gifts via sneak+item, +1 per trade, +4 for kills near a dwarf. Sneak+empty hand = follow (Friend+). Sneak+8 emeralds on miner (Trusted+) = hire 24000 ticks; while underground he drops ore at the player every 400 ticks.

## Guard-style AI + info screen (untested in game)
- Reference: Referencias/guardvillagers-5.0.0-26.3.0.jar, decompiled to _src/guardvillagers (read only, never shipped). Guard goals studied: GuardMeleeGoal (strafe back when close), MoveThroughVillage + GolemRandomStrollInVillage patrol, villagers/guards help when one is hurt.
- DwarfEntity goals: DwarfMeleeGoal (back-step at <=2 blocks), MoveThroughVillageGoal + GolemRandomStrollInVillageGoal (speed 0.5), OpenDoorGoal, home radius 16, target Enemy mobs (interval 5), alert other dwarfs. AFTER_DAMAGE: villager/dwarf hurt by a monster calls dwarfs within 16 blocks. Zombies/illagers also target dwarfs (Mob.targetSelector made accessible via dwarfmod.accesswidener, namespace official).
- Info: Alt + pick-block key (middle mouse) on a dwarf -> C2S DwarfInfoRequest -> S2C DwarfInfoPayload -> DwarfInfoScreen (E or Esc closes). Replaced the Alt+right click chat message.
- Gotcha: in PowerShell 'Rd' is an alias of Remove-Item; a helper function named Rd deleted DwarfEntity.java once (restored from build/libs sources jar).
