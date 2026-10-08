"""Recolors the top-tier dwarven art into the bronze and steel tiers and writes their resource files."""
from PIL import Image
import os, json

RES = os.path.join("..", "dwarfmod", "src", "main", "resources", "assets", "dwarfmod")
TIERS = {
    "bronze": dict(gain=2.3, tint=(1.0, 0.68, 0.38)),
    "steel": dict(gain=1.9, tint=(0.90, 0.95, 1.05)),
}
PIECES = ["helmet", "chestplate", "leggings", "boots"]

def recolor(src, dst, gain, tint):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (r + g + b) / 3
            px[x, y] = tuple(min(255, int(lum * gain * t)) for t in tint) + (a,)
    im.save(dst)

for tier, cfg in TIERS.items():
    name = "dwarven_" + tier
    ent = os.path.join(RES, "textures", "entity")
    recolor(os.path.join(ent, "equipment", "humanoid", "dwarven.png"),
            os.path.join(ent, "equipment", "humanoid", name + ".png"), **cfg)
    recolor(os.path.join(ent, "equipment", "humanoid_leggings", "dwarven.png"),
            os.path.join(ent, "equipment", "humanoid_leggings", name + ".png"), **cfg)
    recolor(os.path.join(ent, "dwarven_gear.png"), os.path.join(ent, name + "_gear.png"), **cfg)
    json.dump({"layers": {"humanoid": [{"texture": "dwarfmod:" + name}],
                          "humanoid_leggings": [{"texture": "dwarfmod:" + name}]}},
              open(os.path.join(RES, "equipment", name + ".json"), "w"))
    for p in PIECES:
        item = "%s_%s" % (name, p)
        recolor(os.path.join(RES, "textures", "item", "dwarven_%s.png" % p),
                os.path.join(RES, "textures", "item", item + ".png"), **cfg)
        json.dump({"model": {"type": "minecraft:model", "model": "dwarfmod:item/" + item}},
                  open(os.path.join(RES, "items", item + ".json"), "w"))
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "dwarfmod:item/" + item}},
                  open(os.path.join(RES, "models", "item", item + ".json"), "w"))
print("ok")
