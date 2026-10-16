"""Shared pieces of the dwarf art scripts: palette, face painters, the box-UV Model/atlas builder
(writes .bbmodel files) and the armor pieces reused by the dwarf and by the player armor."""
from PIL import Image
import base64, io, json, os, random, uuid

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "blockbench")
os.makedirs(OUT, exist_ok=True)
TEX = 128
MOD = os.path.join(os.path.dirname(OUT), "..", "dwarfmod", "src")
RES = os.path.join(MOD, "main", "resources", "assets", "dwarfmod")
JAVA = os.path.join(MOD, "client", "java", "com", "dwarfmod", "client")

# ---------------------------------------------------------------- palette
MAT = {
    # base, light, dark
    "skin":    [(222, 168, 128), (236, 188, 150), (190, 136, 100)],
    "hair":    [(104, 60, 30), (132, 80, 40), (74, 42, 22)],
    "leather": [(152, 80, 42), (186, 108, 60), (112, 56, 28)],
    "rust":    [(140, 74, 40), (176, 98, 52), (100, 50, 26)],
    "iron":    [(140, 140, 144), (184, 184, 188), (96, 96, 102)],
    "pants":   [(92, 74, 48), (112, 92, 60), (66, 52, 34)],
    "boot":    [(128, 64, 32), (160, 88, 46), (90, 44, 22)],
    "belt":    [(168, 134, 92), (196, 164, 116), (126, 98, 64)],
    # warrior
    "steel":   [(150, 156, 166), (196, 202, 212), (98, 104, 114)],
    "mail":    [(108, 112, 120), (150, 154, 162), (62, 64, 70)],
    "horn":    [(226, 214, 182), (244, 236, 210), (180, 166, 132)],
    "cloth":   [(150, 32, 32), (186, 52, 48), (104, 20, 22)],
    "dleather": [(78, 50, 32), (100, 66, 42), (54, 34, 22)],
    "gold":    [(214, 170, 58), (246, 210, 96), (160, 120, 36)],
}
STUD = (210, 210, 214)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


class Painter:
    def __init__(self, seed, size=TEX):
        self.size = size
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rnd = random.Random(seed)

    def put(self, x, y, c):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[x, y] = tuple(c) if len(c) == 4 else tuple(c) + (255,)

    def fill(self, x, y, w, h, mat):
        base, light, dark = MAT[mat]
        for j in range(h):
            for i in range(w):
                r = self.rnd.random()
                c = base
                if mat in ("leather", "rust", "boot"):
                    # weathered patches like the reference
                    if r < 0.18:
                        c = light
                    elif r < 0.34:
                        c = dark
                elif mat in ("hair",):
                    c = light if (i + j * 3) % 5 == 0 else (dark if r < 0.3 else base)
                else:
                    c = light if r < 0.15 else (dark if r < 0.25 else base)
                self.put(x + i, y + j, c)
        # outline: light top, dark bottom
        if w > 2 and h > 2 and mat not in ("skin", "hair"):
            for i in range(w):
                self.put(x + i, y, shade(light, 1.05))
                self.put(x + i, y + h - 1, shade(dark, 0.85))


# ---------------------------------------------------------------- face detail painters
# signature: f(p, x, y, w, h, face)  -- rect of one face in the atlas
def head_face(p, x, y, w, h, face):
    if face == "north":
        p.fill(x, y, w, h, "skin")
        for i in range(w):  # hair line / brows
            p.put(x + i, y, MAT["hair"][2])
            p.put(x + i, y + 1, MAT["hair"][0])
        for i in (1, 2, 5, 6):  # angry brows
            p.put(x + i, y + 3, MAT["hair"][2])
        for ex in (1, 5):  # eyes
            p.put(x + ex, y + 4, (245, 245, 245))
            p.put(x + ex + 1, y + 4, (40, 30, 26))
        p.put(x + 3, y + 5, MAT["skin"][2])  # nose
        p.put(x + 4, y + 5, MAT["skin"][2])
        for i in range(1, 7):  # mustache
            p.put(x + i, y + 6, MAT["hair"][1] if i in (3, 4) else MAT["hair"][0])
        for i in range(w):
            p.put(x + i, y + 7, MAT["hair"][0])
    elif face in ("east", "west"):
        p.fill(x, y, w, h, "hair")
        p.put(x + w // 2, y + 4, MAT["skin"][2])  # ear
        p.put(x + w // 2, y + 5, MAT["skin"][0])
    else:
        p.fill(x, y, w, h, "hair")


def helmet_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "rust")
    if face in ("north", "south", "east", "west"):
        for i in range(w):  # iron rim
            p.put(x + i, y + h - 1, MAT["iron"][0])
            p.put(x + i, y + h - 2, MAT["iron"][2])
        for i in range(1, w - 1, 3):
            p.put(x + i, y + h - 2, STUD)


def crest_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "iron")
    for i in range(1, w, 3):
        p.put(x + i, y + h // 2, STUD)


def beard_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "hair")
    if face == "north":
        for j in range(h):
            for i in range(w):
                if (i + j) % 4 == 0:
                    p.put(x + i, y + j, MAT["hair"][1])


def chest_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "leather")
    if face in ("north", "south"):
        for sx in (1, w - 3):  # shoulder straps
            for j in range(h):
                p.put(x + sx, y + j, MAT["iron"][0])
                p.put(x + sx + 1, y + j, MAT["iron"][2])
        for i in range(w):  # chest band
            p.put(x + i, y + h - 3, MAT["iron"][0])
            p.put(x + i, y + h - 2, MAT["iron"][2])
        if face == "north":  # torn collar
            p.put(x + w // 2 - 1, y, (40, 26, 18))
            p.put(x + w // 2, y, (40, 26, 18))
            p.put(x + w // 2, y + 1, (60, 36, 22))


def skirt_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "leather")
    for i in range(w):  # frayed hem
        if i % 3 != 1:
            p.put(x + i, y + h - 1, MAT["leather"][2])
        else:
            p.put(x + i, y + h - 1, (0, 0, 0, 0))
    if face == "north":
        for j in range(h - 1):  # center split
            p.put(x + w // 2, y + j, MAT["leather"][2])


def belt_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "belt")


def buckle_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "iron")
    if face == "north" and w > 2 and h > 2:
        for i in range(1, w - 1):
            p.put(x + i, y + h // 2, MAT["iron"][2])


def pauldron_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "rust")
    if face not in ("up", "down"):
        for i in range(w):
            p.put(x + i, y + h - 1, MAT["iron"][0])
            p.put(x + i, y + h - 2, MAT["iron"][2])


def arm_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "skin")
    if face == "down":
        return
    for j in range(min(4, h)):  # leather sleeve under the pauldron
        for i in range(w):
            p.put(x + i, y + j, MAT["leather"][0] if (i + j) % 3 else MAT["leather"][2])
    if face == "north" and h > 6:  # knuckles
        p.put(x + 1, y + h - 1, MAT["skin"][2])
        p.put(x + w - 2, y + h - 1, MAT["skin"][2])


def bracer_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "rust")
    if face not in ("up", "down"):
        for i in range(w):
            p.put(x + i, y, MAT["iron"][0])
            p.put(x + i, y + h - 1, MAT["iron"][2])


def body_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "leather")


def leg_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "pants")


def boot_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "boot")
    if face not in ("up", "down"):
        for i in range(w):
            p.put(x + i, y + h - 1, (54, 30, 16))  # sole


def cuff_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "boot")
    if face not in ("up", "down"):
        for i in range(1, w - 1, 2):
            p.put(x + i, y + h // 2, STUD)
        for i in range(w):
            p.put(x + i, y, MAT["boot"][1])


# ---- warrior gear
def steel_helm_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "steel")
    if face in ("north", "south", "east", "west"):
        for i in range(w):
            p.put(x + i, y + h - 1, MAT["steel"][2])
        for i in range(1, w - 1, 2):
            p.put(x + i, y + h - 2, MAT["gold"][1])
    if face in ("north", "south", "up"):
        for j in range(h):
            p.put(x + w // 2, y + j, MAT["steel"][1])


def steel_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "steel")


def studded_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "steel")
    if face not in ("up", "down"):
        for i in range(w):
            p.put(x + i, y + h - 1, MAT["steel"][2])
        for i in range(1, w - 1, 2):
            p.put(x + i, y + h // 2, STUD)


def horn_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "horn")
    for j in range(0, h, 2):
        for i in range(w):
            p.put(x + i, y + j, MAT["horn"][2])


def plate_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "steel")
    if face in ("north", "south"):
        for j in range(2, h, 2):  # lames
            for i in range(w):
                p.put(x + i, y + j, MAT["steel"][2])
        for j in range(h):  # center ridge
            p.put(x + w // 2, y + j, MAT["steel"][1])
        if face == "north":  # gold emblem
            cx, cy = x + w // 2, y + h // 2 - 1
            for dx, dy in ((0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)):
                p.put(cx + dx, cy + dy, MAT["gold"][1 if (dx, dy) == (0, 0) else 0])


def mail_face(p, x, y, w, h, face):
    base, light, dark = MAT["mail"]
    for j in range(h):
        for i in range(w):
            p.put(x + i, y + j, light if (i + j) % 2 == 0 else (dark if j % 2 else base))
    if face not in ("up", "down"):
        for i in range(w):
            p.put(x + i, y + h - 1, dark)


def cape_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "cloth")
    if face in ("north", "south"):
        for j in range(h):
            for i in range(0, w, 3):
                p.put(x + i, y + j, MAT["cloth"][2])
        for i in range(w):
            p.put(x + i, y, MAT["gold"][0])


def dbelt_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "dleather")


def gold_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "gold")


def spaul_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "steel")
    if face not in ("up", "down"):
        for j in range(1, h, 2):
            for i in range(w):
                p.put(x + i, y + j, MAT["steel"][2])
        for i in range(w):
            p.put(x + i, y + h - 1, MAT["gold"][0])


# ---------------------------------------------------------------- model building
class Model:
    def __init__(self, name, seed, tex=TEX, fmt="modded_entity"):
        self.name = name
        self.tex = tex
        self.fmt = fmt  # Blockbench format: modded_entity (Java) or animated_entity_model (GeckoLib plugin)
        self.painter = Painter(seed, tex)
        self.elements = []
        self.groups = {}  # name -> group dict
        self.roots = []
        self.cubes = []  # pending (group, cube spec)
        self.java_cubes = {}  # group -> [(from, to, w, h, d, u, v, inflate)]

    def group(self, name, origin, parent=None):
        g = {"name": name, "origin": list(origin), "color": 0, "uuid": str(uuid.uuid4()),
             "export": True, "isOpen": True, "locked": False, "visibility": True,
             "autouv": 0, "children": []}
        self.groups[name] = g
        if parent:
            self.groups[parent]["children"].append(g)
        else:
            self.roots.append(g)
        return name

    def cube(self, group, name, frm, to, paint, inflate=0.0):
        self.cubes.append((group, name, frm, to, paint, inflate))

    def build(self):
        # shelf-pack box UVs, biggest first
        sized = []
        for c in self.cubes:
            frm, to = c[2], c[3]
            w, h, d = (int(round(to[i] - frm[i])) for i in range(3))
            sized.append((c, w, h, d, 2 * d + 2 * w, d + h))
        order = sorted(range(len(sized)), key=lambda i: (-sized[i][5], -sized[i][4]))
        uv = {}
        x = y = row_h = 0
        for i in order:
            _, w, h, d, uw, vh = sized[i]
            if x + uw > self.tex:
                x, y, row_h = 0, y + row_h, 0
            uv[i] = (x, y)
            x += uw
            row_h = max(row_h, vh)
            assert y + vh <= self.tex, f"texture atlas overflow in {self.name}"
        for i, (c, w, h, d, _, _) in enumerate(sized):
            group, name, frm, to, paint, inflate = c
            u, v = uv[i]
            faces_uv = {
                "north": [u + d, v + d, u + d + w, v + d + h],
                "east": [u, v + d, u + d, v + d + h],
                "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
                "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
                "up": [u + d + w, v + d, u + d, v],
                "down": [u + d + 2 * w, v, u + d + w, v + d],
            }
            for f, r in faces_uv.items():
                x0, x1 = sorted((r[0], r[2]))
                y0, y1 = sorted((r[1], r[3]))
                if x1 > x0 and y1 > y0:
                    paint(self.painter, x0, y0, x1 - x0, y1 - y0, f)
            g = self.groups[group]
            el = {"name": name, "box_uv": True, "rescale": False, "locked": False,
                  "render_order": "default", "allow_mirror_modeling": True,
                  "from": list(frm), "to": list(to), "autouv": 0, "color": len(self.elements) % 8,
                  "inflate": inflate, "origin": list(g["origin"]), "uv_offset": [u, v],
                  "faces": {f: {"uv": r, "texture": 0} for f, r in faces_uv.items()},
                  "type": "cube", "uuid": str(uuid.uuid4())}
            self.elements.append(el)
            g["children"].append(el["uuid"])
            self.java_cubes.setdefault(group, []).append((frm, to, w, h, d, u, v, inflate))

    def save(self, animations=(), name=None, exclude=None):
        """Writes <name>.bbmodel (+ the shared <self.name>.png); groups whose name ends with
        '_<exclude>' are left out, so several variants can share one texture atlas."""
        if not self.elements:
            self.build()
        name = name or self.name
        png = os.path.join(OUT, self.name + ".png")
        self.painter.img.save(png)
        buf = io.BytesIO()
        self.painter.img.save(buf, "PNG")
        dropped = set()

        def keep(g):
            if exclude and (exclude(g["name"]) if callable(exclude) else g["name"].endswith("_" + exclude)):
                dropped.update(c for c in g["children"] if isinstance(c, str))
                return None
            g = dict(g)
            g["children"] = [c if isinstance(c, str) else keep(c) for c in g["children"]]
            g["children"] = [c for c in g["children"] if c is not None]
            return g

        outliner = [keep(g) for g in self.roots]
        data = {
            "meta": {"format_version": "4.10", "model_format": self.fmt, "box_uv": True},
            "name": name, "model_identifier": "", "modded_entity_entity_class": "",
            "modded_entity_version": "Fabric 1.17+", "modded_entity_flip_y": True,
            "visible_box": [1, 2, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
            "timeline_setups": [], "unhandled_root_fields": {},
            "resolution": {"width": self.tex, "height": self.tex},
            "elements": [e for e in self.elements if e["uuid"] not in dropped],
            "outliner": outliner,
            "textures": [{
                "path": "", "name": self.name + ".png", "folder": "entity", "namespace": "dwarfmod",
                "id": "0", "group": "", "width": self.tex, "height": self.tex, "uv_width": self.tex, "uv_height": self.tex,
                "particle": False, "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
                "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
                "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True,
                "saved": True, "uuid": str(uuid.uuid4()), "relative_path": self.name + ".png",
                "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode(),
            }],
            "animations": [self._anim(*a) for a in animations],
        }
        path = os.path.join(OUT, name + ".bbmodel")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=1)
        print("wrote", path, "and", png)

    def _anim(self, name, loop, length, tracks):
        """tracks: {bone: {channel: [(time, (x, y, z)), ...]}}"""
        animators = {}
        for bone, chans in tracks.items():
            kfs = []
            for channel, keys in chans.items():
                for t, (vx, vy, vz) in keys:
                    kfs.append({"channel": channel,
                                "data_points": [{"x": str(vx), "y": str(vy), "z": str(vz)}],
                                "uuid": str(uuid.uuid4()), "time": t, "color": -1,
                                "interpolation": "catmullrom" if loop == "loop" else "linear",
                                "bezier_linked": True, "bezier_left_time": [-0.1, -0.1, -0.1],
                                "bezier_left_value": [0, 0, 0], "bezier_right_time": [0.1, 0.1, 0.1],
                                "bezier_right_value": [0, 0, 0]})
            animators[self.groups[bone]["uuid"]] = {"name": bone, "type": "bone", "keyframes": kfs}
        return {"uuid": str(uuid.uuid4()), "name": name, "loop": loop, "override": False,
                "length": length, "snapping": 24, "selected": False, "anim_time_update": "",
                "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators}



# ---------------------------------------------------------------- worker armor (copper, from the reference image)
def add_helmet(m, g):
    m.cube(g, "helmet", [-4, 25, -4], [4, 28, 4], helmet_face, 0.6)
    m.cube(g, "helmet_crest", [-1, 28, -4], [1, 29, 4], crest_face, 0.5)
    m.cube(g, "nose_guard", [-0.5, 22, -5], [0.5, 25, -4], crest_face, 0.1)
    m.cube(g, "cheek_guard_r", [-5, 21, -3], [-4, 25, 1], helmet_face, 0.25)
    m.cube(g, "cheek_guard_l", [4, 21, -3], [5, 25, 1], helmet_face, 0.25)


def add_chest(m, g_body, g_rarm, g_larm):
    m.cube(g_body, "chestplate", [-5, 14, -3], [5, 20, 3], chest_face, 0.55)
    m.cube(g_body, "tunic_skirt", [-5, 8, -3], [5, 13, 3], skirt_face, 0.6)
    m.cube(g_body, "belt", [-5, 12, -3], [5, 14, 3], belt_face, 0.8)
    m.cube(g_body, "buckle", [-1.5, 12, -4], [1.5, 14, -3], buckle_face, 0.4)
    m.cube(g_rarm, "pauldron_r", [-10, 17, -3], [-5, 20, 3], pauldron_face, 0.35)
    m.cube(g_rarm, "bracer_r", [-9, 11, -2], [-5, 15, 2], bracer_face, 0.35)
    m.cube(g_larm, "pauldron_l", [5, 17, -3], [10, 20, 3], pauldron_face, 0.35)
    m.cube(g_larm, "bracer_l", [5, 11, -2], [9, 15, 2], bracer_face, 0.35)


def add_boots(m, g_r, g_l):
    for g, x0, side in ((g_r, -5, "r"), (g_l, 0, "l")):
        m.cube(g, "boot_" + side, [x0, 0, -2.5], [x0 + 5, 3, 2.5], boot_face, 0.4)
        m.cube(g, "boot_toe_" + side, [x0, 0, -4], [x0 + 5, 2, -2], boot_face, 0.4)
        m.cube(g, "boot_cuff_" + side, [x0, 3, -2.5], [x0 + 5, 5, 2.5], cuff_face, 0.6)


# ---------------------------------------------------------------- warrior armor (steel, horned helmet, red cape)
def add_steel_helmet(m, head):
    m.cube(head, "w_helmet", [-4, 25, -4], [4, 28, 4], steel_helm_face, 0.65)
    m.cube(head, "w_crest", [-0.5, 28, -4], [0.5, 29, 4], steel_face, 0.4)
    m.cube(head, "w_nose_guard", [-0.5, 22, -5], [0.5, 25, -4], steel_face, 0.15)
    for s in (-1, 1):  # -1 right side, +1 left side
        side = "r" if s < 0 else "l"
        x0, x1 = (-5, -4) if s < 0 else (4, 5)
        m.cube(head, "w_cheek_" + side, [x0, 20, -3.5], [x1, 25, 1.5], studded_face, 0.3)
        hx0, hx1 = (-6.5, -4.5) if s < 0 else (4.5, 6.5)
        tx0, tx1 = (-7.5, -6.5) if s < 0 else (6.5, 7.5)
        m.cube(head, "w_horn_base_" + side, [hx0, 25.5, -1], [hx1, 27.5, 1], horn_face, 0.1)
        m.cube(head, "w_horn_mid_" + side, [tx0, 26.5, -0.5], [tx1, 28.5, 0.5], horn_face, 0.15)
        m.cube(head, "w_horn_tip_" + side, [tx0, 28.5, -0.5], [tx1, 30.5, 0.5], horn_face, -0.2)


def add_warrior(m, head, body, rarm, larm, rleg, lleg):
    add_steel_helmet(m, head)
    m.cube(body, "w_breastplate", [-5, 14, -3], [5, 20, 3], plate_face, 0.6)
    m.cube(body, "w_mail_skirt", [-5, 7, -3], [5, 13, 3], mail_face, 0.6)
    m.cube(body, "w_belt", [-5, 12, -3], [5, 14, 3], dbelt_face, 0.85)
    m.cube(body, "w_buckle", [-1.5, 12, -4], [1.5, 14, -3], gold_face, 0.45)
    m.cube(body, "w_cape", [-4.5, 7, 3.7], [4.5, 19, 4.7], cape_face)
    for g, s in ((rarm, -1), (larm, 1)):
        side = "r" if s < 0 else "l"
        bx = (-10.5, -4.5) if s < 0 else (4.5, 10.5)
        lx = (-10, -5) if s < 0 else (5, 10)
        gx = (-9, -5) if s < 0 else (5, 9)
        m.cube(g, "w_pauldron_" + side, [bx[0], 16, -3.5], [bx[1], 20, 3.5], spaul_face, 0.25)
        m.cube(g, "w_lame_" + side, [lx[0], 14.5, -3], [lx[1], 16.5, 3], spaul_face, 0.3)
        m.cube(g, "w_gauntlet_" + side, [gx[0], 10, -2], [gx[1], 14, 2], studded_face, 0.45)
    for g, x0, side in ((rleg, -5, "r"), (lleg, 0, "l")):
        m.cube(g, "w_sabaton_" + side, [x0, 0, -2.5], [x0 + 5, 4, 2.5], studded_face, 0.45)
        m.cube(g, "w_toe_" + side, [x0, 0, -4], [x0 + 5, 2, -2], steel_face, 0.45)
        m.cube(g, "w_knee_" + side, [x0, 6, -3.5], [x0 + 5, 8, -2.5], studded_face, 0.3)



def jf(v):
    return f"{float(v):g}F"


def camel(name):
    a, *rest = name.split("_")
    return a + "".join(r.title() for r in rest)


def java_cubes(group, origin, m):
    ox, oy, oz = origin
    lines = []
    for frm, to, w, h, d, u, v, inf in m.java_cubes.get(group, []):
        box = f"{jf(frm[0] - ox)}, {jf(oy - to[1])}, {jf(frm[2] - oz)}, {jf(w)}, {jf(h)}, {jf(d)}"
        deform = f", new CubeDeformation({jf(inf)})" if inf else ""
        lines.append(f"\n\t\t\t.texOffs({u}, {v}).addBox({box}{deform})")
    return "CubeListBuilder.create()" + "".join(lines)




# ---------------------------------------------------------------- warrior level 3 (imagenes/WhatsApp Image 2026-10-13 at 23.01.18)
MAT.update({
    "dsteel":  [(104, 110, 120), (150, 156, 166), (66, 70, 78)],
    "bsteel":  [(170, 176, 186), (214, 220, 228), (120, 126, 136)],
    "dcloth":  [(58, 44, 36), (76, 60, 48), (40, 30, 24)],
    "redhair": [(168, 62, 26), (204, 92, 44), (118, 40, 16)],
    "glove":   [(112, 68, 38), (142, 90, 52), (80, 46, 24)],
    "wood":    [(96, 62, 34), (124, 84, 48), (68, 42, 22)],
})


def rivet_rim(p, x, y, w, h, rows=(-1,)):
    for r in rows:
        for i in range(w):
            p.put(x + i, y + (h + r if r < 0 else r), MAT["dsteel"][2])
        for i in range(1, w - 1, 2):
            p.put(x + i, y + (h + r if r < 0 else r), STUD)


def spangen_face(p, x, y, w, h, face):
    """Riveted spangenhelm: dark plates, light center band with rivets, studded rim."""
    p.fill(x, y, w, h, "dsteel")
    if face in ("north", "south", "up"):
        for j in range(h):
            p.put(x + w // 2 - 1, y + j, MAT["bsteel"][0])
            p.put(x + w // 2, y + j, MAT["bsteel"][1])
            if j % 2 == 0:
                p.put(x + w // 2, y + j, STUD)
    if face not in ("up", "down"):
        rivet_rim(p, x, y, w, h)


def bsteel_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "bsteel")
    if face not in ("up", "down") and h > 2:
        for i in range(w):
            p.put(x + i, y + h - 1, MAT["bsteel"][2])


def riveted_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "dsteel")
    if face not in ("up", "down"):
        for j in range(0, h, 2):
            p.put(x + w // 2, y + j, STUD)


def elite_plate_face(p, x, y, w, h, face):
    if face == "south":  # dark jerkin with leather straps on the back
        p.fill(x, y, w, h, "dcloth")
        for sx in (2, w - 3):
            for j in range(h):
                p.put(x + sx, y + j, MAT["glove"][0])
        return
    p.fill(x, y, w, h, "bsteel")
    for i in range(w):
        p.put(x + i, y, MAT["bsteel"][2])
        p.put(x + i, y + h - 1, MAT["dsteel"][2])
    if face == "north":  # light cross emblem
        cx, cy = x + w // 2, y + h // 2 - 1
        for d in range(-2, 3):
            p.put(cx, cy + d, (236, 238, 242))
        for d in (-1, 1):
            p.put(cx + d, cy, (236, 238, 242))


def lames_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "bsteel")
    if face not in ("up", "down"):
        for j in range(1, h, 2):
            for i in range(w):
                p.put(x + i, y + j, MAT["dsteel"][0])
        p.put(x + 1, y, STUD)
        p.put(x + w - 2, y, STUD)


def dcloth_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "dcloth")


def glove_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "glove")


def pouch_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "glove")
    if face == "south":
        for i in range(w):
            p.put(x + i, y + 1, MAT["glove"][2])
        for sx in (1, w - 2):
            p.put(x + sx, y + 2, MAT["bsteel"][1])


def redbeard_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "redhair")
    if face == "north":
        for j in range(h):
            for i in range(w):
                if (i + j) % 3 == 0:
                    p.put(x + i, y + j, MAT["redhair"][1])


def braid_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "redhair")
    for j in range(h):
        for i in range(w):
            if (i + j) % 2 == 0:
                p.put(x + i, y + j, MAT["redhair"][2])


def wood_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "wood")
    if face not in ("up", "down") and w > h:  # leather wraps along the haft
        for i in range(0, w, 5):
            p.put(x + i, y, MAT["glove"][2])
            if h > 1:
                p.put(x + i, y + h - 1, MAT["glove"][2])


def edge_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "bsteel")
    for i in range(w):
        p.put(x + i, y, (236, 240, 246))


def add_elite_helmet(m, head):
    m.cube(head, "e_helmet", [-4, 24, -4], [4, 28, 4], spangen_face, 0.7)
    m.cube(head, "e_ridge", [-0.5, 28, -4], [0.5, 29, 4], riveted_face, 0.45)
    m.cube(head, "e_nasal", [-1, 21, -5], [1, 25, -4], riveted_face, 0.2)
    m.cube(head, "e_neck_guard", [-4, 21, 4], [4, 24, 5], spangen_face, 0.5)
    for x0 in (-5, 4):
        side = "r" if x0 < 0 else "l"
        m.cube(head, "e_cheek_" + side, [x0, 19, -3.5], [x0 + 1, 24, 1.5], riveted_face, 0.35)


def add_elite(m, head, body, rarm, larm, rleg, lleg, beard=True):
    add_elite_helmet(m, head)
    if beard:  # red braided beard with gold rings
        m.cube(head, "e_beard", [-4, 15, -5], [4, 22, -4], redbeard_face, 0.1)
        m.cube(head, "e_beard_tip", [-2, 13, -5], [2, 15, -4], redbeard_face)
        for bx in (-3.5, 1.5):
            m.cube(head, "e_braid", [bx, 9, -5.5], [bx + 2, 15, -4.5], braid_face)
            m.cube(head, "e_braid_ring", [bx, 11, -5.5], [bx + 2, 12, -4.5], gold_face, 0.25)
            m.cube(head, "e_braid_end", [bx, 8, -5.5], [bx + 2, 9, -4.5], braid_face, -0.15)
    m.cube(body, "e_breastplate", [-5, 14, -3], [5, 20, 3], elite_plate_face, 0.6)
    m.cube(body, "e_faulds", [-5, 11, -3], [5, 13, 3], lames_face, 0.75)
    m.cube(body, "e_belt", [-5, 12, -3], [5, 14, 3], dbelt_face, 0.9)
    m.cube(body, "e_buckle", [-1.5, 12, -4], [1.5, 14, -3], bsteel_face, 0.55)
    m.cube(body, "e_tunic", [-5, 7, -3], [5, 12, 3], dcloth_face, 0.55)
    m.cube(body, "e_tasset", [-4, 8, -4], [4, 11, -3], lames_face, 0.2)
    m.cube(body, "e_pouch", [-3, 9, 3.6], [3, 12, 5.6], pouch_face)
    for g, s in ((rarm, -1), (larm, 1)):
        side = "r" if s < 0 else "l"
        def X(a, b):  # mirror x-range for the left side
            return (a, b) if s < 0 else (-b, -a)
        p0, p1 = X(-11, -5)
        m.cube(g, "e_pauldron_" + side, [p0, 16.5, -3.5], [p1, 20.5, 3.5], lames_face, 0.3)
        l0, l1 = X(-10.5, -4.5)
        m.cube(g, "e_lame1_" + side, [l0, 14.5, -3], [l1, 16.5, 3], lames_face, 0.3)
        q0, q1 = X(-10, -5)
        m.cube(g, "e_lame2_" + side, [q0, 13, -2.5], [q1, 15, 2.5], lames_face, 0.3)
        a0, a1 = X(-9, -5)
        m.cube(g, "e_sleeve_" + side, [a0, 15, -2], [a1, 18, 2], dcloth_face, 0.15)
        m.cube(g, "e_vambrace_" + side, [a0, 12, -2], [a1, 15, 2], bsteel_face, 0.45)
        m.cube(g, "e_glove_" + side, [a0, 10, -2], [a1, 12, 2], glove_face, 0.3)
    for g, x0, side in ((rleg, -5, "r"), (lleg, 0, "l")):
        m.cube(g, "e_trousers_" + side, [x0, 5, -2.5], [x0 + 5, 10, 2.5], dcloth_face, 0.12)
        m.cube(g, "e_boot_" + side, [x0, 0, -2.5], [x0 + 5, 5, 2.5], bsteel_face, 0.45)
        m.cube(g, "e_boot_toe_" + side, [x0, 0, -4], [x0 + 5, 2, -2], bsteel_face, 0.45)
        m.cube(g, "e_boot_cuff_" + side, [x0, 4.5, -2.5], [x0 + 5, 5.5, 2.5], riveted_face, 0.6)
        m.cube(g, "e_knee_" + side, [x0, 6, -3.5], [x0 + 5, 8, -2.5], lames_face, 0.3)


def add_greataxe(m, g):
    """Two-handed axe laid along +x from the right fist (pivot of `g`), blade on the +y side."""
    m.cube(g, "axe_haft", [-12, 10.5, -0.5], [13, 11.5, 0.5], wood_face, 0.05)
    m.cube(g, "axe_pommel", [-13, 10, -1], [-11, 12, 1], gold_face)
    m.cube(g, "axe_socket", [8, 9.5, -1], [12, 12.5, 1], riveted_face)
    m.cube(g, "axe_blade_neck", [8.5, 12.5, -0.5], [11.5, 15.5, 0.5], bsteel_face)
    m.cube(g, "axe_blade", [7, 15.5, -0.5], [13, 19.5, 0.5], bsteel_face)
    m.cube(g, "axe_edge", [6, 19.5, -0.5], [14, 20.5, 0.5], edge_face)
    m.cube(g, "axe_spike", [9, 7.5, -0.5], [11, 9.5, 0.5], riveted_face)
    m.cube(g, "axe_tip", [12, 10.5, -0.5], [15, 11.5, 0.5], bsteel_face, -0.05)


# ---------------------------------------------------------------- warrior level 2 (guard: round helm with a cross band,
# red beard, light steel plate over chainmail, studded leather bracers, brown boots with steel toe caps)
def gdome_face(p, x, y, w, h, face):
    """Helm dome: mid steel, dark lower rim with rivets."""
    p.fill(x, y, w, h, "steel")
    if face not in ("up", "down"):
        for i in range(w):
            p.put(x + i, y + h - 1, MAT["dsteel"][2])
        for i in range(1, w - 1, 3):
            p.put(x + i, y + h - 2, STUD)


def gband_face(p, x, y, w, h, face):
    """Bright band of the cross over the helm (and the nasal guard)."""
    p.fill(x, y, w, h, "bsteel")
    if face not in ("up", "down") and h > 2:
        for i in range(w):
            p.put(x + i, y + h - 1, MAT["bsteel"][2])
    for j in range(1, h, 3):
        p.put(x + w // 2, y + j, STUD)


def gplate_face(p, x, y, w, h, face):
    """Light steel breastplate: bright top edge, dark bottom edge, center seam and rivets."""
    p.fill(x, y, w, h, "bsteel")
    if face in ("up", "down"):
        return
    for i in range(w):
        p.put(x + i, y, (236, 240, 246))
        p.put(x + i, y + h - 1, MAT["dsteel"][2])
    for j in range(h):
        p.put(x, y + j, MAT["bsteel"][2])
        p.put(x + w - 1, y + j, MAT["bsteel"][2])
    if face in ("north", "south"):
        for j in range(1, h - 1):
            p.put(x + w // 2, y + j, MAT["bsteel"][2] if j % 2 else MAT["bsteel"][1])
        p.put(x + 1, y + 1, STUD)
        p.put(x + w - 2, y + 1, STUD)


def gpauldron_face(p, x, y, w, h, face):
    """Shoulder plate: light steel with a leather-brown edge and a rivet."""
    p.fill(x, y, w, h, "bsteel")
    if face in ("up", "down"):
        return
    for i in range(w):
        p.put(x + i, y, (236, 240, 246))
        p.put(x + i, y + h - 1, MAT["glove"][0])
    if h > 3:
        for i in range(w):
            p.put(x + i, y + h - 2, MAT["glove"][2])
    p.put(x + w // 2, y + h // 2 - 1, STUD)


def gbracer_face(p, x, y, w, h, face):
    """Dark leather bracer studded with steel."""
    p.fill(x, y, w, h, "dleather")
    if face in ("up", "down"):
        return
    for i in range(w):
        p.put(x + i, y, MAT["glove"][0])
        p.put(x + i, y + h - 1, MAT["glove"][0])
    for j in range(1, h - 1, 2):
        for i in range(1, w - 1, 2):
            p.put(x + i, y + j, STUD)


def gbootcap_face(p, x, y, w, h, face):
    p.fill(x, y, w, h, "bsteel")
    if face not in ("up", "down"):
        for i in range(w):
            p.put(x + i, y + h - 1, MAT["bsteel"][2])


def add_guard_helmet(m, head):
    """Round steel helm: dome + brow band + crest band (the cross), nasal guard, cheeks, neck guard."""
    m.cube(head, "g_helmet", [-4, 26, -4], [4, 28, 4], gdome_face, 0.7)
    m.cube(head, "g_brow", [-4, 24.5, -4], [4, 26, 4], gband_face, 0.8)
    m.cube(head, "g_crest_fb", [-0.8, 28, -4], [0.8, 29, 4], gband_face, 0.5)
    m.cube(head, "g_crest_lr", [-4, 28, -0.8], [4, 29, 0.8], gband_face, 0.45)
    m.cube(head, "g_nasal", [-0.8, 21, -5], [0.8, 25, -4], gband_face, 0.2)
    m.cube(head, "g_neck_guard", [-4, 21, 4], [4, 25, 5], gdome_face, 0.5)
    for x0 in (-5, 4):
        side = "r" if x0 < 0 else "l"
        m.cube(head, "g_cheek_" + side, [x0, 20.5, -3.5], [x0 + 1, 25, 1.5], gdome_face, 0.3)


def add_guard_beard(m, head):
    """Long red beard with a leather-wrapped tip (dwarf only; the player armor has none)."""
    m.cube(head, "g_beard", [-4, 15, -5], [4, 22, -4], redbeard_face, 0.1)
    m.cube(head, "g_beard_tip", [-2.5, 12, -5], [2.5, 15, -4], redbeard_face)
    m.cube(head, "g_beard_end", [-1, 8.5, -5], [1, 12, -4.5], braid_face)
    m.cube(head, "g_beard_wrap", [-1.2, 11, -5], [1.2, 12.2, -4.3], glove_face, 0.2)


def add_guard(m, head, body, rarm, larm, rleg, lleg):
    add_guard_helmet(m, head)
    add_guard_beard(m, head)
    m.cube(body, "g_mail", [-5, 13.5, -3], [5, 20, 3], mail_face, 0.65)
    m.cube(body, "g_breastplate", [-4, 14.5, -3], [4, 19.5, 3], gplate_face, 0.55)
    m.cube(body, "g_mail_skirt", [-5, 7, -3], [5, 13, 3], mail_face, 0.6)
    m.cube(body, "g_belt", [-5, 12, -3], [5, 14, 3], glove_face, 0.85)
    m.cube(body, "g_buckle", [-1.5, 12, -4], [1.5, 14, -3], bsteel_face, 0.5)
    for sx in (-3.5, 2.5):  # leather straps over the back plate
        m.cube(body, "g_strap", [sx, 14, 2.9], [sx + 1, 20.2, 3], glove_face, 0.72)
    m.cube(body, "g_pouch", [2, 8.5, 3.6], [5, 12, 5.6], pouch_face)
    for g, s in ((rarm, -1), (larm, 1)):
        side = "r" if s < 0 else "l"
        bx = (-10.5, -4.5) if s < 0 else (4.5, 10.5)
        lx = (-10, -5) if s < 0 else (5, 10)
        gx = (-9, -5) if s < 0 else (5, 9)
        m.cube(g, "g_pauldron_" + side, [bx[0], 16.5, -3.5], [bx[1], 20.5, 3.5], gpauldron_face, 0.3)
        m.cube(g, "g_lame_" + side, [lx[0], 15, -3], [lx[1], 16.8, 3], gpauldron_face, 0.25)
        m.cube(g, "g_bracer_" + side, [gx[0], 10.8, -2], [gx[1], 14.8, 2], gbracer_face, 0.45)
    for g, x0, side in ((rleg, -5, "r"), (lleg, 0, "l")):
        m.cube(g, "g_trousers_" + side, [x0, 5, -2.5], [x0 + 5, 10, 2.5], dcloth_face, 0.12)
        m.cube(g, "g_boot_" + side, [x0, 0, -2.5], [x0 + 5, 5, 2.5], boot_face, 0.45)
        m.cube(g, "g_boot_cuff_" + side, [x0, 4, -2.5], [x0 + 5, 5.5, 2.5], cuff_face, 0.6)
        m.cube(g, "g_toecap_" + side, [x0, 0, -4], [x0 + 5, 2.2, -2], gbootcap_face, 0.5)
