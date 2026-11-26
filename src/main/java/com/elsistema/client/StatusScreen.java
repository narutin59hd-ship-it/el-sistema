package com.elsistema.client;

import com.elsistema.Defs;
import com.elsistema.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ventana de estado del Jugador (tecla V). */
public class StatusScreen extends Screen {
    private static final int Y_NAME = 28;
    private static final int Y_RANK = 40;
    private static final int Y_RACE = 52;
    private static final int Y_DIV1 = 65;
    private static final int Y_HP = 71;
    private static final int Y_EXP = 83;
    private static final int Y_DIV2 = 96;
    private static final int Y_STATS = 102;
    private static final int Y_DIV3 = 173;
    private static final int Y_SKILL = 179;
    private static final int PLUS_X = 112;

    private final long openTime = System.currentTimeMillis();

    public StatusScreen() {
        super(Component.literal("Estado"));
        Hud.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 2.0F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int pw() {
        return Math.min(320, this.width - 16);
    }

    private int ph() {
        return Math.min(232, this.height - 12);
    }

    private int px() {
        return (this.width - pw()) / 2;
    }

    private int py() {
        return (this.height - ph()) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        long now = System.currentTimeMillis();
        float p = Ui.easeOut((now - openTime) / 280F);
        g.fill(0, 0, this.width, this.height, Ui.fade(0x99000208, p));
        int x1 = px();
        int y1 = py();
        int x2 = x1 + pw();
        int y2 = y1 + ph();
        int cy = (y1 + y2) / 2;
        int hh = Math.max(1, Math.round((y2 - y1) * p / 2F));
        Ui.panel(g, x1, cy - hh, x2, cy + hh, Ui.CYAN, Math.min(1F, p * 1.5F));
        float a = Ui.clamp01((p - 0.55F) / 0.45F);
        if (a <= 0F) return;
        g.enableScissor(x1, cy - hh, x2, cy + hh);
        Ui.shine(g, x1, y1, x2, y1 + 16, now, a);
        List<String> tip = content(g, x1, y1, x2, y2, mx, my, a, now);
        g.disableScissor();
        if (tip != null && a >= 1F) {
            Ui.tooltip(g, this.font, tip, mx, my, this.width, this.height);
        }
    }

    private static void label(GuiGraphics g, Font f, String key, String value, int x, int y, int color, float a) {
        String k = key + ":";
        Ui.text(g, f, k, x, y, Ui.GRAY, a);
        Ui.glow(g, f, value, x + f.width(k) + 5, y, color, 0x4050B4FF, a);
    }

    private List<String> content(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        List<String> tip = null;
        Ui.header(g, f, "ESTADO", x1, x2, y1 + 7, Ui.CYAN, a);
        int lx = x1 + 12;
        int mid = x1 + (x2 - x1) / 2 + 4;
        int rx = x2 - 12;

        // --- Datos generales
        String name = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getName().getString() : "?";
        label(g, f, "NOMBRE", name, lx, y1 + Y_NAME, Ui.WHITE, a);
        label(g, f, "NIVEL", String.valueOf(CData.level()), mid, y1 + Y_NAME, Ui.WHITE, a);
        int r = Defs.rank(CData.level());
        label(g, f, "RANGO", Defs.RANK[r], lx, y1 + Y_RANK, Defs.RANK_COLOR[r], a);
        label(g, f, "CLASE", Hud.name(Defs.CLASS_NAME, CData.cls()), mid, y1 + Y_RANK, Ui.WHITE, a);
        label(g, f, "RAZA", Hud.name(Defs.RACE_NAME, CData.race()), lx, y1 + Y_RACE, Ui.WHITE, a);
        label(g, f, "PUNTOS", String.valueOf(CData.points()), mid, y1 + Y_RACE,
                CData.points() > 0 ? Ui.GOLD : Ui.WHITE, a);
        if (Ui.inside(mx, my, mid, y1 + Y_RANK - 1, rx - mid, 10) && CData.cls() >= 0) {
            tip = classTip(CData.cls());
        }
        if (Ui.inside(mx, my, lx, y1 + Y_RACE - 1, mid - lx - 8, 10) && CData.race() >= 0) {
            tip = raceTip(CData.race());
        }
        Ui.divider(g, x1, x2, y1 + Y_DIV1, a);

        // --- HP y MP
        float hp = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getHealth() : 0F;
        float mhp = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getMaxHealth() : 20F;
        String hpText = Math.round(hp) + "/" + Math.round(mhp);
        Ui.text(g, f, "HP", lx, y1 + Y_HP, Ui.HP_RED, a);
        int hbw = (mid - 10) - (lx + 18) - f.width(hpText) - 6;
        Ui.bar(g, lx + 18, y1 + Y_HP + 1, hbw, 5, hp / Math.max(1F, mhp), Ui.HP_RED, a);
        Ui.text(g, f, hpText, lx + 18 + hbw + 6, y1 + Y_HP, Ui.WHITE, a);

        String mpText = CData.mp() + "/" + CData.maxMp();
        Ui.text(g, f, "MP", mid, y1 + Y_HP, Ui.CYAN, a);
        int mbw = rx - (mid + 18) - f.width(mpText) - 6;
        Ui.bar(g, mid + 18, y1 + Y_HP + 1, mbw, 5, CData.mp() / (float) CData.maxMp(), Ui.MP_BLUE, a);
        Ui.text(g, f, mpText, rx - f.width(mpText), y1 + Y_HP, Ui.WHITE, a);

        // --- EXP
        boolean max = CData.level() >= Defs.MAX_LEVEL;
        String xpText = max ? "NIVEL MÁXIMO" : CData.xp() + " / " + CData.need();
        Ui.text(g, f, "EXP", lx, y1 + Y_EXP, Ui.CYAN, a);
        int ebw = rx - (lx + 24) - f.width(xpText) - 6;
        Ui.bar(g, lx + 24, y1 + Y_EXP + 1, ebw, 5, max ? 1F : CData.xp() / (float) CData.need(), Ui.CYAN, a);
        Ui.text(g, f, xpText, rx - f.width(xpText), y1 + Y_EXP, Ui.WHITE, a);
        Ui.divider(g, x1, x2, y1 + Y_DIV2, a);

        // --- Estadísticas
        boolean canAdd = CData.points() > 0;
        for (int i = 0; i < 5; i++) {
            int ry = y1 + Y_STATS + i * 14;
            Ui.text(g, f, Defs.STAT_NAME[i] + ":", lx, ry, Ui.GRAY, a);
            Ui.glow(g, f, String.valueOf(CData.stat(i)), lx + 84, ry, Ui.WHITE, 0x5050B4FF, a);
            if (canAdd) {
                Ui.button(g, f, "+", lx + PLUS_X, ry - 2, 13, 12, mx, my, Ui.GOLD, true, a);
            }
            if (Ui.inside(mx, my, lx, ry - 2, PLUS_X - 2, 13)) {
                tip = statTip(i);
            }
        }
        int bx1 = mid;
        int by1 = y1 + Y_STATS - 3;
        int bx2 = rx;
        int by2 = y1 + Y_STATS + 64;
        Ui.border(g, bx1, by1, bx2, by2, Ui.fade(0xFF3C5A74, a));
        int bcx = (bx1 + bx2) / 2;
        Ui.center(g, f, "PUNTOS DISPONIBLES", bcx, by1 + 7, Ui.GRAY, a);
        float pulse = canAdd ? 0.7F + 0.3F * (float) Math.sin(now / 250.0) : 1F;
        Ui.bigCenter(g, f, String.valueOf(CData.points()), bcx, by1 + 32F, 2.5F,
                canAdd ? Ui.GOLD : Ui.WHITE, 0x60FFC857, a * pulse);
        Ui.center(g, f, canAdd ? "Mayús + clic: +5" : "Sube de nivel para ganar más", bcx, by1 + 54, Ui.MUTED, a);
        Ui.divider(g, x1, x2, y1 + Y_DIV3, a);

        // --- Habilidad
        int c = CData.cls();
        if (c >= 0 && c < Defs.SKILL_NAME.length) {
            String k = "HABILIDAD [" + Hud.key(ElSistemaClient.SKILL) + "]:";
            Ui.text(g, f, k, lx, y1 + Y_SKILL, Ui.GRAY, a);
            Ui.glow(g, f, Defs.SKILL_NAME[c], lx + f.width(k) + 5, y1 + Y_SKILL, Ui.CYAN, 0x5050B4FF, a);
            String cost = Defs.SKILL_COST[c] + " MP  ·  " + (Defs.SKILL_CD[c] / 20) + " s";
            Ui.text(g, f, cost, rx - f.width(cost), y1 + Y_SKILL, Ui.MUTED, a);
            int yy = y1 + Y_SKILL + 12;
            for (String l : Ui.wrap(f, Defs.SKILL_DESC[c], rx - lx)) {
                if (yy > y2 - 24) break;
                Ui.text(g, f, l, lx, yy, Ui.WHITE, a);
                yy += 10;
            }
        }
        Ui.center(g, f, "Esc: cerrar   ·   ratón encima: detalles", (x1 + x2) / 2, y2 - 12, 0xFF5F7387, a);
        return tip;
    }

    private static List<String> statTip(int i) {
        List<String> l = new ArrayList<>();
        l.add(Defs.STAT_NAME[i] + "   " + CData.stat(i));
        for (String s : Defs.STAT_INFO[i]) {
            l.add(s);
        }
        return l;
    }

    private static List<String> raceTip(int r) {
        List<String> l = new ArrayList<>();
        l.add("RAZA: " + Defs.RACE_NAME[r].toUpperCase(Locale.ROOT));
        for (String s : Defs.RACE_BONUS[r]) {
            l.add(s);
        }
        return l;
    }

    private static List<String> classTip(int c) {
        List<String> l = new ArrayList<>();
        l.add("CLASE: " + Defs.CLASS_NAME[c].toUpperCase(Locale.ROOT));
        for (String s : Defs.CLASS_BONUS[c]) {
            l.add(s);
        }
        l.add("Habilidad: " + Defs.SKILL_NAME[c]);
        return l;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && CData.points() > 0) {
            int lx = px() + 12;
            int y1 = py();
            for (int i = 0; i < 5; i++) {
                int ry = y1 + Y_STATS + i * 14;
                if (Ui.inside(mx, my, lx + PLUS_X, ry - 2, 13, 12)) {
                    ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_STAT, i, hasShiftDown() ? 5 : 1));
                    Hud.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ElSistemaClient.STATUS != null && ElSistemaClient.STATUS.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
