"""Genera las texturas, modelos de objetos y traducciones extra de El Sistema 1.2.0.

Solo usa la biblioteca estándar de Python (se ejecuta en GitHub Actions). Todo es determinista: el mismo
script siempre produce los mismos archivos.

Uso: python3 tools/texturas.py <carpeta del proyecto>
"""
import json
import os
import struct
import sys
import zlib

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


# ====================================================================== monstruos humanoides (64x64, como el zombi)

HEAD = (0, 0, 8, 8, 8)
HAT = (32, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
ARM = (40, 16, 4, 12, 4)
LEG = (0, 16, 4, 12, 4)


def humanoid(name, head, body, arm, leg, hat=None, seed=1):
    t = Tex(64, 64)
    paint_box(t, *HEAD, lambda f, x, y, w, h: head(f, x, y, w, h, seed))
    paint_box(t, *BODY, lambda f, x, y, w, h: body(f, x, y, w, h, seed + 1))
    paint_box(t, *ARM, lambda f, x, y, w, h: arm(f, x, y, w, h, seed + 2))
    paint_box(t, *LEG, lambda f, x, y, w, h: leg(f, x, y, w, h, seed + 3))
    if hat:
        paint_box(t, *HAT, lambda f, x, y, w, h: hat(f, x, y, w, h, seed + 4))
    t.save(os.path.join(A, "textures/entity", name + ".png"))


# ---------------------------------------------------------------- goblin

G_SKIN = (96, 146, 60)
G_DARK = (58, 96, 36)
G_LEATHER = (112, 72, 40)
G_LEATHER2 = (84, 52, 28)


def goblin_head(f, x, y, w, h, s):
    c = shade_face(G_SKIN, f, x, y, w, h, s, 0.12)
    if f == "front":
        if y == 2:
            c = mul(G_DARK, 0.9)
        if y == 3 and x in (1, 2, 5, 6):
            c = (20, 20, 10) if x in (2, 5) else (236, 206, 48)
        if 4 <= y <= 5 and x in (3, 4):
            c = mul(G_SKIN, 0.78)
        if y == 6 and 1 <= x <= 6:
            c = (48, 22, 22)
            if x in (2, 5):
                c = (236, 232, 210)
        if y == 7 and x in (2, 5):
            c = (220, 216, 196)
    if f in ("left", "right"):
        # oreja puntiaguda
        if 2 <= y <= 4 and 2 <= x <= 6 and (y - 2) <= (6 - x):
            c = mix(G_SKIN, (160, 200, 110), 0.35) if (x + y) % 3 else mul(G_DARK, 1.0)
    if f == "top" and noise(x, y, 77) > 0.85:
        c = mul(G_DARK, 0.9)
    return c


def goblin_body(f, x, y, w, h, s):
    if y >= 9:
        c = shade_face(G_LEATHER2, f, x, y, w, h, s, 0.16)
        if y == 11 and noise(x, 3, s) > 0.5:
            c = mul(c, 0.7)
        return c
    if 7 <= y <= 8:
        c = shade_face((60, 40, 24), f, x, y, w, h, s, 0.08)
        if f == "front" and x in (3, 4) and y == 7:
            c = (214, 176, 72)
        return c
    c = shade_face(G_LEATHER, f, x, y, w, h, s, 0.18)
    if f == "front" and 3 <= x <= 4:
        c = shade_face(G_SKIN, f, x, y, w, h, s, 0.10)
    if f in ("front", "back") and (x in (0, w - 1)) and y % 3 == 1:
        c = (180, 150, 80)
    return c


def goblin_arm(f, x, y, w, h, s):
    c = shade_face(G_SKIN, f, x, y, w, h, s, 0.12)
    if 7 <= y <= 8:
        c = shade_face(G_LEATHER2, f, x, y, w, h, s, 0.12)
    if y >= 10:
        c = mul(c, 0.82)
    return c


def goblin_leg(f, x, y, w, h, s):
    if y <= 3:
        return shade_face(G_LEATHER2, f, x, y, w, h, s, 0.16)
    if y >= 10:
        return shade_face((70, 50, 32), f, x, y, w, h, s, 0.12)
    return shade_face(G_SKIN, f, x, y, w, h, s, 0.12)


# ---------------------------------------------------------------- orco

O_SKIN = (98, 112, 70)
O_DARK = (62, 72, 44)
O_HAIR = (30, 26, 26)
O_IRON = (132, 136, 142)
O_LEATHER = (92, 60, 36)


def orc_head(f, x, y, w, h, s):
    c = shade_face(O_SKIN, f, x, y, w, h, s, 0.12)
    if f == "front":
        if y == 2:
            c = mul(O_DARK, 0.85)
        if y == 3 and x in (1, 2, 5, 6):
            c = (220, 46, 34) if x in (2, 5) else (120, 20, 16)
        if y == 4 and 2 <= x <= 5:
            c = mul(O_SKIN, 0.8)
        if y == 6 and 1 <= x <= 6:
            c = (40, 24, 20)
        if x in (1, 6) and y in (5, 6):
            c = (238, 232, 214)
        if y == 7 and 2 <= x <= 5:
            c = mul(O_DARK, 0.95)
    if f == "top" and 3 <= x <= 4:
        c = jitter(O_HAIR, x, y, s)
    if f == "back" and 3 <= x <= 4 and y <= 5:
        c = jitter(O_HAIR, x, y, s)
    if f in ("left", "right") and y == 4 and x in (3, 4):
        c = mul(O_SKIN, 0.75)
    return c


def orc_hat(f, x, y, w, h, s):
    if f == "top" and 3 <= x <= 4:
        return jitter(O_HAIR, x, y, s, 0.2)
    if f == "front" and 3 <= x <= 4 and y <= 1:
        return jitter(O_HAIR, x, y, s, 0.2)
    if f == "back" and 3 <= x <= 4 and y <= 6:
        return jitter(O_HAIR, x, y, s, 0.2)
    return None


def orc_body(f, x, y, w, h, s):
    c = shade_face(O_SKIN, f, x, y, w, h, s, 0.12)
    if f == "front":
        if y == 3 and 1 <= x <= 6 and x not in (3, 4):
            c = mul(O_SKIN, 0.82)
        if abs(x - y) <= 0 or abs(x - (y - 1)) <= 0:
            if y <= 7:
                c = jitter(O_LEATHER, x, y, s)
        if y == 5 and x in (5, 6):
            c = (150, 100, 90)
    if f == "back" and (abs((w - 1 - x) - y) == 0 or abs((w - 1 - x) - (y - 1)) == 0) and y <= 7:
        c = jitter(O_LEATHER, x, y, s)
    if 8 <= y <= 9:
        c = shade_face((54, 36, 24), f, x, y, w, h, s, 0.08)
        if f == "front" and 3 <= x <= 4:
            c = O_IRON
    if y >= 10:
        c = shade_face((124, 96, 64), f, x, y, w, h, s, 0.25)
    return c


def orc_arm(f, x, y, w, h, s):
    c = shade_face(O_SKIN, f, x, y, w, h, s, 0.12)
    if y <= 2 or f == "top":
        c = shade_face(O_IRON, f, x, y, w, h, s, 0.10)
        if (x + y) % 3 == 0 and f != "top":
            c = (190, 194, 200)
    if 8 <= y <= 9:
        c = shade_face(O_LEATHER, f, x, y, w, h, s, 0.12)
    return c


def orc_leg(f, x, y, w, h, s):
    c = shade_face((72, 52, 36), f, x, y, w, h, s, 0.14)
    if f == "front" and y == 5 and 1 <= x <= 2:
        c = (96, 72, 48)
    if y >= 9:
        c = shade_face((44, 34, 30), f, x, y, w, h, s, 0.10)
    return c


# ---------------------------------------------------------------- caballero maldito

K_STEEL = (40, 40, 50)
K_HI = (84, 84, 102)
K_RED = (150, 22, 34)
K_CAPE = (56, 18, 66)


def knight_head(f, x, y, w, h, s):
    c = shade_face(K_STEEL, f, x, y, w, h, s, 0.08)
    if f == "front":
        if 3 <= x <= 4:
            c = mul(K_HI, 0.95)
        if y == 3:
            c = (8, 6, 10)
            if x in (2, 5):
                c = (255, 72, 56)
            if x in (1, 6):
                c = (120, 24, 20)
        if y == 6 and x in (1, 3, 4, 6):
            c = (12, 10, 14)
    if f == "top" and 3 <= x <= 4:
        c = jitter(K_RED, x, y, s)
    if f in ("left", "right") and y == 4 and 2 <= x <= 5:
        c = mul(K_HI, 0.9)
    return c


def knight_hat(f, x, y, w, h, s):
    if f == "top" and 3 <= x <= 4:
        return jitter(mix(K_RED, (60, 0, 8), 0.3), x, y, s, 0.2)
    if f == "back" and 3 <= x <= 4 and y <= 3:
        return jitter(K_RED, x, y, s, 0.2)
    return None


def knight_body(f, x, y, w, h, s):
    if f == "back":
        c = jitter(K_CAPE, x, y, s, 0.12)
        if x % 3 == 1:
            c = mul(c, 0.78)
        if y == h - 1 and x % 2 == 0:
            c = mul(c, 0.6)
        return c
    c = shade_face(K_STEEL, f, x, y, w, h, s, 0.08)
    if f == "front":
        if y in (0, 4, 8):
            c = mul(K_HI, 0.9)
        if 3 <= x <= 4 and 2 <= y <= 6:
            c = K_RED
        if 2 <= x <= 5 and y == 3:
            c = K_RED
        if y == 7:
            c = mul(K_RED, 0.8)
        if y >= 9 and x % 4 == 0:
            c = mul(K_HI, 0.8)
    return c


def knight_arm(f, x, y, w, h, s):
    c = shade_face(K_STEEL, f, x, y, w, h, s, 0.08)
    if y <= 3 or f == "top":
        c = shade_face(K_HI, f, x, y, w, h, s, 0.08)
        if y == 3:
            c = K_RED
    if y in (6, 9):
        c = mul(K_HI, 0.85)
    if y >= 10:
        c = mul(c, 0.8)
    return c


def knight_leg(f, x, y, w, h, s):
    c = shade_face(K_STEEL, f, x, y, w, h, s, 0.08)
    if y in (4, 5) and f == "front":
        c = K_HI
    if y == 0:
        c = mul(K_RED, 0.8)
    if y >= 10:
        c = mul(K_HI, 0.75)
    return c


# ---------------------------------------------------------------- coloso de piedra

S_STONE = (122, 122, 128)
S_MOSS = (78, 110, 58)
S_RUNE = (95, 215, 255)


def stone(f, x, y, w, h, s, base=S_STONE):
    c = shade_face(base, f, x, y, w, h, s, 0.18)
    n = noise(x, y, s + 9)
    if n > 0.9:
        c = mul(c, 0.62)
    if f == "top" and noise(x, y, s + 3) > 0.55:
        c = jitter(S_MOSS, x, y, s, 0.2)
    return c


def colossus_head(f, x, y, w, h, s):
    c = stone(f, x, y, w, h, s)
    if f == "front":
        if y == 2:
            c = mul(S_STONE, 0.7)
        if y == 3 and x in (1, 2, 5, 6):
            c = S_RUNE if x in (2, 5) else mix(S_RUNE, (255, 255, 255), 0.5)
        if y == 6 and 2 <= x <= 5:
            c = (40, 40, 44)
    return c


def colossus_body(f, x, y, w, h, s):
    c = stone(f, x, y, w, h, s)
    if f == "front":
        if (x == 3 or x == 4) and 1 <= y <= 9:
            c = S_RUNE if y % 2 else mix(S_RUNE, (20, 60, 90), 0.4)
        if y == 4 and 1 <= x <= 6:
            c = mix(S_RUNE, (20, 60, 90), 0.3)
    if f == "back" and noise(x, y, 41) > 0.8:
        c = mul(c, 0.7)
    return c


def colossus_arm(f, x, y, w, h, s):
    c = stone(f, x, y, w, h, s)
    if f in ("front", "left", "right") and y in (3, 7) and 1 <= x <= 2:
        c = S_RUNE
    if y >= 10:
        c = mul(c, 0.8)
    return c


def colossus_leg(f, x, y, w, h, s):
    c = stone(f, x, y, w, h, s)
    if y >= 10:
        c = mul(c, 0.75)
    return c


# ====================================================================== lobo colmillo de acero (64x64)

W_FUR = (112, 118, 128)
W_DARK = (66, 70, 80)
W_LIGHT = (188, 196, 206)


def wolf_fur(f, x, y, w, h, s, base=W_FUR):
    c = shade_face(base, f, x, y, w, h, s, 0.14)
    if noise(x * 3, y, s + 5) > 0.82:
        c = mix(c, W_LIGHT, 0.45)
    return c


def lobo():
    t = Tex(64, 64)

    def body(f, x, y, w, h):
        c = wolf_fur(f, x, y, w, h, 11)
        if f == "top" and 3 <= x <= 4:
            c = jitter(W_DARK, x, y, 12)
        if f in ("left", "right") and y == 1 and x % 4 == 1:
            c = W_LIGHT
        return c

    def mane(f, x, y, w, h):
        c = wolf_fur(f, x, y, w, h, 13, mix(W_FUR, (70, 80, 110), 0.35))
        if (x + y) % 3 == 0:
            c = mul(c, 0.85)
        return c

    def head(f, x, y, w, h):
        c = wolf_fur(f, x, y, w, h, 14)
        if f == "front":
            if y == 2 and x in (1, 5):
                c = (255, 170, 40)
            if y == 2 and x in (2, 4):
                c = (140, 60, 10)
            if y == 1 and x in (1, 2, 4, 5):
                c = W_DARK
        if f == "top" and 2 <= x <= 4:
            c = jitter(W_DARK, x, y, 15)
        return c

    def snout(f, x, y, w, h):
        c = wolf_fur(f, x, y, w, h, 16, mix(W_FUR, W_LIGHT, 0.3))
        if f == "front":
            if y == 0 and 1 <= x <= 2:
                c = (24, 24, 28)
            if y == 2:
                c = (226, 232, 240) if x in (0, 3) else (60, 30, 34)
        if f in ("left", "right") and y == 2:
            c = (226, 232, 240) if x % 2 == 0 else (60, 30, 34)
        return c

    def ear(f, x, y, w, h):
        c = wolf_fur(f, x, y, w, h, 17, W_DARK)
        if f == "front" and y >= 1:
            c = (150, 96, 96)
        return c

    def leg(f, x, y, w, h):
        c = wolf_fur(f, x, y, w, h, 18)
        if y >= 7:
            c = mul(W_DARK, 0.9)
        if f == "front" and y == h - 1:
            c = W_LIGHT
        return c

    def tail(f, x, y, w, h):
        c = wolf_fur(f, x, y, w, h, 19)
        if y >= h - 3:
            c = mul(W_DARK, 0.85)
        return c

    paint_box(t, 0, 0, 8, 7, 16, body)
    paint_box(t, 0, 24, 10, 9, 7, mane)
    paint_box(t, 0, 41, 7, 7, 6, head)
    paint_box(t, 28, 41, 4, 3, 4, snout)
    paint_box(t, 44, 41, 2, 3, 1, ear)
    paint_box(t, 50, 41, 2, 3, 1, ear)
    paint_box(t, 48, 0, 3, 9, 3, leg)
    paint_box(t, 48, 13, 2, 9, 2, tail)
    t.save(os.path.join(A, "textures/entity/lobo_acero.png"))


# ====================================================================== oso de hielo (128x64)

B_FUR = (226, 236, 244)
B_SHADE = (170, 190, 212)
B_ICE = (120, 210, 255)


def oso():
    t = Tex(128, 64)

    def fur(f, x, y, w, h, s, base=B_FUR):
        c = shade_face(base, f, x, y, w, h, s, 0.06)
        if noise(x, y * 2, s) > 0.78:
            c = mix(c, B_SHADE, 0.5)
        return c

    def body(f, x, y, w, h):
        c = fur(f, x, y, w, h, 21)
        if f == "top":
            # cristales de hielo en el lomo
            if (x - 3) % 5 in (0, 1) and (y + x) % 7 < 3:
                c = mix(B_ICE, (255, 255, 255), 0.25 if (x + y) % 2 else 0.0)
        if f in ("left", "right") and y <= 1 and x % 6 in (2, 3):
            c = B_ICE
        return c

    def head(f, x, y, w, h):
        c = fur(f, x, y, w, h, 22)
        if f == "front":
            if y == 3 and x in (2, 7):
                c = (60, 200, 255)
            if y == 3 and x in (3, 6):
                c = (20, 60, 110)
            if y == 2 and x in (2, 3, 6, 7):
                c = B_SHADE
        if f == "top" and 3 <= x <= 6 and y <= 3:
            c = mix(B_ICE, B_FUR, 0.4)
        return c

    def snout(f, x, y, w, h):
        c = fur(f, x, y, w, h, 23, mix(B_FUR, B_SHADE, 0.3))
        if f == "front":
            if y == 0 and 2 <= x <= 3:
                c = (30, 34, 44)
            if y == 3 and x in (1, 4):
                c = (240, 250, 255)
            if y == 3 and x in (2, 3):
                c = (80, 40, 50)
        return c

    def ear(f, x, y, w, h):
        c = fur(f, x, y, w, h, 24, B_SHADE)
        if f == "front" and y >= 1 and x == 1:
            c = (150, 170, 196)
        return c

    def leg(f, x, y, w, h):
        c = fur(f, x, y, w, h, 25)
        if y >= 7:
            c = mul(B_SHADE, 0.9)
        if f == "front" and y == h - 1 and x % 2 == 0:
            c = (60, 70, 90)
        return c

    def tail(f, x, y, w, h):
        return fur(f, x, y, w, h, 26)

    paint_box(t, 0, 0, 14, 14, 24, body)
    paint_box(t, 0, 40, 10, 9, 8, head)
    paint_box(t, 38, 40, 6, 4, 4, snout)
    paint_box(t, 60, 40, 3, 3, 2, ear)
    paint_box(t, 72, 40, 3, 3, 2, ear)
    paint_box(t, 78, 0, 6, 9, 6, leg)
    paint_box(t, 78, 17, 3, 3, 3, tail)
    t.save(os.path.join(A, "textures/entity/oso_hielo.png"))


# ====================================================================== hormiga soldado (64x64)

H_CHITIN = (74, 34, 24)
H_DARK = (36, 16, 12)
H_RED = (170, 52, 28)


def hormiga():
    t = Tex(64, 64)

    def chitin(f, x, y, w, h, s, base=H_CHITIN):
        c = shade_face(base, f, x, y, w, h, s, 0.10)
        if f == "top" and noise(x, y, s) > 0.86:
            c = mix(c, (220, 140, 110), 0.5)
        return c

    def head(f, x, y, w, h):
        c = chitin(f, x, y, w, h, 31, H_RED)
        if f == "front":
            if y == 1 and x in (0, 1, 4, 5):
                c = (14, 10, 10) if x in (0, 5) else (60, 60, 70)
            if y == 2 and x in (0, 5):
                c = (14, 10, 10)
            if y >= 3 and 2 <= x <= 3:
                c = mul(H_RED, 0.7)
        if f in ("left", "right") and 1 <= y <= 2 and 1 <= x <= 3:
            c = (14, 10, 10) if (x + y) % 2 else (90, 90, 100)
        return c

    def mandible(f, x, y, w, h):
        c = chitin(f, x, y, w, h, 32, H_DARK)
        if f in ("top", "front") and (x == 0 or y == 0):
            c = (200, 170, 120)
        return c

    def antenna(f, x, y, w, h):
        return chitin(f, x, y, w, h, 33, H_DARK)

    def thorax(f, x, y, w, h):
        c = chitin(f, x, y, w, h, 34)
        if f == "top" and 2 <= x <= 4:
            c = mix(c, H_RED, 0.5)
        return c

    def abdomen(f, x, y, w, h):
        c = chitin(f, x, y, w, h, 35)
        band = y if f in ("left", "right", "front", "back") else x
        if f == "top" and y % 3 == 2:
            c = mul(H_DARK, 1.1)
        if f in ("left", "right") and x % 3 == 2:
            c = mul(H_DARK, 1.1)
        if f == "top" and 3 <= x <= 6 and y % 3 == 0:
            c = mix(c, H_RED, 0.6)
        return c

    def leg(f, x, y, w, h):
        c = chitin(f, x, y, w, h, 36, H_DARK)
        if f in ("front", "top", "back", "bottom") and x in (5, 6):
            c = mix(H_RED, H_DARK, 0.4)
        return c

    paint_box(t, 0, 0, 6, 5, 5, head)
    paint_box(t, 24, 0, 2, 1, 3, mandible)
    paint_box(t, 24, 5, 2, 1, 3, mandible)
    paint_box(t, 36, 0, 1, 4, 1, antenna)
    paint_box(t, 40, 0, 1, 4, 1, antenna)
    paint_box(t, 0, 12, 7, 5, 7, thorax)
    paint_box(t, 0, 26, 10, 8, 12, abdomen)
    paint_box(t, 36, 10, 12, 2, 2, leg)
    t.save(os.path.join(A, "textures/entity/hormiga.png"))


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

    for n in ("daga_asesino", "espada_caballero", "hacha_berserker", "guadana_monarca", "baculo_arcano"):
        write(n, {"parent": "minecraft:item/handheld", "textures": {"layer0": "elsistema:item/" + n}})
    names = ["pocion_mana", "elixir_vida", "piedra_retorno", "velo_sombra"]
    for v in ARMOR_ICONS.values():
        names.extend(v)
    for n in names:
        write(n, {"parent": "minecraft:item/generated", "textures": {"layer0": "elsistema:item/" + n}})
    for e in ("goblin", "lobo_acero", "oso_hielo", "orco", "hormiga", "caballero", "coloso"):
        write(e + "_huevo", {"parent": "minecraft:item/template_spawn_egg"})


def locales():
    lang = os.path.join(A, "lang")
    with open(os.path.join(lang, "es_es.json"), encoding="utf-8") as f:
        es = f.read()
    for loc in ("es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve"):
        with open(os.path.join(lang, loc + ".json"), "w", encoding="utf-8") as f:
            f.write(es)


def main():
    humanoid("goblin", goblin_head, goblin_body, goblin_arm, goblin_leg, seed=1)
    humanoid("orco", orc_head, orc_body, orc_arm, orc_leg, hat=orc_hat, seed=11)
    humanoid("caballero", knight_head, knight_body, knight_arm, knight_leg, hat=knight_hat, seed=21)
    humanoid("coloso", colossus_head, colossus_body, colossus_arm, colossus_leg, seed=31)
    lobo()
    oso()
    hormiga()
    sombra()
    armors()
    items()
    armor_icons()
    models()
    locales()
    print("texturas generadas en", A)


if __name__ == "__main__":
    main()
