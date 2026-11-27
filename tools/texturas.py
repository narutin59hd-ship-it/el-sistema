"""Genera las texturas, modelos de objetos y traducciones extra de El Sistema (1.5.0).

Solo usa la biblioteca estándar de Python (se ejecuta en GitHub Actions). Todo es determinista: el mismo
script siempre produce los mismos archivos.

Uso: python3 tools/texturas.py <carpeta del proyecto>
"""
import json
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_modelos  # noqa: E402
import monstruos  # noqa: E402
import ciudadanos  # noqa: E402
import pack_sistema  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
A = os.path.join(ROOT, "src/main/resources/assets/elsistema")


# ====================================================================== utilidades


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


def jitter(c, x, y, seed, amount=0.10):
    k = 1.0 + (noise(x, y, seed) - 0.5) * 2 * amount
    return mul(c, k)


class Tex:
    def __init__(self, w, h):
        self.w = w
        self.h = h
        self.p = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]

    def put(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            if len(c) == 4:
                self.p[y][x] = tuple(c)
            else:
                self.p[y][x] = (c[0], c[1], c[2], a)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.p[y][x]
        return (0, 0, 0, 0)

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
        data += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        data += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        data += chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(data)


def faces(u, v, w, h, d):
    """Rectángulos (x0, y0, ancho, alto) de cada cara de una caja de Minecraft."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(t, u, v, w, h, d, fn, only=None):
    """fn(cara, lx, ly, ancho, alto) -> color (o None para dejarlo transparente)."""
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        if only and name not in only:
            continue
        for ly in range(fh):
            for lx in range(fw):
                c = fn(name, lx, ly, fw, fh)
                if c is not None:
                    t.put(x0 + lx, y0 + ly, c)


def edge(lx, ly, fw, fh):
    return lx == 0 or ly == 0 or lx == fw - 1 or ly == fh - 1


def shade_face(c, name, lx, ly, fw, fh, seed, amount=0.10):
    """Color base con ruido, borde algo más oscuro y caras laterales/inferiores sombreadas."""
    k = {"top": 1.08, "bottom": 0.70, "front": 1.0, "back": 0.86, "right": 0.92, "left": 0.92}[name]
    col = jitter(mul(c, k), lx * 7 + fw, ly * 5 + fh, seed, amount)
    if edge(lx, ly, fw, fh):
        col = mul(col, 0.86)
    return col


# Cajas del modelo humanoide (64x64, como el zombi): las usan las capas de armadura
HEAD = (0, 0, 8, 8, 8)
HAT = (32, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
ARM = (40, 16, 4, 12, 4)
LEG = (0, 16, 4, 12, 4)


# ====================================================================== monstruos (desde la 1.5.0: tools/monstruos.py)


def save_rows(path, w, h, rows):
    t = Tex(w, h)
    t.p = [list(r) for r in rows]
    t.save(path)


def monsters():
    if not gen_modelos.check(ROOT):
        raise SystemExit("MonsterLayers.java no coincide con tools/modelos.py: ejecuta python3 tools/gen_modelos.py .")
    monstruos.generate(save_rows, os.path.join(A, "textures/entity"))


# ====================================================================== capa de las sombras (16x16)


def sombra():
    t = Tex(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 51)
            c = mix((14, 6, 24), (40, 18, 66), n)
            wisp = noise(x // 2, (y + x // 3) // 2, 52)
            if wisp > 0.82:
                c = mix(c, (122, 66, 200), 0.55)
            t.put(x, y, c)
    t.save(os.path.join(A, "textures/entity/sombra.png"))


# ====================================================================== capas de armadura (64x32)


def armor_set(name, pal, helm, chest, arms, legs_l2, body_l2, boots, hat=None):
    l1 = Tex(64, 32)
    paint_box(l1, *HEAD, lambda f, x, y, w, h: helm(f, x, y, w, h, pal))
    if hat:
        paint_box(l1, *HAT, lambda f, x, y, w, h: hat(f, x, y, w, h, pal))
    paint_box(l1, *BODY, lambda f, x, y, w, h: chest(f, x, y, w, h, pal))
    paint_box(l1, *ARM, lambda f, x, y, w, h: arms(f, x, y, w, h, pal))
    paint_box(l1, *LEG, lambda f, x, y, w, h: boots(f, x, y, w, h, pal))
    l1.save(os.path.join(A, "textures/models/armor", name + "_layer_1.png"))
    l2 = Tex(64, 32)
    paint_box(l2, *BODY, lambda f, x, y, w, h: body_l2(f, x, y, w, h, pal))
    paint_box(l2, *LEG, lambda f, x, y, w, h: legs_l2(f, x, y, w, h, pal))
    l2.save(os.path.join(A, "textures/models/armor", name + "_layer_2.png"))


def a_main(pal, f, x, y, w, h, seed=3):
    return shade_face(pal["main"], f, x, y, w, h, seed, pal.get("noise", 0.10))


def helm_open(f, x, y, w, h, pal):
    """Capucha o casco que deja ver la cara."""
    if f == "bottom":
        return None
    c = a_main(pal, f, x, y, w, h)
    if f == "front":
        if y >= 2 and 1 <= x <= 6:
            return None
        if y == 1 or (y >= 2 and x in (0, 7)):
            c = pal["trim"]
    return c


def helm_closed(f, x, y, w, h, pal):
    """Yelmo cerrado con rendija para los ojos."""
    if f == "bottom":
        return None
    c = a_main(pal, f, x, y, w, h)
    if f == "front":
        if 3 <= x <= 4:
            c = pal["hi"]
        if y == 3:
            c = (10, 8, 12)
            if x in (2, 5) and pal.get("eyes"):
                c = pal["eyes"]
        if y == 6 and x in (1, 2, 5, 6):
            c = (14, 12, 16)
    if f == "top" and 3 <= x <= 4:
        c = pal["trim"]
    return c


def chest_generic(f, x, y, w, h, pal):
    if f in ("top", "bottom"):
        return a_main(pal, f, x, y, w, h)
    c = a_main(pal, f, x, y, w, h)
    if y >= 10:
        return None if f != "front" or y == 11 else mul(c, 0.9)
    if f == "front":
        emb = pal.get("emblem")
        if emb and emb(x, y):
            c = pal["accent"]
        if y in (0, 9):
            c = pal["trim"]
    if f == "back" and pal.get("cape"):
        c = jitter(pal["cape"], x, y, 7, 0.12)
        if x % 3 == 1:
            c = mul(c, 0.8)
        if x in (0, w - 1):
            c = pal["accent"]
    if f in ("left", "right") and y == 0:
        c = pal["trim"]
    return c


def arms_generic(f, x, y, w, h, pal):
    if y <= 4 or f == "top":
        c = a_main(pal, f, x, y, w, h)
        if y == 4:
            c = pal["trim"]
        return c
    if 8 <= y <= 10:
        c = a_main(pal, f, x, y, w, h)
        if y == 8:
            c = pal["trim"]
        if y == 10 and f == "front" and pal.get("accent"):
            c = pal["accent"]
        return c
    if pal.get("sleeves"):
        return a_main(pal, f, x, y, w, h)
    return None


def legs_generic(f, x, y, w, h, pal):
    if y > 9 or f == "bottom":
        return None
    c = a_main(pal, f, x, y, w, h)
    if f == "front" and y in (4, 5) and pal.get("knee"):
        c = pal["hi"]
    if y == 9:
        c = pal["trim"]
    return c


def belt_generic(f, x, y, w, h, pal):
    if f in ("top",):
        return None
    if f == "bottom":
        return a_main(pal, f, x, y, w, h)
    if y < 8:
        return None
    c = a_main(pal, f, x, y, w, h)
    if y == 8:
        c = pal["trim"]
        if f == "front" and 3 <= x <= 4:
            c = pal["accent"]
    return c


def boots_generic(f, x, y, w, h, pal):
    if f == "top":
        return None
    if y < 7 and f != "bottom":
        return None
    c = a_main(pal, f, x, y, w, h)
    if y == 7:
        c = pal["trim"]
    if f == "front" and y == 11:
        c = mul(c, 0.7)
    return c


def crown_hat(f, x, y, w, h, pal):
    if f in ("bottom", "top"):
        return None
    if y <= 2:
        spike = (x % 2 == 0 and y <= 1) or y == 2
        if not spike:
            return None
        if y == 2:
            return pal["silver"] if x % 3 else pal["gem"]
        return pal["silver"] if y == 1 else mix(pal["gem"], (255, 255, 255), 0.3)
    return None


PAL_CAZADOR = {"main": (78, 94, 54), "trim": (112, 74, 42), "hi": (110, 126, 80), "accent": (206, 170, 90),
               "emblem": lambda x, y: (x == y - 1 or x == 8 - y) and 1 <= y <= 7, "noise": 0.14}
PAL_ARCHIMAGO = {"main": (40, 52, 128), "trim": (222, 182, 64), "hi": (70, 90, 180), "accent": (110, 206, 255),
                 "emblem": lambda x, y: (x in (3, 4) and 2 <= y <= 8) or (y == 4 and 2 <= x <= 5),
                 "sleeves": True, "noise": 0.08}
PAL_CABALLERO = {"main": (38, 38, 46), "trim": (150, 22, 34), "hi": (86, 86, 104), "accent": (150, 22, 34),
                 "emblem": lambda x, y: (x in (3, 4) and 2 <= y <= 7) or (y == 3 and 2 <= x <= 5),
                 "eyes": (255, 70, 52), "knee": True, "sleeves": True, "noise": 0.07}
PAL_MONARCA = {"main": (26, 20, 36), "trim": (155, 92, 255), "hi": (70, 54, 96), "accent": (190, 120, 255),
               "silver": (176, 176, 196), "gem": (170, 70, 255), "cape": (44, 16, 70),
               "emblem": lambda x, y: (abs(x - 3.5) * 1.6 + 1 >= y - 1 and abs(x - 3.5) * 1.6 <= y) and y <= 7,
               "eyes": (190, 120, 255), "knee": True, "sleeves": True, "noise": 0.06}


def armors():
    def archimago_robe(f, x, y, w, h, pal):
        if f == "bottom":
            return None
        c = a_main(pal, f, x, y, w, h)
        if y == 11 or (f == "front" and x == 0):
            c = pal["trim"]
        if f == "front" and y in (3, 7) and x == 2:
            c = pal["accent"]
        return c

    def archimago_sandals(f, x, y, w, h, pal):
        if f == "top" or (y < 9 and f != "bottom"):
            return None
        c = shade_face((110, 74, 42), f, x, y, w, h, 5, 0.1)
        if y == 10 and x % 2 == 1 and f != "bottom":
            return None
        return c

    armor_set("cazador", PAL_CAZADOR, helm_open, chest_generic, arms_generic, legs_generic, belt_generic, boots_generic)
    armor_set("archimago", PAL_ARCHIMAGO, helm_open, chest_generic, arms_generic, archimago_robe, belt_generic,
              archimago_sandals)
    armor_set("caballero_negro", PAL_CABALLERO, helm_closed, chest_generic, arms_generic, legs_generic, belt_generic,
              boots_generic)
    armor_set("monarca", PAL_MONARCA, helm_closed, chest_generic, arms_generic, legs_generic, belt_generic,
              boots_generic, hat=crown_hat)


# ====================================================================== iconos de objetos (16x16)


def outline(t, color):
    solid = {(x, y) for y in range(16) for x in range(16) if t.get(x, y)[3] > 0}
    for y in range(16):
        for x in range(16):
            if (x, y) in solid:
                continue
            if any((x + dx, y + dy) in solid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                t.put(x, y, color)


def from_mask(rows, palette):
    t = Tex(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                t.put(x, y, palette[ch])
    return t


def save_item(t, name):
    t.save(os.path.join(A, "textures/item", name + ".png"))


DAGA = [
    "................",
    "................",
    "............aW..",
    "...........aWb..",
    "..........aWb...",
    ".........aWb....",
    "........aWb.....",
    "....g..aWb......",
    "....gg.Wb.......",
    ".....ggb........",
    "......hgg.......",
    ".....h..g.......",
    "....h...........",
    "...p............",
    "................",
    "................"]

ESPADA = [
    "..............R.",
    ".............RkR",
    "............RkR.",
    "...........RkR..",
    "..........RkR...",
    ".........RkR....",
    "........RkR.....",
    ".......RkR......",
    "..gg..RkR.......",
    "...gGRkR........",
    "....GkR.........",
    "....hGg.........",
    "...h..gg........",
    "..h.............",
    ".pp.............",
    ".pp............."]

HACHA = [
    "................",
    "........ssS.....",
    ".......sSSSS....",
    "......sSSSSSS...",
    "......sSSSrSS...",
    ".......sSSrrS...",
    "........sw.rS...",
    ".......w.....S..",
    "......w.........",
    ".....w..........",
    "....w...........",
    "...w............",
    "..w.............",
    ".w..............",
    "w...............",
    "................"]

GUADANA = [
    "......vvvvvv....",
    "....vvPPPPPPvv..",
    "...vPPvvvvvvPPv.",
    "..vPv......vvPPv",
    "..vv.........vP.",
    ".v............vh",
    "..............h.",
    ".............h..",
    "............h...",
    "...........h....",
    "..........h.....",
    ".........h......",
    "........h.......",
    ".......h........",
    "......h.........",
    ".....p.........."]

BACULO = [
    "............cc..",
    "...........cCCc.",
    "..........cCWCc.",
    "..........cCCCc.",
    "...........gcc..",
    "..........gg....",
    ".........w......",
    "........w.......",
    ".......w........",
    "......w.........",
    ".....w..........",
    "....w...........",
    "...w............",
    "..w.............",
    ".g..............",
    "................"]

POCION = [
    "................",
    "................",
    "......kkkk......",
    ".......cc.......",
    ".......gg.......",
    "......gLLg......",
    ".....gLLLLg.....",
    "....gLLLLLLg....",
    "....gLLLLLLg....",
    "....gLLLLLLg....",
    "....gLLLLLLg....",
    ".....gLLLLg.....",
    "......gggg......",
    "................",
    "................",
    "................"]

PIEDRA = [
    "................",
    "................",
    "......ssss......",
    "....ssSSSSss....",
    "...sSSSSSSSSs...",
    "...sSSSSSSSSs...",
    "..sSSSSSSSSSSs..",
    "..sSSSSSSSSSSs..",
    "..sSSSSSSSSSSs..",
    "..sSSSSSSSSSSs..",
    "...sSSSSSSSSs...",
    "...sSSSSSSSSs...",
    "....ssSSSSss....",
    "......ssss......",
    "................",
    "................"]


def shade_icon(t, base_names=None):
    """Brillo arriba a la izquierda y sombra abajo a la derecha sobre los píxeles ya pintados."""
    out = Tex(16, 16)
    for y in range(16):
        for x in range(16):
            c = t.get(x, y)
            if c[3] == 0:
                continue
            up = t.get(x - 1, y)[3] and t.get(x, y - 1)[3]
            dn = t.get(x + 1, y)[3] and t.get(x, y + 1)[3]
            col = c[:3]
            if not up:
                col = mix(col, (255, 255, 255), 0.25)
            elif not dn:
                col = mul(col, 0.75)
            out.put(x, y, col)
    return out


AGUIJON = [
    "...............R",
    "..............Rr",
    ".............Rr.",
    "............Rr..",
    "...........Rr...",
    "..........Rr....",
    ".........Rr.....",
    "........Rr......",
    "...gg..Rr.......",
    "...gGGRr........",
    "....GGr.........",
    "....hGGg........",
    "...h..gg........",
    "..h.............",
    ".p..............",
    "pp.............."]

CORAZON = [
    "................",
    "................",
    "...oooo..oooo...",
    "..oOOOOooOOOOo..",
    ".oOOyOOOOOOOOOo.",
    ".oOyyOOOOOOOOOo.",
    ".oOOOOOOOOOOOOo.",
    ".oOOOOOOOOOOOOo.",
    "..oOOOOOOOOOOo..",
    "...oOOOOOOOOo...",
    "....oOOOOOOo....",
    ".....oOOOOo.....",
    "......oOOo......",
    ".......oo.......",
    "................",
    "................"]


def legendaries():
    # Hoja del Juicio: hoja blanca y celeste con guarda de oro
    p = {"R": (236, 240, 255), "k": (120, 206, 255), "g": (176, 136, 36), "G": (255, 214, 90), "h": (70, 46, 30),
         "p": (90, 200, 255)}
    t = from_mask(ESPADA, p)
    outline(t, (24, 18, 8))
    save_item(t, "hoja_juicio")

    # Colmillo del Eclipse: daga negra violácea con gema roja
    p = {"a": (120, 108, 176), "W": (226, 216, 255), "b": (70, 50, 124), "g": (44, 32, 66), "h": (30, 20, 40),
         "p": (230, 40, 60)}
    t = shade_icon(from_mask(DAGA, p))
    outline(t, (10, 6, 18))
    save_item(t, "colmillo_eclipse")

    # Aguijón Carmesí: estoque fino carmesí con guarda dorada
    p = {"R": (255, 120, 140), "r": (170, 20, 50), "g": (150, 110, 30), "G": (240, 200, 80), "h": (60, 12, 24),
         "p": (230, 60, 90)}
    t = from_mask(AGUIJON, p)
    outline(t, (18, 4, 10))
    save_item(t, "aguijon_carmesi")

    # Corazón Primordial: corazón de roca con magma dentro
    p = {"o": (64, 44, 42), "O": (230, 104, 32), "y": (255, 232, 160)}
    t = from_mask(CORAZON, p)
    for y in range(16):
        for x in range(16):
            c = t.get(x, y)
            if c[3] and c[:3] == (230, 104, 32) and noise(x, y, 91) > 0.72:
                t.put(x, y, (120, 40, 20))
    t = shade_icon(t)
    outline(t, (22, 10, 8))
    save_item(t, "corazon_primordial")


def line(t, x0, y0, x1, y1, color):
    """Línea de 1 píxel entre dos puntos."""
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(n + 1):
        k = i / n
        t.put(int(round(x0 + (x1 - x0) * k)), int(round(y0 + (y1 - y0) * k)), color)


def bow_frame(pull):
    """Arco del Monarca: arco negro violáceo con puntas de oro. pull = -1 en reposo, 0..2 tensándose."""
    import math
    t = Tex(16, 16)
    limb_dark = (52, 26, 92)
    limb = (98, 52, 168)
    gold = (236, 196, 88)
    pts = []
    for i in range(0, 91, 3):
        ang = math.radians(180 + i)
        pts.append((15 + 12.6 * math.cos(ang), 15 + 12.6 * math.sin(ang)))
    for k, (x, y) in enumerate(pts):
        c = gold if k < 3 or k > len(pts) - 4 else (limb if 11 <= k <= 19 else limb_dark)
        t.put(int(round(x)), int(round(y)), c)
        if 8 <= k <= 22:
            # parte central más gruesa (la empuñadura)
            t.put(int(round(x)) + 1, int(round(y)) + 1, limb_dark)
    for (x, y) in ((6, 6), (7, 6), (6, 7)):
        t.put(x, y, gold)
    e1 = (2.4, 15.0)
    e2 = (15.0, 2.4)
    string = (222, 212, 255)
    if pull < 0:
        line(t, e1[0], e1[1], e2[0], e2[1], string)
    else:
        d = 1.2 + pull * 1.1
        px, py = 8.7 + d, 8.7 + d
        line(t, e1[0], e1[1], px, py, string)
        line(t, px, py, e2[0], e2[1], string)
        shaft = (170, 120, 255)
        tip = (240, 220, 255)
        line(t, px, py, px - 8.5, py - 8.5, shaft)
        for (x, y) in ((px - 8.5, py - 8.5), (px - 7.5, py - 8.5), (px - 8.5, py - 7.5)):
            t.put(int(round(x)), int(round(y)), tip)
    t = shade_icon(t)
    outline(t, (14, 6, 24))
    return t


def sovereign():
    """Armas del Monarca (recompensa del prestigio): negro, violeta y oro."""
    # Filo del Monarca: espadón negro con filo violeta
    p = {"R": (196, 140, 255), "k": (24, 16, 40), "g": (180, 140, 50), "G": (255, 214, 90), "h": (44, 28, 66),
         "p": (200, 130, 255)}
    t = from_mask(ESPADA, p)
    outline(t, (10, 4, 18))
    save_item(t, "filo_monarca")

    # Colmillos del Monarca: daga negra con filo de alma
    p = {"a": (150, 110, 230), "W": (236, 226, 255), "b": (66, 40, 120), "g": (230, 190, 80), "h": (30, 18, 46),
         "p": (200, 120, 255)}
    t = shade_icon(from_mask(DAGA, p))
    outline(t, (10, 4, 18))
    save_item(t, "colmillos_monarca")

    # Báculo del Monarca: báculo oscuro con orbe de almas
    p = {"c": (90, 40, 150), "C": (190, 120, 255), "W": (255, 255, 255), "g": (236, 196, 88), "w": (44, 28, 66)}
    t = from_mask(BACULO, p)
    outline(t, (10, 4, 18))
    save_item(t, "baculo_monarca")

    # Martillo del Monarca: cabeza enorme de obsidiana con gema y remates de oro
    t = Tex(16, 16)
    for k in range(10):
        x, y = 1 + k, 14 - k
        t.put(x, y, (236, 196, 88) if k % 3 == 0 else (66, 40, 96))
    cx, cy = 10.0, 5.0
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            a = (dx - dy) / 1.4142
            b = (dx + dy) / 1.4142
            if abs(a) <= 2.3 and abs(b) <= 4.7:
                if abs(b) > 3.7:
                    c = (236, 196, 88)
                elif abs(a) < 0.9 and abs(b) < 1.4:
                    c = (210, 130, 255)
                else:
                    c = mix((34, 24, 52), (92, 70, 136), (a + 2.3) / 4.6)
                t.put(x, y, c)
    t = shade_icon(t)
    outline(t, (10, 4, 18))
    save_item(t, "martillo_monarca")

    # Arco del Monarca y sus 3 posiciones al tensarlo
    save_item(bow_frame(-1), "arco_monarca")
    for i in range(3):
        save_item(bow_frame(i), "arco_monarca_pulling_%d" % i)


def items():
    p = {"a": (150, 150, 170), "W": (232, 230, 255), "b": (110, 96, 150), "g": (60, 50, 70), "h": (40, 30, 34),
         "p": (150, 110, 255)}
    t = shade_icon(from_mask(DAGA, p))
    outline(t, (16, 12, 22))
    save_item(t, "daga_asesino")

    p = {"R": (200, 30, 40), "k": (30, 28, 36), "g": (70, 70, 80), "G": (120, 120, 140), "h": (60, 34, 30),
         "p": (150, 22, 34)}
    t = from_mask(ESPADA, p)
    outline(t, (10, 8, 12))
    save_item(t, "espada_caballero")

    t = Tex(16, 16)
    for k in range(11):
        x, y = 2 + k, 14 - k
        t.put(x, y, (112, 70, 40) if k % 4 else (150, 40, 36))
    t.put(1, 15, (60, 40, 30))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 10.5, y - 5.5
            a = (-dx - dy) / 1.4142
            b = (dx - dy) / 1.4142
            if -0.6 <= a <= 6.6 and abs(b) <= 1.8 + a * 0.62:
                if a > 5.3:
                    c = (214, 218, 228)
                elif 2.4 <= a <= 3.4 and abs(b) < 1.2 + a * 0.5:
                    c = (176, 36, 34)
                else:
                    c = mix((96, 98, 108), (150, 152, 164), a / 6.6)
                t.put(x, y, c)
    t = shade_icon(t)
    outline(t, (20, 16, 16))
    save_item(t, "hacha_berserker")

    p = {"v": (60, 30, 100), "P": (176, 120, 255), "h": (40, 30, 50), "p": (190, 120, 255)}
    t = from_mask(GUADANA, p)
    outline(t, (10, 6, 16))
    save_item(t, "guadana_monarca")

    p = {"c": (60, 140, 220), "C": (120, 220, 255), "W": (255, 255, 255), "g": (222, 182, 64), "w": (110, 76, 44)}
    t = from_mask(BACULO, p)
    outline(t, (14, 18, 30))
    save_item(t, "baculo_arcano")

    for name, liquid in (("pocion_mana", (60, 110, 255)), ("elixir_vida", (240, 60, 90))):
        p = {"k": (130, 90, 60) if name == "pocion_mana" else (222, 182, 64), "c": (200, 200, 220),
             "g": (190, 220, 240), "L": liquid}
        t = from_mask(POCION, p)
        t = shade_icon(t)
        for (x, y) in ((6, 6), (6, 7), (5, 8)):
            t.put(x, y, (255, 255, 255))
        outline(t, (20, 24, 40))
        save_item(t, name)

    p = {"s": (70, 74, 86), "S": (112, 118, 132)}
    t = shade_icon(from_mask(PIEDRA, p))
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 2.6 <= r <= 3.6 and not (x == 7 and y < 6) and not (x == 8 and y < 6):
                t.put(x, y, (95, 215, 255))
            if r < 1.0:
                t.put(x, y, (200, 245, 255))
    for (x, y) in ((7, 3), (8, 3), (6, 4), (9, 4)):
        t.put(x, y, (95, 215, 255))
    outline(t, (24, 26, 32))
    save_item(t, "piedra_retorno")

    save_item(Tex(16, 16), "velo_sombra")


HELMET = [
    "................",
    "................",
    "................",
    "....########....",
    "...##########...",
    "...##########...",
    "...###....###...",
    "...##......##...",
    "...##......##...",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................"]
CHESTPLATE = [
    "................",
    "..###......###..",
    ".#####....#####.",
    ".##############.",
    ".##############.",
    "..############..",
    "...##########...",
    "....########....",
    "....########....",
    "....########....",
    "....########....",
    "....########....",
    "....########....",
    "................",
    "................",
    "................"]
LEGGINGS = [
    "................",
    "................",
    "....########....",
    "....########....",
    "....########....",
    "....###..###....",
    "....###..###....",
    "....###..###....",
    "....###..###....",
    "....###..###....",
    "....###..###....",
    "....###..###....",
    "................",
    "................",
    "................",
    "................"]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "....##....##....",
    "....##....##....",
    "....##....##....",
    "...###....###...",
    "..####....####..",
    "..####....####..",
    "................",
    "................",
    "................"]

ARMOR_ICONS = {
    "cazador": ("cazador_casco", "cazador_peto", "cazador_grebas", "cazador_botas"),
    "archimago": ("archimago_capucha", "archimago_tunica", "archimago_faldones", "archimago_sandalias"),
    "caballero_negro": ("caballero_yelmo", "caballero_coraza", "caballero_quijotes", "caballero_escarpes"),
    "monarca": ("monarca_corona", "monarca_manto", "monarca_grebas", "monarca_botas"),
}


def armor_icons():
    pals = {"cazador": PAL_CAZADOR, "archimago": PAL_ARCHIMAGO, "caballero_negro": PAL_CABALLERO,
            "monarca": PAL_MONARCA}
    for set_name, names in ARMOR_ICONS.items():
        pal = pals[set_name]
        for mask, name, kind in zip((HELMET, CHESTPLATE, LEGGINGS, BOOTS), names, range(4)):
            t = Tex(16, 16)
            for y, row in enumerate(mask):
                for x, ch in enumerate(row):
                    if ch != "#":
                        continue
                    c = jitter(pal["main"], x, y, 60 + kind, 0.08)
                    t.put(x, y, c)
            # detalles
            for y in range(16):
                for x in range(16):
                    if t.get(x, y)[3] == 0:
                        continue
                    below = t.get(x, y + 1)[3] == 0
                    if kind == 0 and y == 3:
                        t.put(x, y, pal["trim"])
                    if kind == 1 and (y == 12 or (x in (7, 8) and 3 <= y <= 10)):
                        t.put(x, y, pal["trim"] if y == 12 else pal["accent"])
                    if kind == 2 and y == 2:
                        t.put(x, y, pal["trim"])
                    if kind == 3 and y == 7:
                        t.put(x, y, pal["trim"])
                    if below and kind in (2, 3):
                        t.put(x, y, mul(pal["main"], 0.7))
            if set_name == "monarca" and kind == 0:
                for x in (4, 6, 9, 11):
                    t.put(x, 2, pal["silver"])
                t.put(7, 1, pal["gem"])
                t.put(8, 1, pal["gem"])
                t.put(7, 2, pal["silver"])
                t.put(8, 2, pal["silver"])
            if set_name == "caballero_negro" and kind == 0:
                for x in range(5, 11):
                    t.put(x, 6, (12, 10, 14))
                t.put(6, 6, pal["eyes"])
                t.put(9, 6, pal["eyes"])
            if set_name == "monarca" and kind == 1:
                for (x, y) in ((5, 4), (6, 5), (9, 5), (10, 4), (7, 6), (8, 6)):
                    t.put(x, y, pal["accent"])
            t = shade_icon(t)
            outline(t, mul(pal["main"], 0.35))
            save_item(t, name)


# ====================================================================== modelos de objetos y traducciones


def models():
    d = os.path.join(A, "models/item")
    os.makedirs(d, exist_ok=True)

    def write(name, data):
        with open(os.path.join(d, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
            f.write("\n")

    for n in ("daga_asesino", "espada_caballero", "hacha_berserker", "guadana_monarca", "baculo_arcano",
              "hoja_juicio", "colmillo_eclipse", "aguijon_carmesi", "filo_monarca", "colmillos_monarca",
              "baculo_monarca", "martillo_monarca"):
        write(n, {"parent": "minecraft:item/handheld", "textures": {"layer0": "elsistema:item/" + n}})
    # Arco del Monarca: se tensa como el arco normal
    write("arco_monarca", {
        "parent": "minecraft:item/bow",
        "textures": {"layer0": "elsistema:item/arco_monarca"},
        "overrides": [
            {"predicate": {"pulling": 1}, "model": "elsistema:item/arco_monarca_pulling_0"},
            {"predicate": {"pulling": 1, "pull": 0.65}, "model": "elsistema:item/arco_monarca_pulling_1"},
            {"predicate": {"pulling": 1, "pull": 0.9}, "model": "elsistema:item/arco_monarca_pulling_2"}]})
    for i in range(3):
        write("arco_monarca_pulling_%d" % i, {"parent": "minecraft:item/bow",
                                                "textures": {"layer0": "elsistema:item/arco_monarca_pulling_%d" % i}})
    names = ["pocion_mana", "elixir_vida", "piedra_retorno", "velo_sombra", "corazon_primordial"]
    for v in ARMOR_ICONS.values():
        names.extend(v)
    for n in names:
        write(n, {"parent": "minecraft:item/generated", "textures": {"layer0": "elsistema:item/" + n}})
    for e in ("goblin", "lobo_acero", "oso_hielo", "orco", "hormiga", "caballero", "coloso",
              "guardian_juicio", "bestia_eclipse", "reina_carmesi", "coloso_primordial"):
        write(e + "_huevo", {"parent": "minecraft:item/template_spawn_egg"})


def locales():
    lang = os.path.join(A, "lang")
    with open(os.path.join(lang, "es_es.json"), encoding="utf-8") as f:
        es = f.read()
    for loc in ("es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve"):
        with open(os.path.join(lang, loc + ".json"), "w", encoding="utf-8") as f:
            f.write(es)


def main():
    monsters()
    sombra()
    armors()
    items()
    legendaries()
    sovereign()
    armor_icons()
    models()
    locales()
    ciudadanos.generate(os.path.join(A, "textures/entity/ciudadano"))
    pack_sistema.generate(os.path.join(ROOT, "src/main/resources/resourcepacks/sistema"))
    print("texturas generadas en", A)


if __name__ == "__main__":
    main()
