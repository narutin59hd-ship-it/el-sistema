"""Aspectos (64x64, formato de jugador) de los habitantes de la Ciudad de los Cazadores (1.7.0).

Diseños propios: la recepcionista de la Asociación, el herrero, la alquimista, el mercader de esencias,
el maestro del gremio y cuatro vecinos. Sólo usa la biblioteca estándar de Python.
"""
import os
import struct
import zlib

NAMES = ["recepcionista", "herrero", "alquimista", "mercader", "gremio", "cazadora", "estudiante", "oficinista", "vecino"]


def noise(x, y, seed=0):
    h = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def clamp(v):
    return max(0, min(255, int(round(v))))


def mul(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def mix(c, d, t):
    return tuple(clamp(a + (b - a) * t) for a, b in zip(c[:3], d[:3]))


class Skin:
    def __init__(self):
        self.p = [[(0, 0, 0, 0) for _ in range(64)] for _ in range(64)]

    def put(self, x, y, c):
        if c is None:
            return
        self.p[y][x] = (c[0], c[1], c[2], 255)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = bytearray()
        for row in self.p:
            raw.append(0)
            for px in row:
                raw.extend(bytes(px))

        def chunk(tag, data):
            c = struct.pack(">I", len(data)) + tag + data
            return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        data = b"\x89PNG\r\n\x1a\n"
        data += chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 6, 0, 0, 0))
        data += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        data += chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(data)


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


# Cajas del modelo de jugador (brazos anchos)
HEAD = (0, 0, 8, 8, 8)
HAT = (32, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
JACKET = (16, 32, 8, 12, 4)
R_ARM = (40, 16, 4, 12, 4)
R_SLEEVE = (40, 32, 4, 12, 4)
L_ARM = (32, 48, 4, 12, 4)
L_SLEEVE = (48, 48, 4, 12, 4)
R_LEG = (0, 16, 4, 12, 4)
R_PANTS = (0, 32, 4, 12, 4)
L_LEG = (16, 48, 4, 12, 4)
L_PANTS = (0, 48, 4, 12, 4)

SHADE = {"top": 1.08, "bottom": 0.72, "front": 1.0, "back": 0.86, "right": 0.93, "left": 0.93}


def paint(s, box, fn, seed):
    u, v, w, h, d = box
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for ly in range(fh):
            for lx in range(fw):
                c = fn(name, lx, ly, fw, fh)
                if c is None:
                    continue
                k = SHADE[name] * (1.0 + (noise(x0 + lx, y0 + ly, seed) - 0.5) * 0.10)
                s.put(x0 + lx, y0 + ly, mul(c, k))


# ====================================================================== piezas


def head_fn(S):
    skin, hair = S["skin"], S["hair"]
    style = S.get("style", "short")
    brow = mul(hair, 0.75) if style != "bald" else mul(skin, 0.7)

    def fn(f, x, y, w, h):
        if f == "top":
            if style == "bald":
                return hair if (x in (0, 7) or y == 7) else mix(skin, (255, 230, 210), 0.15)
            return hair if not (style == "part" and x == 3) else mul(hair, 0.8)
        if f == "bottom":
            return mul(skin, 0.85)
        if f == "back":
            limit = {"short": 5, "bob": 6, "long": 8, "bun": 6, "bald": 2, "part": 5}[style]
            if y < limit:
                return hair
            return mul(skin, 0.9)
        if f in ("right", "left"):
            # lx alto = hacia la cara en el lado derecho; al revés en el izquierdo
            front = x if f == "right" else w - 1 - x
            if style == "long":
                return hair if not (front >= 6 and y >= 3) else skin
            if style == "bob":
                if y < 6 and front <= 5:
                    return hair
                if y < 2:
                    return hair
                return skin if front >= 4 else mul(skin, 0.95)
            if style == "bald":
                if 2 <= y <= 4 and front <= 4:
                    return hair
                return skin
            if y < 2 or (y < 4 and front <= 4):
                return hair
            if y == 4 and front == 5:
                return mul(skin, 0.85)  # oreja
            return skin
        # cara (front)
        if style != "bald":
            if y == 0:
                return hair
            if y == 1 and (style in ("short", "part", "long", "bob", "bun")):
                if style == "part":
                    return hair if x != 3 and (x < 2 or x > 4) else skin
                return hair if (x < 3 or x > 5 or style in ("long", "bob")) and not (style == "short" and 2 < x < 6) else skin
            if style in ("long", "bob") and y < 6 and x in (0, 7):
                return hair
        if y == 3 and x in (1, 2, 5, 6):
            return brow
        if y == 4:
            if x in (1, 6):
                return (236, 236, 240)
            if x in (2, 5):
                return S.get("eyes", (60, 40, 30))
        if y == 5 and x in (3, 4):
            return mul(skin, 0.88)
        if y == 6 and x in (3, 4):
            return S.get("lips", mix(mul(skin, 0.7), (150, 60, 60), 0.4))
        return skin

    return fn


def face_extras(s, S):
    """Barba, bigote, gafas, cicatriz y monóculo sobre la cara (front de la cabeza: x 8..15, y 8..15)."""
    fx, fy = 8, 8
    beard = S.get("beard")
    if beard:
        for x in range(8):
            for y in (6, 7):
                if y == 6 and x in (3, 4):
                    continue
                s.put(fx + x, fy + y, mul(beard, 0.95 + 0.1 * noise(x, y, 5)))
        for x in (0, 7):
            s.put(fx + x, fy + 5, beard)
        for y in range(4, 8):  # patillas hasta la barba
            s.put(0 + 7, 8 + y, beard)
            s.put(16 + 0, 8 + y, beard)
    stache = S.get("mustache")
    if stache:
        for x in range(2, 6):
            s.put(fx + x, fy + 5, stache)
    if S.get("glasses"):
        g = S["glasses"]
        for x in (0, 3, 4, 7):
            s.put(fx + x, fy + 4, g)
        for x in (1, 2, 5, 6):
            s.put(fx + x, fy + 3, g)
    if S.get("scar"):
        for (x, y) in ((1, 2), (2, 3), (2, 5), (1, 6)):
            s.put(fx + x, fy + y, (196, 110, 110))
    if S.get("monocle"):
        s.put(fx + 6, fy + 3, (230, 190, 70))
        s.put(fx + 7, fy + 4, (230, 190, 70))
        s.put(fx + 6, fy + 5, (230, 190, 70))
        s.put(fx + 5, fy + 4, (150, 200, 220))


def torso_fn(S):
    shirt = S["shirt"]
    pants = S["pants"]
    kind = S.get("torso", "plain")

    def fn(f, x, y, w, h):
        if f in ("top", "bottom"):
            return shirt if f == "top" else pants
        c = shirt
        if y >= 10:
            c = S.get("belt", mul(pants, 0.7))
            if f == "front" and y == 10 and x in (3, 4):
                return S.get("buckle", (200, 180, 90))
        if f == "front":
            if kind == "tie" and 1 <= y <= 8 and x in (3, 4):
                return S["tie"] if not (y == 1 and x == 4) else mul(S["tie"], 0.85)
            if kind == "apron" and y >= 2:
                if 1 <= x <= 6:
                    return mix(S["apron"], (0, 0, 0), 0.08 * noise(x, y, 9))
            if kind == "apron" and y < 2 and x in (1, 6):
                return S["apron"]
            if kind == "strap" and abs((x + 0.5) - (y + 0.5) * 0.8) < 0.8:
                return S["strap"]
            if kind == "vest" and 2 <= x <= 5 and y < 10:
                return S["vest"] if not (x in (3, 4) and y % 3 == 1) else (210, 180, 90)
            if kind == "check" and y < 10:
                return mul(shirt, 0.8) if (x + y) % 3 == 0 or x % 3 == 0 else shirt
            if S.get("badge") and x in (5, 6) and y == 3:
                return S["badge"]
            if kind == "plain" and x in (3, 4) and y in (2, 5, 8) and S.get("buttons"):
                return S["buttons"]
        if f == "back" and kind == "apron" and y < 10 and x in (2, 5) and y < 3:
            return S["apron"]
        return c

    return fn


def jacket_fn(S):
    coat = S.get("coat")
    if not coat:
        return None
    open_front = S.get("coat_open", True)
    trim = S.get("coat_trim")

    def fn(f, x, y, w, h):
        if f == "front" and open_front and x in (3, 4) and y < 11:
            return None
        if f == "top":
            return coat
        if f == "bottom":
            return None
        if trim and f == "front" and open_front and x in (2, 5):
            return trim
        if trim and y == h - 1:
            return trim
        if f == "front" and S.get("badge") and x == 6 and y == 3:
            return S["badge"]
        return coat

    return fn


def arm_fn(S, left):
    sleeve = S.get("sleeve", S["shirt"])
    skin = S["skin"]
    length = S.get("sleeve_len", 9)
    glove = S.get("glove")

    def fn(f, x, y, w, h):
        if f == "top":
            return S.get("pauldron", sleeve if length > 0 else skin)
        if f == "bottom":
            return glove or skin
        if S.get("pauldron") and y < 3:
            return mix(S["pauldron"], (255, 255, 255), 0.1 if y == 0 else 0.0)
        if y < length:
            if S.get("soot") and length == 0:
                return mul(skin, 0.85)
            return sleeve
        if glove and y >= h - 3:
            return glove
        if S.get("soot") and noise(x, y + (7 if left else 0), 31) < 0.25:
            return mul(skin, 0.7)
        if y == length and S.get("cuff"):
            return S["cuff"]
        return skin

    return fn


def sleeve_fn(S):
    coat = S.get("coat")
    if not coat or not S.get("coat_sleeves", True):
        return None
    trim = S.get("coat_trim")

    def fn(f, x, y, w, h):
        if f == "bottom":
            return None
        if y >= 10:
            return None
        if trim and y == 9:
            return trim
        return coat

    return fn


def leg_fn(S, left):
    pants, shoes = S["pants"], S.get("shoes", (40, 36, 34))
    skirt = S.get("skirt")

    def fn(f, x, y, w, h):
        if f == "top":
            return skirt or pants
        if f == "bottom":
            return mul(shoes, 0.8)
        if y >= h - 2:
            return shoes if not (y == h - 2 and f == "front" and x in (1, 2)) else mul(shoes, 1.25)
        if skirt:
            if y < 5:
                return skirt
            return S.get("stockings", mul(S["skin"], 0.9))
        if S.get("cargo") and f in ("right", "left") and 3 <= y <= 5:
            return mul(pants, 0.82)
        if S.get("pinstripe") and f == "front" and x == (1 if left else 2):
            return mul(pants, 1.15)
        return pants

    return fn


def pants_overlay_fn(S):
    coat = S.get("coat")
    if not coat or not S.get("long_coat"):
        return None
    trim = S.get("coat_trim")

    def fn(f, x, y, w, h):
        if f in ("top", "bottom"):
            return None
        if y > 6:
            return None
        if f == "front" and S.get("coat_open", True) and x == 3:
            return None
        if trim and y == 6:
            return trim
        return coat

    return fn


def hat_fn(S):
    kind = S.get("hat")
    if not kind:
        return None
    col = S.get("hat_color", (120, 30, 30))

    def fn(f, x, y, w, h):
        if kind == "bandana":
            if f == "top":
                return col
            if f == "bottom":
                return None
            if y < 2 or (f == "back" and y < 3 and x in (3, 4)):
                return col if not (y == 1 and x % 3 == 0) else mul(col, 0.8)
            return None
        if kind == "beret":
            if f == "top":
                return col
            if f == "bottom":
                return None
            if y == 0:
                return col
            if y == 1:
                return S.get("hat_band", mul(col, 0.7))
            return None
        if kind == "goggles":
            if f in ("top", "bottom"):
                return None
            if y == 2:
                if f == "front":
                    return (120, 220, 230) if x in (1, 2, 5, 6) else col
                return mul(col, 0.6)
            return None
        return None

    return fn


def make(S):
    s = Skin()
    paint(s, HEAD, head_fn(S), 1)
    face_extras(s, S)
    paint(s, BODY, torso_fn(S), 2)
    paint(s, R_ARM, arm_fn(S, False), 3)
    paint(s, L_ARM, arm_fn(S, True), 4)
    paint(s, R_LEG, leg_fn(S, False), 5)
    paint(s, L_LEG, leg_fn(S, True), 6)
    for box, fn, seed in ((HAT, hat_fn(S), 7), (JACKET, jacket_fn(S), 8), (R_SLEEVE, sleeve_fn(S), 9),
                          (L_SLEEVE, sleeve_fn(S), 10), (R_PANTS, pants_overlay_fn(S), 11), (L_PANTS, pants_overlay_fn(S), 12)):
        if fn:
            paint(s, box, fn, seed)
    return s


# ====================================================================== los vecinos (diseños propios)

SKINS = {
    "recepcionista": dict(skin=(238, 200, 172), hair=(44, 30, 26), style="bob", eyes=(70, 50, 40),
                          shirt=(240, 242, 246), torso="tie", tie=(80, 190, 240), coat=(32, 44, 82),
                          coat_trim=(90, 200, 245), badge=(95, 215, 255), pants=(30, 38, 70), skirt=(30, 38, 70),
                          stockings=(70, 62, 66), shoes=(26, 24, 30), sleeve=(32, 44, 82), sleeve_len=10),
    "herrero": dict(skin=(196, 140, 104), hair=(30, 22, 18), style="short", eyes=(50, 34, 24), beard=(36, 26, 20),
                    shirt=(58, 54, 52), torso="apron", apron=(120, 74, 40), belt=(70, 44, 26), pants=(76, 60, 44),
                    shoes=(44, 32, 24), sleeve_len=0, soot=True, hat="bandana", hat_color=(170, 40, 34),
                    glove=(90, 58, 34)),
    "alquimista": dict(skin=(240, 214, 194), hair=(226, 226, 236), style="long", eyes=(110, 70, 150),
                       shirt=(186, 160, 220), torso="strap", strap=(110, 70, 40), coat=(52, 110, 74),
                       coat_trim=(200, 180, 90), long_coat=True, pants=(48, 44, 56), shoes=(52, 40, 34),
                       sleeve=(52, 110, 74), hat="goggles", hat_color=(184, 140, 70)),
    "mercader": dict(skin=(214, 170, 136), hair=(36, 28, 28), style="short", eyes=(60, 40, 30), mustache=(36, 28, 28),
                     monocle=True, shirt=(236, 232, 220), torso="vest", vest=(30, 26, 34), coat=(92, 40, 120),
                     coat_trim=(220, 180, 70), long_coat=True, pants=(36, 30, 44), shoes=(30, 24, 22),
                     sleeve=(92, 40, 120), hat="beret", hat_color=(92, 40, 120), hat_band=(220, 180, 70)),
    "gremio": dict(skin=(212, 168, 136), hair=(150, 150, 156), style="short", eyes=(60, 70, 80), beard=(130, 130, 136),
                   scar=True, shirt=(110, 46, 40), coat=(70, 36, 30), coat_trim=(150, 150, 160), coat_open=False,
                   pauldron=(140, 144, 152), pants=(52, 44, 40), shoes=(40, 30, 26), sleeve=(70, 36, 30),
                   glove=(60, 40, 30)),
    "cazadora": dict(skin=(236, 196, 170), hair=(214, 96, 40), style="long", eyes=(60, 120, 80),
                     shirt=(34, 34, 40), torso="plain", buttons=(90, 220, 240), coat=(24, 24, 30), coat_trim=(80, 210, 240),
                     coat_sleeves=True, pants=(76, 84, 52), cargo=True, shoes=(40, 34, 30), sleeve=(24, 24, 30),
                     glove=(30, 30, 34)),
    "estudiante": dict(skin=(232, 196, 166), hair=(24, 22, 26), style="part", eyes=(50, 40, 34), glasses=(30, 30, 36),
                       shirt=(244, 244, 248), torso="tie", tie=(176, 40, 46), coat=(36, 48, 96), coat_trim=(220, 200, 120),
                       pants=(120, 120, 126), shoes=(30, 26, 24), sleeve=(36, 48, 96)),
    "oficinista": dict(skin=(228, 186, 156), hair=(30, 24, 22), style="bob", eyes=(56, 40, 34),
                       shirt=(236, 238, 242), torso="plain", buttons=(200, 200, 206), coat=(96, 98, 106),
                       coat_trim=(70, 72, 80), pants=(86, 88, 96), pinstripe=True, shoes=(36, 30, 30), sleeve=(96, 98, 106)),
    "vecino": dict(skin=(214, 172, 140), hair=(196, 196, 200), style="bald", eyes=(70, 60, 50), mustache=(200, 200, 204),
                   glasses=(120, 100, 60), shirt=(170, 60, 52), torso="check", coat=(200, 176, 132), coat_trim=(150, 126, 90),
                   pants=(100, 78, 58), shoes=(60, 44, 32), sleeve=(200, 176, 132)),
}


def generate(folder):
    for name in NAMES:
        make(SKINS[name]).save(os.path.join(folder, name + ".png"))
    return len(NAMES)


if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/ciudadanos"
    print(generate(out), "aspectos en", out)
