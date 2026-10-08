"""Draws 16x16 mug icons for the dwarven drinks and writes item/model json."""
from PIL import Image
import os, json

RES = os.path.join("..", "dwarfmod", "src", "main", "resources", "assets", "dwarfmod")
OL = (24, 20, 18, 255); WOOD = (120, 82, 44, 255); WOOD2 = (160, 112, 60, 255); BAND = (150, 155, 168, 255)
FOAM = (250, 246, 232, 255)
DRINKS = {"dwarven_ale": (214, 140, 40), "dwarven_mead": (240, 196, 70),
          "dwarven_stout": (70, 42, 26), "dwarven_spirit": (90, 170, 230)}

def mug(liquid):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = im.load()
    for y in range(3, 14):
        for x in range(3, 11):
            edge = x in (3, 10) or y in (3, 13)
            px[x, y] = OL if edge else (WOOD2 if x < 5 else WOOD)
    for y in range(5, 12):
        for x in range(4, 10):
            px[x, y] = liquid + (255,)
    for x in range(4, 10):
        px[x, 4] = FOAM
        px[x, 5] = tuple(min(255, c + 25) for c in liquid) + (255,)
    for x in range(3, 11):
        px[x, 8] = BAND if x not in (3, 10) else OL
    for (x, y) in [(11, 5), (12, 5), (12, 6), (12, 7), (12, 8), (12, 9), (11, 10), (11, 6), (11, 9)]:
        px[x, y] = OL if (x, y) in [(12, 5), (12, 6), (12, 7), (12, 8), (12, 9)] else WOOD2
    return im

for name, col in DRINKS.items():
    mug(col).save(os.path.join(RES, "textures", "item", name + ".png"))
    json.dump({"model": {"type": "minecraft:model", "model": "dwarfmod:item/" + name}}, open(os.path.join(RES, "items", name + ".json"), "w"))
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "dwarfmod:item/" + name}}, open(os.path.join(RES, "models", "item", name + ".json"), "w"))
print("ok")
