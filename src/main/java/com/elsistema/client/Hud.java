package com.elsistema.client;

import com.elsistema.Defs;
import com.elsistema.Net;
import com.elsistema.Shop;
import com.elsistema.Trees;
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
    private static int bigGlow = 0x9050B4FF;
    private static int bigFlash = 0x663A8DFF;
    private static long callStart = -1;
    private static String callText = "";
    private static int callColor = Ui.WHITE;
    private static long xpTime = -1;
    private static int xpAmount = 0;
    private static long goldTime = -1;
    private static int goldAmount = 0;
    private static long warnTime = -1;
    private static String warnText = "";
    private static long corpseStart = -1;
    private static long corpseUntil = -1;
    private static long corpseTotal = 1;
    private static long ariseTime = -1;
    private static int ariseCount = 0;

    private record Note(String text, int color, long start) {}

    private static final List<Note> NOTES = new ArrayList<>();

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

    private static void big(String title, String sub, int glow, int flash) {
        bigStart = System.currentTimeMillis();
        bigTitle = title;
        bigSub = sub;
        bigGlow = glow;
        bigFlash = flash;
    }

    private static void callout(String text, int color) {
        callStart = System.currentTimeMillis();
        callText = text;
        callColor = color;
    }

    private static void warn(String text) {
        warnTime = System.currentTimeMillis();
        warnText = text;
        error();
    }

    /** Aviso corto (sin sonido de error) que aparece debajo del panel. */
    private static void note(String text, int color) {
        NOTES.add(0, new Note(text, color, System.currentTimeMillis()));
        while (NOTES.size() > 3) {
            NOTES.remove(NOTES.size() - 1);
        }
    }

    private static String shadowName(int id) {
        ClientShadows.Entry e = ClientShadows.byId(id);
        return e == null ? "Una sombra" : "Tu sombra " + e.name();
    }

    /** Se llama al salir del mundo. */
    public static void reset() {
        TOASTS.clear();
        NOTES.clear();
        corpseUntil = -1;
        bigStart = -1;
        callStart = -1;
        warnTime = -1;
    }

    // ================================================================== avisos del servidor

    public static void onNotify(int kind, int a, int b) {
        Minecraft mc = Minecraft.getInstance();
        String v = key(ElSistemaClient.STATUS);
        long now = System.currentTimeMillis();
        switch (kind) {
            case Net.N_LEVEL_UP -> {
                big("¡SUBISTE DE NIVEL!", "Nivel " + a + "   ·   +" + b + " puntos de estadística   ·   +1 de habilidad", 0x9050B4FF, 0x663A8DFF);
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
                big("¡HAS DESPERTADO!", name(Defs.RACE_NAME, a) + "   ·   " + name(Defs.CLASS_NAME, b), 0x9050B4FF, 0x663A8DFF);
                toast("NOTIFICACIÓN", Ui.CYAN, "Ahora eres un Jugador.",
                        v + ": ventana del Sistema   ·   " + key(ElSistemaClient.SKILL) + ": habilidad",
                        key(ElSistemaClient.ARISE) + ": ¡Levántate! (sobre un cadáver)   ·   "
                                + key(ElSistemaClient.SHADOWS) + ": sombras");
            }
            case Net.N_XP -> {
                xpAmount = (xpTime > 0 && now - xpTime < 1500 ? xpAmount : 0) + a;
                xpTime = now;
            }
            case Net.N_COINS -> {
                goldAmount = (goldTime > 0 && now - goldTime < 1500 ? goldAmount : 0) + a;
                goldTime = now;
            }
            case Net.N_SKILL -> callout(name(Defs.SKILL_NAME, a).toUpperCase(Locale.ROOT), Ui.WHITE);
            case Net.N_COOLDOWN -> warn("Habilidad en enfriamiento: " + a + " s");
            case Net.N_NO_MP -> warn("Maná insuficiente (" + b + "/" + a + " MP)");
            case Net.N_RANK_UP -> toast("NOTIFICACIÓN", Defs.RANK_COLOR[Math.max(0, Math.min(5, a))],
                    "¡Tu rango de cazador ha subido!",
                    "Rango " + name(Defs.RANK, b) + "   »   Rango " + name(Defs.RANK, a));
            case Net.N_NOT_AWAKENED -> warn("Aún no has despertado. Pulsa " + v + ".");
            case Net.N_MAX_LEVEL -> toast("NOTIFICACIÓN", Ui.GOLD, "Has alcanzado el nivel máximo.",
                    "Ya no queda nadie por encima de ti.");
            case Net.N_WELCOME -> toast("NOTIFICACIÓN", Ui.CYAN, "Bienvenido de nuevo, Jugador.");
            case Net.N_GATE_OPEN -> toast("¡ALERTA!", Defs.RANK_COLOR[Math.max(0, Math.min(5, a))],
                    "Se ha abierto una puerta de mazmorra de rango " + name(Defs.RANK, a) + ".",
                    "Está a " + b + " bloques de ti. Entra en ella para empezar.");
            case Net.N_GATE_FAIL -> warn(a == 1 ? "La mazmorra no cabe aquí. Prueba en otro sitio." : "No hay sitio para abrir la puerta aquí.");
            case Net.N_DUNGEON_ENTER -> big("MAZMORRA DE RANGO " + name(Defs.RANK, a),
                    "Nivel " + b + "   ·   Elimina a todos los monstruos", 0x90B050FF, 0x55301060);
            case Net.N_WAVE -> callout("OLEADA " + a + "/" + b, Ui.CYAN);
            case Net.N_BOSS -> big("¡JEFE DE MAZMORRA!", "Rango " + name(Defs.RANK, a) + "   ·   Derrótalo para terminar", 0x90FF3030, 0x66B01010);
            case Net.N_DUNGEON_CLEAR -> {
                big("¡MAZMORRA COMPLETADA!", "+" + b + " de oro   ·   Recoge el botín: vuelves en 8 s", 0x90FFC857, 0x55FFC857);
                toast("RECOMPENSA", Ui.GOLD, "Mazmorra de rango " + name(Defs.RANK, a) + " superada.",
                        "Has recibido oro, EXP, núcleos de maná y botín.");
            }
            case Net.N_DUNGEON_FAIL -> toast("NOTIFICACIÓN", Ui.RED,
                    a == 1 ? "Se acabó el tiempo. La mazmorra se ha cerrado." : "Has salido de la mazmorra.");
            case Net.N_DODGE -> callout("¡ESQUIVADO!", Ui.CYAN);
            case Net.N_LAST_STAND -> callout("¡ÚLTIMA RESISTENCIA!", Ui.GOLD);
            case Net.N_RUNE -> toast("NOTIFICACIÓN", 0xFFB57BFF, "+1 punto de habilidad.",
                    "Tienes " + a + ". Úsalos en la pestaña Habilidades (" + v + ").");
            case Net.N_BOUGHT -> toast("TIENDA", Ui.GOLD, "Has comprado: "
                    + (a >= 0 && a < Shop.OFFERS.size() ? Shop.OFFERS.get(a).name() : "?") + (b > 1 ? " x" + b : "") + ".");
            case Net.N_NO_COINS -> warn("Oro insuficiente (" + b + "/" + a + ")");
            case Net.N_SOLD -> {
                if (a <= 0) {
                    warn("No tienes núcleos de maná para vender.");
                } else {
                    toast("TIENDA", Ui.GOLD, "Has vendido " + a + " núcleos de maná.", "+" + b + " de oro");
                }
            }
            case Net.N_TREE_UP -> {
                Trees.Node[] t = Trees.tree(a, CData.treeIndex(a));
                if (t != null && b >= 0 && b < t.length) callout(t[b].name().toUpperCase(Locale.ROOT), a == 2 ? Ui.SHADOW : Ui.PURPLE);
            }
            // ---------------------------------------------------------- sombras
            case Net.N_SHADOW_MARK -> ClientShadows.mark(a);
            case Net.N_CORPSE -> {
                if (CData.awakened()) {
                    corpseStart = now;
                    corpseTotal = Math.max(1, a) * 1000L;
                    corpseUntil = now + corpseTotal;
                    sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
                }
            }
            case Net.N_ARISE_OK -> {
                corpseUntil = -1;
                ariseCount = ariseTime > 0 && now - ariseTime < 1500 ? ariseCount + 1 : 1;
                ariseTime = now;
                big(ariseCount > 1 ? "¡LEVÁNTATE! x" + ariseCount : "¡LEVÁNTATE!",
                        "Una sombra de rango " + name(Defs.RANK, b) + " se une a tu ejército", 0x909A4DFF, 0x55200040);
            }
            case Net.N_ARISE_FAIL -> {
                if (a <= 0) corpseUntil = -1;
                warn(a > 0 ? "La extracción ha fallado. Quedan " + a + " intentos." : "La extracción ha fallado. El alma se ha desvanecido.");
            }
            case Net.N_ARISE_NO_MP -> warn("Maná insuficiente para la extracción (" + b + "/" + a + " MP)");
            case Net.N_NO_CORPSE -> warn("No hay ningún cadáver tuyo cerca para extraer.");
            case Net.N_ARMY_FULL -> warn("Tu ejército está lleno (" + a + "/" + b + "). Mejóralo en el árbol de sombras.");
            case Net.N_SHADOWS_OUT -> callout(a == 1 ? "SAL, SOMBRA" : "SALID, SOMBRAS (" + a + ")", Ui.SHADOW);
            case Net.N_SHADOWS_IN -> note(a == 1 ? "Una sombra ha vuelto a tu sombra." : a + " sombras han vuelto a tu sombra.", Ui.PURPLE);
            case Net.N_SHADOW_BACK -> note(shadowName(b) + " ha caído. Vuelve en " + a + " s.", 0xFFD08AFF);
            case Net.N_SHADOW_LEVEL -> note(shadowName(a) + " ha subido al nivel " + b + ".", Ui.SHADOW);
            case Net.N_NO_SHADOWS -> warn(a == 0 ? "Aún no tienes sombras. Mata una criatura y pulsa " + key(ElSistemaClient.ARISE) + "."
                    : "No hay sombras disponibles: están fuera o recuperándose.");
            case Net.N_RANK_REQ -> warn("Necesitas rango " + name(Defs.RANK, a) + " para comprar esto.");
            case Net.N_MANA -> note("+" + a + " de maná", Ui.CYAN);
            case Net.N_RETURN -> note(a == 1 ? "Has salido de la mazmorra." : "La piedra te ha devuelto a casa.", Ui.CYAN);
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
        bottom = warnLine(g, f, now, bottom);
        notes(g, f, now, bottom);
        floating(g, f, now);
        corpseHint(g, f, now, sw, sh);
        toasts(g, f, now, sw);
        big(g, f, now, sw, sh);
        callout(g, f, now, sw, sh);
    }

    /** Panel de la esquina: nivel, rango, maná, EXP, oro y habilidad. */
    private static int panel(GuiGraphics g, Font f, long now) {
        int x1 = PX;
        int y1 = PY;
        int x2 = PX + PW;
        boolean pts = CData.points() > 0;
        int y2 = y1 + (pts ? 90 : 80);
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

        Ui.text(g, f, "ORO", x1 + 5, y1 + 46, Ui.GOLD, 1F);
        Ui.text(g, f, String.valueOf(CData.coins()), bx, y1 + 46, Ui.WHITE, 1F);
        if (CData.skillPoints() > 0) {
            float pulse = 0.6F + 0.4F * (float) Math.sin(now / 300.0);
            String sp = CData.skillPoints() + " PH";
            Ui.glow(g, f, sp, x2 - 5 - f.width(sp), y1 + 46, 0xFFB57BFF, 0x50B57BFF, pulse);
        }

        Ui.text(g, f, "SOM", x1 + 5, y1 + 56, Ui.SHADOW, 1F);
        Ui.text(g, f, CData.shadowCount() + "/" + CData.shadowMax(), bx, y1 + 56, Ui.WHITE, 1F);
        int out = ClientShadows.out();
        String so = out > 0 ? out + " fuera  [" + key(ElSistemaClient.SHADOWS) + "]" : "[" + key(ElSistemaClient.SHADOWS) + "] invocar";
        Ui.text(g, f, so, x2 - 5 - f.width(so), y1 + 56, out > 0 ? Ui.PURPLE : Ui.MUTED, 1F);

        String skill = "[" + key(ElSistemaClient.SKILL) + "] " + name(Defs.SKILL_NAME, CData.cls());
        Ui.text(g, f, skill, x1 + 5, y1 + 66, Ui.WHITE, 1F);
        int cd = CData.cooldown();
        if (cd > 0) {
            String s = String.format(Locale.ROOT, "%.1fs", cd / 20F);
            Ui.text(g, f, s, x2 - 5 - f.width(s), y1 + 66, Ui.GRAY, 1F);
            float frac = cd / (float) CData.cooldownTotal();
            g.fill(x1 + 5, y1 + 76, x1 + 5 + Math.round((PW - 10) * frac), y1 + 77, Ui.fade(Ui.CYAN, 0.8F));
        } else {
            float pulse = 0.6F + 0.4F * (float) Math.sin(now / 250.0);
            String s = "LISTO";
            Ui.glow(g, f, s, x2 - 5 - f.width(s), y1 + 66, Ui.CYAN, Ui.GLOW, pulse);
        }
        if (pts) {
            float pulse = 0.6F + 0.4F * (float) Math.sin(now / 300.0);
            Ui.text(g, f, "+" + CData.points() + " puntos sin usar  [" + key(ElSistemaClient.STATUS) + "]",
                    x1 + 5, y1 + 80, Ui.GOLD, pulse);
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

    private static int warnLine(GuiGraphics g, Font f, long now, int y) {
        if (warnTime < 0) return y;
        long age = now - warnTime;
        if (age > 2200) {
            warnTime = -1;
            return y;
        }
        float a = 1F - Ui.clamp01((age - 1700) / 500F);
        Ui.glow(g, f, warnText, PX + 2, y + 6, Ui.RED, 0x50FF3030, a);
        return y + 12;
    }

    private static void notes(GuiGraphics g, Font f, long now, int y) {
        NOTES.removeIf(n -> now - n.start() > 3500);
        int yy = y + 6;
        for (Note n : NOTES) {
            long age = now - n.start();
            float a = Ui.clamp01(age / 150F) * (1F - Ui.clamp01((age - 2900) / 600F));
            int slide = Math.round((1F - Ui.easeOut(age / 250F)) * -20F);
            Ui.glow(g, f, n.text(), PX + 2 + slide, yy, n.color(), 0x40000000 | (n.color() & 0x00FFFFFF), a);
            yy += 11;
        }
    }

    /** «[G] ¡LEVÁNTATE!» bajo la mira mientras el cadáver todavía humea. */
    private static void corpseHint(GuiGraphics g, Font f, long now, int sw, int sh) {
        if (corpseUntil < 0) return;
        long left = corpseUntil - now;
        if (left <= 0) {
            corpseUntil = -1;
            return;
        }
        float a = Ui.clamp01((now - corpseStart) / 200F) * Ui.clamp01(left / 500F);
        float pulse = 0.75F + 0.25F * (float) Math.sin(now / 160.0);
        String s = "[" + key(ElSistemaClient.ARISE) + "]  ¡LEVÁNTATE!";
        int cx = sw / 2;
        int y = sh / 2 + 20;
        int w = f.width(s) + 18;
        Ui.panel(g, cx - w / 2, y - 4, cx + w / 2, y + 13, Ui.SHADOW, a * 0.95F);
        Ui.glow(g, f, s, cx - f.width(s) / 2, y, Ui.WHITE, 0xA07A3DFF, a * pulse);
        float frac = left / (float) corpseTotal;
        g.fill(cx - w / 2 + 2, y + 10, cx - w / 2 + 2 + Math.round((w - 4) * frac), y + 11, Ui.fade(Ui.SHADOW, a));
    }

    /** "+12 EXP" y "+5 oro" que suben y se desvanecen junto al panel. */
    private static void floating(GuiGraphics g, Font f, long now) {
        if (!CData.awakened()) return;
        if (xpTime >= 0) {
            long age = now - xpTime;
            if (age > 1600) {
                xpTime = -1;
                xpAmount = 0;
            } else {
                float a = 1F - Ui.clamp01((age - 1000) / 600F);
                int rise = Math.round(Ui.easeOut(age / 1600F) * 10F);
                Ui.glow(g, f, "+" + xpAmount + " EXP", PX + PW + 8, PY + 36 - rise, Ui.CYAN, Ui.GLOW, a);
            }
        }
        if (goldTime >= 0) {
            long age = now - goldTime;
            if (age > 1600) {
                goldTime = -1;
                goldAmount = 0;
            } else {
                float a = 1F - Ui.clamp01((age - 1000) / 600F);
                int rise = Math.round(Ui.easeOut(age / 1600F) * 10F);
                Ui.glow(g, f, "+" + goldAmount + " oro", PX + PW + 8, PY + 48 - rise, Ui.GOLD, 0x50FFC857, a);
            }
        }
    }

    /** Ventanas de notificación que se despliegan arriba en el centro (debajo de la barra de jefe). */
    private static void toasts(GuiGraphics g, Font f, long now, int sw) {
        TOASTS.removeIf(t -> now - t.start() > t.dur());
        int y = 44;
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

    /** Animación grande: destello, líneas que se abren y título que aparece de golpe. */
    private static void big(GuiGraphics g, Font f, long now, int sw, int sh) {
        if (bigStart < 0) return;
        long age = now - bigStart;
        if (age > 2800) {
            bigStart = -1;
            return;
        }
        float flash = 1F - Ui.clamp01(age / 650F);
        if (flash > 0F) {
            g.fill(0, 0, sw, sh, Ui.fade(bigFlash, flash));
        }
        float out = Ui.clamp01((2800 - age) / 500F);
        float cy = sh * 0.42F;
        float lw = Ui.easeOut(age / 450F) * sw * 0.40F;
        int lx1 = Math.round(sw / 2F - lw);
        int lx2 = Math.round(sw / 2F + lw);
        int lineColor = 0xFF000000 | (bigGlow & 0x00FFFFFF);
        g.fill(lx1, Math.round(cy - 20), lx2, Math.round(cy - 19), Ui.fade(lineColor, out));
        g.fill(lx1, Math.round(cy + 17), lx2, Math.round(cy + 18), Ui.fade(lineColor, out));
        float scale = 3.2F - 1.1F * Ui.easeOutBack(age / 380F);
        float ta = out * Ui.clamp01(age / 120F);
        Ui.bigCenter(g, f, bigTitle, sw / 2F, cy - 2F, scale, Ui.WHITE, bigGlow, ta);
        float sa = out * Ui.clamp01((age - 300F) / 300F);
        Ui.center(g, f, bigSub, sw / 2, Math.round(cy + 24), lineColor, sa);
    }

    /** Texto en grande en la parte baja (habilidades, oleadas, esquivar...). */
    private static void callout(GuiGraphics g, Font f, long now, int sw, int sh) {
        if (callStart < 0) return;
        long age = now - callStart;
        if (age > 1200) {
            callStart = -1;
            return;
        }
        float scale = 2.2F - 0.6F * Ui.easeOutBack(age / 220F);
        float a = Ui.clamp01(age / 80F) * Ui.clamp01((1200 - age) / 350F);
        Ui.bigCenter(g, f, "« " + callText + " »", sw / 2F, sh * 0.66F, scale, callColor, 0x9050B4FF, a);
    }
}
