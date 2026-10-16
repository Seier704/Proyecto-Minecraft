"""Player-wearable 3D armor of the dwarf warrior (WarriorGearModel.java + *_gear.png) and the loose
armor display model. The dwarf entity itself (GeckoLib) is made by make_dwarf_geo.py."""
from bbtools import *

BONES = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"]

# ---------------------------------------------------------------- player armor (the warrior's gear, wearable)
# Same pieces as the dwarf, refitted to the player body: head 24-32, body 8x12x4 at 12-24,
# arms 4x12x4 at x 4..8, legs 4x12x4. One HumanoidModel layer per equipment slot, like DwarvenGearModel.
PLAYER_PIVOTS = {"head": (0, 24, 0), "body": (0, 24, 0), "right_arm": (-5, 22, 0),
                 "left_arm": (5, 22, 0), "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}
SLOTS = ["head", "chest", "legs", "feet"]
LEGS_X = (("r", -3.9), ("l", -0.1))  # x of each leg's outer-left edge (legs are 4 wide)


class Shift:
    """Moves dwarf head pieces up onto the taller player neck."""
    def __init__(self, m, dy):
        self.m, self.dy = m, dy

    def cube(self, g, name, frm, to, paint, inflate=0.0):
        self.m.cube(g, name, [frm[0], frm[1] + self.dy, frm[2]], [to[0], to[1] + self.dy, to[2]], paint, inflate)


def player_copper(m):
    add_helmet(Shift(m, 4), "head_head")
    m.cube("chest_body", "chestplate", [-4, 17, -2], [4, 24, 2], chest_face, 0.6)
    for s, g in ((-1, "chest_right_arm"), (1, "chest_left_arm")):
        x0, x1 = (-9, -4) if s < 0 else (4, 9)
        b0, b1 = (-8, -4) if s < 0 else (4, 8)
        m.cube(g, "pauldron", [x0, 21, -3], [x1, 24, 3], pauldron_face, 0.35)
        m.cube(g, "bracer", [b0, 13, -2], [b1, 17, 2], bracer_face, 0.35)
    m.cube("legs_body", "tunic_skirt", [-4, 8, -2], [4, 13, 2], skirt_face, 0.65)
    m.cube("legs_body", "belt", [-4, 12, -2], [4, 14, 2], belt_face, 0.8)
    m.cube("legs_body", "buckle", [-1.5, 12, -3], [1.5, 14, -2], buckle_face, 0.4)
    for side, x0 in LEGS_X:
        g = "legs_right_leg" if side == "r" else "legs_left_leg"
        m.cube(g, "leg_wrap", [x0, 5, -2], [x0 + 4, 9, 2], bracer_face, 0.3)
        g = "feet_right_leg" if side == "r" else "feet_left_leg"
        m.cube(g, "boot", [x0, 0, -2], [x0 + 4, 3, 2], boot_face, 0.4)
        m.cube(g, "boot_toe", [x0, 0, -3], [x0 + 4, 2, -2], boot_face, 0.4)
        m.cube(g, "boot_cuff", [x0, 3, -2], [x0 + 4, 5, 2], cuff_face, 0.6)


def player_steel(m):
    add_steel_helmet(Shift(m, 4), "head_head")
    m.cube("chest_body", "w_breastplate", [-4, 17, -2], [4, 24, 2], plate_face, 0.6)
    m.cube("chest_body", "w_cape", [-3.5, 11, 2.7], [3.5, 23, 3.7], cape_face)
    for s, g in ((-1, "chest_right_arm"), (1, "chest_left_arm")):
        bx = (-9.5, -3.5) if s < 0 else (3.5, 9.5)
        lx = (-9, -4) if s < 0 else (4, 9)
        gx = (-8, -4) if s < 0 else (4, 8)
        m.cube(g, "w_pauldron", [bx[0], 20, -3.5], [bx[1], 24, 3.5], spaul_face, 0.25)
        m.cube(g, "w_lame", [lx[0], 18.5, -3], [lx[1], 20.5, 3], spaul_face, 0.3)
        m.cube(g, "w_gauntlet", [gx[0], 12, -2], [gx[1], 16, 2], studded_face, 0.45)
    m.cube("legs_body", "w_mail_skirt", [-4, 8, -2], [4, 14, 2], mail_face, 0.6)
    m.cube("legs_body", "w_belt", [-4, 14, -2], [4, 16, 2], dbelt_face, 0.85)
    m.cube("legs_body", "w_buckle", [-1.5, 14, -3], [1.5, 16, -2], gold_face, 0.45)
    for side, x0 in LEGS_X:
        g = "legs_right_leg" if side == "r" else "legs_left_leg"
        m.cube(g, "w_knee", [x0, 6, -3], [x0 + 4, 8, -2], studded_face, 0.3)
        g = "feet_right_leg" if side == "r" else "feet_left_leg"
        m.cube(g, "w_sabaton", [x0, 0, -2], [x0 + 4, 4, 2], studded_face, 0.45)
        m.cube(g, "w_toe", [x0, 0, -3], [x0 + 4, 2, -2], steel_face, 0.45)


def player_guard(m):
    """Tier 2 set: round cross-banded helm, light steel plate over chainmail, studded bracers, steel toe caps."""
    add_guard_helmet(Shift(m, 4), "head_head")
    m.cube("chest_body", "g_mail", [-4, 16.5, -2], [4, 24, 2], mail_face, 0.7)
    m.cube("chest_body", "g_breastplate", [-3.5, 17.5, -2], [3.5, 23.5, 2], gplate_face, 0.6)
    for sx in (-2.5, 1.5):
        m.cube("chest_body", "g_strap", [sx, 17, 1.9], [sx + 1, 23.5, 2], glove_face, 0.72)
    for s, g in ((-1, "chest_right_arm"), (1, "chest_left_arm")):
        bx = (-9.5, -3.5) if s < 0 else (3.5, 9.5)
        lx = (-9, -4) if s < 0 else (4, 9)
        gx = (-8, -4) if s < 0 else (4, 8)
        m.cube(g, "g_pauldron", [bx[0], 20, -3.5], [bx[1], 24, 3.5], gpauldron_face, 0.3)
        m.cube(g, "g_lame", [lx[0], 18.5, -3], [lx[1], 20.3, 3], gpauldron_face, 0.25)
        m.cube(g, "g_bracer", [gx[0], 12.8, -2], [gx[1], 16.8, 2], gbracer_face, 0.45)
    m.cube("legs_body", "g_mail_skirt", [-4, 8, -2], [4, 14, 2], mail_face, 0.6)
    m.cube("legs_body", "g_belt", [-4, 14, -2], [4, 16, 2], glove_face, 0.85)
    m.cube("legs_body", "g_buckle", [-1.5, 14, -3], [1.5, 16, -2], bsteel_face, 0.5)
    m.cube("legs_body", "g_pouch", [1, 10, 2.6], [4, 13, 4.6], pouch_face)
    for side, x0 in LEGS_X:
        g = "legs_right_leg" if side == "r" else "legs_left_leg"
        m.cube(g, "g_trousers", [x0, 5, -2], [x0 + 4, 10, 2], dcloth_face, 0.15)
        g = "feet_right_leg" if side == "r" else "feet_left_leg"
        m.cube(g, "g_boot", [x0, 0, -2], [x0 + 4, 5, 2], boot_face, 0.45)
        m.cube(g, "g_boot_cuff", [x0, 4, -2], [x0 + 4, 5.5, 2], cuff_face, 0.6)
        m.cube(g, "g_toecap", [x0, 0, -3], [x0 + 4, 2.2, -2], gbootcap_face, 0.5)


def player_veteran(m):
    add_elite_helmet(Shift(m, 4), "head_head")
    m.cube("chest_body", "e_breastplate", [-4, 17, -2], [4, 24, 2], elite_plate_face, 0.6)
    for s, g in ((-1, "chest_right_arm"), (1, "chest_left_arm")):
        def X(a, b):
            return (a, b) if s < 0 else (-b, -a)
        m.cube(g, "e_pauldron", [*X(-10, -4)[:1], 20.5, -3.5], [X(-10, -4)[1], 24.5, 3.5], lames_face, 0.3)
        m.cube(g, "e_lame1", [X(-9.5, -3.5)[0], 18.5, -3], [X(-9.5, -3.5)[1], 20.5, 3], lames_face, 0.3)
        m.cube(g, "e_lame2", [X(-9, -4)[0], 17, -2.5], [X(-9, -4)[1], 19, 2.5], lames_face, 0.3)
        m.cube(g, "e_vambrace", [X(-8, -4)[0], 14, -2], [X(-8, -4)[1], 17, 2], bsteel_face, 0.45)
        m.cube(g, "e_glove", [X(-8, -4)[0], 12, -2], [X(-8, -4)[1], 14, 2], glove_face, 0.3)
    m.cube("legs_body", "e_tunic", [-4, 9, -2], [4, 14, 2], dcloth_face, 0.55)
    m.cube("legs_body", "e_faulds", [-4, 13, -2], [4, 15, 2], lames_face, 0.75)
    m.cube("legs_body", "e_belt", [-4, 14, -2], [4, 16, 2], dbelt_face, 0.9)
    m.cube("legs_body", "e_buckle", [-1.5, 14, -3], [1.5, 16, -2], bsteel_face, 0.55)
    m.cube("legs_body", "e_tasset", [-3, 10, -3], [3, 13, -2], lames_face, 0.2)
    m.cube("legs_body", "e_pouch", [-3, 10, 2.6], [3, 13, 4.6], pouch_face)
    for side, x0 in LEGS_X:
        g = "legs_right_leg" if side == "r" else "legs_left_leg"
        m.cube(g, "e_knee", [x0, 6, -3], [x0 + 4, 8, -2], lames_face, 0.3)
        g = "feet_right_leg" if side == "r" else "feet_left_leg"
        m.cube(g, "e_boot", [x0, 0, -2], [x0 + 4, 5, 2], bsteel_face, 0.45)
        m.cube(g, "e_boot_toe", [x0, 0, -3], [x0 + 4, 2, -2], bsteel_face, 0.45)
        m.cube(g, "e_boot_cuff", [x0, 4.5, -2], [x0 + 4, 5.5, 2], riveted_face, 0.6)


def player_layers(m, prefix):
    out = ""
    for slot in SLOTS:
        body = ""
        for bone in BONES:
            ox, oy, oz = PLAYER_PIVOTS[bone]
            var = "PartDefinition head = " if bone == "head" else ""
            body += (f"\t\t{var}root.addOrReplaceChild(\"{bone}\", {java_cubes(f'{slot}_{bone}', (ox, oy, oz), m)},"
                     f" PartPose.offset({jf(ox)}, {jf(24 - oy)}, {jf(oz)}));\n")
            if bone == "head":
                body += "\t\thead.addOrReplaceChild(\"hat\", CubeListBuilder.create(), PartPose.ZERO);\n"
        out += (f"\tpublic static LayerDefinition {prefix}{slot.title()}() {{\n"
                f"\t\tMeshDefinition mesh = new MeshDefinition();\n\t\tPartDefinition root = mesh.getRoot();\n"
                f"{body}\t\treturn LayerDefinition.create(mesh, {TEX}, {TEX});\n\t}}\n\n")
    return out


gear_java = ""
for tier, seed, build in (("dwarven_warrior_copper", 31, player_copper), ("dwarven_warrior_steel", 37, player_guard),
                          ("dwarven_warrior_veteran", 41, player_veteran)):
    pm = Model(tier + "_gear", seed)
    for slot in SLOTS:
        for bone, origin in PLAYER_PIVOTS.items():
            pm.group(f"{slot}_{bone}", origin)
    build(pm)
    pm.save()
    pm.painter.img.save(os.path.join(MOD, "main", "resources", "assets", "dwarfmod", "textures", "entity", tier + "_gear.png"))
    gear_java += player_layers(pm, tier.split("_")[-1])

with open(os.path.join(JAVA, "WarriorGearModel.java"), "w", encoding="utf-8", newline="\n") as f:
    f.write(f"""package com.dwarfmod.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

// GENERATED by art/make_bbmodel.py: the dwarf warriors' copper, steel and veteran gear refitted to the player body,
// one layer per equipment slot (rendered through DwarvenGearModel by DwarvenArmorRenderer).
public final class WarriorGearModel {{
{gear_java.rstrip()}
}}
""")
print("wrote WarriorGearModel.java")

# ---------------------------------------------------------------- armor set (display)
armor = Model("dwarven_armor", 23)
armor.group("helmet", [0, 20, 0])
armor.group("chestplate", [0, 11, 0])
armor.group("right_pauldron", [-7, 18, 0], "chestplate")
armor.group("left_pauldron", [7, 18, 0], "chestplate")
armor.group("right_boot", [-2.5, 10, 0])
armor.group("left_boot", [2.5, 10, 0])
add_helmet(armor, "helmet")
add_chest(armor, "chestplate", "right_pauldron", "left_pauldron")
add_boots(armor, "right_boot", "left_boot")
armor.save()

