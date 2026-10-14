"""Item icons and resource files for the dwarf warriors' armor sets (copper, steel, veteran).
The 3D worn models and their *_gear.png textures come from make_bbmodel.py."""
from PIL import Image
import os, json

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dwarfmod", "src", "main", "resources", "assets", "dwarfmod")
SETS = {
    "dwarven_warrior_copper": dict(gain=1.8, tint=(1.0, 0.52, 0.30)),
    "dwarven_warrior_steel": dict(gain=1.55, tint=(0.95, 0.98, 1.06)),
    "dwarven_warrior_veteran": dict(gain=1.25, tint=(0.92, 0.96, 1.04)),
}
PIECES = ["helmet", "chestplate", "leggings", "boots"]
GOLD = (236, 196, 80, 255)


def recolor(src, dst, gain, tint, trim=False):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (r + g + b) / 3
            px[x, y] = tuple(min(255, int(lum * gain * t)) for t in tint) + (a,)
    if trim:  # steel set: gold trim on the lowest opaque row, like the worn gear
        rows = [y for y in range(im.height) if any(px[x, y][3] for x in range(im.width))]
        if rows:
            for x in range(im.width):
                if px[x, rows[-1]][3]:
                    px[x, rows[-1]] = GOLD
    im.save(dst)


def dump(obj, *path):
    with open(os.path.join(RES, *path), "w") as f:
        json.dump(obj, f)


for name, cfg in SETS.items():
    steel = not name.endswith("copper")  # steel and veteran get the gold trim
    ent = os.path.join(RES, "textures", "entity", "equipment")
    for layer in ("humanoid", "humanoid_leggings"):
        recolor(os.path.join(ent, layer, "dwarven.png"), os.path.join(ent, layer, name + ".png"), **cfg)
    dump({"layers": {"humanoid": [{"texture": "dwarfmod:" + name}],
                     "humanoid_leggings": [{"texture": "dwarfmod:" + name}]}}, "equipment", name + ".json")
    for p in PIECES:
        item = "%s_%s" % (name, p)
        recolor(os.path.join(RES, "textures", "item", "dwarven_%s.png" % p),
                os.path.join(RES, "textures", "item", item + ".png"), trim=steel, **cfg)
        dump({"model": {"type": "minecraft:model", "model": "dwarfmod:item/" + item}}, "items", item + ".json")
        dump({"parent": "minecraft:item/generated", "textures": {"layer0": "dwarfmod:item/" + item}},
             "models", "item", item + ".json")
print("ok")
