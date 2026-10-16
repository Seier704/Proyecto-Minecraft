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

## Blockbench model + warrior role (untested in game)
- art/make_bbmodel.py generates art/blockbench/dwarf.bbmodel (copper worker gear, from imagenes/WhatsApp Image 2026-10-13), dwarf_warrior.bbmodel, dwarven_armor.bbmodel, the shared 128x128 textures/entity/dwarf.png and the Java DwarfModel + DwarfAnimations. Edit the script, never the generated Java.
- Animations (idle/walk/attack) are vanilla AnimationDefinition/KeyframeAnimation: idle by ageInTicks, walk via applyWalk(walkPos, walkSpeed), attack driven by swingAnimation (attacks and mining). Blockbench nests head/arms in body; HumanoidModel needs them flat, so DwarfModel.attachToBody() re-parents them.
- Each bone has <bone>_worker / <bone>_warrior child parts; DwarfRenderState.role toggles which gear shows.
- Role 3 warrior (/dwarf warrior): permanent attribute modifiers +20 health, +3 damage, +8 armor; always holds the dwarven axe; home = village bell (never places one); does not sleep; trades monster drops for shields/axes/stout/golden apple, L5 dwarven axe. Village spawn cap raised 3 -> 4 (warrior is the 4th).
- Warrior look by level: gear groups renamed copper/steel; copper = workers + warrior lvl 1 (the reference photo), steel = warrior lvl 2+. Level synced to the client via DATA_LEVEL (getDwarfLevel()).
- Warrior armor for players: ModItems.WARRIOR_COPPER / WARRIOR_STEEL (WARRIOR_TIERS, not in TIERS). 3D worn models = WarriorGearModel (generated by make_bbmodel.py, same pieces refitted to the player body), rendered by DwarvenArmorRenderer.registerSet; icons/equipment/items JSON by art/make_warrior_items.py.
- Warrior trades: L1 copper set (emeralds + copper ingots), L2 copper piece + emeralds -> steel piece. Dwarfs whose offers were already generated don't get the new L1 offers.

## GeckoLib dwarf + warrior tiers (tested in game via DwarfLookGameTest screenshots)
- GeckoLib 5.5.7 (Modrinth maven, version id in gradle.properties geckolib_version). Dwarf = GeoEntity; DwarfRenderer extends GeoEntityRenderer with DefaultedEntityGeoModel("dwarf"). Old vanilla DwarfModel/DwarfAnimations/DwarfRenderState removed.
- art/make_dwarf_geo.py generates geckolib/models/entity/dwarf.geo.json, geckolib/animations/entity/dwarf.animation.json, textures/entity/dwarf.png and art/blockbench/dwarf*.bbmodel (GeckoLib "animated_entity_model" format; needs the GeckoLib Blockbench plugin). Shared painters/Model in art/bbtools.py; player armor in art/make_bbmodel.py.
- One geometry holds every look; DwarfRenderer hides <bone>_copper/_steel/_elite, beard and greataxe per role/tier.
- Conventions: Blockbench space; anim rotation applied as Rz(z)Ry(-y)Rx(-x), position (-x,y,z); geo.json mirrors x (origin=-to.x, pivot=-x, bone rotation [-x,-y,z]).
- Warrior two-handed axe: greataxe bone is a child of body; arms are solved per frame (IK + arm y-scale up to ~1.3) so both fists sit on the haft. Animations: idle/walk/attack (workers), idle_axe/walk_axe/attack_axe (warriors).
- Warrior tier (DATA_TIER, NBT WarriorTier) rolled at spawn 60/30/10%: +10/20/30 health, +4/8/12 armor, +2/4/7 damage. /dwarf warrior [1-3].
- Attacks: controller "action" triggerable anims; warriors start attack_axe and the hit lands 11 ticks later (MeleeGoal.axeSwing); workers trigger "attack" on hits and when mining a block.
- Warrior armor (copper/steel/veteran) only from the blacksmith: L2 copper (emeralds+copper), L3 steel (copper piece+emeralds), L5 veteran (steel piece+diamonds).
- BannerGameTest fails at "No dwarf became working" (Mine button stays disabled after the row click).

## Warrior tier 2 "guard" look (generated, not yet seen in game)
- Reference: front/back/armor-set image (round helm with cross band, red beard, light steel plate over chainmail, studded bracers, brown boots with steel toe caps). Slot name stays `steel` (gear group `*_steel`, item set `dwarven_warrior_steel`), so Java/trades/animations are unchanged.
- bbtools.py: `add_guard`, `add_guard_helmet`, `add_guard_beard` + painters g*_face. make_dwarf_geo.py uses `add_guard` for the `_steel` groups; make_bbmodel.py `player_guard` builds the worn armor (WarriorGearModel + dwarven_warrior_steel_gear.png). Old `add_warrior`/`player_steel` (horned helm + cape) are kept but unused.
- DwarfRenderer hides the base beard for steel too (the guard brings its own red beard). preview_warrior_2.png added to the preview strip.
- Python is not installed system-wide; run the scripts with any Python 3 + pillow + numpy from art/.
