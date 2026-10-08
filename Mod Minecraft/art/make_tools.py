"""Draws 16x16 icons for the dwarven tools and writes their item/model json."""
from PIL import Image
import os, json

RES = os.path.join("..", "dwarfmod", "src", "main", "resources", "assets", "dwarfmod")
OL = (14, 15, 18, 255); D = (52, 56, 66, 255); M = (96, 102, 116, 255); L = (170, 176, 190, 255)
W1 = (92, 62, 34, 255); W2 = (132, 92, 48, 255); BR = (200, 150, 70, 255)

def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))

def handle(px):
    for i in range(2, 13):
        px[i, 15 - i] = W2
        if i + 1 < 16:
            px[i + 1, 15 - i] = W1
    px[1, 14] = OL; px[2, 14] = W1
    px[3, 12] = BR; px[4, 11] = BR

def blob(px, pts, c=M):
    for x, y in pts:
        px[x, y] = c

def draw(name):
    im = new(); px = im.load()
    handle(px)
    if name == "pickaxe":
        for x, y in [(5, 3), (6, 2), (7, 2), (8, 2), (9, 2), (10, 3), (11, 4), (12, 5), (13, 6), (4, 4), (5, 4), (11, 5), (12, 6), (6, 3), (10, 4)]:
            px[x, y] = M
        for x, y in [(6, 2), (7, 2), (8, 2), (9, 2)]:
            px[x, y] = L
        px[13, 7] = OL; px[4, 5] = OL
    elif name == "axe":
        for x, y in [(8, 2), (9, 2), (10, 3), (11, 4), (11, 5), (10, 5), (9, 5), (8, 4), (9, 4), (10, 4), (9, 3), (7, 3), (7, 4), (8, 3)]:
            px[x, y] = M
        for x, y in [(9, 2), (10, 3), (11, 4), (11, 5)]:
            px[x, y] = L
        px[10, 6] = OL; px[8, 5] = D
    elif name == "shovel":
        for x, y in [(11, 3), (12, 2), (12, 3), (13, 3), (11, 4), (12, 4), (13, 4), (12, 5), (10, 4), (11, 2), (13, 2)]:
            px[x, y] = M
        for x, y in [(12, 2), (13, 3), (13, 4)]:
            px[x, y] = L
        px[10, 5] = D
    elif name == "hoe":
        for x, y in [(6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (11, 4), (6, 4), (11, 5), (10, 4), (12, 4)]:
            px[x, y] = M
        for x, y in [(7, 3), (8, 3), (9, 3)]:
            px[x, y] = L
        px[6, 5] = D
    elif name == "sword":
        im = new(); px = im.load()
        for i in range(4, 13):
            px[i, 15 - i] = M
            px[i + 1, 15 - i] = L
            px[i, 14 - i] = D
        px[13, 2] = L; px[13, 3] = L; px[14, 2] = L
        for x, y in [(3, 9), (4, 8), (5, 7), (6, 10), (2, 8), (5, 10)]:
            px[x, y] = BR
        for x, y in [(2, 12), (3, 13), (1, 13), (2, 14)]:
            px[x, y] = W2
        px[1, 14] = OL
    return im

for t in ["pickaxe", "axe", "shovel", "hoe", "sword"]:
    item = "dwarven_" + t
    draw(t).save(os.path.join(RES, "textures", "item", item + ".png"))
    json.dump({"model": {"type": "minecraft:model", "model": "dwarfmod:item/" + item}},
              open(os.path.join(RES, "items", item + ".json"), "w"))
    json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": "dwarfmod:item/" + item}},
              open(os.path.join(RES, "models", "item", item + ".json"), "w"))
print("ok")
