package com.elsistema.client;

import com.elsistema.Defs;
import com.elsistema.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** HUD del Sistema: panel de estado, notificaciones y animaciones. */
public final class Hud {
    private Hud() {}

    private record Toast(String title, List<String> lines, int color, long start, long dur) {}

    private static final List<Toast> TOASTS = new ArrayList<>();
    private static final int PX = 6;
    private static final int PY = 6;
    private static final int PW = 156;

    private static long bigStart = -1;
    private static String bigTitle = "";
    private static String bigSub = "";
    private static long callStart = -1;
    private static String callText = "";
    private static long xpTime = -1;
    private static int xpAmount = 0;
    private static long warnTime = -1;
    private static String warnText = "";

    // ================================================================== utilidades

    public static String name(String[] arr, int i) {
        return i >= 0 && i < arr.length ? arr[i] : "?";
    }

    public static String key(KeyMapping k) {
        return k == null ? "?" : k.getTranslatedKeyMessage().getString();
    }

    public static void sound(SoundEvent s, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(s, volume, pitch);
        }
    }

    public static void click() {
        sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35F, 1.8F);
    }

    public static void error() {
        sound(SoundEvents.DISPENSER_FAIL, 0.6F, 1.2F);
    }

    public static void ding() {
        sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
    }

    public static void toast(String title, int color, String... lines) {
        long now = System.currentTimeMillis();
        TOASTS.add(0, new Toast(title, List.of(lines), color, now, 4200));
        while (TOASTS.size() > 3) {
            TOASTS.remove(TOASTS.size() - 1);
        }
        ding();
    }

    private static void big(String title, String sub) {
        bigStart = System.currentTimeMillis();
        bigTitle = title;
        bigSub = sub;
    }

    private static void warn(String text) {
        warnTime = System.currentTimeMillis();
        warnText = text;
        error();
    }

    // ================================================================== avisos del servidor

    public static void onNotify(int kind, int a, int b) {
        Minecraft mc = Minecraft.getInstance();
        String v = key(ElSistemaClient.STATUS);
        switch (kind) {
            case Net.N_LEVEL_UP -> {
                big("¡SUBISTE DE NIVEL!", "Nivel " + a + "   ·   +" + b + " puntos de estadística");
                toast("NOTIFICACIÓN", Ui.CYAN, "Has subido al nivel " + a + ".",
                        "Tu salud y tu maná se han restaurado.",
                        "Pulsa " + v + " para repartir tus puntos.");
            }
            case Net.N_AWAKEN_PROMPT -> {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(new AwakeningScreen());
                } else {
                    toast("NOTIFICACIÓN", Ui.CYAN, "Tienes una misión pendiente.",
                            "Pulsa " + v + " para abrir la ventana del Sistema.");
                }
            }
            case Net.N_AWAKENED -> {
                big("¡HAS DESPERTADO!", name(Defs.RACE_NAME, a) + "   ·   " + name(Defs.CLASS_NAME, b));
                toast("NOTIFICACIÓN", Ui.CYAN, "Ahora eres un Jugador.",
                        v + ": ventana de estado   ·   " + key(ElSistemaClient.SKILL) + ": habilidad");
            }
            case Net.N_XP -> {
                long now = System.currentTimeMillis();
                xpAmount = (xpTime > 0 && now - xpTime < 1500 ? xpAmount : 0) + a;
                xpTime = now;
            }
            case Net.N_SKILL -> {
                callStart = System.currentTimeMillis();
                callText = name(Defs.SKILL_NAME, a).toUpperCase(Locale.ROOT);
            }
            case Net.N_COOLDOWN -> warn("Habilidad en enfriamiento: " + a + " s");
            case Net.N_NO_MP -> warn("Maná insuficiente (" + b + "/" + a + " MP)");
            case Net.N_RANK_UP -> toast("NOTIFICACIÓN", Defs.RANK_COLOR[Math.max(0, Math.min(5, a))],
                    "¡Tu rango de cazador ha subido!",
                    "Rango " + name(Defs.RANK, b) + "   »   Rango " + name(Defs.RANK, a));
            case Net.N_NOT_AWAKENED -> warn("Aún no has despertado. Pulsa " + v + ".");
            case Net.N_MAX_LEVEL -> toast("NOTIFICACIÓN", Ui.GOLD, "Has alcanzado el nivel máximo.",
                    "Ya no queda nadie por encima de ti.");
            case Net.N_WELCOME -> toast("NOTIFICACIÓN", Ui.CYAN, "Bienvenido de nuevo, Jugador.");
            default -> {
            }
        }
    }

    // ================================================================== dibujo

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font f = mc.font;
        long now = System.currentTimeMillis();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int bottom = CData.awakened() ? panel(g, f, now) : hint(g, f, now);
        warnLine(g, f, now, bottom);
        xpGain(g, f, now);
        toasts(g, f, now, sw);
        big(g, f, now, sw, sh);
        callout(g, f, now, sw, sh);
    }

    /** Panel de la esquina: nivel, rango, maná, EXP y habilidad. */
    private static int panel(GuiGraphics g, Font f, long now) {
        int x1 = PX;
        int y1 = PY;
        int x2 = PX + PW;
        boolean pts = CData.points() > 0;
        int y2 = y1 + (pts ? 70 : 60);
        Ui.panel(g, x1, y1, x2, y2, Ui.CYAN, 0.9F);
        Ui.shine(g, x1, y1, x2, y1 + 14, now, 0.9F);

        int lvl = CData.level();
        Ui.glow(g, f, "Nv. " + lvl, x1 + 5, y1 + 4, Ui.WHITE, Ui.GLOW, 1F);
        int r = Defs.rank(lvl);
        String rk = "RANGO " + Defs.RANK[r];
        Ui.glow(g, f, rk, x2 - 5 - f.width(rk), y1 + 4, Defs.RANK_COLOR[r], 0x40FFFFFF, 1F);
        String sub = name(Defs.CLASS_NAME, CData.cls()) + " · " + name(Defs.RACE_NAME, CData.race());
        Ui.text(g, f, sub.toUpperCase(Locale.ROOT), x1 + 5, y1 + 15, Ui.GRAY, 1F);

        int bx = x1 + 25;
        int bw = 72;
        int tx = bx + bw + 5;
        Ui.text(g, f, "MP", x1 + 5, y1 + 26, Ui.CYAN, 1F);
        Ui.bar(g, bx, y1 + 27, bw, 5, CData.mp() / (float) CData.maxMp(), Ui.MP_BLUE, 1F);
        Ui.text(g, f, CData.mp() + "/" + CData.maxMp(), tx, y1 + 26, Ui.WHITE, 1F);

        boolean max = lvl >= Defs.MAX_LEVEL;
        Ui.text(g, f, "EXP", x1 + 5, y1 + 36, Ui.CYAN, 1F);
        Ui.bar(g, bx, y1 + 37, bw, 5, max ? 1F : CData.xp() / (float) CData.need(), Ui.CYAN, 1F);
        Ui.text(g, f, max ? "MÁX" : CData.xp() + "/" + CData.need(), tx, y1 + 36, Ui.WHITE, 1F);

        String skill = "[" + key(ElSistemaClient.SKILL) + "] " + name(Defs.SKILL_NAME, CData.cls());
        Ui.text(g, f, skill, x1 + 5, y1 + 46, Ui.WHITE, 1F);
        int cd = CData.cooldown();
        if (cd > 0) {
            String s = String.format(Locale.ROOT, "%.1fs", cd / 20F);
            Ui.text(g, f, s, x2 - 5 - f.width(s), y1 + 46, Ui.GRAY, 1F);
            float frac = cd / (float) CData.cooldownTotal();
            g.fill(x1 + 5, y1 + 56, x1 + 5 + Math.round((PW - 10) * frac), y1 + 57, Ui.fade(Ui.CYAN, 0.8F));
        } else {
            float pulse = 0.6F + 0.4F * (float) Math.sin(now / 250.0);
            String s = "LISTO";
            Ui.glow(g, f, s, x2 - 5 - f.width(s), y1 + 46, Ui.CYAN, Ui.GLOW, pulse);
        }
        if (pts) {
            float pulse = 0.6F + 0.4F * (float) Math.sin(now / 300.0);
            Ui.text(g, f, "+" + CData.points() + " puntos sin usar  [" + key(ElSistemaClient.STATUS) + "]",
                    x1 + 5, y1 + 60, Ui.GOLD, pulse);
        }
        return y2;
    }

    /** Aviso para quien todavía no ha despertado. */
    private static int hint(GuiGraphics g, Font f, long now) {
        float pulse = 0.65F + 0.35F * (float) Math.sin(now / 300.0);
        String s = "[" + key(ElSistemaClient.STATUS) + "] El Sistema te está esperando...";
        int w = f.width(s) + 14;
        Ui.panel(g, PX, PY, PX + w, PY + 18, Ui.CYAN, pulse);
        Ui.text(g, f, s, PX + 7, PY + 5, Ui.WHITE, pulse);
        return PY + 18;
    }

    private static void warnLine(GuiGraphics g, Font f, long now, int y) {
        if (warnTime < 0) return;
        long age = now - warnTime;
        if (age > 1600) {
            warnTime = -1;
            return;
        }
        float a = 1F - Ui.clamp01((age - 1100) / 500F);
        Ui.glow(g, f, warnText, PX + 2, y + 6, Ui.RED, 0x50FF3030, a);
    }

    /** "+12 EXP" que sube y se desvanece junto al panel. */
    private static void xpGain(GuiGraphics g, Font f, long now) {
        if (xpTime < 0 || !CData.awakened()) return;
        long age = now - xpTime;
        if (age > 1600) {
            xpTime = -1;
            xpAmount = 0;
            return;
        }
        float a = 1F - Ui.clamp01((age - 1000) / 600F);
        int rise = Math.round(Ui.easeOut(age / 1600F) * 10F);
        Ui.glow(g, f, "+" + xpAmount + " EXP", PX + PW + 8, PY + 36 - rise, Ui.CYAN, Ui.GLOW, a);
    }

    /** Ventanas de notificación que se despliegan arriba en el centro. */
    private static void toasts(GuiGraphics g, Font f, long now, int sw) {
        TOASTS.removeIf(t -> now - t.start() > t.dur());
        int y = 30;
        for (Toast t : TOASTS) {
            long age = now - t.start();
            float in = Ui.easeOut(age / 280F);
            float a = Ui.clamp01((t.dur() - age) / 400F);
            int w = f.width(Ui.spaced(t.title())) + 60;
            for (String l : t.lines()) {
                w = Math.max(w, f.width(l) + 24);
            }
            w = Math.max(w, 180);
            int h = 24 + t.lines().size() * 11;
            int x1 = (sw - w) / 2;
            int x2 = x1 + w;
            int cy = y + h / 2;
            int hh = Math.max(1, Math.round(h * in / 2F));
            Ui.panel(g, x1, cy - hh, x2, cy + hh, t.color(), a);
            float ca = a * Ui.clamp01((in - 0.6F) / 0.4F);
            if (ca > 0F) {
                Ui.header(g, f, t.title(), x1, x2, y + 6, t.color(), ca);
                for (int i = 0; i < t.lines().size(); i++) {
                    Ui.center(g, f, t.lines().get(i), (x1 + x2) / 2, y + 22 + i * 11, Ui.WHITE, ca);
                }
            }
            y += h + 8;
        }
    }

    /** Animación grande: destello azul, líneas que se abren y título que aparece de golpe. */
    private static void big(GuiGraphics g, Font f, long now, int sw, int sh) {
        if (bigStart < 0) return;
        long age = now - bigStart;
        if (age > 2800) {
            bigStart = -1;
            return;
        }
        float flash = 1F - Ui.clamp01(age / 650F);
        if (flash > 0F) {
            g.fill(0, 0, sw, sh, Ui.fade(0x663A8DFF, flash));
        }
        float out = Ui.clamp01((2800 - age) / 500F);
        float cy = sh * 0.42F;
        float lw = Ui.easeOut(age / 450F) * sw * 0.40F;
        int lx1 = Math.round(sw / 2F - lw);
        int lx2 = Math.round(sw / 2F + lw);
        g.fill(lx1, Math.round(cy - 20), lx2, Math.round(cy - 19), Ui.fade(Ui.CYAN, out));
        g.fill(lx1, Math.round(cy + 17), lx2, Math.round(cy + 18), Ui.fade(Ui.CYAN, out));
        float scale = 3.4F - 1.2F * Ui.easeOutBack(age / 380F);
        float ta = out * Ui.clamp01(age / 120F);
        Ui.bigCenter(g, f, bigTitle, sw / 2F, cy - 2F, scale, Ui.WHITE, 0x9050B4FF, ta);
        float sa = out * Ui.clamp01((age - 300F) / 300F);
        Ui.center(g, f, bigSub, sw / 2, Math.round(cy + 24), Ui.CYAN, sa);
    }

    /** Nombre de la habilidad en grande al usarla. */
    private static void callout(GuiGraphics g, Font f, long now, int sw, int sh) {
        if (callStart < 0) return;
        long age = now - callStart;
        if (age > 1200) {
            callStart = -1;
            return;
        }
        float scale = 2.2F - 0.6F * Ui.easeOutBack(age / 220F);
        float a = Ui.clamp01(age / 80F) * Ui.clamp01((1200 - age) / 350F);
        Ui.bigCenter(g, f, "« " + callText + " »", sw / 2F, sh * 0.66F, scale, Ui.WHITE, 0x9050B4FF, a);
    }
}
