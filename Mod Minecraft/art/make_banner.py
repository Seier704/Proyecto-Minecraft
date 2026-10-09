"""Dwarven miner banner: 32x32 block atlas (cloth / wood / gold / iron) + 16x16 item icon.
Usage: python make_banner.py <assets/dwarfmod/textures dir>"""
from PIL import Image
import sys

out = sys.argv[1]
T = (0, 0, 0, 0)
def c(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
RED1, RED2, RED3, RED4 = c("#5e0f12"), c("#7d171b"), c("#9c2227"), c("#b8323a")
GOLD1, GOLD2, GOLD3 = c("#8a6a14"), c("#d4a62a"), c("#f6dc6e")
WOOD1, WOOD2, WOOD3 = c("#3b2615"), c("#573a20"), c("#6e4b2a")
IRON1, IRON2, IRON3, IRON4 = c("#2b2d33"), c("#4a4d56"), c("#767a85"), c("#a3a7b1")

im = Image.new("RGBA", (32, 32), T)
px = im.putpixel

# ---- cloth: texels (0,0)-(19,21) -------------------------------------------------
W, H = 20, 22
for y in range(H):
    for x in range(W):
        col = RED3
        if x % 5 == 2: col = RED2            # woven vertical folds
        if x % 5 == 3: col = RED4 if y % 7 else RED3
        if x % 5 == 4: col = RED2
        px((x, y), col)
# swallow-tail notch at the bottom
for y in range(H - 6, H):
    d = y - (H - 7)
    for x in range(W // 2 - d, W // 2 + d):
        px((x, y), T)
# gold trim: top bar, sides, tails
for x in range(W):
    px((x, 0), GOLD3); px((x, 1), GOLD2); px((x, 2), GOLD1)
for y in range(3, H - 6):
    px((0, y), GOLD2); px((1, y), GOLD1); px((W - 1, y), GOLD2); px((W - 2, y), GOLD1)
for y in range(H - 6, H):
    d = y - (H - 7)
    for x in (0, 1, W - 1, W - 2):
        if px is not None and im.getpixel((x, y)) != T:
            px((x, y), GOLD2)
    for x in (W // 2 - d - 1, W // 2 - d, W // 2 + d - 1, W // 2 + d):
        if 0 <= x < W and im.getpixel((x, y)) != T:
            px((x, y), GOLD2)
# emblem: two crossed pickaxes on a small anvil, centred at (10, 10)
def line(p, q, col):
    (x0, y0), (x1, y1) = p, q
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        px((round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)), col)
line((4, 5), (15, 16), WOOD2); line((15, 5), (4, 16), WOOD2)     # handles
line((4, 4), (8, 4), IRON3); line((3, 5), (9, 5), IRON2)          # pick head left
line((16, 4), (12, 4), IRON3); line((17, 5), (11, 5), IRON2)      # pick head right
line((4, 4), (3, 6), IRON4); line((15, 4), (16, 6), IRON4)
for x in range(6, 14):                                            # anvil
    px((x, 14), IRON4)
for x in range(7, 13):
    px((x, 15), IRON3)
for x in range(8, 12):
    px((x, 16), IRON2)
for x in range(7, 13):
    px((x, 17), IRON1)

# ---- wood: texels (22,0)-(29,7) --------------------------------------------------
for y in range(8):
    for x in range(8):
        px((22 + x, y), WOOD2 if (x + (y // 2)) % 3 else WOOD3)
    px((22 + (y * 3) % 8, y), WOOD1)
# ---- gold: texels (22,10)-(25,13) ------------------------------------------------
for y in range(4):
    for x in range(4):
        px((22 + x, 10 + y), GOLD3 if (x + y) % 4 == 0 else GOLD2)
    px((22 + y, 13), GOLD1)
# ---- iron/stone base: texels (22,16)-(29,23) -------------------------------------
for y in range(8):
    for x in range(8):
        px((22 + x, 16 + y), IRON3 if (x * 3 + y * 5) % 7 else IRON2)
    px((22, 16 + y), IRON1)
im.save(f"{out}/block/miner_banner.png")

# ---- 16x16 inventory icon --------------------------------------------------------
ic = Image.new("RGBA", (16, 16), T)
ip = ic.putpixel
for x in range(1, 15):
    ip((x, 1), WOOD2); ip((x, 2), WOOD1)                           # crossbar
ip((0, 1), GOLD2); ip((0, 2), GOLD2); ip((15, 1), GOLD2); ip((15, 2), GOLD2)
ip((7, 0), GOLD3); ip((8, 0), GOLD3)
for y in range(3, 14):
    for x in range(3, 13):
        ip((x, y), RED3 if x % 3 else RED2)
for x in range(3, 13):
    ip((x, 3), GOLD2)
for y in range(3, 14):
    ip((3, y), GOLD2); ip((12, y), GOLD2)
for y in range(12, 15):                                            # tail notch
    d = y - 11
    for x in range(8 - d, 8 + d):
        ip((x, y), T)
for y in range(14, 15):
    pass
for i in range(5):                                                 # crossed picks
    ip((5 + i, 5 + i), WOOD2); ip((10 - i, 5 + i), WOOD2)
for x in (5, 6, 7, 9, 10, 11):
    ip((x, 5), IRON4)
for x in range(6, 10):
    ip((x, 11), IRON3)
ic.save(f"{out}/item/miner_banner.png")
