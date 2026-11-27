"""Pack de texturas del Sistema (1.8.0): interfaz, barra rápida, inventario, cofres, sol, luna y los shaders de
los tonos de luz.

Lo llama tools/texturas.py y escribe en src/main/resources/resourcepacks/sistema (el mod lo registra como pack
integrado con Fabric API, así que sale en Opciones > Paquetes de recursos y se puede quitar). Solo usa la biblioteca
estándar de Python y es determinista.

Los tamaños y las rejillas de las texturas son los de Minecraft 1.21.1 (los huecos del inventario y de los cofres
están en las mismas posiciones que en el juego), pero todos los dibujos son propios.
"""
import json
import math
import os
import struct
import zlib

# ====================================================================== colores del Sistema

NAVY_DEEP = (4, 9, 17)
NAVY = (9, 19, 33)
NAVY_HI = (18, 42, 68)
CYAN = (95, 215, 255)
CYAN_MID = (52, 140, 190)
CYAN_DIM = (38, 92, 128)
CYAN_DARK = (22, 52, 76)
WHITE = (228, 249, 255)
OUTLINE = (6, 14, 24)


def clamp(v):
    return max(0, min(255, int(round(v))))


def lerp(a, b, t):
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def smooth(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


# ====================================================================== lienzo RGBA con mezcla


class Img:
    def __init__(self, w, h):
        self.w = w
        self.h = h
        self.p = [[0.0, 0.0, 0.0, 0.0] for _ in range(w * h)]

    def set(self, x, y, c, a=1.0):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y * self.w + x] = [float(c[0]), float(c[1]), float(c[2]), float(a)]

    def px(self, x, y, c, a=1.0):
        """Pinta encima (mezcla normal)."""
        if not (0 <= x < self.w and 0 <= y < self.h) or a <= 0:
            return
        d = self.p[y * self.w + x]
        da = d[3]
        oa = a + da * (1 - a)
        if oa <= 0:
            return
        for i in range(3):
            d[i] = (c[i] * a + d[i] * da * (1 - a)) / oa
        d[3] = oa

    def add(self, x, y, c, k=1.0):
        """Suma luz (para el sol y la luna, que el juego dibuja sumando)."""
        if 0 <= x < self.w and 0 <= y < self.h:
            d = self.p[y * self.w + x]
            for i in range(3):
                d[i] = min(255.0, d[i] + c[i] * k)
            d[3] = 1.0

    def get(self, x, y):
        return self.p[y * self.w + x]

    def rect(self, x0, y0, x1, y1, c, a=1.0):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.px(x, y, c, a)

    def fill(self, x0, y0, x1, y1, c, a=1.0):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.set(x, y, c, a)

    def hline(self, x0, x1, y, c, a=1.0):
        for x in range(x0, x1):
            self.px(x, y, c, a)

    def vline(self, x, y0, y1, c, a=1.0):
        for y in range(y0, y1):
            self.px(x, y, c, a)

    def border(self, x0, y0, x1, y1, c, a=1.0):
        """Borde de 1 px del rectángulo [x0, x1) x [y0, y1)."""
        self.hline(x0, x1, y0, c, a)
        self.hline(x0, x1, y1 - 1, c, a)
        self.vline(x0, y0 + 1, y1 - 1, c, a)
        self.vline(x1 - 1, y0 + 1, y1 - 1, c, a)

    def corners(self, x0, y0, x1, y1, n, c, a=1.0, t=1, ny=None):
        """Esquinas en L (las de las ventanas holográficas del Sistema)."""
        ny = n if ny is None else ny
        for k in range(t):
            self.hline(x0, x0 + n, y0 + k, c, a)
            self.hline(x1 - n, x1, y0 + k, c, a)
            self.hline(x0, x0 + n, y1 - 1 - k, c, a)
            self.hline(x1 - n, x1, y1 - 1 - k, c, a)
            self.vline(x0 + k, y0, y0 + ny, c, a)
            self.vline(x1 - 1 - k, y0, y0 + ny, c, a)
            self.vline(x0 + k, y1 - ny, y1, c, a)
            self.vline(x1 - 1 - k, y1 - ny, y1, c, a)

    def vgrad(self, x0, y0, x1, y1, top, bottom, a=1.0):
        h = max(1, y1 - y0 - 1)
        for y in range(y0, y1):
            c = lerp(top, bottom, (y - y0) / h)
            for x in range(x0, x1):
                self.px(x, y, c, a)

    def faded_line(self, x0, x1, y, c, a):
        """Línea horizontal que se desvanece hacia los lados."""
        mid = (x0 + x1 - 1) / 2.0
        half = max(1.0, (x1 - x0) / 2.0)
        for x in range(x0, x1):
            k = 1.0 - abs(x - mid) / half
            self.px(x, y, c, a * k ** 0.7)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                r, g, b, a = self.p[y * self.w + x]
                raw.extend((clamp(r), clamp(g), clamp(b), clamp(a * 255)))

        def chunk(tag, data):
            c = struct.pack(">I", len(data)) + tag + data
            return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        data = b"\x89PNG\r\n\x1a\n"
        data += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        data += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        data += chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(data)


def write_json(path, obj, indent=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def nine_slice(path, w, h, border):
    write_json(path + ".mcmeta", {"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}})


# ====================================================================== botones y controles


def button(kind):
    t = Img(200, 20)
    if kind == "disabled":
        t.fill(0, 0, 200, 20, (8, 12, 18), 0.86)
        t.border(0, 0, 200, 20, (34, 44, 56))
        return t
    hot = kind == "highlighted"
    top, bottom = ((24, 62, 96), (12, 32, 54)) if hot else ((16, 36, 58), (8, 18, 32))
    t.fill(1, 1, 199, 19, (0, 0, 0), 0)
    t.vgrad(1, 1, 199, 19, top, bottom, 0.93)
    for y in range(4, 18, 3):
        t.hline(1, 199, y, (255, 255, 255), 0.025)
    t.hline(1, 199, 1, (90, 170, 220) if hot else (36, 82, 118), 0.75)
    t.hline(1, 199, 18, (2, 6, 12), 0.7)
    t.border(0, 0, 200, 20, CYAN if hot else CYAN_DIM)
    if hot:
        t.border(1, 1, 199, 19, (60, 150, 210), 0.45)
    t.corners(0, 0, 200, 20, 4, WHITE if hot else CYAN, 1.0, 1, 3)
    return t


def slider(hot):
    t = Img(200, 20)
    t.fill(1, 1, 199, 19, (5, 11, 19), 0.9)
    t.hline(1, 199, 1, (20, 40, 60), 0.6)
    t.border(0, 0, 200, 20, (70, 160, 210) if hot else (30, 64, 90))
    return t


def slider_handle(hot):
    t = Img(8, 20)
    t.fill(1, 1, 7, 19, (60, 150, 200) if hot else (30, 88, 128))
    t.vline(3, 3, 17, WHITE if hot else (150, 225, 255), 0.8)
    t.vline(4, 3, 17, WHITE if hot else (150, 225, 255), 0.5)
    t.border(0, 0, 8, 20, WHITE if hot else CYAN)
    return t


def text_field(hot):
    t = Img(200, 20)
    t.fill(1, 1, 199, 19, (2, 6, 12), 0.93)
    t.border(0, 0, 200, 20, CYAN if hot else (40, 72, 100))
    return t


def checkbox(hot, selected):
    t = Img(20, 20)
    t.fill(1, 1, 19, 19, (16, 40, 64) if hot else (8, 18, 32), 0.94)
    t.border(0, 0, 20, 20, CYAN if hot else (40, 92, 128))
    t.corners(0, 0, 20, 20, 4, WHITE if hot else CYAN)
    if selected:
        c = WHITE if hot else CYAN
        pts = [(4, 10), (5, 11), (6, 12), (7, 13), (8, 14), (9, 13), (10, 12), (11, 11), (12, 10), (13, 9), (14, 8),
               (15, 7), (16, 6)]
        for x, y in pts:
            t.px(x, y, c)
            t.px(x, y - 1, c)
            t.px(x, y + 1, (10, 30, 50), 0.6)
    return t


def scroller(background):
    t = Img(6, 32)
    if background:
        t.fill(1, 1, 5, 31, (4, 8, 14), 0.8)
        t.border(0, 0, 6, 32, (18, 32, 46))
    else:
        t.fill(1, 1, 5, 31, (40, 120, 170))
        t.vline(2, 2, 30, (120, 210, 245), 0.6)
        t.border(0, 0, 6, 32, CYAN)
    return t


# ====================================================================== HUD


def hotbar():
    t = Img(182, 22)
    t.border(0, 0, 182, 22, OUTLINE)
    for m in range(9):
        x0 = 1 + 20 * m
        t.fill(x0 + 1, 2, x0 + 19, 20, (6, 13, 24), 0.74)
        t.border(x0, 1, x0 + 20, 21, (34, 78, 108))
        t.hline(x0, x0 + 20, 1, (52, 112, 150))
        # puntito de luz en la esquina de cada hueco
        t.px(x0 + 1, 2, (70, 150, 200), 0.6)
    t.corners(0, 0, 182, 22, 6, CYAN, 1.0, 1, 4)
    return t


def hotbar_selection():
    t = Img(24, 23)
    t.border(0, 0, 24, 23, (4, 10, 18), 0.75)
    t.border(1, 1, 23, 22, CYAN)
    t.border(2, 2, 22, 21, (60, 160, 215), 0.85)
    t.border(3, 3, 21, 20, (30, 90, 130), 0.5)
    t.rect(4, 4, 20, 19, CYAN, 0.10)
    t.corners(1, 1, 23, 22, 5, WHITE)
    return t


def offhand(right):
    t = Img(29, 24)
    x0 = 8 if right else 1
    t.border(x0 - 1, 1, x0 + 21, 23, OUTLINE)
    t.fill(x0 + 1, 3, x0 + 19, 21, (6, 13, 24), 0.74)
    t.border(x0, 2, x0 + 20, 22, (34, 78, 108))
    t.hline(x0, x0 + 20, 2, (52, 112, 150))
    t.corners(x0 - 1, 1, x0 + 21, 23, 5, CYAN, 1.0, 1, 4)
    return t


def xp_bar(progress):
    t = Img(182, 5)
    if not progress:
        t.fill(0, 0, 182, 5, (6, 14, 12), 0.92)
        t.fill(1, 1, 181, 4, (12, 32, 28), 0.88)
        t.border(0, 0, 182, 5, (16, 44, 36))
        for k in range(1, 10):
            t.vline(1 + 18 * k, 1, 4, (4, 12, 10))
        return t
    rows = [(26, 92, 64), (178, 255, 214), (96, 232, 164), (54, 176, 116), (22, 84, 58)]
    for y, c in enumerate(rows):
        for x in range(182):
            k = 0.88 + 0.12 * (x / 181.0)
            t.set(x, y, tuple(v * k for v in c))
    for k in range(1, 10):
        t.vline(1 + 18 * k, 1, 4, (24, 96, 62), 0.9)
    return t


def crosshair():
    t = Img(15, 15)
    w = (255, 255, 255)
    t.set(7, 7, w)
    for i in range(1, 5):
        t.set(7, i, w)
        t.set(7, 14 - i, w)
        t.set(i, 7, w)
        t.set(14 - i, 7, w)
    return t


# ====================================================================== fondos de los menús


def menu_background(alpha, grid):
    t = Img(16, 16)
    t.fill(0, 0, 16, 16, (5, 12, 24), alpha)
    for i in range(16):
        t.px(i, 0, CYAN, grid)
        if i > 0:
            t.px(0, i, CYAN, grid)
    return t


def separator(cyan_first):
    t = Img(32, 2)
    rows = [(CYAN, 0.42), ((0, 0, 0), 0.72)]
    if not cyan_first:
        rows.reverse()
    for y, (c, a) in enumerate(rows):
        for x in range(32):
            t.set(x, y, c, a)
    return t


# ====================================================================== inventario y cofres


def panel(t, w, h, top_corners=True, bottom_corners=True):
    """Ventana del Sistema opaca: fondo azul oscuro, brillo arriba, líneas de escaneo, borde y esquinas."""
    t.fill(0, 0, w, h, NAVY)
    t.vgrad(1, 1, w - 1, 16, (26, 60, 98), NAVY, 1.0)
    for y in range(2, h - 1, 3):
        t.hline(1, w - 1, y, (255, 255, 255), 0.018)
    t.border(0, 0, w, h, (46, 108, 146))
    t.border(1, 1, w - 1, h - 1, (14, 30, 48))
    if top_corners:
        for k in range(2):
            t.hline(0, 10, k, CYAN)
            t.hline(w - 10, w, k, CYAN)
            t.vline(k, 0, 10, CYAN)
            t.vline(w - 1 - k, 0, 10, CYAN)
    if bottom_corners:
        for k in range(2):
            t.hline(0, 10, h - 1 - k, CYAN)
            t.hline(w - 10, w, h - 1 - k, CYAN)
            t.vline(k, h - 10, h, CYAN)
            t.vline(w - 1 - k, h - 10, h, CYAN)


def slot(t, x, y, size=18):
    """Hueco de 18x18 cuyo objeto va en (x + 1, y + 1)."""
    t.fill(x, y, x + size, y + size, (3, 8, 15))
    t.hline(x, x + size, y, (22, 46, 68))
    t.vline(x, y, y + size, (22, 46, 68))
    t.hline(x, x + size, y + size - 1, (56, 108, 144))
    t.vline(x + size - 1, y, y + size, (56, 108, 144))
    t.px(x + size - 1, y, (34, 72, 100))
    t.px(x, y + size - 1, (34, 72, 100))


def inventory():
    t = Img(256, 256)
    panel(t, 176, 166)
    # armadura
    for i in range(4):
        slot(t, 7, 7 + 18 * i)
    # recuadro del jugador, con una plataforma holográfica
    t.fill(25, 7, 76, 78, (2, 5, 10))
    t.border(25, 7, 76, 78, (36, 74, 104))
    for y in range(8, 77):
        k = (y - 8) / 69.0
        t.hline(26, 75, y, (20, 60, 110), 0.10 * k * k)
    cx, cy = 50.5, 71.0
    for y in range(64, 78):
        for x in range(26, 75):
            d = math.hypot((x - cx) / 21.0, (y - cy) / 4.6)
            if d < 1.0:
                ring = 0.55 if 0.82 < d < 1.0 else 0.0
                t.px(x, y, CYAN, 0.16 * (1 - d) + ring * 0.5)
    t.corners(25, 7, 76, 78, 5, CYAN_MID)
    # mano izquierda
    slot(t, 76, 61)
    # fabricación 2x2, flecha y resultado
    for i in range(2):
        for j in range(2):
            slot(t, 97 + 18 * j, 17 + 18 * i)
    arrow(t, 135, 35)
    slot(t, 153, 27)
    t.corners(152, 26, 172, 46, 4, CYAN_MID)
    # inventario y barra rápida
    t.faded_line(8, 168, 80, CYAN, 0.45)
    for r in range(3):
        for c in range(9):
            slot(t, 7 + 18 * c, 83 + 18 * r)
    t.faded_line(8, 168, 139, CYAN, 0.25)
    for c in range(9):
        slot(t, 7 + 18 * c, 141)
    return t


def arrow(t, x0, cy):
    for x in range(x0, x0 + 11):
        for y in range(cy - 1, cy + 2):
            t.px(x, y, CYAN, 0.55 + 0.4 * (x - x0) / 10.0)
    for i in range(5):
        x = x0 + 11 + i
        for y in range(cy - (4 - i), cy + (4 - i) + 1):
            t.px(x, y, CYAN)
    t.px(x0 + 15, cy, WHITE)


def chest():
    """generic_54.png: arriba 17 + 18·filas píxeles; abajo, desde y = 126, 96 píxeles con el inventario."""
    t = Img(256, 256)
    panel(t, 176, 222)
    for r in range(6):
        for c in range(9):
            slot(t, 7 + 18 * c, 17 + 18 * r)
    # parte de abajo (y = 126...221)
    t.faded_line(8, 168, 127, CYAN, 0.45)
    for r in range(3):
        for c in range(9):
            slot(t, 7 + 18 * c, 139 + 18 * r)
    t.faded_line(8, 168, 195, CYAN, 0.25)
    for c in range(9):
        slot(t, 7 + 18 * c, 197)
    return t


# ====================================================================== sol y luna (el juego los suma al cielo)


def sun():
    t = Img(32, 32)
    t.fill(0, 0, 32, 32, (0, 0, 0))
    cx = cy = 15.5
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - cy)
            if d <= 5.6:
                rim = smooth(4.2, 5.6, d)
                c = lerp((255, 253, 236), (255, 214, 120), rim)
                t.set(x, y, c)
            else:
                g = math.exp(-(d - 5.6) / 3.3) * 0.85
                halo = math.exp(-((d - 9.0) ** 2) / 2.2) * 0.10
                t.add(x, y, (255, 178, 84), g)
                t.add(x, y, (90, 200, 255), halo)
    return t


def moon_phases():
    t = Img(128, 64)
    t.fill(0, 0, 128, 64, (0, 0, 0))
    angles = [0, 1, 2, 3, 4, 3, 2, 1]
    for p in range(8):
        ox, oy = (p % 4) * 32, (p // 4) * 32
        theta = angles[p] * math.pi / 4
        side = 0 if p == 0 or p == 4 else (-1 if p < 4 else 1)
        cx = ox + 15.5
        cy = oy + 15.5
        r = 5.4
        lit_amount = (1 + math.cos(theta)) / 2
        for y in range(oy, oy + 32):
            for x in range(ox, ox + 32):
                dx = (x - cx) / r
                dy = (y - cy) / r
                d = math.hypot(dx, dy)
                if d <= 1.0:
                    sx = dx if side >= 0 else -dx
                    limit = -math.cos(theta) * math.sqrt(max(0.0, 1 - dy * dy))
                    lit = p == 0 or (p != 4 and sx > limit)
                    if lit:
                        crater = 0.0
                        for (ux, uy, ur) in ((-0.35, -0.25, 0.28), (0.3, 0.35, 0.22), (0.15, -0.45, 0.16)):
                            if math.hypot(dx - ux, dy - uy) < ur:
                                crater = 0.22
                        c = lerp((222, 240, 255), (150, 186, 220), crater + 0.25 * smooth(0.6, 1.0, d))
                        t.set(x, y, c)
                    else:
                        t.set(x, y, (10, 20, 36))
                else:
                    g = math.exp(-(d * r - r) / 3.0) * (0.25 + 0.6 * lit_amount)
                    t.add(x, y, (64, 140, 235), g)
    return t


# ====================================================================== icono del pack


def pack_icon():
    s = 128
    t = Img(s, s)
    for y in range(s):
        for x in range(s):
            d = math.hypot(x - 63.5, y - 63.5) / 90.0
            t.set(x, y, lerp((16, 38, 66), NAVY_DEEP, min(1.0, d)))
    for i in range(0, s, 16):
        for k in range(s):
            t.px(i, k, CYAN, 0.05)
            t.px(k, i, CYAN, 0.05)
    for y in range(2, s, 3):
        t.hline(0, s, y, (255, 255, 255), 0.02)
    # resplandor y cristal
    for y in range(s):
        for x in range(s):
            d = math.hypot(x - 63.5, y - 62)
            t.px(x, y, (60, 170, 255), 0.42 * math.exp(-d / 22.0))
    top, mid, bot = 20, 58, 106
    for y in range(top, bot + 1):
        if y <= mid:
            half = 24 * (y - top) / (mid - top)
        else:
            half = 24 * (bot - y) / (bot - mid)
        for x in range(int(63.5 - half), int(63.5 + half) + 1):
            u = (x - 63.5) / max(1.0, half)
            if y <= mid:
                c = lerp((170, 236, 255), (70, 170, 230), (u + 1) / 2)
            else:
                c = lerp((40, 120, 190), (18, 60, 110), (u + 1) / 2)
            if abs(u) < 0.08:
                c = lerp(c, WHITE, 0.6)
            t.px(x, y, c)
    for y in range(top, bot + 1):
        half = 24 * ((y - top) / (mid - top) if y <= mid else (bot - y) / (bot - mid))
        t.px(int(63.5 - half), y, WHITE, 0.9)
        t.px(int(63.5 + half), y, (120, 220, 255), 0.9)
    t.hline(40, 88, mid, WHITE, 0.8)
    for (x, y) in ((70, 34), (52, 50), (76, 46)):
        t.px(x, y, WHITE)
        t.px(x + 1, y, WHITE, 0.5)
        t.px(x, y + 1, WHITE, 0.5)
    t.border(0, 0, s, s, CYAN_DIM)
    for k in range(3):
        t.hline(0, 22, k, CYAN)
        t.hline(s - 22, s, k, CYAN)
        t.hline(0, 22, s - 1 - k, CYAN)
        t.hline(s - 22, s, s - 1 - k, CYAN)
        t.vline(k, 0, 22, CYAN)
        t.vline(s - 1 - k, 0, 22, CYAN)
        t.vline(k, s - 22, s, CYAN)
        t.vline(s - 1 - k, s - 22, s, CYAN)
    return t


# ====================================================================== shaders de los tonos (post-procesado)

POST_VSH = """#version 150

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;

out vec2 texCoord;
out vec2 oneTexel;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
    oneTexel = 1.0 / InSize;
    texCoord = Position.xy / OutSize;
}
"""

BRIGHT_FSH = """#version 150

// El Sistema: se queda solo con lo que brilla (para el resplandor)
uniform sampler2D DiffuseSampler;
uniform float Threshold;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float k = smoothstep(Threshold, Threshold + 0.2, l);
    fragColor = vec4(c * k, 1.0);
}
"""

BLUR_FSH = """#version 150

// El Sistema: desenfoque gaussiano en una dirección
uniform sampler2D DiffuseSampler;
uniform vec2 BlurDir;
uniform float Radius;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec2 stride = oneTexel * BlurDir * (Radius / 4.0);
    vec3 sum = texture(DiffuseSampler, texCoord).rgb * 0.227027;
    sum += texture(DiffuseSampler, texCoord + stride).rgb * 0.1945946;
    sum += texture(DiffuseSampler, texCoord - stride).rgb * 0.1945946;
    sum += texture(DiffuseSampler, texCoord + stride * 2.0).rgb * 0.1216216;
    sum += texture(DiffuseSampler, texCoord - stride * 2.0).rgb * 0.1216216;
    sum += texture(DiffuseSampler, texCoord + stride * 3.0).rgb * 0.054054;
    sum += texture(DiffuseSampler, texCoord - stride * 3.0).rgb * 0.054054;
    sum += texture(DiffuseSampler, texCoord + stride * 4.0).rgb * 0.016216;
    sum += texture(DiffuseSampler, texCoord - stride * 4.0).rgb * 0.016216;
    fragColor = vec4(sum, 1.0);
}
"""

GRADE_COMMON = """
uniform float Intensity;
uniform float Vignette;
uniform float Saturation;
uniform float Cool;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

// Tonos del Sistema: las sombras tiran a azul, las luces se quedan algo cálidas, un poco más de color y de contraste
vec3 grade(vec3 c) {
    float l = dot(c, LUMA);
    float ws = 1.0 - smoothstep(0.0, 0.5, l);
    float wh = smoothstep(0.55, 1.0, l);
    c *= mix(vec3(1.0), vec3(0.80, 0.95, 1.22), ws * Cool);
    c *= mix(vec3(1.0), vec3(1.05, 1.0, 0.93), wh * 0.5);
    float g = dot(c, LUMA);
    c = mix(vec3(g), c, Saturation);
    c = clamp(c, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.30);
    return c;
}

float vignette() {
    vec2 d = texCoord - 0.5;
    return clamp(1.0 - dot(d, d) * Vignette, 0.0, 1.0);
}
"""

GRADE_FSH = """#version 150

// El Sistema: tonos de color + resplandor + viñeta
uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform float Bloom;
""" + GRADE_COMMON + """
void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    vec3 glow = texture(BloomSampler, texCoord).rgb;
    vec3 c = grade(base + glow * Bloom) * vignette();
    fragColor = vec4(mix(base, c, Intensity), 1.0);
}
"""

GRADE_LIGHT_FSH = """#version 150

// El Sistema (modo rendimiento): tonos de color + viñeta, sin resplandor
uniform sampler2D DiffuseSampler;
""" + GRADE_COMMON + """
void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    vec3 c = grade(base) * vignette();
    fragColor = vec4(mix(base, c, Intensity), 1.0);
}
"""

COPY_FSH = """#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(DiffuseSampler, texCoord).rgb, 1.0);
}
"""

IDENTITY = [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]


def program(fragment, samplers, extra, insize=False):
    uniforms = [{"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": IDENTITY}]
    if insize:
        uniforms.append({"name": "InSize", "type": "float", "count": 2, "values": [1.0, 1.0]})
    uniforms.append({"name": "OutSize", "type": "float", "count": 2, "values": [1.0, 1.0]})
    for name, values in extra:
        uniforms.append({"name": name, "type": "float", "count": len(values), "values": values})
    return {
        "blend": {"func": "add", "srcrgb": "one", "dstrgb": "zero"},
        "vertex": "elsistema_post",
        "fragment": fragment,
        "attributes": ["Position"],
        "samplers": [{"name": s} for s in samplers],
        "uniforms": uniforms,
    }


GRADE_UNIFORMS = [("Intensity", [1.0]), ("Vignette", [0.9]), ("Saturation", [1.12]), ("Cool", [0.55])]

# Intensidad del shader (ajuste del mod): suave, normal, intenso
LEVELS = {
    "suave": {"Intensity": 0.6, "Bloom": 0.30, "Vignette": 0.55, "Saturation": 1.06, "Cool": 0.30, "Radius": 8.0},
    "normal": {"Intensity": 0.9, "Bloom": 0.45, "Vignette": 0.90, "Saturation": 1.12, "Cool": 0.45, "Radius": 10.0},
    "intenso": {"Intensity": 1.0, "Bloom": 0.70, "Vignette": 1.25, "Saturation": 1.20, "Cool": 0.60, "Radius": 12.0},
}


def chain(level, light):
    v = LEVELS[level]
    grade_uniforms = [{"name": k, "values": [v[k]]} for k in ("Intensity", "Vignette", "Saturation", "Cool")]
    if light:
        return {
            "targets": ["swap"],
            "passes": [
                {"name": "elsistema_grade_ligero", "intarget": "minecraft:main", "outtarget": "swap",
                 "uniforms": grade_uniforms},
                {"name": "elsistema_copy", "intarget": "swap", "outtarget": "minecraft:main"},
            ],
        }
    return {
        "targets": ["swap", "brillo", "brillo2"],
        "passes": [
            {"name": "elsistema_bright", "intarget": "minecraft:main", "outtarget": "brillo",
             "uniforms": [{"name": "Threshold", "values": [0.80]}]},
            {"name": "elsistema_blur", "intarget": "brillo", "outtarget": "brillo2", "use_linear_filter": True,
             "uniforms": [{"name": "BlurDir", "values": [1.0, 0.0]}, {"name": "Radius", "values": [v["Radius"]]}]},
            {"name": "elsistema_blur", "intarget": "brillo2", "outtarget": "brillo", "use_linear_filter": True,
             "uniforms": [{"name": "BlurDir", "values": [0.0, 1.0]}, {"name": "Radius", "values": [v["Radius"]]}]},
            {"name": "elsistema_grade", "intarget": "minecraft:main", "outtarget": "swap",
             "auxtargets": [{"name": "BloomSampler", "id": "brillo"}],
             "uniforms": grade_uniforms + [{"name": "Bloom", "values": [v["Bloom"]]}]},
            {"name": "elsistema_copy", "intarget": "swap", "outtarget": "minecraft:main"},
        ],
    }


def shaders(root):
    prog = os.path.join(root, "assets/minecraft/shaders/program")
    write_text(os.path.join(prog, "elsistema_post.vsh"), POST_VSH)
    write_text(os.path.join(prog, "elsistema_bright.fsh"), BRIGHT_FSH)
    write_text(os.path.join(prog, "elsistema_blur.fsh"), BLUR_FSH)
    write_text(os.path.join(prog, "elsistema_grade.fsh"), GRADE_FSH)
    write_text(os.path.join(prog, "elsistema_grade_ligero.fsh"), GRADE_LIGHT_FSH)
    write_text(os.path.join(prog, "elsistema_copy.fsh"), COPY_FSH)
    write_json(os.path.join(prog, "elsistema_bright.json"),
               program("elsistema_bright", ["DiffuseSampler"], [("Threshold", [0.8])]), 2)
    write_json(os.path.join(prog, "elsistema_blur.json"),
               program("elsistema_blur", ["DiffuseSampler"], [("BlurDir", [1.0, 0.0]), ("Radius", [10.0])], True), 2)
    write_json(os.path.join(prog, "elsistema_grade.json"),
               program("elsistema_grade", ["DiffuseSampler", "BloomSampler"], GRADE_UNIFORMS + [("Bloom", [0.6])]), 2)
    write_json(os.path.join(prog, "elsistema_grade_ligero.json"),
               program("elsistema_grade_ligero", ["DiffuseSampler"], GRADE_UNIFORMS), 2)
    write_json(os.path.join(prog, "elsistema_copy.json"), program("elsistema_copy", ["DiffuseSampler"], []), 2)
    post = os.path.join(root, "assets/elsistema/shaders/post")
    for level in LEVELS:
        write_json(os.path.join(post, "tonos_%s.json" % level), chain(level, False), 2)
        write_json(os.path.join(post, "tonos_%s_ligero.json" % level), chain(level, True), 2)


# ====================================================================== todo el pack


def generate(root):
    write_json(os.path.join(root, "pack.mcmeta"), {"pack": {
        "pack_format": 34,
        "description": "§bEl Sistema§7: interfaz, luz y shaders del Sistema"}}, 2)
    pack_icon().save(os.path.join(root, "pack.png"))

    w = os.path.join(root, "assets/minecraft/textures/gui/sprites/widget")
    for kind in ("normal", "highlighted"):
        name = "button" if kind == "normal" else "button_highlighted"
        button(kind).save(os.path.join(w, name + ".png"))
        nine_slice(os.path.join(w, name + ".png"), 200, 20, {"left": 4, "top": 3, "right": 4, "bottom": 3})
    button("disabled").save(os.path.join(w, "button_disabled.png"))
    nine_slice(os.path.join(w, "button_disabled.png"), 200, 20, 1)
    for hot in (False, True):
        sfx = "_highlighted" if hot else ""
        slider(hot).save(os.path.join(w, "slider%s.png" % sfx))
        nine_slice(os.path.join(w, "slider%s.png" % sfx), 200, 20, 1)
        slider_handle(hot).save(os.path.join(w, "slider_handle%s.png" % sfx))
        nine_slice(os.path.join(w, "slider_handle%s.png" % sfx), 8, 20, {"left": 2, "top": 2, "right": 2, "bottom": 3})
        text_field(hot).save(os.path.join(w, "text_field%s.png" % sfx))
        nine_slice(os.path.join(w, "text_field%s.png" % sfx), 200, 20, 1)
        for sel in (False, True):
            name = "checkbox" + ("_selected" if sel else "") + sfx
            checkbox(hot, sel).save(os.path.join(w, name + ".png"))
    scroller(False).save(os.path.join(w, "scroller.png"))
    nine_slice(os.path.join(w, "scroller.png"), 6, 32, 1)
    scroller(True).save(os.path.join(w, "scroller_background.png"))
    nine_slice(os.path.join(w, "scroller_background.png"), 6, 32, 1)

    h = os.path.join(root, "assets/minecraft/textures/gui/sprites/hud")
    hotbar().save(os.path.join(h, "hotbar.png"))
    hotbar_selection().save(os.path.join(h, "hotbar_selection.png"))
    offhand(False).save(os.path.join(h, "hotbar_offhand_left.png"))
    offhand(True).save(os.path.join(h, "hotbar_offhand_right.png"))
    xp_bar(False).save(os.path.join(h, "experience_bar_background.png"))
    xp_bar(True).save(os.path.join(h, "experience_bar_progress.png"))
    crosshair().save(os.path.join(h, "crosshair.png"))

    g = os.path.join(root, "assets/minecraft/textures/gui")
    menu_background(0.36, 0.07).save(os.path.join(g, "menu_background.png"))
    menu_background(0.30, 0.06).save(os.path.join(g, "inworld_menu_background.png"))
    menu_background(0.52, 0.06).save(os.path.join(g, "menu_list_background.png"))
    menu_background(0.46, 0.05).save(os.path.join(g, "inworld_menu_list_background.png"))
    for prefix in ("", "inworld_"):
        separator(True).save(os.path.join(g, prefix + "header_separator.png"))
        separator(False).save(os.path.join(g, prefix + "footer_separator.png"))
    inventory().save(os.path.join(g, "container/inventory.png"))
    chest().save(os.path.join(g, "container/generic_54.png"))

    e = os.path.join(root, "assets/minecraft/textures/environment")
    sun().save(os.path.join(e, "sun.png"))
    moon_phases().save(os.path.join(e, "moon_phases.png"))

    shaders(root)

    # Marca del pack y ajustes de los tonos de luz (los lee client/Tonos.java)
    write_json(os.path.join(root, "assets/elsistema/sistema_pack.json"), {
        "version": 1,
        "etiquetas": "A8E6FF",
        "luz": {
            "calida": [1.24, 0.95, 0.64],
            "noche": [0.70, 0.92, 1.32],
            "sombra": [0.70, 0.84, 1.40],
            "fuerza": 1.0,
        },
    }, 2)


if __name__ == "__main__":
    import sys
    generate(sys.argv[1] if len(sys.argv) > 1 else "pack_sistema")
