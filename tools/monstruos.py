"""Texturas de los monstruos de El Sistema (desde la 1.5.0): diseños propios, a doble resolución, pintados
pieza a pieza sobre los modelos de modelos.py. Solo biblioteca estándar y totalmente determinista.

Cada pintor recibe (cara, x, y, ancho, alto, caja) en píxeles de esa cara y devuelve un color (r, g, b),
(r, g, b, a) o None (transparente). Las caras: top, bottom, front, back, right (lado derecho del monstruo) y
left. En front, x crece hacia el lado izquierdo del monstruo (lo que ve quien lo mira de frente) e y hacia abajo.
"""
import math

import modelos

D = modelos.DENSITY


# ====================================================================== ruido y color


def h2(x, y, s=0):
    n = (int(x) * 374761393 + int(y) * 668265263 + int(s) * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, s, sx, sy=None):
    sy = sy or sx
    fx, fy = x / sx, y / sy
    ix, iy = math.floor(fx), math.floor(fy)
    tx, ty = fx - ix, fy - iy
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    a = h2(ix, iy, s)
    b = h2(ix + 1, iy, s)
    c = h2(ix, iy + 1, s)
    d = h2(ix + 1, iy + 1, s)
    return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty


def fbm(x, y, s, scale=8.0, octaves=3, stretch=1.0):
    tot = 0.0
    amp = 0.5
    norm = 0.0
    sc = scale
    for o in range(octaves):
        tot += amp * vnoise(x, y, s + o * 101, sc, sc * stretch)
        norm += amp
        amp *= 0.5
        sc = max(1.0, sc / 2.0)
    return tot / norm


def clamp(v):
    return max(0, min(255, int(round(v))))


def mul(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def add(c, k):
    return tuple(clamp(v + k) for v in c[:3])


def mix(c, d, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a + (b - a) * t) for a, b in zip(c[:3], d[:3]))


FACE = {"top": 1.10, "bottom": 0.72, "front": 1.0, "back": 0.88, "right": 0.93, "left": 0.93}


def ao(x, y, W, H, k=0.80, px=1):
    """Oscurece el borde de cada cara (da volumen a las cajas)."""
    if x < px or y < px or x >= W - px or y >= H - px:
        return k
    return 1.0


def shaded(c, f, x, y, W, H, edge=0.82):
    c = mul(c, FACE[f])
    return mul(c, ao(x, y, W, H, edge))


def vgrad(y, H, top=1.08, bottom=0.88):
    if H <= 1:
        return 1.0
    return top + (bottom - top) * (y / (H - 1))


def in_rect(x, y, x0, y0, w, h):
    return x0 <= x < x0 + w and y0 <= y < y0 + h


def in_ellipse(x, y, cx, cy, rx, ry):
    return ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0


# ====================================================================== materiales


def skin(base, f, x, y, W, H, s, mottle=(0.9, 1.1), spots=None, edge=0.84):
    n = fbm(x, y, s, 6.0, 3)
    c = mul(base, mottle[0] + (mottle[1] - mottle[0]) * n)
    if spots and fbm(x + 50, y + 30, s + 7, 3.0, 2) > 0.72:
        c = mix(c, spots, 0.55)
    c = mul(c, vgrad(y, H, 1.06, 0.92) if f not in ("top", "bottom") else 1.0)
    return shaded(c, f, x, y, W, H, edge)


def fur(base, dark, light, f, x, y, W, H, s, vertical=True, edge=0.86):
    if vertical:
        n = vnoise(x, y + 5 * h2(x // 2, 0, s), s, 2.0, 5.0)
        m = fbm(x, y, s + 3, 7.0, 2)
    else:
        n = vnoise(x + 5 * h2(0, y // 2, s), y, s, 5.0, 2.0)
        m = fbm(x, y, s + 3, 7.0, 2)
    c = mix(dark, base, 0.45 + 0.55 * m)
    if n > 0.72:
        c = mix(c, light, (n - 0.72) * 1.8)
    elif n < 0.18:
        c = mix(c, dark, (0.18 - n) * 1.8)
    return shaded(c, f, x, y, W, H, edge)


def metal(base, f, x, y, W, H, s, hi=None, bevel=True, scratch=0.10, edge=0.70):
    hi = hi or add(base, 60)
    n = vnoise(x, y, s, 6.0, 1.0)
    c = mul(base, 0.93 + 0.12 * n)
    if h2(x * 3 + 1, y, s) > 1.0 - scratch * 0.2:
        c = add(c, 26)
    if bevel and f != "bottom":
        if y < 2 or (f in ("front", "back") and x < 1):
            c = mix(c, hi, 0.55)
        elif y >= H - 2:
            c = mul(c, 0.72)
    return shaded(c, f, x, y, W, H, edge)


def stone(base, f, x, y, W, H, s, seam=0.62, crack=True, edge=0.80):
    cell = 7.0
    gx, gy = x / cell, y / cell
    ix, iy = math.floor(gx), math.floor(gy)
    best = 9.0
    second = 9.0
    best_id = (0, 0)
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            cx = ix + ox + 0.15 + 0.7 * h2(ix + ox, iy + oy, s)
            cy = iy + oy + 0.15 + 0.7 * h2(ix + ox, iy + oy, s + 9)
            dd = (gx - cx) ** 2 + (gy - cy) ** 2
            if dd < best:
                second = best
                best = dd
                best_id = (ix + ox, iy + oy)
            elif dd < second:
                second = dd
    border = math.sqrt(second) - math.sqrt(best)
    tone = 0.84 + 0.30 * h2(best_id[0], best_id[1], s + 4)
    c = mul(base, tone * (0.92 + 0.16 * fbm(x, y, s + 2, 3.0, 2)))
    if border < 0.10:
        c = mul(c, seam)
    elif border < 0.20:
        c = mul(c, 0.86)
    if crack and fbm(x, y, s + 11, 5.0, 3) > 0.60 and abs(vnoise(x, y, s + 12, 3.0) - 0.5) < 0.05:
        c = mul(c, 0.58)
    return shaded(c, f, x, y, W, H, edge)


def chitin(base, dark, hi, f, x, y, W, H, s, bands=0, edge=0.78):
    t = y / max(1, H - 1)
    c = mix(base, dark, 0.15 + 0.55 * t if f not in ("top",) else 0.10)
    # Brillo: una franja diagonal clara
    if f in ("top", "front", "right", "left", "back"):
        g = (x * 0.6 + y) % (W * 0.6 + H * 0.5 + 6)
        if 2 <= g < 4.5 and f != "bottom":
            c = mix(c, hi, 0.45)
    if bands and f != "bottom":
        period = max(3, H // bands) if f != "top" else max(3, W // bands)
        coord = y if f != "top" else x
        if coord % period == period - 1:
            c = mul(c, 0.62)
        elif coord % period == 0:
            c = mix(c, hi, 0.18)
    c = mul(c, 0.94 + 0.12 * fbm(x, y, s, 4.0, 2))
    return shaded(c, f, x, y, W, H, edge)


def leather(base, f, x, y, W, H, s, stitch=None, edge=0.78):
    c = mul(base, 0.88 + 0.24 * fbm(x, y, s, 4.0, 3))
    if stitch and f in ("front", "back") and (y == 1 or y == H - 2) and x % 3 == 1:
        c = stitch
    return shaded(c, f, x, y, W, H, edge)


def cloth(base, f, x, y, W, H, s, torn=False, edge=0.84):
    if torn and f in ("front", "back", "right", "left"):
        cut = H - 1 - int(3 * vnoise(x, 0, s + 5, 2.0))
        if y > cut:
            return None
    weave = 0.96 if (x + y) % 2 == 0 else 1.04
    fold = 0.90 + 0.12 * math.sin(x * 0.9 + 2.0 * h2(0, 0, s))
    c = mul(base, weave * fold * (0.92 + 0.16 * fbm(x, y, s, 5.0, 2)))
    return shaded(c, f, x, y, W, H, edge)


def glow_line(x, y, s, thresh=0.6, width=0.03, scale=6.0):
    """Líneas finas que serpentean (runas, grietas de luz, magma): cuanto más alto thresh, menos líneas."""
    n = vnoise(x, y, s, scale)
    return abs(n - 0.5) < width and fbm(x, y, s + 3, 9.0, 2) > thresh


# ====================================================================== GOBLIN

G_SKIN = (108, 150, 54)
G_SHADE = (62, 98, 34)
G_LIGHT = (150, 186, 78)
G_LEATHER = (112, 70, 38)
G_LEATHER_D = (70, 42, 22)
G_CLOTH = (126, 96, 58)
G_BONE = (226, 216, 184)


def goblin_skin(f, x, y, W, H, s):
    col = skin(G_SKIN, f, x, y, W, H, s, (0.80, 1.16), spots=(74, 106, 36), edge=0.78)
    if fbm(x + 13, y + 7, s + 3, 2.0, 2) > 0.80:
        col = mix(col, (88, 74, 44), 0.35)  # suciedad
    return col


def goblin_head(f, x, y, W, H, c):
    col = goblin_skin(f, x, y, W, H, 3)
    if f == "top":
        # Pelos sueltos y verrugas
        if h2(x, y, 41) > 0.93:
            return (40, 34, 24)
        return col
    if f != "front":
        return col
    # Cara de 18x16 píxeles
    if 4 <= y <= 6 and (2 <= x <= 7 or 10 <= x <= 15):
        col = mul(col, 0.72)  # cuencas
    for ex in (3, 11):
        if in_rect(x, y, ex, 5, 4, 3):
            if y == 5:
                return (40, 30, 18)
            iris = (252, 206, 40) if (x - ex) in (1, 2) else (232, 160, 20)
            if x - ex == (2 if ex == 3 else 1):
                return (14, 10, 8)  # pupila
            return iris
    # Boca ancha con dientes torcidos
    if 11 <= y <= 13 and 2 <= x <= 15:
        if y == 11 and x % 2 == 0 and x not in (2, 15):
            return G_BONE
        if y == 13 and x % 3 == 1 and 4 <= x <= 13:
            return mul(G_BONE, 0.9)
        if y == 12 or (y == 11 and x % 2 == 1) or (y == 13 and x % 3 != 1):
            return (64, 16, 14)
    if y == 10 and 3 <= x <= 14:
        return mul(col, 0.8)
    if y == 15 and 5 <= x <= 12:
        return mul(col, 0.88)
    return col


def goblin_nose(f, x, y, W, H, c):
    col = goblin_skin(f, x, y, W, H, 5)
    if f == "front" and y == H - 2 and x in (0, W - 1):
        return (40, 52, 20)
    if f == "bottom":
        return mul(col, 0.7)
    return col


def goblin_brow(f, x, y, W, H, c):
    return mul(goblin_skin(f, x, y, W, H, 9), 0.74)


def goblin_ear(f, x, y, W, H, c):
    col = goblin_skin(f, x, y, W, H, 7)
    if f in ("front", "back"):
        # Oreja puntiaguda: se estrecha hacia la punta (la punta está a la izquierda por delante y a la derecha por detrás)
        t = x / (W - 1.0) if f == "front" else 1.0 - x / (W - 1.0)
        half = 0.5 * H * (0.22 + 0.78 * t)
        if abs(y + 0.5 - H / 2.0) > half:
            return None
        if abs(y + 0.5 - H / 2.0) < half - 1.2 and 0.25 < t < 0.9:
            col = mix(col, (172, 100, 78), 0.55)  # interior
        return col
    if f in ("top", "bottom"):
        return col if x >= W * 0.72 else None
    if f == "right":
        return col if H // 2 - 1 <= y <= H // 2 else None
    return col


def goblin_body(f, x, y, W, H, c):
    col = goblin_skin(f, x, y, W, H, 11)
    if f in ("top", "bottom"):
        return col
    # Costillas a los lados y en el pecho
    if f == "front":
        if 6 <= y <= 12 and (y % 3 == 0) and (1 <= x <= 4 or W - 5 <= x <= W - 2):
            col = mul(col, 0.8)
        # Correa de cuero cruzada del hombro derecho a la cadera izquierda
        d = x - y * (W - 2) / (H - 2.0)
        if -1.5 <= d - 1 <= 1.5:
            return leather(G_LEATHER, f, x, y, W, H, 13)
        # Colgante de hueso
        if in_rect(x, y, W // 2 - 1, 7, 2, 3):
            return G_BONE if y < 9 else mul(G_BONE, 0.8)
    if f == "back":
        d = (W - 1 - x) - y * (W - 2) / (H - 2.0)
        if -1.5 <= d - 1 <= 1.5:
            return leather(G_LEATHER, f, x, y, W, H, 13)
    return col


def goblin_belt(f, x, y, W, H, c):
    col = leather(G_LEATHER_D, f, x, y, W, H, 17)
    if f == "front" and W // 2 - 2 <= x <= W // 2 + 1 and 0 <= y <= H - 1:
        return G_BONE if (x + y) % 3 else mul(G_BONE, 0.75)
    return col


def goblin_flap(f, x, y, W, H, c):
    return cloth(G_CLOTH, f, x, y, W, H, 19, torn=True)


def goblin_arm(f, x, y, W, H, c):
    col = goblin_skin(f, x, y, W, H, 23)
    if f == "bottom":
        return mul((60, 86, 30), 1.0)
    if y >= H - 4 and f != "top":
        col = mul(col, 0.86)  # mano
        if y == H - 1 and x % 2 == 0:
            return (40, 34, 22)  # uñas
    return col


def goblin_bracer(f, x, y, W, H, c):
    col = leather(G_LEATHER, f, x, y, W, H, 29)
    if y % 2 == 0 and f not in ("top", "bottom"):
        col = mul(col, 0.85)
    return col


def goblin_leg(f, x, y, W, H, c):
    if y < 6 and f != "bottom":
        # Pantalón corto rasgado
        col = cloth(G_LEATHER_D, f, x, y, W, H, 31)
        if y == 5 and h2(x, y, 31) > 0.5:
            return goblin_skin(f, x, y, W, H, 37)
        return col
    return goblin_skin(f, x, y, W, H, 37)


def goblin_foot(f, x, y, W, H, c):
    col = mul(goblin_skin(f, x, y, W, H, 41), 0.88)
    if f == "front" and y == H - 1 and x % 2 == 1:
        return (36, 30, 20)
    return col


GOBLIN = {
    "g_head": goblin_head, "g_nose": goblin_nose, "g_brow": goblin_brow, "g_ear": goblin_ear,
    "g_body": goblin_body, "g_belt": goblin_belt, "g_flap": goblin_flap, "g_arm": goblin_arm,
    "g_bracer": goblin_bracer, "g_leg": goblin_leg, "g_foot": goblin_foot,
}


# ====================================================================== ORCO

O_SKIN = (92, 112, 70)
O_SPOT = (66, 84, 48)
O_IRON = (78, 80, 88)
O_RUST = (138, 74, 40)
O_LEATHER = (92, 60, 36)
O_FUR = (108, 82, 54)
O_TUSK = (234, 224, 192)


def orc_skin(f, x, y, W, H, s):
    col = skin(O_SKIN, f, x, y, W, H, s, (0.84, 1.10), spots=O_SPOT)
    # Cicatrices claras
    if f in ("front", "back", "right", "left") and abs((x - y * 0.8) % 23 - 11) < 0.6 and fbm(x, y, s + 5, 6.0) > 0.55:
        col = mix(col, (170, 150, 120), 0.6)
    return col


def orc_head(f, x, y, W, H, c):
    col = orc_skin(f, x, y, W, H, 51)
    if f == "top":
        # Cabeza rapada con una franja de pelo hacia la coleta
        if 6 <= x <= 9:
            return fur((40, 32, 28), (20, 16, 14), (70, 58, 50), f, x, y, W, H, 52)
        return mul(col, 0.92)
    if f != "front":
        return col
    # Cara de 16x16: frente baja, ojos rojos hundidos, pintura de guerra
    if 5 <= y <= 7:
        col = mul(col, 0.62)
        for ex in (2, 10):
            if in_rect(x, y, ex, 6, 4, 2):
                return (255, 96, 40) if y == 6 and (x - ex) in (1, 2) else (170, 40, 20)
    if 8 <= y <= 13 and (x in (3, 4) or x in (11, 12)) and y % 2 == 0:
        return (150, 36, 28)  # pintura roja bajo los ojos
    if y == 9 and 6 <= x <= 9:
        return mul(col, 0.7)  # nariz chata
    if 12 <= y <= 13 and 3 <= x <= 12:
        return (40, 20, 16)
    return col


def orc_brow(f, x, y, W, H, c):
    return mul(orc_skin(f, x, y, W, H, 53), 0.70)


def orc_jaw(f, x, y, W, H, c):
    col = mul(orc_skin(f, x, y, W, H, 55), 0.92)
    if f == "front" and y <= 1 and x % 3 != 0:
        return O_TUSK if y == 0 else mul(O_TUSK, 0.8)
    if f == "top":
        return (44, 20, 16)
    return col


def orc_tusk(f, x, y, W, H, c):
    col = mul(O_TUSK, 0.92 + 0.1 * vgrad(y, H, 1.1, 0.8))
    if y <= 1:
        col = mix(col, (255, 250, 236), 0.4)
    return shaded(col, f, x, y, W, H, 0.85)


def orc_knot(f, x, y, W, H, c):
    col = fur((44, 34, 30), (18, 14, 12), (82, 70, 60), f, x, y, W, H, 57)
    if f in ("front", "back", "right", "left") and y in (H - 2, H - 1):
        return metal((176, 120, 54), f, x, y, W, H, 58, bevel=False)
    return col


def orc_ear(f, x, y, W, H, c):
    col = orc_skin(f, x, y, W, H, 59)
    if f in ("front", "back") and y == 1:
        return (200, 170, 90)  # aro
    return col


def orc_body(f, x, y, W, H, c):
    col = orc_skin(f, x, y, W, H, 61)
    if f in ("front", "back") and f != "top":
        # Músculos del pecho
        if f == "front" and (y == 8 and 2 <= x <= W - 3 or x == W // 2 and 2 <= y <= 14):
            col = mul(col, 0.82)
    return col


def orc_strap(f, x, y, W, H, c):
    # Arnés de cuero en X con una placa de hierro en el centro
    if f not in ("front", "back"):
        return leather(O_LEATHER, f, x, y, W, H, 63)
    cx = W / 2.0
    on = abs((x + 0.5 - cx) - (y + 0.5 - H / 2.0)) < 2.0 or abs((x + 0.5 - cx) + (y + 0.5 - H / 2.0)) < 2.0
    plate = in_rect(x, y, int(cx) - 3, H // 2 - 3, 6, 6)
    if plate:
        col = metal(O_IRON, f, x - int(cx) + 3, y - H // 2 + 3, 6, 6, 64)
        if (x - int(cx) + 3, y - H // 2 + 3) in ((1, 1), (4, 1), (1, 4), (4, 4)):
            return (200, 200, 206)
        return col
    if on:
        col = leather(O_LEATHER, f, x, y, W, H, 63)
        if (x + y) % 4 == 0:
            col = mul(col, 0.8)
        return col
    return None


def orc_belt(f, x, y, W, H, c):
    col = leather(mul(O_LEATHER, 0.8), f, x, y, W, H, 65, stitch=(150, 120, 80))
    if f == "front" and W // 2 - 3 <= x <= W // 2 + 2:
        return metal(O_IRON, f, x, y, W, H, 66, bevel=False)
    return col


def orc_pad(f, x, y, W, H, c):
    col = metal(O_IRON, f, x, y, W, H, 67)
    if fbm(x, y, 68, 4.0, 3) > 0.62:
        col = mix(col, O_RUST, 0.6)
    if f in ("front", "back", "right", "left") and y == H - 2 and x % 3 == 1:
        return (196, 196, 204)  # remaches
    return col


def orc_spike(f, x, y, W, H, c):
    col = metal((186, 190, 198), f, x, y, W, H, 69, bevel=False)
    if y <= 1:
        col = add(col, 30)
    return col


def orc_arm(f, x, y, W, H, c):
    col = orc_skin(f, x, y, W, H, 71)
    if f == "bottom":
        return mul(O_SKIN, 0.6)
    if y >= H - 5 and f != "top":
        col = mul(col, 0.9)  # puño
        if f == "front" and y == H - 5 and x % 2 == 0:
            col = mul(col, 0.75)
    return col


def orc_wrist(f, x, y, W, H, c):
    col = leather(O_LEATHER, f, x, y, W, H, 73)
    if y == H // 2 and f not in ("top", "bottom"):
        return metal(O_IRON, f, x, y, W, H, 74, bevel=False)
    return col


def orc_leg(f, x, y, W, H, c):
    col = leather((70, 52, 38), f, x, y, W, H, 75)
    if y >= H - 4 and f != "top":
        col = leather((48, 34, 24), f, x, y, W, H, 76)  # botas
        if y == H - 4:
            col = mul(col, 0.7)
    return col


def orc_fur(f, x, y, W, H, c):
    if f == "bottom":
        return None
    col = fur(O_FUR, (64, 46, 30), (170, 140, 100), f, x, y, W, H, 77)
    if f != "top" and y >= H - 2 and h2(x, y, 78) > 0.55:
        return None  # bordes desiguales
    return col


ORCO = {
    "o_head": orc_head, "o_brow": orc_brow, "o_jaw": orc_jaw, "o_tusk": orc_tusk, "o_knot": orc_knot, "o_ear": orc_ear,
    "o_body": orc_body, "o_belt": orc_belt, "o_strap": orc_strap, "o_pad": orc_pad, "o_spike": orc_spike,
    "o_spike2": orc_spike, "o_arm": orc_arm, "o_wrist": orc_wrist, "o_leg": orc_leg, "o_fur": orc_fur,
}


# ====================================================================== CABALLERO MALDITO y GUARDIÁN DEL JUICIO


class KnightPal:
    def __init__(self, steel, hi, accent, glow, cloth, horn, rune=True, patina=None):
        self.steel, self.hi, self.accent, self.glow, self.cloth, self.horn = steel, hi, accent, glow, cloth, horn
        self.rune = rune
        self.patina = patina


CURSED = KnightPal(steel=(60, 66, 76), hi=(132, 142, 154), accent=(78, 132, 120), glow=(110, 255, 230),
                   cloth=(30, 56, 46), horn=(74, 66, 60), patina=(70, 120, 106))
JUDGE = KnightPal(steel=(196, 202, 214), hi=(250, 252, 255), accent=(228, 188, 74), glow=(255, 236, 170),
                  cloth=(40, 66, 156), horn=(236, 196, 88), rune=False)


def k_steel(P, f, x, y, W, H, s, bevel=True):
    col = metal(P.steel, f, x, y, W, H, s, hi=P.hi, bevel=bevel)
    if P.patina and fbm(x, y, s + 21, 5.0, 3) > 0.70:
        col = mix(col, P.patina, 0.45)
    if P.rune and glow_line(x, y, s + 33, 0.64, 0.022, 7.0):
        col = mix(col, P.glow, 0.8)
    return col


def knight_painters(prefix, P):
    p = prefix

    def helm(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 101)
        if f == "front":
            # Rendija de la visera con ojos de luz y agujeros para respirar
            if 6 <= y <= 7 and 2 <= x <= W - 3:
                if y == 6 and (4 <= x <= 5 or W - 6 <= x <= W - 5):
                    return P.glow
                if y == 7 and (4 <= x <= 5 or W - 6 <= x <= W - 5):
                    return mul(P.glow, 0.6)
                return (8, 10, 12)
            if 10 <= y <= 13 and x % 3 == 1 and 4 <= x <= W - 5:
                return (14, 16, 20)
            if x == W // 2 or x == W // 2 - 1:
                return mix(col, P.hi, 0.35)  # nervio central
        if f in ("right", "left") and y == 6 and 1 <= x <= W - 2:
            return mul(col, 0.6)
        return col

    def visor(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 103)
        if f == "front" and 2 <= y <= 3 and 2 <= x <= W - 3:
            if (3 <= x <= 4 or W - 5 <= x <= W - 4) and y == 2:
                return P.glow
            return (8, 10, 12)
        return col

    def crest(f, x, y, W, H, c):
        col = metal(P.hi if not P.rune else mul(P.hi, 0.85), f, x, y, W, H, 105)
        if P.accent and y == H - 1:
            col = mix(col, P.accent, 0.5)
        return col

    def horn(f, x, y, W, H, c):
        t = y / max(1, H - 1)
        col = mix(P.horn, mul(P.horn, 0.75), t)
        if (y % 3) == 0:
            col = mul(col, 0.85)
        return shaded(col, f, x, y, W, H, 0.82)

    def horn_tip(f, x, y, W, H, c):
        col = mix(mul(P.horn, 0.6), (20, 18, 16) if P.rune else mul(P.horn, 1.15), 1.0 - y / max(1, H - 1))
        return shaded(col, f, x, y, W, H, 0.85)

    def halo(f, x, y, W, H, c):
        col = mix((255, 214, 110), (255, 246, 200), vnoise(x, y, 107, 3.0))
        return col

    def chest(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 109)
        if f == "front":
            if x in (W // 2 - 1, W // 2) and y < H - 4:
                col = mix(col, P.hi, 0.4)
            if y in (H - 5, H - 4):
                col = leather((46, 34, 26), f, x, y, W, H, 110)  # cinturón
                if W // 2 - 2 <= x <= W // 2 + 1:
                    col = P.accent
        if f == "back" and y in (H - 5, H - 4):
            col = leather((46, 34, 26), f, x, y, W, H, 110)
        return col

    def plate(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 111)
        if f == "front":
            # Emblema: un rombo con una gema
            cx, cy = W / 2.0, H / 2.0
            d = abs(x + 0.5 - cx) / (W * 0.30) + abs(y + 0.5 - cy) / (H * 0.42)
            if d < 0.45:
                return P.glow if not P.rune or d < 0.25 else mul(P.glow, 0.7)
            if d < 1.0 and (d > 0.8):
                return P.accent
        return col

    def skirt(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 113)
        if f not in ("top", "bottom") and y % 3 == 2:
            col = mul(col, 0.62)
        elif f not in ("top", "bottom") and y % 3 == 0:
            col = mix(col, P.hi, 0.25)
        return col

    def tabard(f, x, y, W, H, c):
        col = cloth(P.cloth, f, x, y, W, H, 115, torn=P.rune)
        if col is None:
            return None
        if not P.rune and f == "front" and (x in (0, W - 1) or y == 0):
            return P.accent
        if f == "front" and 5 <= y <= 9 and W // 2 - 2 <= x <= W // 2 + 1:
            return P.accent if not P.rune else mix(col, P.glow, 0.25)
        return col

    def arm(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 117)
        if f not in ("top", "bottom") and y in (H // 2 - 1, H // 2):
            col = mul(col, 0.66)  # codo
        return col

    def pauldron(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 119)
        if f not in ("top", "bottom") and y in (H // 2, H - 1):
            col = mul(col, 0.65)
        if f not in ("top", "bottom") and y == 1 and P.accent:
            col = mix(col, P.accent, 0.55)
        return col

    def ridge(f, x, y, W, H, c):
        return metal(P.hi, f, x, y, W, H, 121)

    def gauntlet(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 123)
        if f not in ("top", "bottom") and y % 2 == 1:
            col = mul(col, 0.8)
        return col

    def leg(f, x, y, W, H, c):
        # Cota de malla con placa delantera en el muslo
        if f in ("right", "left", "back") or (f == "front" and y > H // 2 + 2):
            ring = 0.7 if (x + (y // 2) % 2) % 2 == 0 and y % 2 == 0 else 1.0
            col = mul(mix(P.steel, (40, 44, 50), 0.35 if P.rune else 0.15), ring * (0.9 + 0.2 * h2(x, y, 125)))
            return shaded(col, f, x, y, W, H, 0.8)
        return k_steel(P, f, x, y, W, H, 127)

    def knee(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 129)
        if f == "front" and x == W // 2:
            col = mix(col, P.accent or P.hi, 0.6)
        return col

    def boot(f, x, y, W, H, c):
        col = k_steel(P, f, x, y, W, H, 131)
        if f == "bottom":
            return (24, 22, 20)
        if f == "front" and y == 0:
            col = mix(col, P.hi, 0.4)
        return col

    return {
        p + "helm": helm, p + "visor": visor, p + "crest": crest, p + "horn": horn, p + "hornt": horn_tip,
        p + "halo_f": halo, p + "halo_r": halo, p + "chest": chest, p + "plate": plate, p + "skirt": skirt,
        p + "tabard": tabard, p + "arm": arm, p + "pauldron": pauldron, p + "ridge": ridge,
        p + "gauntlet": gauntlet, p + "leg": leg, p + "knee": knee, p + "boot": boot,
    }


# ====================================================================== COLOSO (piedra con runas) y COLOSO PRIMORDIAL (magma)


class StonePal:
    def __init__(self, stone, moss, glow, glow2, eyes, moss_amount=0.55):
        self.stone, self.moss, self.glow, self.glow2, self.eyes, self.moss_amount = stone, moss, glow, glow2, eyes, moss_amount


RUNE_STONE = StonePal((122, 120, 114), (76, 110, 54), (95, 215, 255), (200, 245, 255), (150, 240, 255))
MAGMA_STONE = StonePal((60, 52, 62), (96, 88, 92), (255, 116, 30), (255, 214, 90), (255, 226, 150), moss_amount=0.75)


def colossus_painters(P):
    def rock(f, x, y, W, H, s, moss=True, runes=True):
        col = stone(P.stone, f, x, y, W, H, s)
        if moss and (f == "top" or (f != "bottom" and y < 3)) and fbm(x, y, s + 40, 5.0, 3) > P.moss_amount:
            col = mix(col, P.moss, 0.75 * (1.0 if f == "top" else 0.7))
        if runes and f != "bottom" and glow_line(x, y, s + 50, 0.52, 0.03, 7.0):
            col = mix(P.glow, P.glow2, h2(x, y, s))
        return col

    def torso(f, x, y, W, H, c):
        col = rock(f, x, y, W, H, 141)
        if f == "front":
            # Gran runa en el pecho: un círculo con una línea
            cx, cy = W / 2.0, H * 0.55
            r = math.hypot(x + 0.5 - cx, (y + 0.5 - cy) * 1.1)
            if 4.2 < r < 5.6 or (abs(x + 0.5 - cx) < 0.8 and r < 4.2) or r < 1.6:
                return mix(P.glow, P.glow2, 0.3 + 0.4 * h2(x, y, 142))
        return col

    def belly(f, x, y, W, H, c):
        return mul(rock(f, x, y, W, H, 143, moss=False), 0.86)

    def head(f, x, y, W, H, c):
        col = rock(f, x, y, W, H, 145, runes=False)
        if f == "front":
            # Cara de 14x14: ojos de luz hundidos y boca de grieta
            if 4 <= y <= 7:
                col = mul(col, 0.5)
                if 5 <= y <= 6 and (2 <= x <= 4 or W - 5 <= x <= W - 3):
                    return P.eyes if y == 5 else mix(P.eyes, P.glow, 0.6)
            if y in (10, 11) and 3 <= x <= W - 4:
                return mul(col, 0.45) if y == 10 else mix(mul(col, 0.5), P.glow, 0.35)
        return col

    def brow(f, x, y, W, H, c):
        return mul(rock(f, x, y, W, H, 147, runes=False), 0.8)

    def chin(f, x, y, W, H, c):
        return mul(rock(f, x, y, W, H, 149, moss=False, runes=False), 0.85)

    def arm(f, x, y, W, H, c):
        col = rock(f, x, y, W, H, 151)
        if f != "bottom" and y % 10 in (0,) and y > 0:
            col = mul(col, 0.7)  # anillos de roca
        return col

    def fist(f, x, y, W, H, c):
        col = mul(rock(f, x, y, W, H, 153, moss=False), 0.88)
        if f == "front" and y == H // 2 and x % 3 == 0:
            col = mul(col, 0.6)  # nudillos
        return col

    def boulder(f, x, y, W, H, c):
        return rock(f, x, y, W, H, 155)

    def leg(f, x, y, W, H, c):
        return rock(f, x, y, W, H, 157)

    def foot(f, x, y, W, H, c):
        return mul(rock(f, x, y, W, H, 159, moss=False), 0.82)

    def small(f, x, y, W, H, c):
        return rock(f, x, y, W, H, 161, runes=False)

    return {"c_torso": torso, "c_belly": belly, "c_head": head, "c_brow": brow, "c_chin": chin, "c_arm": arm,
            "c_fist": fist, "c_boulder": boulder, "c_leg": leg, "c_foot": foot, "c_rock1": small, "c_rock2": small}


# ====================================================================== LOBO COLMILLO DE ACERO y BESTIA DEL ECLIPSE


class WolfPal:
    def __init__(self, fur, dark, light, belly, steel, steel_hi, eye, eye2, mark=None):
        self.fur, self.dark, self.light, self.belly = fur, dark, light, belly
        self.steel, self.steel_hi, self.eye, self.eye2, self.mark = steel, steel_hi, eye, eye2, mark


STEEL_WOLF = WolfPal((80, 84, 92), (42, 44, 50), (150, 154, 162), (122, 126, 132), (174, 180, 190), (236, 240, 246),
                     (255, 170, 40), (150, 70, 10))
ECLIPSE_WOLF = WolfPal((44, 36, 64), (18, 14, 30), (96, 84, 130), (60, 52, 84), (112, 104, 150), (210, 200, 250),
                       (255, 60, 70), (130, 0, 20), mark=(200, 190, 245))


def wolf_painters(P):
    def pelt(f, x, y, W, H, s, belly=False):
        col = fur(P.fur, P.dark, P.light, f, x, y, W, H, s, vertical=f not in ("top", "bottom"))
        if f == "top":
            # Raya oscura por el lomo
            if abs(x + 0.5 - W / 2.0) < W * 0.18:
                col = mix(col, P.dark, 0.5)
        if belly and f == "bottom":
            col = fur(P.belly, P.dark, P.light, f, x, y, W, H, s + 1, vertical=False)
        if P.mark and f in ("right", "left") and glow_line(x, y, s + 9, 0.45, 0.04, 9.0):
            col = mix(col, P.mark, 0.8)
        return col

    def body(f, x, y, W, H, c):
        return pelt(f, x, y, W, H, 171, belly=True)

    def ruff(f, x, y, W, H, c):
        col = fur(mix(P.fur, P.light, 0.18), P.dark, P.light, f, x, y, W, H, 173)
        if f == "front" and P.mark:
            # Luna del eclipse en el pecho
            r = math.hypot(x + 0.5 - W / 2.0, y + 0.5 - H * 0.55)
            if 2.5 < r < 4.2:
                return P.mark
        return col

    def saddle(f, x, y, W, H, c):
        col = metal(P.steel, f, x, y, W, H, 175, hi=P.steel_hi)
        if f in ("top",) and (x % 5 == 0):
            col = mul(col, 0.7)
        if f in ("right", "left", "front", "back") and y == H - 1 and x % 3 == 1:
            return P.steel_hi
        return col

    def spine(f, x, y, W, H, c):
        col = metal(P.steel_hi if y < 2 else P.steel, f, x, y, W, H, 177, hi=P.steel_hi)
        return col

    def skull(f, x, y, W, H, c):
        col = pelt(f, x, y, W, H, 179)
        if f == "front":
            # Ojos y ceño (14x12)
            for ex in (2, 9):
                if in_rect(x, y, ex, 5, 3, 2):
                    if y == 5:
                        return P.eye if x - ex < 2 else mul(P.eye, 0.7)
                    return P.eye2
            if y == 4 and (1 <= x <= 5 or 8 <= x <= 12):
                return P.dark
            if P.mark:
                r = math.hypot(x + 0.5 - W / 2.0, y + 0.5 - 2.0)
                if 1.0 < r < 2.2:
                    return P.mark
        return col

    def snout(f, x, y, W, H, c):
        col = fur(mix(P.fur, P.dark, 0.25), P.dark, P.light, f, x, y, W, H, 181)
        if f == "front":
            if y <= 2 and W // 2 - 2 <= x <= W // 2 + 1:
                return (20, 18, 20) if y < 2 else (46, 42, 46)  # nariz
            if y >= H - 2:
                return (40, 18, 20)  # boca
        if f == "top" and y >= H - 3 and W // 2 - 2 <= x <= W // 2 + 1:
            return (24, 22, 24)
        return col

    def jaw(f, x, y, W, H, c):
        col = fur(mul(P.fur, 0.8), P.dark, P.light, f, x, y, W, H, 183)
        if f == "top":
            return (92, 30, 34)
        if f == "front" and y == 0 and x % 2 == 0:
            return P.steel_hi
        return col

    def ear(f, x, y, W, H, c):
        col = fur(P.dark, mul(P.dark, 0.7), P.fur, f, x, y, W, H, 185)
        if f == "front" and 1 <= x <= W - 2 and y >= 1:
            col = mix(col, (120, 80, 80), 0.4)
        return col

    def fang(f, x, y, W, H, c):
        return metal(P.steel_hi, f, x, y, W, H, 187, hi=(255, 255, 255))

    def leg(f, x, y, W, H, c):
        return pelt(f, x, y, W, H, 189)

    def paw(f, x, y, W, H, c):
        col = fur(mul(P.fur, 0.75), P.dark, P.light, f, x, y, W, H, 191)
        if f == "front" and y >= H - 2 and x % 2 == 1:
            return P.steel_hi  # garras
        if f == "bottom":
            return (30, 26, 28)
        return col

    def tail(f, x, y, W, H, c):
        return pelt(f, x, y, W, H, 193)

    def tailtip(f, x, y, W, H, c):
        return metal(P.steel, f, x, y, W, H, 195, hi=P.steel_hi)

    return {"w_body": body, "w_ruff": ruff, "w_saddle": saddle, "w_spine": spine, "w_spine3": spine,
            "w_skull": skull, "w_snout": snout, "w_jaw": jaw, "w_ear": ear, "w_fang": fang, "w_leg": leg,
            "w_paw": paw, "w_tail": tail, "w_tailtip": tailtip}


# ====================================================================== OSO DE HIELO

B_FUR = (226, 238, 246)
B_SHADOW = (156, 184, 206)
B_LIGHT = (250, 254, 255)
B_ICE = (150, 220, 255)
B_ICE_D = (54, 128, 210)


def ice(f, x, y, W, H, s):
    t = (x + y * 0.7) / max(1.0, W + H * 0.7)
    col = mix(B_ICE, B_ICE_D, t * 0.8 + 0.2 * vnoise(x, y, s, 3.0))
    if x == 0 or y == 0:
        col = mix(col, (240, 252, 255), 0.6)
    if (x + y) % 5 == 0 and f != "bottom":
        col = add(col, 25)
    return col


def oso_painters():
    def pelt(f, x, y, W, H, s):
        col = fur(B_FUR, B_SHADOW, B_LIGHT, f, x, y, W, H, s, vertical=f not in ("top", "bottom"), edge=0.9)
        if f in ("right", "left", "front", "back") and y >= H - 3:
            col = mix(col, B_SHADOW, 0.35)
        if f == "top" and fbm(x, y, s + 5, 5.0) > 0.66:
            col = mix(col, (200, 236, 255), 0.6)  # escarcha
        return col

    def body(f, x, y, W, H, c):
        return pelt(f, x, y, W, H, 201)

    def hump(f, x, y, W, H, c):
        return pelt(f, x, y, W, H, 203)

    def crystal(f, x, y, W, H, c):
        return ice(f, x, y, W, H, 205)

    def skull(f, x, y, W, H, c):
        col = pelt(f, x, y, W, H, 207)
        if f == "front":
            # 20x18: ojos azules helados
            for ex in (4, 13):
                if in_rect(x, y, ex, 6, 3, 2):
                    return (120, 222, 255) if y == 6 else (40, 90, 140)
            if y == 5 and (3 <= x <= 7 or 12 <= x <= 16):
                return mul(B_SHADOW, 0.85)
        return col

    def snout(f, x, y, W, H, c):
        col = pelt(f, x, y, W, H, 209)
        if f == "front":
            if y <= 2 and W // 2 - 2 <= x <= W // 2 + 1:
                return (24, 26, 32)
            if y == H - 2 and 2 <= x <= W - 3:
                return (90, 40, 50)
        if f == "top" and y >= H - 3 and W // 2 - 2 <= x <= W // 2 + 1:
            return (30, 32, 38)
        return col

    def ear(f, x, y, W, H, c):
        col = pelt(f, x, y, W, H, 211)
        if f == "front" and 1 <= x <= W - 2 and 1 <= y:
            col = mix(col, (170, 150, 170), 0.5)
        return col

    def leg(f, x, y, W, H, c):
        return pelt(f, x, y, W, H, 213)

    def claws(f, x, y, W, H, c):
        if f == "front" and x % 3 == 2:
            return None
        return shaded((52, 66, 88), f, x, y, W, H, 0.85)

    def tail(f, x, y, W, H, c):
        return pelt(f, x, y, W, H, 215)

    return {"b_body": body, "b_hump": hump, "b_ice1": crystal, "b_ice2": crystal, "b_ice4": crystal,
            "b_ice5": crystal, "b_skull": skull, "b_snout": snout, "b_ear": ear, "b_crest": crystal,
            "b_leg": leg, "b_claws": claws, "b_tail": tail}


# ====================================================================== HORMIGA SOLDADO y REINA CARMESÍ


class AntPal:
    def __init__(self, shell, dark, hi, joint, eye, mand, gold=None):
        self.shell, self.dark, self.hi, self.joint, self.eye, self.mand, self.gold = shell, dark, hi, joint, eye, mand, gold


SOLDIER = AntPal((84, 40, 24), (30, 14, 10), (170, 104, 70), (156, 74, 30), (20, 20, 26), (196, 84, 34))
QUEEN = AntPal((128, 20, 42), (48, 6, 18), (236, 120, 130), (236, 186, 70), (255, 214, 90), (236, 186, 70), gold=(236, 186, 70))


def ant_painters(prefix, P):
    p = prefix

    def shell(f, x, y, W, H, s, bands=0):
        return chitin(P.shell, P.dark, P.hi, f, x, y, W, H, s, bands=bands)

    def thorax(f, x, y, W, H, c):
        col = shell(f, x, y, W, H, 221)
        if f == "top" and y in (H // 3, 2 * H // 3):
            col = mul(col, 0.7)
        return col

    def petiole(f, x, y, W, H, c):
        return mul(shell(f, x, y, W, H, 223), 0.8)

    def gaster(f, x, y, W, H, c):
        col = chitin(P.shell, P.dark, P.hi, f, x, y, W, H, 225, bands=4)
        if P.gold and f in ("top", "right", "left") and (y if f != "top" else x) % max(3, (H if f != "top" else W) // 4) == 0:
            col = mix(col, P.gold, 0.6)
        if not P.gold and f == "top" and fbm(x, y, 226, 3.0) > 0.7:
            col = mix(col, (130, 70, 40), 0.3)
        return col

    def head(f, x, y, W, H, c):
        col = shell(f, x, y, W, H, 227)
        if f in ("right", "left"):
            # Ojos compuestos a los lados
            if in_ellipse(x, y, W * 0.45, H * 0.38, W * 0.28, H * 0.24):
                e = P.eye
                if (x + y) % 2 == 0:
                    e = add(e, 30)
                if in_rect(x, y, int(W * 0.35), int(H * 0.25), 1, 1):
                    return (240, 240, 240)
                return e
        if f == "front":
            if y >= H - 3 and 2 <= x <= W - 3:
                return mul(P.dark, 0.8)  # boca
            if y == 2 and (x == 3 or x == W - 4):
                return P.joint
        return col

    def crown(f, x, y, W, H, c):
        if f in ("front", "back", "right", "left") and y < 2 and x % 3 != 1:
            return None
        col = metal(P.gold or P.joint, f, x, y, W, H, 229, bevel=False)
        if f == "front" and x == W // 2 and y == 2:
            return (180, 30, 60)
        return col

    def mandible(f, x, y, W, H, c):
        col = chitin(mul(P.shell, 0.8), P.dark, P.hi, f, x, y, W, H, 231)
        if f in ("right", "left") and y == H - 1 and x % 2 == 0:
            return P.mand  # dientes
        if f == "front":
            return P.mand
        return col

    def antenna(f, x, y, W, H, c):
        return shaded(mix(P.dark, P.shell, 0.4 + 0.3 * (y % 3 == 0)), f, x, y, W, H, 0.85)

    def antenna_tip(f, x, y, W, H, c):
        col = mix(P.dark, P.joint, 0.2 + 0.6 * (1 - y / max(1, H - 1)))
        return shaded(col, f, x, y, W, H, 0.85)

    def femur(f, x, y, W, H, c):
        col = shell(f, x, y, W, H, 233)
        if f in ("front", "back", "top", "bottom") and (x < 2 or x >= W - 2):
            col = P.joint
        return col

    def tibia(f, x, y, W, H, c):
        col = shell(f, x, y, W, H, 235)
        if y < 2:
            return P.joint
        if y >= H - 2:
            col = mul(P.dark, 0.9)
        if f in ("front", "back") and y % 4 == 3:
            col = mix(col, P.hi, 0.35)  # pelillos
        return col

    return {p + "thorax": thorax, p + "petiole": petiole, p + "gaster": gaster, p + "head": head, p + "crown": crown,
            p + "mand": mandible, p + "ant": antenna, p + "ant2": antenna_tip, p + "femur": femur, p + "tibia": tibia}


# ====================================================================== pintar y guardar


def paint(model, painters, default=None):
    W, H = model.png_size()
    px = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]
    for part, c in model.all_cubes():
        if c.uv_of is not None:
            continue
        fn = painters.get(c.name, default)
        if fn is None:
            raise KeyError("sin pintor para " + c.name + " en " + model.name)
        for face, (x0, y0, w, h) in modelos.faces(c).items():
            FW, FH = w * D, h * D
            for ly in range(FH):
                for lx in range(FW):
                    col = fn(face, lx, ly, FW, FH, c)
                    if col is None:
                        continue
                    if len(col) == 3:
                        col = (col[0], col[1], col[2], 255)
                    px[y0 * D + ly][x0 * D + lx] = tuple(int(v) for v in col)
    return W, H, px


def textures():
    """Nombre de la textura -> (modelo, pintores, colores que brillan en la oscuridad como tramos (de, a))."""
    m = modelos.all_models()

    def seg(c, k=0.6):
        return (c, mul(c, k))

    return {
        "goblin": (m["goblin"], GOBLIN, [((252, 206, 40), (232, 160, 20))]),
        "orco": (m["orco"], ORCO, [((255, 96, 40), (170, 40, 20))]),
        "caballero": (m["caballero"], knight_painters("k_", CURSED), [seg(CURSED.glow)]),
        "guardian_juicio": (m["guardian_juicio"], knight_painters("j_", JUDGE),
                            [seg(JUDGE.glow), ((255, 214, 110), (255, 246, 200))]),
        "coloso": (m["coloso"], colossus_painters(RUNE_STONE), [(RUNE_STONE.glow, RUNE_STONE.glow2), seg(RUNE_STONE.eyes, 1.0)]),
        "coloso_primordial": (m["coloso"], colossus_painters(MAGMA_STONE),
                              [(MAGMA_STONE.glow, MAGMA_STONE.glow2), seg(MAGMA_STONE.eyes, 1.0)]),
        "lobo_acero": (m["lobo"], wolf_painters(STEEL_WOLF), [seg(STEEL_WOLF.eye, 0.7)]),
        "bestia_eclipse": (m["lobo"], wolf_painters(ECLIPSE_WOLF), [seg(ECLIPSE_WOLF.eye, 0.7), seg(ECLIPSE_WOLF.mark, 1.0)]),
        "oso_hielo": (m["oso"], oso_painters(), [((120, 222, 255), (120, 222, 255))]),
        "hormiga": (m["hormiga"], ant_painters("a_", SOLDIER), None),
        "reina_carmesi": (m["reina_carmesi"], ant_painters("q_", QUEEN), [(QUEEN.eye, add(QUEEN.eye, 30))]),
    }


def near_segment(c, a, b, tol=14.0):
    ax, ay, az = a
    bx, by, bz = b
    dx, dy, dz = bx - ax, by - ay, bz - az
    ll = dx * dx + dy * dy + dz * dz
    t = 0.0 if ll == 0 else max(0.0, min(1.0, ((c[0] - ax) * dx + (c[1] - ay) * dy + (c[2] - az) * dz) / ll))
    px, py, pz = ax + dx * t, ay + dy * t, az + dz * t
    return (c[0] - px) ** 2 + (c[1] - py) ** 2 + (c[2] - pz) ** 2 <= tol * tol


def glow_of(px, segments):
    """Textura de brillo: negro salvo los píxeles que brillan (se suma a la imagen, como los ojos de araña)."""
    out = []
    for row in px:
        r = []
        for c in row:
            if c[3] > 0 and any(near_segment(c, a, b) for a, b in segments):
                r.append((c[0], c[1], c[2], 255))
            else:
                r.append((0, 0, 0, 0))
        out.append(r)
    return out


def generate(save_png, folder):
    """save_png(ruta, ancho, alto, filas_de_píxeles) la pone texturas.py (escritor de PNG sin dependencias)."""
    import os
    for name, (model, painters, glow) in textures().items():
        W, H, px = paint(model, painters)
        save_png(os.path.join(folder, name + ".png"), W, H, px)
        if glow:
            save_png(os.path.join(folder, name + "_brillo.png"), W, H, glow_of(px, glow))
