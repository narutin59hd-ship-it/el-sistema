"""Modelos 3D de los monstruos de El Sistema (desde la 1.5.0).

Cada modelo es un árbol de piezas (Part) con cajas (Cube), igual que en Minecraft. Las coordenadas de
textura (UV) se reparten solas (empaquetado por filas) y las usan:
  - texturas.py, para pintar cada caja en su sitio (a doble resolución: 2 píxeles por unidad), y
  - gen_modelos.py, que escribe src/main/java/com/elsistema/client/render/MonsterLayers.java.

Solo usa la biblioteca estándar. Unidades de Minecraft: 16 = un bloque, Y hacia abajo, el suelo en y = 24
y la cara del monstruo mirando a -Z.
"""
import math

# Píxeles de textura por unidad de modelo (las texturas son el doble de detalladas que las de Minecraft).
DENSITY = 2


class Cube:
    def __init__(self, name, x, y, z, w, h, d, mirror=False, grow=0.0, uv_of=None):
        self.name = name
        self.x, self.y, self.z = float(x), float(y), float(z)
        self.w, self.h, self.d = int(w), int(h), int(d)
        assert self.w > 0 and self.h > 0 and self.d > 0, name
        self.mirror = mirror
        self.grow = grow
        self.uv_of = uv_of  # nombre de otra caja con las mismas medidas cuya textura se comparte
        self.u = self.v = None

    def footprint(self):
        return 2 * (self.w + self.d), self.h + self.d


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
        self.name = name
        self.pivot = tuple(float(p) for p in pivot)
        self.rot = tuple(float(r) for r in rot)
        self.cubes = list(cubes)
        self.children = list(children)

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()


class Model:
    def __init__(self, name, parts, tex_w=64):
        self.name = name
        self.parts = parts
        self.tex_w = tex_w
        self.tex_h = 0
        self.pack()

    def all_parts(self):
        for p in self.parts:
            yield from p.walk()

    def all_cubes(self):
        for p in self.all_parts():
            for c in p.cubes:
                yield p, c

    def cube(self, name):
        for _, c in self.all_cubes():
            if c.name == name:
                return c
        raise KeyError(name)

    def pack(self):
        """Reparte las cajas por la textura en filas (de la más alta a la más baja), sin solaparse."""
        own = []
        seen = set()
        for _, c in self.all_cubes():
            if c.uv_of is None:
                assert c.name not in seen, "nombre repetido: " + c.name
                seen.add(c.name)
                own.append(c)
        order = sorted(own, key=lambda c: (-c.footprint()[1], -c.footprint()[0], c.name))
        x = y = row_h = 0
        for c in order:
            fw, fh = c.footprint()
            assert fw <= self.tex_w, c.name
            if x + fw > self.tex_w:
                x = 0
                y += row_h
                row_h = 0
            c.u, c.v = x, y
            x += fw
            row_h = max(row_h, fh)
        h = y + row_h
        self.tex_h = max(16, int(math.ceil(h / 16.0)) * 16)
        for _, c in self.all_cubes():
            if c.uv_of is not None:
                src = self.cube(c.uv_of)
                assert (src.w, src.h, src.d) == (c.w, c.h, c.d), c.name + " no tiene las medidas de " + c.uv_of
                c.u, c.v = src.u, src.v

    def png_size(self):
        return self.tex_w * DENSITY, self.tex_h * DENSITY


def faces(c):
    """Rectángulos (x, y, ancho, alto) en unidades de textura de cada cara visible de una caja."""
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


# ====================================================================== piezas de ayuda


def humanoid_parts(head, body, right_arm, left_arm, right_leg, left_leg):
    """Las 7 piezas que necesita HumanoidModel (el sombrero va vacío)."""
    return [head, Part("hat", head.pivot), body, right_arm, left_arm, right_leg, left_leg]


def mirror_x(cube_list, suffix="_l"):
    """Copia unas cajas al otro lado (X cambiado de signo) compartiendo su textura en espejo."""
    out = []
    for c in cube_list:
        out.append(Cube(c.name + suffix, -c.x - c.w, c.y, c.z, c.w, c.h, c.d, mirror=not c.mirror, grow=c.grow,
                        uv_of=c.uv_of or c.name))
    return out


# ====================================================================== GOBLIN (pequeño, cabezón y orejudo)
# Proporciones propias: piernas cortas (7), cuerpo flaco (10), cabeza grande. Las piezas se recolocan en
# MonsterModels.Proportions (cabeza y cuerpo en y=7, brazos en y=9, piernas en y=17).


def goblin():
    head = Part("head", (0, 7, -0.5), cubes=[
        Cube("g_head", -4.5, -8, -4, 9, 8, 8),
        Cube("g_nose", -1, -4.5, -5.5, 2, 3, 2),
        Cube("g_brow", -4, -6.5, -4.6, 8, 1, 1),
    ], children=[
        Part("ear_r", (-4.5, -5, 0), (0, 0.45, 0.25), cubes=[Cube("g_ear", -5, -1.5, -0.5, 5, 3, 1)]),
        Part("ear_l", (4.5, -5, 0), (0, -0.45, -0.25), cubes=[Cube("g_ear_l", 0, -1.5, -0.5, 5, 3, 1, mirror=True, uv_of="g_ear")]),
    ])
    body = Part("body", (0, 7, 0), cubes=[
        Cube("g_body", -3.5, 0, -2, 7, 10, 4),
        Cube("g_belt", -4, 8, -2.5, 8, 2, 5, grow=0.05),
        Cube("g_flap", -2.5, 10, -2.8, 5, 4, 1),
    ])
    right_arm = Part("right_arm", (-4.5, 9, 0), cubes=[
        Cube("g_arm", -1.5, -1, -1, 2, 11, 2),
        Cube("g_bracer", -1.5, 5, -1, 2, 3, 2, grow=0.3),
    ])
    left_arm = Part("left_arm", (4.5, 9, 0), cubes=[
        Cube("g_arm_l", -0.5, -1, -1, 2, 11, 2, mirror=True, uv_of="g_arm"),
        Cube("g_bracer_l", -0.5, 5, -1, 2, 3, 2, mirror=True, grow=0.3, uv_of="g_bracer"),
    ])
    right_leg = Part("right_leg", (-1.8, 17, 0), cubes=[
        Cube("g_leg", -1.5, 0, -1.5, 3, 7, 3),
        Cube("g_foot", -1.5, 5, -3, 3, 2, 2),
    ])
    left_leg = Part("left_leg", (1.8, 17, 0), cubes=[
        Cube("g_leg_l", -1.5, 0, -1.5, 3, 7, 3, mirror=True, uv_of="g_leg"),
        Cube("g_foot_l", -1.5, 5, -3, 3, 2, 2, mirror=True, uv_of="g_foot"),
    ])
    return Model("goblin", humanoid_parts(head, body, right_arm, left_arm, right_leg, left_leg), tex_w=64)


# ====================================================================== ORCO (bruto, hombreras con pinchos y colmillos)
# Proporciones: piernas 11 (y=13), cuerpo 12 (y=1), cabeza adelantada, brazos gruesos en x=±6.5.


def orco():
    head = Part("head", (0, -0.5, -1.5), cubes=[
        Cube("o_head", -4, -8, -4, 8, 8, 8),
        Cube("o_brow", -4.5, -7.2, -4.6, 9, 1, 1),
        Cube("o_jaw", -3.5, -3, -5.2, 7, 3, 2),
        Cube("o_tusk", -3, -4.6, -5.7, 1, 2, 1),
        Cube("o_tusk_l", 2, -4.6, -5.7, 1, 2, 1, mirror=True, uv_of="o_tusk"),
        Cube("o_knot", -1, -10.5, 0.5, 2, 3, 3),
        Cube("o_ear", -5, -6, -1, 1, 3, 2),
        Cube("o_ear_l", 4, -6, -1, 1, 3, 2, mirror=True, uv_of="o_ear"),
    ])
    body = Part("body", (0, 1, 0), cubes=[
        Cube("o_body", -5, 0, -3, 10, 12, 6),
        Cube("o_belt", -5.5, 9, -3.5, 11, 2, 7, grow=0.05),
        Cube("o_strap", -5.2, -0.2, -3.3, 10, 10, 1, grow=0.05),
    ])
    pad = [Cube("o_pad", -4.5, -2.5, -3, 6, 4, 6, grow=0.1), Cube("o_spike", -3, -5, -0.5, 1, 3, 1),
           Cube("o_spike2", -1, -4.5, -2, 1, 2, 1)]
    right_arm = Part("right_arm", (-6.5, 3, 0), cubes=[
        Cube("o_arm", -3, -2, -2.5, 5, 13, 5),
        Cube("o_wrist", -3, 7, -2.5, 5, 3, 5, grow=0.25),
    ] + pad)
    left_arm = Part("left_arm", (6.5, 3, 0), cubes=[
        Cube("o_arm_l", -2, -2, -2.5, 5, 13, 5, mirror=True, uv_of="o_arm"),
        Cube("o_wrist_l", -2, 7, -2.5, 5, 3, 5, mirror=True, grow=0.25, uv_of="o_wrist"),
    ] + mirror_x(pad))
    right_leg = Part("right_leg", (-2.6, 13, 0), cubes=[
        Cube("o_leg", -2.5, 0, -2.5, 5, 11, 5),
        Cube("o_fur", -2.5, 0, -2.5, 5, 4, 5, grow=0.35),
    ])
    left_leg = Part("left_leg", (2.6, 13, 0), cubes=[
        Cube("o_leg_l", -2.5, 0, -2.5, 5, 11, 5, mirror=True, uv_of="o_leg"),
        Cube("o_fur_l", -2.5, 0, -2.5, 5, 4, 5, mirror=True, grow=0.35, uv_of="o_fur"),
    ])
    return Model("orco", humanoid_parts(head, body, right_arm, left_arm, right_leg, left_leg), tex_w=128)


# ====================================================================== CABALLERO MALDITO (armadura con cuernos)
# Proporciones normales de humanoide.


def knight_parts(prefix, halo=False):
    p = prefix
    head_children = [
        Part("horn_r", (-4, -6.5, -0.5), (-0.35, 0, -0.6), cubes=[Cube(p + "horn", -1, -4, -1, 2, 4, 2)],
             children=[Part("horn_r2", (0, -4, 0), (-0.45, 0, 0.35), cubes=[Cube(p + "hornt", -0.5, -3, -0.5, 1, 3, 1)])]),
        Part("horn_l", (4, -6.5, -0.5), (-0.35, 0, 0.6), cubes=[Cube(p + "horn_l", -1, -4, -1, 2, 4, 2, mirror=True, uv_of=p + "horn")],
             children=[Part("horn_l2", (0, -4, 0), (-0.45, 0, -0.35), cubes=[Cube(p + "hornt_l", -0.5, -3, -0.5, 1, 3, 1, mirror=True,
                                                                                 uv_of=p + "hornt")])]),
    ]
    if halo:
        head_children = [Part("halo", (0, -12.5, 0), (0.12, 0, 0), cubes=[
            Cube(p + "halo_f", -5, 0, -5, 10, 1, 1),
            Cube(p + "halo_b", -5, 0, 4, 10, 1, 1, uv_of=p + "halo_f"),
            Cube(p + "halo_r", -5, 0, -4, 1, 1, 8),
            Cube(p + "halo_l", 4, 0, -4, 1, 1, 8, mirror=True, uv_of=p + "halo_r"),
        ])]
    head = Part("head", (0, 0, 0), cubes=[
        Cube(p + "helm", -4, -8, -4, 8, 8, 8, grow=0.25),
        Cube(p + "visor", -3.5, -5.5, -4.8, 7, 3, 1),
        Cube(p + "crest", -0.5, -9.2, -4, 1, 2, 8),
    ], children=head_children)
    body = Part("body", (0, 0, 0), cubes=[
        Cube(p + "chest", -4, 0, -2, 8, 12, 4, grow=0.3),
        Cube(p + "plate", -3.5, 1, -2.9, 7, 6, 1),
        Cube(p + "skirt", -4.5, 10, -2.5, 9, 4, 5, grow=0.1),
        Cube(p + "tabard", -2, 10.5, -3.2, 4, 8, 1),
    ])
    pauldron = [Cube(p + "pauldron", -4.5, -3, -3, 6, 4, 6, grow=0.15), Cube(p + "ridge", -3, -4.2, -2.5, 1, 2, 5)]
    right_arm = Part("right_arm", (-5, 2, 0), cubes=[Cube(p + "arm", -3, -2, -2, 4, 12, 4, grow=0.2)] + pauldron + [
        Cube(p + "gauntlet", -3, 6.5, -2, 4, 4, 4, grow=0.35)])
    left_arm = Part("left_arm", (5, 2, 0), cubes=[Cube(p + "arm_l", -1, -2, -2, 4, 12, 4, mirror=True, grow=0.2, uv_of=p + "arm")]
                    + mirror_x(pauldron) + [Cube(p + "gauntlet_l", -1, 6.5, -2, 4, 4, 4, mirror=True, grow=0.35,
                                                 uv_of=p + "gauntlet")])
    right_leg = Part("right_leg", (-1.9, 12, 0), cubes=[
        Cube(p + "leg", -2, 0, -2, 4, 12, 4, grow=0.2),
        Cube(p + "knee", -2.5, 3.5, -2.8, 5, 3, 1),
        Cube(p + "boot", -2, 9, -2.75, 4, 3, 5, grow=0.25),
    ])
    left_leg = Part("left_leg", (1.9, 12, 0), cubes=[
        Cube(p + "leg_l", -2, 0, -2, 4, 12, 4, mirror=True, grow=0.2, uv_of=p + "leg"),
        Cube(p + "knee_l", -2.5, 3.5, -2.8, 5, 3, 1, mirror=True, uv_of=p + "knee"),
        Cube(p + "boot_l", -2, 9, -2.75, 4, 3, 5, mirror=True, grow=0.25, uv_of=p + "boot"),
    ])
    return humanoid_parts(head, body, right_arm, left_arm, right_leg, left_leg)


def caballero():
    return Model("caballero", knight_parts("k_"), tex_w=128)


def guardian():
    return Model("guardian_juicio", knight_parts("j_", halo=True), tex_w=128)


# ====================================================================== COLOSO (gigante de piedra, brazos hasta el suelo)


def coloso(name="coloso"):
    p = "c_"
    body = Part("body", (0, 13, 1), cubes=[
        Cube(p + "torso", -6, -13, -4.5, 12, 13, 9),
        Cube(p + "belly", -4.5, -3, -4, 9, 4, 7),
        Cube(p + "rock1", -5, -15, 0, 4, 3, 4),
        Cube(p + "rock2", 1.5, -14.5, 1.5, 3, 2, 3),
    ])
    head = Part("head", (0, 1, -3.5), cubes=[
        Cube(p + "head", -3.5, -6, -4, 7, 7, 7),
        Cube(p + "brow", -4, -6.2, -4.6, 8, 1, 1),
        Cube(p + "chin", -2.5, 0, -4.2, 5, 2, 3),
    ])
    arm = [Cube(p + "arm", -2.5, -2, -2.5, 5, 16, 5), Cube(p + "fist", -3, 14, -3, 6, 5, 6),
           Cube(p + "boulder", -3.5, -4, -3.5, 7, 5, 7)]
    right_arm = Part("right_arm", (-8.5, 2, 0.5), cubes=arm)
    left_arm = Part("left_arm", (8.5, 2, 0.5), cubes=mirror_x(arm))
    leg = [Cube(p + "leg", -3, 0, -3, 6, 11, 6), Cube(p + "foot", -3.5, 9, -4, 7, 2, 7)]
    right_leg = Part("right_leg", (-3.5, 13, 1), cubes=leg)
    left_leg = Part("left_leg", (3.5, 13, 1), cubes=mirror_x(leg))
    return Model(name, [body, head, right_arm, left_arm, right_leg, left_leg], tex_w=128)


# ====================================================================== LOBO COLMILLO DE ACERO


def lobo():
    p = "w_"
    body = Part("body", (0, 12.5, 1), cubes=[
        Cube(p + "body", -3.5, -3, -7, 7, 6, 14),
        Cube(p + "ruff", -4.5, -4.5, -9, 9, 9, 6),
        Cube(p + "saddle", -4, -4.2, -3, 8, 2, 7, grow=0.2),
    ], children=[
        Part("spine1", (0, -4.5, -1.5), (-0.5, 0, 0), cubes=[Cube(p + "spine", -0.5, -3, -1, 1, 3, 2)]),
        Part("spine2", (0, -3.5, 2), (-0.55, 0, 0), cubes=[Cube(p + "spine2", -0.5, -2.5, -1, 1, 3, 2, uv_of=p + "spine")]),
        Part("spine3", (0, -3.2, 5), (-0.6, 0, 0), cubes=[Cube(p + "spine3", -0.5, -2, -1, 1, 2, 2)]),
    ])
    head = Part("head", (0, 11, -8), cubes=[
        Cube(p + "skull", -3.5, -3.5, -4, 7, 6, 5),
        Cube(p + "snout", -2, -0.5, -8, 4, 3, 4),
        Cube(p + "jaw", -1.5, 2.5, -7.5, 3, 1, 4),
        Cube(p + "ear", -3.5, -6, -1.5, 2, 3, 1),
        Cube(p + "ear_l", 1.5, -6, -1.5, 2, 3, 1, mirror=True, uv_of=p + "ear"),
        Cube(p + "fang", -1.5, 2.5, -8, 1, 2, 1),
        Cube(p + "fang_l", 0.5, 2.5, -8, 1, 2, 1, mirror=True, uv_of=p + "fang"),
    ])
    leg = [Cube(p + "leg", -1.5, 0, -1.5, 3, 8, 3), Cube(p + "paw", -2, 7, -2.5, 4, 2, 4)]
    legs = [Part("leg0", (-2.5, 15, 6), cubes=leg), Part("leg1", (2.5, 15, 6), cubes=mirror_x(leg, "_b")),
            Part("leg2", (-2.5, 15, -5), cubes=[Cube(p + "leg_c", -1.5, 0, -1.5, 3, 8, 3, uv_of=p + "leg"),
                                                Cube(p + "paw_c", -2, 7, -2.5, 4, 2, 4, uv_of=p + "paw")]),
            Part("leg3", (2.5, 15, -5), cubes=[Cube(p + "leg_d", -1.5, 0, -1.5, 3, 8, 3, mirror=True, uv_of=p + "leg"),
                                               Cube(p + "paw_d", -2, 7, -2.5, 4, 2, 4, mirror=True, uv_of=p + "paw")])]
    tail = Part("tail", (0, 10.5, 7.5), (0.95, 0, 0), cubes=[
        Cube(p + "tail", -1, 0, -1, 2, 8, 2),
        Cube(p + "tailtip", -1.5, 6.5, -1.5, 3, 3, 3),
    ])
    return Model("lobo", [body, head] + legs + [tail], tex_w=128)


# ====================================================================== OSO DE HIELO (con cristales en el lomo)


def oso():
    p = "b_"
    crystals = [
        ("ice1", (-2.5, -6.5, -6), (0.15, 0, -0.35), (-1, -6, -1, 2, 6, 2)),
        ("ice2", (2, -6.5, -3), (-0.1, 0, 0.4), (-1, -5, -1, 2, 5, 2)),
        ("ice3", (-1.5, -6.5, 1), (-0.25, 0, -0.2), (-1, -6, -1, 2, 6, 2)),
        ("ice4", (2.5, -6.5, 4.5), (-0.3, 0, 0.3), (-1, -4, -1, 2, 4, 2)),
        ("ice5", (-2, -6.5, 8), (-0.4, 0, -0.25), (-1, -3, -1, 2, 3, 2)),
    ]
    kids = []
    for i, (n, piv, rot, box) in enumerate(crystals):
        share = p + "ice1" if box[4] == 6 and n != "ice1" else None
        kids.append(Part(n, piv, rot, cubes=[Cube(p + n, *box, uv_of=share)]))
    body = Part("body", (0, 10, 0), cubes=[
        Cube(p + "body", -7, -6.5, -12, 14, 13, 24),
        Cube(p + "hump", -5.5, -9, -10, 11, 4, 10),
    ], children=kids)
    head = Part("head", (0, 7.5, -12), cubes=[
        Cube(p + "skull", -5, -5, -6, 10, 9, 7),
        Cube(p + "snout", -3, 0, -10, 6, 4, 4),
        Cube(p + "ear", -5.5, -7, -3, 3, 3, 2),
        Cube(p + "ear_l", 2.5, -7, -3, 3, 3, 2, mirror=True, uv_of=p + "ear"),
    ], children=[Part("crest", (0, -5, -2), (-0.3, 0, 0), cubes=[Cube(p + "crest", -1, -3, -1, 2, 3, 2)])])
    leg = [Cube(p + "leg", -3, 0, -3, 6, 9, 6), Cube(p + "claws", -3, 7, -4, 6, 2, 1)]
    legs = [Part("leg0", (-4.5, 15, 8), cubes=leg),
            Part("leg1", (4.5, 15, 8), cubes=mirror_x(leg, "_b")),
            Part("leg2", (-4.5, 15, -8), cubes=[Cube(p + "leg_c", -3, 0, -3, 6, 9, 6, uv_of=p + "leg"),
                                                Cube(p + "claws_c", -3, 7, -4, 6, 2, 1, uv_of=p + "claws")]),
            Part("leg3", (4.5, 15, -8), cubes=[Cube(p + "leg_d", -3, 0, -3, 6, 9, 6, mirror=True, uv_of=p + "leg"),
                                               Cube(p + "claws_d", -3, 7, -4, 6, 2, 1, mirror=True, uv_of=p + "claws")])]
    tail = Part("tail", (0, 6, 12), (0.4, 0, 0), cubes=[Cube(p + "tail", -1.5, -1, -1, 3, 3, 3)])
    return Model("oso", [body, head] + legs + [tail], tex_w=128)


# ====================================================================== HORMIGA SOLDADO (y la reina, con más abdomen)


def hormiga(queen=False):
    p = "q_" if queen else "a_"
    gaster = (-6, -5, 0, 12, 10, 15) if queen else (-4.5, -4, 0, 9, 8, 11)
    thorax = Part("thorax", (0, 16, 0), cubes=[
        Cube(p + "thorax", -3, -2.5, -4, 6, 5, 8),
        Cube(p + "petiole", -1.5, -1, 4, 3, 3, 2),
    ], children=[Part("gaster", (0, -0.5, 5.5), (-0.28, 0, 0), cubes=[Cube(p + "gaster", *gaster)])])
    head_cubes = [Cube(p + "head", -3.5, -3.5, -6, 7, 6, 6)]
    if queen:
        head_cubes += [Cube(p + "crown", -3, -5.5, -4, 6, 2, 3)]
    head = Part("head", (0, 15, -4), cubes=head_cubes, children=[
        Part("mandible_r", (-2, 1.5, -5.5), (0, -0.4, 0), cubes=[Cube(p + "mand", -1, -1, -4, 2, 2, 4)]),
        Part("mandible_l", (2, 1.5, -5.5), (0, 0.4, 0), cubes=[Cube(p + "mand_l", -1, -1, -4, 2, 2, 4, mirror=True, uv_of=p + "mand")]),
        Part("antenna_r", (-1.5, -3.5, -5), (0.5, 0, -0.35), cubes=[Cube(p + "ant", -0.5, -5, -0.5, 1, 5, 1)],
             children=[Part("antenna_r2", (0, -5, 0), (0.9, 0, 0), cubes=[Cube(p + "ant2", -0.5, -4, -0.5, 1, 4, 1)])]),
        Part("antenna_l", (1.5, -3.5, -5), (0.5, 0, 0.35), cubes=[Cube(p + "ant_l", -0.5, -5, -0.5, 1, 5, 1, mirror=True,
                                                                        uv_of=p + "ant")],
             children=[Part("antenna_l2", (0, -5, 0), (0.9, 0, 0), cubes=[Cube(p + "ant2_l", -0.5, -4, -0.5, 1, 4, 1, mirror=True,
                                                                                uv_of=p + "ant2")])]),
    ])
    legs = []
    for i, z in enumerate((-3, 0, 3)):
        for side in (-1, 1):
            nm = "leg%d%s" % (i, "r" if side < 0 else "l")
            yaw = (0.55, 0.0, -0.55)[i] * side
            femur = Cube(p + ("femur" if side < 0 else "femur_l"), -7 if side < 0 else 0, -1, -1, 7, 2, 2, mirror=side > 0,
                         uv_of=None if (side < 0 and i == 0) else p + "femur")
            tibia = Cube(p + ("tibia" if side < 0 else "tibia_l"), -1, 0, -1, 2, 11, 2, mirror=side > 0,
                         uv_of=None if (side < 0 and i == 0) else p + "tibia")
            femur.name = p + nm + "_f" if not (side < 0 and i == 0) else p + "femur"
            tibia.name = p + nm + "_t" if not (side < 0 and i == 0) else p + "tibia"
            legs.append(Part(nm, (3 * side, 16, z), (0, yaw, -0.45 * side), cubes=[femur], children=[
                Part(nm + "_lo", (7 * side, 0, 0), (0, 0, 0.2 * side), cubes=[tibia])]))
    return Model("reina_carmesi" if queen else "hormiga", [thorax, head] + legs, tex_w=128)


ALL = None


def all_models():
    """Modelo -> se usa para: (lista de nombres de textura)."""
    global ALL
    if ALL is None:
        ALL = {
            "goblin": goblin(),
            "orco": orco(),
            "caballero": caballero(),
            "guardian_juicio": guardian(),
            "coloso": coloso(),
            "lobo": lobo(),
            "oso": oso(),
            "hormiga": hormiga(),
            "reina_carmesi": hormiga(queen=True),
        }
    return ALL
