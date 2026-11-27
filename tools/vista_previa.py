"""Vista previa (solo para desarrollo, necesita Pillow): dibuja un modelo de modelos.py con su textura,
igual que Minecraft reparte las UV, para revisar formas y texturas sin abrir el juego.

Uso: python3 tools/vista_previa.py <modelo> <textura.png> <salida.png> [yaw_grados] [pitch_grados] [escala]
"""
import math
import sys

from PIL import Image, ImageDraw

import modelos


def rot_matrix(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    rxm = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    rym = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rzm = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat_mul(rzm, mat_mul(rym, rxm))


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, t, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + t[i] for i in range(3))


def cube_faces(c):
    x0, y0, z0 = c.x - c.grow, c.y - c.grow, c.z - c.grow
    x1, y1, z1 = c.x + c.w + c.grow, c.y + c.h + c.grow, c.z + c.d + c.grow
    if c.mirror:
        x0, x1 = x1, x0
    v7, v0, v1, v2 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    v3, v4, v5, v6 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + 2 * w, u + 2 * d + w, u + 2 * d + 2 * w
    f10, f11, f12 = v, v + d, v + d + h
    return [
        ("top", [v4, v3, v7, v0], f5, f10, f6, f11),
        ("bottom", [v1, v2, v6, v5], f6, f11, f7, f10),
        ("right", [v7, v3, v6, v2], f4, f11, f5, f12),
        ("front", [v0, v7, v2, v1], f5, f11, f6, f12),
        ("left", [v4, v0, v1, v5], f6, f11, f8, f12),
        ("back", [v3, v4, v5, v6], f8, f11, f9, f12),
    ]


SHADE = {"top": 1.0, "bottom": 0.55, "front": 0.92, "back": 0.7, "right": 0.8, "left": 0.8}


def render(model, tex, yaw=-30.0, pitch=12.0, scale=10, size=None):
    D = modelos.DENSITY
    tw, th = tex.size
    px = tex.load()
    cam = rot_matrix(math.radians(pitch), 0, 0)
    cam = mat_mul(cam, rot_matrix(0, math.radians(yaw), 0))
    quads = []

    def visit(part, m, t):
        lm = rot_matrix(*part.rot)
        nm = mat_mul(m, lm)
        nt = apply(m, t, part.pivot)
        for c in part.cubes:
            for name, verts, u1, v1, u2, v2 in cube_faces(c):
                wv = [apply(nm, nt, p) for p in verts]
                # vértices: 0=(u2,v1) 1=(u1,v1) 2=(u1,v2) 3=(u2,v2)
                pu1, pv1, pu2, pv2 = int(round(u1 * D)), int(round(v1 * D)), int(round(u2 * D)), int(round(v2 * D))
                nu = abs(pu2 - pu1)
                nv = abs(pv2 - pv1)
                if nu == 0 or nv == 0:
                    continue

                def at(s, tt):
                    a = [(1 - s) * (1 - tt), s * (1 - tt), (1 - s) * tt, s * tt]
                    pts = [wv[1], wv[0], wv[2], wv[3]]
                    return tuple(sum(a[k] * pts[k][i] for k in range(4)) for i in range(3))

                su = 1 if pu2 > pu1 else -1
                sv = 1 if pv2 > pv1 else -1
                for j in range(nv):
                    for i in range(nu):
                        tx = pu1 + i * su + (0 if su > 0 else -1)
                        ty = pv1 + j * sv + (0 if sv > 0 else -1)
                        if not (0 <= tx < tw and 0 <= ty < th):
                            continue
                        col = px[tx, ty]
                        if len(col) == 4 and col[3] < 8:
                            continue
                        s0, s1 = i / nu, (i + 1) / nu
                        t0, t1 = j / nv, (j + 1) / nv
                        corners = [at(s0, t0), at(s1, t0), at(s1, t1), at(s0, t1)]
                        quads.append((corners, col, SHADE[name]))
        for ch in part.children:
            visit(ch, nm, nt)

    ident = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    for p in model.parts:
        visit(p, ident, (0, 0, 0))
    proj = []
    for corners, col, shade in quads:
        cc = [apply(cam, (0, 0, 0), (p[0], p[1] - 12, p[2])) for p in corners]
        depth = sum(p[2] for p in cc) / 4.0
        proj.append((depth, [(p[0], p[1]) for p in cc], col, shade))
    xs = [x for _, pts, _, _ in proj for x, _ in pts]
    ys = [y for _, pts, _, _ in proj for _, y in pts]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    W = int((maxx - minx) * scale) + 20
    H = int((maxy - miny) * scale) + 20
    img = Image.new("RGBA", (W, H), (40, 44, 52, 255))
    dr = ImageDraw.Draw(img)
    proj.sort(key=lambda q: -q[0])
    for depth, pts, col, shade in proj:
        poly = [((x - minx) * scale + 10, (y - miny) * scale + 10) for x, y in pts]
        c = tuple(int(v * shade) for v in col[:3]) + (255,)
        dr.polygon(poly, fill=c)
    return img


def sheet(model, tex_path, out, views=((-25, 10), (155, 10), (-90, 0)), scale=8):
    tex = Image.open(tex_path).convert("RGBA")
    imgs = [render(model, tex, y, p, scale) for y, p in views]
    W = sum(i.size[0] for i in imgs) + 10 * (len(imgs) - 1)
    H = max(i.size[1] for i in imgs)
    out_img = Image.new("RGBA", (W, H), (30, 32, 38, 255))
    x = 0
    for i in imgs:
        out_img.paste(i, (x, H - i.size[1]))
        x += i.size[0] + 10
    out_img.save(out)


if __name__ == "__main__":
    m = modelos.all_models()[sys.argv[1]]
    if len(sys.argv) > 4:
        tex = Image.open(sys.argv[2]).convert("RGBA")
        render(m, tex, float(sys.argv[4]), float(sys.argv[5]) if len(sys.argv) > 5 else 10.0,
               int(sys.argv[6]) if len(sys.argv) > 6 else 10).save(sys.argv[3])
    else:
        sheet(m, sys.argv[2], sys.argv[3])
