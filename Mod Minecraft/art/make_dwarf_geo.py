"""The dwarf entity for GeckoLib: geometry, texture and animations, all from this script.

Outputs
 - art/blockbench/dwarf.bbmodel          every variant in one file (GeckoLib "Animated Entity Model" format),
                                          exactly what the mod loads; toggle the *_copper / *_steel / *_elite
                                          groups to look at one variant
 - art/blockbench/dwarf_<variant>.bbmodel worker, warrior_1, warrior_2, warrior_3: one look each, for viewing
 - assets/dwarfmod/geckolib/models/entity/dwarf.geo.json
 - assets/dwarfmod/geckolib/animations/entity/dwarf.animation.json
 - assets/dwarfmod/textures/entity/dwarf.png
 - art/blockbench/preview_*.png          posed frames of the warrior animations (sanity check)

Conventions: Blockbench space (y up, the dwarf faces -z, right arm at -x). Animation values are stored the
way Blockbench shows them; Blockbench (and GeckoLib, which copies it) applies rotation as
Rz(z) * Ry(-y) * Rx(-x) and position as (-x, y, z). The two-handed axe poses are solved with that same math
(fk below), so both fists stay on the haft in Blockbench and in game.
"""
import json, math
import numpy as np
from PIL import Image, ImageDraw
from bbtools import *

GEO_TEX = 256
GEARS = ["copper", "steel", "elite"]
BONES = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"]

# ---------------------------------------------------------------- model
m = Model("dwarf", 11, tex=GEO_TEX, fmt="animated_entity_model")
m.group("root", [0, 0, 0])
m.group("body", [0, 11, 0], "root")
m.group("head", [0, 20, 0], "body")
m.group("beard", [0, 20, 0], "head")
m.group("right_arm", [-7, 18, 0], "body")
m.group("left_arm", [7, 18, 0], "body")
m.group("right_item", [-7, 11, 0], "right_arm")  # anchors for held items (ItemInHandGeoLayer)
m.group("left_item", [7, 11, 0], "left_arm")
# Held items point forward out of the fist (Blockbench stores the group rotation as shown; .geo.json flips x/y).
ITEM_ROT = [-90, 0, 0]
for g in ("right_item", "left_item"):
    m.groups[g]["rotation"] = list(ITEM_ROT)
m.group("greataxe", [-7, 11, 0], "body")  # warriors only; posed by the animations, see warrior_pose()
m.group("right_leg", [-2.5, 10, 0], "root")
m.group("left_leg", [2.5, 10, 0], "root")
for bone in BONES:
    for gear in GEARS:
        m.group(f"{bone}_{gear}", m.groups[bone]["origin"], bone)

m.cube("head", "head", [-4, 20, -4], [4, 28, 4], head_face)
m.cube("beard", "beard", [-4, 16, -5], [4, 22, -4], beard_face, 0.05)
m.cube("beard", "beard_tip", [-3, 14, -5], [3, 16, -4], beard_face)
m.cube("body", "torso", [-5, 10, -3], [5, 20, 3], body_face)
m.cube("right_arm", "arm_r", [-9, 10, -2], [-5, 20, 2], arm_face)
m.cube("left_arm", "arm_l", [5, 10, -2], [9, 20, 2], arm_face)
m.cube("right_leg", "leg_r", [-5, 0, -2.5], [0, 10, 2.5], leg_face)
m.cube("left_leg", "leg_l", [0, 0, -2.5], [5, 10, 2.5], leg_face)
add_helmet(m, "head_copper")
add_chest(m, "body_copper", "right_arm_copper", "left_arm_copper")
add_boots(m, "right_leg_copper", "left_leg_copper")
add_guard(m, *(f"{b}_steel" for b in BONES))  # warrior tier 2: guard look (steel plate over mail, red beard)
add_elite(m, *(f"{b}_elite" for b in BONES))
add_greataxe(m, "greataxe")
m.build()

PARENT = {}
for g in m.groups.values():
    for c in g["children"]:
        if isinstance(c, dict):
            PARENT[c["name"]] = g["name"]
PIVOT = {n: np.array(g["origin"], float) for n, g in m.groups.items()}


# ---------------------------------------------------------------- Blockbench transform math
def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def anim_rot(v):
    x, y, z = (math.radians(a) for a in v)
    return rz(z) @ ry(-y) @ rx(-x)


SHOULDER = {"right_arm": PIVOT["right_arm"], "left_arm": PIVOT["left_arm"]}
FIST = 7.0  # shoulder pivot -> fist center
AXE_PIVOT = PIVOT["greataxe"]
GRIP = 5.0  # right fist -> left fist along the haft


def aim_arm(arm, hand):
    """Rotation (x, 0, z) and y-scale that put the fist of `arm` on `hand` (body space)."""
    v = hand - SHOULDER[arm]
    n = np.linalg.norm(v)
    d = v / n
    ax = math.degrees(math.asin(max(-1.0, min(1.0, d[2]))))
    az = math.degrees(math.atan2(d[0], -d[1]))
    return (ax, 0.0, az), n / FIST


def aim_axe(u, edge):
    """Rotation of the greataxe bone so its haft (+x) points along u and the blade (+y) toward `edge`."""
    u = u / np.linalg.norm(u)
    beta = math.asin(max(-1.0, min(1.0, -u[2])))
    gz = math.degrees(math.atan2(u[1], u[0]))
    gy = -math.degrees(beta)
    e = edge - np.dot(edge, u) * u
    e /= np.linalg.norm(e)
    best, best_dot = 0.0, -2.0
    for gx in np.arange(-180, 180, 0.5):
        b = anim_rot((gx, gy, gz)) @ np.array([0, 1.0, 0])
        if np.dot(b, e) > best_dot:
            best, best_dot = float(gx), float(np.dot(b, e))
    return (best, gy, gz)


def warrior_pose(fist, u, edge):
    """Both arms + axe for a two-handed grip: right fist at `fist`, left fist GRIP further along the haft."""
    u = np.asarray(u, float) / np.linalg.norm(u)
    fist = np.asarray(fist, float)
    left = fist + GRIP * u
    (r_rot, r_scale), (l_rot, l_scale) = aim_arm("right_arm", fist), aim_arm("left_arm", left)
    off = fist - AXE_PIVOT
    return {
        "right_arm": {"rotation": r_rot, "scale": (1.0, r_scale, 1.0)},
        "left_arm": {"rotation": l_rot, "scale": (1.0, l_scale, 1.0)},
        "greataxe": {"rotation": aim_axe(u, np.asarray(edge, float)), "position": (-off[0], off[1], off[2])},
    }


# ---------------------------------------------------------------- animation helpers
def smooth(a, b, f):
    f = f * f * (3 - 2 * f)
    return tuple(x + (y - x) * f for x, y in zip(a, b))


def sample_keys(keys, t):
    """keys: [(time, value_tuple)] -> smoothly interpolated value at t."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            return smooth(v0, v1, (t - t0) / (t1 - t0) if t1 > t0 else 1.0)
    return keys[-1][1]


def unwrap(prev, cur):
    return tuple(c + 360 * round((p - c) / 360) for p, c in zip(prev, cur))


def baked(name, length, loop, drive, step=0.05):
    """Samples drive(t) -> {bone: {channel: (x,y,z)}} densely; returns {bone: {channel: [(t, v)]}}."""
    tracks = {}
    n = int(round(length / step))
    for i in range(n + 1):
        t = round(i * step, 4)
        for bone, chans in drive(t).items():
            for ch, v in chans.items():
                lst = tracks.setdefault(bone, {}).setdefault(ch, [])
                v = tuple(round(float(x), 3) for x in v)
                if ch == "rotation" and lst:
                    v = unwrap(lst[-1][1], v)
                lst.append((t, v))
    return {"name": name, "length": length, "loop": loop, "lerp": "linear", "tracks": tracks}


def keyed(name, length, loop, tracks):
    return {"name": name, "length": length, "loop": loop, "lerp": "catmullrom", "tracks": tracks}


Z = (0, 0, 0)

# ---- worker dwarves (blacksmith, brewer, miner): hand-keyed
idle = keyed("idle", 2.0, True, {
    "body": {"position": [(0, Z), (1.0, (0, -0.25, 0)), (2.0, Z)]},
    "head": {"rotation": [(0, Z), (1.0, (-2, 0, 0)), (2.0, Z)]},
    "right_arm": {"rotation": [(0, (0, 0, -2)), (1.0, (0, 0, -4)), (2.0, (0, 0, -2))]},
    "left_arm": {"rotation": [(0, (0, 0, 2)), (1.0, (0, 0, 4)), (2.0, (0, 0, 2))]},
})
walk = keyed("walk", 1.0, True, {
    "right_leg": {"rotation": [(0, (25, 0, 0)), (0.5, (-25, 0, 0)), (1.0, (25, 0, 0))]},
    "left_leg": {"rotation": [(0, (-25, 0, 0)), (0.5, (25, 0, 0)), (1.0, (-25, 0, 0))]},
    "right_arm": {"rotation": [(0, (-22, 0, -3)), (0.5, (22, 0, -3)), (1.0, (-22, 0, -3))]},
    "left_arm": {"rotation": [(0, (22, 0, 3)), (0.5, (-22, 0, 3)), (1.0, (22, 0, 3))]},
    "body": {"position": [(0, Z), (0.25, (0, 0.5, 0)), (0.5, Z), (0.75, (0, 0.5, 0)), (1.0, Z)],
             "rotation": [(0, (0, 4, 0)), (0.5, (0, -4, 0)), (1.0, (0, 4, 0))]},
    "head": {"rotation": [(0, (0, -4, 0)), (0.5, (0, 4, 0)), (1.0, (0, -4, 0))]},
})
attack = keyed("attack", 0.6, False, {
    "right_arm": {"rotation": [(0, Z), (0.18, (-150, -10, 0)), (0.32, (-30, 10, 0)), (0.6, Z)]},
    "left_arm": {"rotation": [(0, Z), (0.18, (-20, 0, 8)), (0.32, (15, 0, 4)), (0.6, Z)]},
    "body": {"rotation": [(0, Z), (0.18, (-6, 12, 0)), (0.32, (10, -12, 0)), (0.6, Z)]},
    "head": {"rotation": [(0, Z), (0.18, (-4, -8, 0)), (0.32, (4, 8, 0)), (0.6, Z)]},
    "right_leg": {"rotation": [(0, Z), (0.32, (-15, 0, 0)), (0.6, Z)]},
    "left_leg": {"rotation": [(0, Z), (0.32, (12, 0, 0)), (0.6, Z)]},
})

# ---- warriors: two-handed greataxe, solved per frame
FWD = (0, 0, -1)
READY = dict(fist=(-4.5, 12.6, -5.2), u=(0.84, 0.52, -0.16), edge=(-0.52, 0.84, -0.1))  # diagonal across the chest, blade facing out
READY["fist"] = None  # solved below


def solve_fist(hint, u, spread=3.0, step=0.25):
    """Right-fist position near `hint` for haft direction u that keeps both arms close to their natural
    length (stretch ~1.1, at most ~1.3) and both fists in front of the torso/beard."""
    u = np.asarray(u, float) / np.linalg.norm(u)
    r = np.arange(-spread, spread + 1e-6, step)
    gx, gy, gz = np.meshgrid(r, r, r, indexing="ij")
    cand = np.stack([gx.ravel(), gy.ravel(), gz.ravel()], 1) + np.asarray(hint, float)
    left = cand + GRIP * u
    rs = np.linalg.norm(cand - SHOULDER["right_arm"], axis=1) / FIST
    ls = np.linalg.norm(left - SHOULDER["left_arm"], axis=1) / FIST
    cost = (np.maximum(rs, ls) - 1.1) ** 2 * 40 + np.maximum(0, np.maximum(rs, ls) - 1.3) * 200
    cost += np.sum((cand - hint) ** 2, 1) * 0.15
    for hand in (cand, left):  # fists (2 px half size) must stay out of the body: torso z>-3.6, beard z>-5.6
        inside = (hand[:, 1] > 9) & (hand[:, 1] < 20.5) & (np.abs(hand[:, 0]) < 7)
        front = np.where(hand[:, 1] > 13, -6.4, -5.2)
        cost += inside * np.maximum(0, hand[:, 2] - front) * 30
    return tuple(cand[int(np.argmin(cost))])


READY["fist"] = solve_fist((-4.5, 12.6, -5.2), READY["u"])
print("ready fist", np.round(READY["fist"], 2))

def ready_pose(t, bob=0.0, sway=0.0):
    f = np.array(READY["fist"]) + np.array([0, bob, 0])
    u = np.array(READY["u"]) + np.array([0, 0, sway])
    return warrior_pose(f, u, READY["edge"])


def idle_axe_drive(t):
    ph = math.sin(2 * math.pi * t / 2.4)
    pose = ready_pose(t, bob=-0.25 * (1 - math.cos(2 * math.pi * t / 2.4)) / 2, sway=0.04 * ph)
    pose["body"] = {"position": (0, -0.25 * (1 - math.cos(2 * math.pi * t / 2.4)) / 2, 0)}
    pose["head"] = {"rotation": (-2 * (1 - math.cos(2 * math.pi * t / 2.4)) / 2, 0, 0)}
    return pose


def walk_axe_drive(t):
    w = 2 * math.pi * t  # 1 s cycle
    pose = ready_pose(t, bob=0.3 * math.sin(2 * w), sway=0.05 * math.sin(w))
    pose["right_leg"] = {"rotation": (25 * math.cos(w), 0, 0)}
    pose["left_leg"] = {"rotation": (-25 * math.cos(w), 0, 0)}
    pose["body"] = {"position": (0, 0.5 * abs(math.sin(w)), 0), "rotation": (0, 4 * math.cos(w), 0)}
    pose["head"] = {"rotation": (0, -4 * math.cos(w), 0)}
    return pose


# keyframes of the overhead-to-front cleave (body space, then the body itself twists and leans)
ATTACK_KEYS = [
    # time, fist, haft dir, blade facing, body rot, body y, head rot, right leg, left leg
    (0.00, None, READY["u"], FWD, Z, 0.0, Z, Z, Z),
    (0.38, (-3.5, 15.2, -5.6), (0.70, 0.62, 0.36), (-0.5, -0.3, -0.8), (-8, 45, 0), 0.0, (-6, -25, 0), (6, 0, 0), (-6, 0, 0)),
    (0.55, (-1.0, 14.5, -6.5), (0.32, -0.74, -0.59), (0, -1, -0.2), (14, -38, 0), -1.0, (6, 22, 0), (-18, 0, 0), (14, 0, 0)),
    (0.70, (-1.0, 14.0, -6.3), (0.22, -0.84, -0.49), (0, -1, 0.1), (16, -45, 0), -1.1, (6, 26, 0), (-18, 0, 0), (14, 0, 0)),
    (0.88, (-1.0, 14.5, -6.0), (0.62, 0.05, -0.78), (0, -0.4, -1), (8, -25, 0), -0.6, (4, 15, 0), (-10, 0, 0), (8, 0, 0)),
    (1.10, None, READY["u"], FWD, Z, 0.0, Z, Z, Z),
]

ATTACK_KEYS = [(k[0], solve_fist(k[1], k[2]) if 0 < i < len(ATTACK_KEYS) - 1 else READY["fist"]) + k[2:]
               for i, k in enumerate(ATTACK_KEYS)]
for k in ATTACK_KEYS:
    print("attack key", k[0], "fist", np.round(k[1], 2))


def attack_axe_drive(t):
    k = [(row[0], tuple(np.concatenate([np.ravel(x) for x in row[1:]]))) for row in ATTACK_KEYS]
    v = sample_keys(k, t)
    fist, u, edge = v[0:3], v[3:6], v[6:9]
    body_rot, body_y, head_rot, rleg, lleg = v[9:12], v[12], v[13:16], v[16:19], v[19:22]
    if 0 < t < ATTACK_KEYS[-1][0]:  # between keys, re-fit the grip so neither arm over-stretches
        fist = solve_fist(fist, u, spread=2.0, step=0.125)
    pose = warrior_pose(fist, u, edge)
    pose["body"] = {"rotation": body_rot, "position": (0, body_y, 0)}
    pose["head"] = {"rotation": head_rot}
    pose["right_leg"] = {"rotation": rleg}
    pose["left_leg"] = {"rotation": lleg}
    return pose


idle_axe = baked("idle_axe", 2.4, True, idle_axe_drive, step=0.1)
walk_axe = baked("walk_axe", 1.0, True, walk_axe_drive)
attack_axe = baked("attack_axe", 1.1, False, attack_axe_drive)
ANIMS = [idle, walk, attack, idle_axe, walk_axe, attack_axe]

# reach check: arm stretch needed for the two-handed grip (1.0 = natural length)
for a in (idle_axe, walk_axe, attack_axe):
    sc = [v[1] for arm in ("right_arm", "left_arm") for _, v in a["tracks"][arm]["scale"]]
    worst = max((v[1], t) for arm in ("right_arm", "left_arm") for t, v in a["tracks"][arm]["scale"])
    print(f"{a['name']:11s} arm stretch {min(sc):.2f} .. {max(sc):.2f}  (worst at t={worst[1]})")


# ---------------------------------------------------------------- posed preview (painter's algorithm)
def value_at(track, t):
    for (t0, v0), (t1, v1) in zip(track, track[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(a + (b - a) * f for a, b in zip(v0, v1))
    return track[-1][1] if t > track[-1][0] else track[0][1]


def bone_matrices(anim, t):
    """World 4x4 per bone in body/root space for anim at time t."""
    mats = {}

    def get(name):
        if name in mats:
            return mats[name]
        tr = anim["tracks"].get(name, {}) if anim else {}
        rot = value_at(tr["rotation"], t) if "rotation" in tr else Z
        pos = value_at(tr["position"], t) if "position" in tr else Z
        scl = value_at(tr["scale"], t) if "scale" in tr else (1, 1, 1)
        p = PIVOT[name]
        M = np.eye(4)
        M[:3, :3] = anim_rot(rot) @ np.diag(scl)
        M[:3, 3] = p + np.array([-pos[0], pos[1], pos[2]]) - M[:3, :3] @ p
        par = PARENT.get(name)
        mats[name] = (get(par) @ M) if par else M
        return mats[name]

    for n in m.groups:
        get(n)
    return mats


def render(anim, t, visible, path, yaw=35, pitch=12, S=9):
    mats = bone_matrices(anim, t)
    view = rx(math.radians(-pitch)) @ ry(math.radians(yaw))
    tex = m.painter.img
    polys = []
    for e, grp in zip(m.elements, m.element_groups):
        if not visible(grp):
            continue
        inf = e["inflate"]
        lo, hi = np.array(e["from"]) - inf, np.array(e["to"]) + inf
        corners = {}
        for i in range(8):
            c = np.array([hi[0] if i & 1 else lo[0], hi[1] if i & 2 else lo[1], hi[2] if i & 4 else lo[2], 1.0])
            corners[i] = view @ (mats[grp] @ c)[:3]
        faces = {"north": (0, 1, 3, 2), "south": (4, 5, 7, 6), "west": (1, 5, 7, 3),
                 "east": (0, 4, 6, 2), "up": (2, 3, 7, 6), "down": (0, 1, 5, 4)}
        for f, idx in faces.items():
            uv = e["faces"][f]["uv"]
            box = (min(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[0], uv[2]), max(uv[1], uv[3]))
            if box[2] <= box[0] or box[3] <= box[1]:
                continue
            col = np.array(tex.crop(box).convert("RGBA")).reshape(-1, 4)
            col = col[col[:, 3] > 0]
            if not len(col):
                continue
            pts = [corners[i] for i in idx]
            n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
            light = 0.55 + 0.45 * abs(n[2] / (np.linalg.norm(n) + 1e-9)) * (1 if f != "down" else 0.7)
            c = tuple(int(v * light) for v in col[:, :3].mean(0)) + (255,)
            polys.append((np.mean([p[2] for p in pts]), [(p[0], p[1]) for p in pts], c))
    W, H = 46 * S, 46 * S
    img = Image.new("RGBA", (W, H), (60, 70, 90, 255))
    d = ImageDraw.Draw(img)
    for _, pts, c in sorted(polys, key=lambda q: -q[0]):  # viewer at -z: far (+z) first
        d.polygon([(W / 2 - x * S, H - 6 * S - y * S) for x, y in pts], fill=c, outline=(0, 0, 0, 90))
    img.save(path)
    return img


m.element_groups = []
for g in m.groups.values():
    for c in g["children"]:
        if isinstance(c, str):
            m.element_groups.append((c, g["name"]))
order = {uid: grp for uid, grp in m.element_groups}
m.element_groups = [order[e["uuid"]] for e in m.elements]


def show(variant):
    tier = {"worker": 0, "warrior_1": 1, "warrior_2": 2, "warrior_3": 3}[variant]
    gear = {0: "copper", 1: "copper", 2: "steel", 3: "elite"}[tier]

    def vis(name):
        if name == "greataxe":
            return tier > 0
        if name == "beard":
            return gear not in ("elite", "steel")
        for gg in GEARS:
            if name.endswith("_" + gg):
                return gg == gear
        return True
    return vis


for variant in ("warrior_3", "warrior_2", "warrior_1"):
    vis = show(variant)
    frames = [render(attack_axe, t, vis, os.path.join(OUT, "_frame.png")) for t in (0.0, 0.38, 0.55, 0.7)]
    frames.append(render(walk_axe, 0.25, vis, os.path.join(OUT, "_frame.png")))
    strip = Image.new("RGBA", (sum(f.width for f in frames), frames[0].height))
    x = 0
    for f in frames:
        strip.paste(f, (x, 0))
        x += f.width
    strip.save(os.path.join(OUT, f"preview_{variant}.png"))
os.remove(os.path.join(OUT, "_frame.png"))


# ---------------------------------------------------------------- writers
def fnum(v):
    return float(f"{v:.4g}")


def bb_animation(a, present):
    animators = {}
    for bone, chans in a["tracks"].items():
        if bone not in present:
            continue
        kfs = []
        for ch, keys in chans.items():
            for t, (x, y, z) in keys:
                kfs.append({"channel": ch, "data_points": [{"x": str(fnum(x)), "y": str(fnum(y)), "z": str(fnum(z))}],
                            "uuid": str(uuid.uuid4()), "time": t, "color": -1, "interpolation": a["lerp"]})
        animators[m.groups[bone]["uuid"]] = {"name": bone, "type": "bone", "keyframes": kfs}
    return {"uuid": str(uuid.uuid4()), "name": "animation.dwarf." + a["name"],
            "loop": "loop" if a["loop"] else "once", "override": False, "length": a["length"], "snapping": 20,
            "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
            "animators": animators}


def save_bb(name, exclude, anims):
    m.save(name=name, exclude=exclude)
    path = os.path.join(OUT, name + ".bbmodel")
    data = json.load(open(path, encoding="utf-8"))
    present = set()

    def walk_groups(gs):
        for g in gs:
            if isinstance(g, dict):
                present.add(g["name"])
                walk_groups(g["children"])
    walk_groups(data["outliner"])
    data["animations"] = [bb_animation(a, present) for a in anims]
    data["model_identifier"] = "dwarf"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)


WORKER_ANIMS = [idle, walk, attack]
WARRIOR_ANIMS = [idle_axe, walk_axe, attack_axe]
save_bb("dwarf", None, ANIMS)
for variant in ("worker", "warrior_1", "warrior_2", "warrior_3"):
    vis = show(variant)
    save_bb("dwarf_" + variant, lambda n, vis=vis: not vis(n), WORKER_ANIMS if variant == "worker" else WARRIOR_ANIMS)

# GeckoLib geometry (Bedrock format: x mirrored, cube origin = min corner)
bones = []
for name, g in m.groups.items():
    px, py, pz = g["origin"]
    b = {"name": name, "pivot": [-px, py, pz]}
    if "rotation" in g:
        rx_, ry_, rz_ = g["rotation"]
        b["rotation"] = [-rx_, -ry_, rz_]
    if name in PARENT:
        b["parent"] = PARENT[name]
    cubes = []
    for frm, to, w, h, d, u, v, inf in m.java_cubes.get(name, []):
        c = {"origin": [-to[0], frm[1], frm[2]], "size": [w, h, d], "uv": [u, v]}
        if inf:
            c["inflate"] = inf
        cubes.append(c)
    if cubes:
        b["cubes"] = cubes
    bones.append(b)
geo = {"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.dwarf", "texture_width": GEO_TEX, "texture_height": GEO_TEX,
                    "visible_bounds_width": 4, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.25, 0]},
    "bones": bones}]}

anim_json = {"format_version": "1.8.0", "animations": {}}
for a in ANIMS:
    bones_out = {}
    for bone, chans in a["tracks"].items():
        out = {}
        for ch, keys in chans.items():
            out[ch] = {f"{t:g}": ({"post": [fnum(x) for x in v], "lerp_mode": "catmullrom"} if a["lerp"] == "catmullrom"
                                  else [fnum(x) for x in v]) for t, v in keys}
        bones_out[bone] = out
    entry = {"loop": a["loop"], "animation_length": a["length"], "bones": bones_out}
    anim_json["animations"]["animation.dwarf." + a["name"]] = entry

for sub, obj in (("models", geo), ("animations", anim_json)):
    d = os.path.join(RES, "geckolib", sub, "entity")
    os.makedirs(d, exist_ok=True)
    ext = "geo" if sub == "models" else "animation"
    with open(os.path.join(d, f"dwarf.{ext}.json"), "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=1)
m.painter.img.save(os.path.join(RES, "textures", "entity", "dwarf.png"))
print("wrote geckolib dwarf.geo.json / dwarf.animation.json / dwarf.png and the .bbmodel files")
