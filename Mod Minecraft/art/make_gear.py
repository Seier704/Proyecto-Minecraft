"""Generates the 3D gear texture (128x128) and the Java model class for the dwarven armor."""
from PIL import Image
import random, sys, os

random.seed(7)
MAT = {
    "dark":  [(30, 32, 38), (46, 49, 57), (74, 78, 88)],     # base, mid, edge
    "trim":  [(88, 93, 104), (126, 132, 144), (190, 195, 206)],
    "bronze": [(92, 62, 34), (132, 92, 48), (190, 142, 78)],
    "mail":  [(26, 27, 32), (52, 55, 63), (82, 86, 96)],
}
# slot -> part -> list of (x,y,z,w,h,d,material)
G = {"head": {}, "chest": {}, "legs": {}, "feet": {}}
def add(slot, part, *c):
    G[slot].setdefault(part, []).append(c)

add("head", "head", -5, -9, -5, 10, 4, 10, "dark")
add("head", "head", -1, -11, -5, 2, 2, 10, "trim")
add("head", "head", -5, -5, 4, 10, 2, 1, "dark")
add("head", "head", -5, -5, -5, 10, 1, 1, "trim")
add("head", "head", -1, -6, -5, 2, 5, 1, "trim")
add("head", "head", -5, -5, -4, 1, 4, 6, "dark")
add("head", "head", 4, -5, -4, 1, 4, 6, "dark")
add("head", "head", -5, -3, 3, 10, 4, 1, "mail")
add("head", "head", -6, -8, -1, 1, 3, 3, "bronze")
add("head", "head", 5, -8, -1, 1, 3, 3, "bronze")

add("chest", "body", -5, -1, -3, 10, 2, 6, "dark")
add("chest", "body", -5, 1, -3, 10, 6, 1, "dark")
add("chest", "body", -1, 1, -4, 2, 8, 1, "trim")
add("chest", "body", -5, 1, 2, 10, 6, 1, "dark")
add("chest", "body", -4, 7, -3, 8, 2, 1, "trim")
add("chest", "right_arm", -6, -4, -4, 7, 2, 8, "dark")
add("chest", "right_arm", -5, -2, -3, 6, 2, 6, "trim")
add("chest", "right_arm", -4, -6, -1, 2, 2, 2, "bronze")
add("chest", "right_arm", -4, 3, -3, 5, 5, 6, "dark")
add("chest", "right_arm", -4, 8, -3, 5, 1, 6, "trim")
add("chest", "left_arm", -1, -4, -4, 7, 2, 8, "dark")
add("chest", "left_arm", 0, -2, -3, 6, 2, 6, "trim")
add("chest", "left_arm", 2, -6, -1, 2, 2, 2, "bronze")
add("chest", "left_arm", -1, 3, -3, 5, 5, 6, "dark")
add("chest", "left_arm", -1, 8, -3, 5, 1, 6, "trim")

add("legs", "body", -5, 9, -3, 10, 3, 6, "dark")
add("legs", "body", -2, 9, -4, 4, 3, 1, "trim")
add("legs", "body", -5, 12, -3, 10, 4, 1, "mail")
add("legs", "body", -5, 12, 2, 10, 4, 1, "mail")
add("legs", "body", -5, 12, -2, 1, 4, 4, "mail")
add("legs", "body", 4, 12, -2, 1, 4, 4, "mail")
for p in ("right_leg", "left_leg"):
    add("legs", p, -3, 4, -3, 6, 3, 2, "trim")

for p in ("right_leg", "left_leg"):
    add("feet", p, -3, 8, -3, 6, 4, 6, "dark")
    add("feet", p, -3, 10, -5, 6, 2, 2, "dark")
    add("feet", p, -4, 7, -4, 8, 1, 8, "trim")

W = H = 128
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

def fill(x, y, w, h, mat, hexmail=False):
    base, mid, edge = MAT[mat]
    for j in range(h):
        for i in range(w):
            c = base
            if random.random() < 0.25:
                c = mid
            if mat == "mail":
                c = mid if (i + (j % 2) * 2) % 4 < 2 and j % 2 == 0 else base
            if i == 0 or j == 0:
                c = edge
            elif i == w - 1 or j == h - 1:
                c = tuple(int(v * 0.6) for v in base)
            px[x + i, y + j] = c + (255,)
    if mat in ("dark", "trim") and w > 4 and h > 4:
        for rx, ry in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            px[x + rx, y + ry] = MAT["bronze"][2] + (255,)

cx = cy = 0
rowh = 0
alloc = {}
def pack(w, h):
    global cx, cy, rowh
    if cx + w > W:
        cx = 0; cy += rowh; rowh = 0
    pos = (cx, cy)
    cx += w; rowh = max(rowh, h)
    return pos

def paint(c):
    x, y, z, w, h, d, mat = c
    if c in alloc:
        return alloc[c]
    u, v = pack(2 * d + 2 * w, d + h)
    fill(u + d, v, w, d, mat)
    fill(u + d + w, v, w, d, mat)
    fill(u, v + d, d, h, mat)
    fill(u + d, v + d, w, h, mat)
    fill(u + d + w, v + d, d, h, mat)
    fill(u + 2 * d + w, v + d, w, h, mat)
    alloc[c] = (u, v)
    return (u, v)

PARTS = [("head", (0, 0, 0)), ("body", (0, 0, 0)), ("right_arm", (-5, 2, 0)),
         ("left_arm", (5, 2, 0)), ("right_leg", (-1.9, 12, 0)), ("left_leg", (1.9, 12, 0))]

java = []
for slot, parts in G.items():
    lines = []
    for name, (px_, py_, pz_) in PARTS:
        cb = ""
        for c in parts.get(name, []):
            u, v = paint(c)
            x, y, z, w, h, d, _ = c
            cb += "\n\t\t\t.texOffs(%d, %d).addBox(%sF, %sF, %sF, %sF, %sF, %sF)" % (u, v, x, y, z, w, h, d)
        pre = 'PartDefinition head = ' if name == 'head' else ''
        lines.append('\t\t%sroot.addOrReplaceChild("%s", CubeListBuilder.create()%s, PartPose.offset(%sF, %sF, %sF));'
                     % (pre, name, cb, px_, py_, pz_))
        if name == 'head':
            lines.append('\t\thead.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);')
    java.append("\tpublic static LayerDefinition %s() {\n\t\tMeshDefinition mesh = new MeshDefinition();\n\t\tPartDefinition root = mesh.getRoot();\n"
                "%s\n\t\treturn LayerDefinition.create(mesh, 128, 128);\n\t}\n" % (slot, "\n".join(lines)))

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
img.save(os.path.join(out, "dwarven_gear.png"))
open(os.path.join(out, "gen_layers.txt"), "w").write("\n".join(java))
print("atlas used height", cy + rowh)


