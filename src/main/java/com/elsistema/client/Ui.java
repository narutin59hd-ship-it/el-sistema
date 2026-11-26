package com.elsistema.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Utilidades de dibujo con el estilo de las ventanas del Sistema (azul, brillo y esquinas). */
public final class Ui {
    private Ui() {}

    public static final int CYAN = 0xFF5FD7FF;
    public static final int MP_BLUE = 0xFF3D7BFF;
    public static final int HP_RED = 0xFFE34B4B;
    public static final int GOLD = 0xFFFFC857;
    public static final int RED = 0xFFFF5A5A;
    public static final int GREEN = 0xFF6BFF9A;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFF9FB3C8;
    public static final int MUTED = 0xFF7F95AA;
    public static final int BG = 0xE0060D18;
    public static final int GLOW = 0x7050B4FF;

    // ------------------------------------------------------------------ matemáticas

    public static float clamp01(float v) {
        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }

    public static float easeOut(float t) {
        t = clamp01(t);
        float u = 1F - t;
        return 1F - u * u * u;
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158F;
        float c3 = c1 + 1F;
        float u = t - 1F;
        return 1F + c3 * u * u * u + c1 * u * u;
    }

    /** Multiplica la transparencia de un color ARGB. */
    public static int fade(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int na = Math.round(base * clamp01(a));
        return (na << 24) | (color & 0x00FFFFFF);
    }

    private static int lighten(int c) {
        int r = (c >> 16) & 0xFF;
        int gr = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        r += (255 - r) / 2;
        gr += (255 - gr) / 2;
        b += (255 - b) / 2;
        return (c & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ formas

    public static void border(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        if (((c >>> 24) & 0xFF) == 0) return;
        g.fill(x1, y1, x2, y1 + 1, c);
        g.fill(x1, y2 - 1, x2, y2, c);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, c);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, c);
    }

    /** Esquinas en forma de L, típicas de las ventanas holográficas. */
    public static void corners(GuiGraphics g, int x1, int y1, int x2, int y2, int len, int c) {
        if (((c >>> 24) & 0xFF) == 0) return;
        g.fill(x1, y1, x1 + len, y1 + 2, c);
        g.fill(x1, y1, x1 + 2, y1 + len, c);
        g.fill(x2 - len, y1, x2, y1 + 2, c);
        g.fill(x2 - 2, y1, x2, y1 + len, c);
        g.fill(x1, y2 - 2, x1 + len, y2, c);
        g.fill(x1, y2 - len, x1 + 2, y2, c);
        g.fill(x2 - len, y2 - 2, x2, y2, c);
        g.fill(x2 - 2, y2 - len, x2, y2, c);
    }

    /** Ventana del Sistema: fondo azul oscuro, resplandor, líneas de escaneo, borde y esquinas. */
    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, int accent, float a) {
        if (a <= 0.01F || y2 - y1 < 2) return;
        for (int i = 4; i >= 1; i--) {
            border(g, x1 - i, y1 - i, x2 + i, y2 + i, fade(accent, a * 0.28F / i));
        }
        g.fill(x1, y1, x2, y2, fade(BG, a));
        int gh = Math.min(16, y2 - y1);
        g.fillGradient(x1, y1, x2, y1 + gh, fade(0x553A8DFF, a), fade(0x003A8DFF, a));
        for (int y = y1 + 2; y < y2 - 1; y += 3) {
            g.fill(x1 + 1, y, x2 - 1, y + 1, fade(0x0C5FD7FF, a));
        }
        border(g, x1, y1, x2, y2, fade(accent, a * 0.85F));
        if (y2 - y1 > 10) {
            corners(g, x1 - 2, y1 - 2, x2 + 2, y2 + 2, 8, fade(accent, a));
        }
    }

    /** Destello de luz que recorre la cabecera cada pocos segundos. */
    public static void shine(GuiGraphics g, int x1, int y1, int x2, int y2, long now, float a) {
        int period = 3200;
        float t = (now % period) / (float) period;
        int span = x2 - x1;
        int sx = x1 + Math.round((span + 30) * t) - 30;
        int cx1 = Math.max(x1 + 1, sx);
        int cx2 = Math.min(x2 - 1, sx + 18);
        if (cx2 > cx1) {
            g.fill(cx1, y1 + 1, cx2, y2, fade(0x225FD7FF, a));
        }
    }

    /** Línea horizontal que se desvanece hacia los lados. */
    public static void divider(GuiGraphics g, int x1, int x2, int y, float a) {
        int cx = (x1 + x2) / 2;
        int half = Math.max(1, (x2 - x1) / 2 - 10);
        for (int i = 0; i < half; i += 4) {
            float fa = a * (1F - (float) i / half) * 0.8F;
            g.fill(cx + i, y, cx + i + 4, y + 1, fade(CYAN, fa));
            g.fill(cx - i - 4, y, cx - i, y + 1, fade(CYAN, fa));
        }
    }

    /** Cabecera con el icono [!] y el título con letras separadas. */
    public static void header(GuiGraphics g, Font f, String title, int x1, int x2, int y, int accent, float a) {
        int cx = (x1 + x2) / 2;
        String t = spaced(title);
        int tw = f.width(t);
        int bx = cx - tw / 2 - 17;
        border(g, bx, y - 2, bx + 11, y + 9, fade(accent, a));
        center(g, f, "!", bx + 6, y, WHITE, a);
        glow(g, f, t, cx - tw / 2, y, WHITE, GLOW, a);
        divider(g, x1, x2, y + 12, a);
    }

    // ------------------------------------------------------------------ texto

    public static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int color, float a) {
        int ta = Math.round(((color >>> 24) & 0xFF) * clamp01(a));
        if (ta < 5) return;
        g.drawString(f, s, x, y, (ta << 24) | (color & 0x00FFFFFF), false);
    }

    /** Texto con un halo de color alrededor. */
    public static void glow(GuiGraphics g, Font f, String s, int x, int y, int color, int glowColor, float a) {
        int ga = Math.round(((glowColor >>> 24) & 0xFF) * clamp01(a));
        if (ga >= 5) {
            int gc = (ga << 24) | (glowColor & 0x00FFFFFF);
            g.drawString(f, s, x - 1, y, gc, false);
            g.drawString(f, s, x + 1, y, gc, false);
            g.drawString(f, s, x, y - 1, gc, false);
            g.drawString(f, s, x, y + 1, gc, false);
        }
        text(g, f, s, x, y, color, a);
    }

    public static void center(GuiGraphics g, Font f, String s, int cx, int y, int color, float a) {
        text(g, f, s, cx - f.width(s) / 2, y, color, a);
    }

    /** Texto grande centrado (con escala) y con halo. */
    public static void bigCenter(GuiGraphics g, Font f, String s, float cx, float cy, float scale, int color, int glowColor, float a) {
        if (a <= 0.02F) return;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        glow(g, f, s, -f.width(s) / 2, -4, color, glowColor, a);
        g.pose().popPose();
    }

    public static List<String> wrap(Font f, String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (f.width(test) > maxW && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    // ------------------------------------------------------------------ controles

    /** Barra con degradado y punta brillante. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color, float a) {
        if (w <= 0) return;
        g.fill(x, y, x + w, y + h, fade(0xC0101A28, a));
        int fw = Math.round(w * clamp01(frac));
        if (fw > 0) {
            g.fillGradient(x, y, x + fw, y + h, fade(lighten(color), a), fade(color, a));
            g.fill(x + fw - 1, y, x + fw, y + h, fade(0xFFFFFFFF, a * 0.8F));
        }
        border(g, x - 1, y - 1, x + w + 1, y + h + 1, fade(0x805FD7FF, a));
    }

    /** Botón del Sistema. Devuelve true si el ratón está encima. */
    public static boolean button(GuiGraphics g, Font f, String label, int x, int y, int w, int h,
                                 int mx, int my, int accent, boolean enabled, float a) {
        boolean hover = enabled && inside(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, fade(hover ? 0x903A8DFF : 0x70101A28, a));
        border(g, x, y, x + w, y + h, fade(enabled ? accent : 0xFF4A5560, a * (hover ? 1F : 0.85F)));
        if (hover) {
            border(g, x - 1, y - 1, x + w + 1, y + h + 1, fade(accent, a * 0.4F));
        }
        center(g, f, label, x + w / 2, y + (h - 8) / 2, enabled ? WHITE : 0xFF6A7580, a);
        return hover;
    }

    /** Ventanita de información junto al ratón (por encima de todo). */
    public static void tooltip(GuiGraphics g, Font f, List<String> lines, int mx, int my, int sw, int sh) {
        if (lines == null || lines.isEmpty()) return;
        int w = 0;
        for (String s : lines) {
            w = Math.max(w, f.width(s));
        }
        w += 12;
        int h = lines.size() * 10 + 8;
        int x = mx + 12;
        int y = my - 4;
        if (x + w > sw - 4) x = mx - w - 12;
        if (x < 4) x = 4;
        if (y + h > sh - 4) y = sh - h - 4;
        if (y < 4) y = 4;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        panel(g, x, y, x + w, y + h, CYAN, 1F);
        for (int i = 0; i < lines.size(); i++) {
            text(g, f, lines.get(i), x + 6, y + 5 + i * 10, i == 0 ? CYAN : WHITE, 1F);
        }
        g.pose().popPose();
    }
}
