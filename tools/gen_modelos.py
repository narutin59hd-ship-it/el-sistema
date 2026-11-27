"""Escribe src/main/java/com/elsistema/client/render/MonsterLayers.java a partir de modelos.py.

Uso (al cambiar un modelo): python3 tools/gen_modelos.py <carpeta del proyecto>
texturas.py comprueba en cada compilación que el archivo Java coincide con modelos.py.
"""
import os
import sys

import modelos

JAVA = "src/main/java/com/elsistema/client/render/MonsterLayers.java"

METHODS = [
    ("goblin", "goblin"), ("orco", "orco"), ("caballero", "caballero"), ("guardian", "guardian_juicio"),
    ("coloso", "coloso"), ("lobo", "lobo"), ("oso", "oso"), ("hormiga", "hormiga"), ("reina", "reina_carmesi"),
]


def fl(v):
    v = round(float(v), 4)
    if v == int(v):
        return "%d.0F" % int(v)
    return ("%r" % v) + "F"


def cubes_java(part, indent):
    if not part.cubes:
        return "CubeListBuilder.create()"
    out = ["CubeListBuilder.create()"]
    for c in part.cubes:
        line = ".texOffs(%d, %d)" % (c.u, c.v)
        if c.mirror:
            line += ".mirror()"
        dims = ", ".join(fl(v) for v in (c.x, c.y, c.z, c.w, c.h, c.d))
        if c.grow:
            line += ".addBox(%s, new CubeDeformation(%s))" % (dims, fl(c.grow))
        else:
            line += ".addBox(%s)" % dims
        if c.mirror:
            line += ".mirror(false)"
        out.append(line)
    return ("\n" + " " * (indent + 8)).join(out)


def pose_java(part):
    px, py, pz = part.pivot
    rx, ry, rz = part.rot
    if rx == 0 and ry == 0 and rz == 0:
        return "PartPose.offset(%s, %s, %s)" % (fl(px), fl(py), fl(pz))
    return "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(fl(v) for v in (px, py, pz, rx, ry, rz))


def part_java(part, parent_var, lines, counter, indent=8):
    counter[0] += 1
    var = "p%d" % counter[0]
    pad = " " * indent
    code = "%sPartDefinition %s = %s.addOrReplaceChild(\"%s\", %s,\n%s        %s);" % (
        pad, var, parent_var, part.name, cubes_java(part, indent), pad, pose_java(part))
    lines.append(code)
    for ch in part.children:
        part_java(ch, var, lines, counter, indent)


def generate():
    models = modelos.all_models()
    out = []
    out.append("package com.elsistema.client.render;")
    out.append("")
    out.append("import net.minecraft.client.model.geom.PartPose;")
    out.append("import net.minecraft.client.model.geom.builders.CubeDeformation;")
    out.append("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    out.append("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    out.append("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    out.append("import net.minecraft.client.model.geom.builders.PartDefinition;")
    out.append("")
    out.append("/**")
    out.append(" * Modelos de los monstruos (desde la 1.5.0). ARCHIVO GENERADO por tools/gen_modelos.py a partir de")
    out.append(" * tools/modelos.py: no lo edites a mano (texturas.py comprueba que coincide con las texturas).")
    out.append(" */")
    out.append("@SuppressWarnings(\"unused\")")
    out.append("public final class MonsterLayers {")
    out.append("    private MonsterLayers() {}")
    for method, key in METHODS:
        m = models[key]
        out.append("")
        out.append("    /** %s (textura %dx%d, dibujada a %dx%d). */" % (key, m.tex_w, m.tex_h, m.png_size()[0], m.png_size()[1]))
        out.append("    public static LayerDefinition %s() {" % method)
        out.append("        MeshDefinition mesh = new MeshDefinition();")
        out.append("        PartDefinition root = mesh.getRoot();")
        lines = []
        counter = [0]
        for p in m.parts:
            part_java(p, "root", lines, counter)
        out.extend(lines)
        out.append("        return LayerDefinition.create(mesh, %d, %d);" % (m.tex_w, m.tex_h))
        out.append("    }")
    out.append("}")
    return "\n".join(out) + "\n"


def check(root):
    """True si el Java del proyecto coincide con modelos.py."""
    path = os.path.join(root, JAVA)
    if not os.path.exists(path):
        return False
    with open(path, encoding="utf-8") as f:
        return f.read() == generate()


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    with open(os.path.join(root, JAVA), "w", encoding="utf-8") as f:
        f.write(generate())
    print("escrito", JAVA)
