package com.elsistema.client;

import com.elsistema.Defs;
import com.elsistema.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Despertar: aceptar el Sistema, elegir raza y elegir clase. */
public class AwakeningScreen extends Screen {
    private static final Item[] RACE_ICON = {Items.PLAYER_HEAD, Items.OAK_SAPLING, Items.IRON_PICKAXE, Items.IRON_AXE, Items.BLAZE_POWDER};
    private static final Item[] CLASS_ICON = {Items.DIAMOND_SWORD, Items.BOW, Items.AMETHYST_SHARD, Items.ECHO_SHARD, Items.SHIELD};
    private static final String[] MESSAGE = {
            "Has cumplido los requisitos de la Misión Secreta:",
            "«El Valor de los Débiles».",
            "",
            "¿Aceptas convertirte en un Jugador?"
    };
    private static final int LIST_W = 104;
    private static final int ENTRY_H = 22;
    private static final int ENTRY_STEP = 26;

    private final long openTime = System.currentTimeMillis();
    private int stage = 0;
    private long stageStart = openTime;
    private long typeStart = openTime + 350;
    private long refuseTime = -1;
    private int race = -1;
    private int cls = -1;

    public AwakeningScreen() {
        super(Component.literal("Despertar"));
        Hud.sound(SoundEvents.BEACON_ACTIVATE, 0.6F, 1.8F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ geometría

    private int pw() {
        return stage == 0 ? Math.min(300, this.width - 16) : Math.min(360, this.width - 16);
    }

    private int ph() {
        return stage == 0 ? 136 : Math.min(216, this.height - 12);
    }

    private int px() {
        return (this.width - pw()) / 2;
    }

    private int py() {
        return (this.height - ph()) / 2;
    }

    private void goStage(int s) {
        stage = s;
        stageStart = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ texto que se escribe solo

    private int totalChars() {
        int t = 0;
        for (String l : MESSAGE) {
            t += l.length() + 6;
        }
        return t;
    }

    private int shownChars(long now) {
        return (int) Math.max(0, (now - typeStart) / 22);
    }

    private boolean typingDone(long now) {
        return shownChars(now) >= totalChars();
    }

    private int shake(long now) {
        if (refuseTime < 0) return 0;
        long age = now - refuseTime;
        if (age > 450) return 0;
        return Math.round((float) Math.sin(age * 0.09) * 5F * (1F - age / 450F));
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        long now = System.currentTimeMillis();
        float bg = Ui.clamp01((now - openTime) / 300F);
        g.fill(0, 0, this.width, this.height, Ui.fade(0xA0000208, bg));
        float p = Ui.easeOut((now - stageStart) / 300F);
        int x1 = px() + shake(now);
        int y1 = py();
        int x2 = x1 + pw();
        int y2 = y1 + ph();
        int cy = (y1 + y2) / 2;
        int hh = Math.max(1, Math.round(ph() * p / 2F));
        int accent = refuseTime > 0 && now - refuseTime < 450 ? Ui.RED : Ui.CYAN;
        Ui.panel(g, x1, cy - hh, x2, cy + hh, accent, Math.min(1F, p * 1.5F));
        float a = Ui.clamp01((p - 0.55F) / 0.45F);
        if (a <= 0F) return;
        g.enableScissor(x1, cy - hh, x2, cy + hh);
        Ui.shine(g, x1, y1, x2, y1 + 16, now, a);
        if (stage == 0) {
            drawPrompt(g, x1, y1, x2, mx, my, a, now);
        } else {
            drawChoice(g, x1, y1, x2, y2, mx, my, a, stage == 1);
        }
        g.disableScissor();
    }

    private void drawPrompt(GuiGraphics g, int x1, int y1, int x2, int mx, int my, float a, long now) {
        Font f = this.font;
        int cx = (x1 + x2) / 2;
        Ui.header(g, f, "NOTIFICACIÓN", x1, x2, y1 + 8, Ui.CYAN, a);
        int shown = shownChars(now);
        int used = 0;
        int y = y1 + 32;
        for (String line : MESSAGE) {
            int n = Math.max(0, Math.min(line.length(), shown - used));
            if (n > 0) {
                int lx = cx - f.width(line) / 2;
                Ui.text(g, f, line.substring(0, n), lx, y, line.startsWith("«") ? Ui.GOLD : Ui.WHITE, a);
            }
            used += line.length() + 6;
            y += 12;
        }
        int by = y1 + ph() - 26;
        if (refuseTime > 0 && now - refuseTime < 3000) {
            float ra = a * Ui.clamp01((3000 - (now - refuseTime)) / 500F);
            Ui.center(g, f, "ERROR: Esa opción no está disponible.", cx, by - 24, Ui.RED, ra);
            Ui.center(g, f, "El Sistema ya te ha elegido.", cx, by - 13, Ui.RED, ra);
        }
        if (typingDone(now)) {
            Ui.button(g, f, "ACEPTAR", cx - 92, by, 84, 16, mx, my, Ui.CYAN, true, a);
            Ui.button(g, f, "RECHAZAR", cx + 8, by, 84, 16, mx, my, 0xFF8090A0, true, a);
        } else {
            Ui.center(g, f, "(clic para saltar)", cx, by + 4, 0xFF5F7387, a);
        }
    }

    private void drawChoice(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, boolean races) {
        Font f = this.font;
        Ui.header(g, f, races ? "ELIGE TU RAZA" : "ELIGE TU CLASE", x1, x2, y1 + 8, Ui.CYAN, a);
        String[] names = races ? Defs.RACE_NAME : Defs.CLASS_NAME;
        Item[] icons = races ? RACE_ICON : CLASS_ICON;
        int sel = races ? race : cls;
        int hovered = -1;
        int lx = x1 + 10;
        int ly = y1 + 28;
        for (int i = 0; i < names.length; i++) {
            int ey = ly + i * ENTRY_STEP;
            boolean hov = Ui.inside(mx, my, lx, ey, LIST_W, ENTRY_H);
            if (hov) hovered = i;
            boolean on = i == sel;
            g.fill(lx, ey, lx + LIST_W, ey + ENTRY_H, Ui.fade(on ? 0x903A8DFF : (hov ? 0x60305070 : 0x50101A28), a));
            Ui.border(g, lx, ey, lx + LIST_W, ey + ENTRY_H, Ui.fade(on ? Ui.CYAN : (hov ? 0xFF8FD8FF : 0xFF3C5A74), a));
            if (a > 0.9F) {
                g.renderItem(new ItemStack(icons[i]), lx + 3, ey + 3);
            }
            Ui.text(g, f, names[i], lx + 24, ey + 7, on ? Ui.WHITE : 0xFFCFE6FF, a);
        }

        int dx = x1 + 124;
        int dw = x2 - 12 - dx;
        int dy = y1 + 28;
        Ui.border(g, dx - 6, dy - 2, x2 - 6, y2 - 30, Ui.fade(0xFF2A4A66, a));
        int show = hovered >= 0 ? hovered : sel;
        if (show < 0) {
            Ui.center(g, f, races ? "Elige una raza de la lista." : "Elige una clase de la lista.",
                    dx + dw / 2, dy + 60, Ui.GRAY, a);
        } else {
            drawDetails(g, f, show, races, dx, dy, dw, y2 - 34, a);
        }

        int by = y2 - 24;
        if (races) {
            Ui.button(g, f, "SIGUIENTE  »", x2 - 104, by, 94, 16, mx, my, Ui.CYAN, race >= 0, a);
        } else {
            Ui.button(g, f, "«  ATRÁS", x1 + 10, by, 70, 16, mx, my, 0xFF8090A0, true, a);
            Ui.button(g, f, "DESPERTAR", x2 - 104, by, 94, 16, mx, my, Ui.GOLD, cls >= 0, a);
        }
    }

    private void drawDetails(GuiGraphics g, Font f, int i, boolean races, int x, int y, int w, int maxY, float a) {
        String name = races ? Defs.RACE_NAME[i] : Defs.CLASS_NAME[i];
        Ui.glow(g, f, name.toUpperCase(Locale.ROOT), x, y + 2, Ui.WHITE, Ui.GLOW, a);
        int yy = y + 16;
        for (String l : Ui.wrap(f, races ? Defs.RACE_DESC[i] : Defs.CLASS_DESC[i], w)) {
            if (yy > maxY) return;
            Ui.text(g, f, l, x, yy, Ui.GRAY, a);
            yy += 10;
        }
        yy += 4;
        for (String b : races ? Defs.RACE_BONUS[i] : Defs.CLASS_BONUS[i]) {
            int c = b.startsWith("+") ? Ui.GREEN : (b.startsWith("-") ? Ui.RED : Ui.WHITE);
            for (String l : Ui.wrap(f, b, w)) {
                if (yy > maxY) return;
                Ui.text(g, f, l, x, yy, c, a);
                yy += 10;
            }
        }
        if (!races) {
            yy += 4;
            if (yy > maxY) return;
            Ui.text(g, f, "HABILIDAD [" + Hud.key(ElSistemaClient.SKILL) + "]: " + Defs.SKILL_NAME[i], x, yy, Ui.CYAN, a);
            yy += 10;
            for (String l : Ui.wrap(f, Defs.SKILL_DESC[i], w)) {
                if (yy > maxY) return;
                Ui.text(g, f, l, x, yy, Ui.WHITE, a);
                yy += 10;
            }
            if (yy > maxY) return;
            Ui.text(g, f, Defs.SKILL_COST[i] + " MP  ·  " + (Defs.SKILL_CD[i] / 20) + " s de enfriamiento", x, yy, Ui.MUTED, a);
        }
    }

    // ------------------------------------------------------------------ ratón y teclado

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        long now = System.currentTimeMillis();
        int x1 = px();
        int y1 = py();
        int x2 = x1 + pw();
        int y2 = y1 + ph();
        int cx = (x1 + x2) / 2;

        if (stage == 0) {
            if (!typingDone(now)) {
                typeStart = now - 100000;
                return true;
            }
            int by = y1 + ph() - 26;
            if (Ui.inside(mx, my, cx - 92, by, 84, 16)) {
                Hud.click();
                goStage(1);
                return true;
            }
            if (Ui.inside(mx, my, cx + 8, by, 84, 16)) {
                refuseTime = now;
                Hud.error();
                return true;
            }
            return true;
        }

        boolean races = stage == 1;
        int lx = x1 + 10;
        int ly = y1 + 28;
        for (int i = 0; i < 5; i++) {
            int ey = ly + i * ENTRY_STEP;
            if (Ui.inside(mx, my, lx, ey, LIST_W, ENTRY_H)) {
                if (races) {
                    race = i;
                } else {
                    cls = i;
                }
                Hud.click();
                return true;
            }
        }
        int by = y2 - 24;
        if (races) {
            if (race >= 0 && Ui.inside(mx, my, x2 - 104, by, 94, 16)) {
                Hud.click();
                goStage(2);
                return true;
            }
        } else {
            if (Ui.inside(mx, my, x1 + 10, by, 70, 16)) {
                Hud.click();
                goStage(1);
                return true;
            }
            if (cls >= 0 && race >= 0 && Ui.inside(mx, my, x2 - 104, by, 94, 16)) {
                ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_CHOOSE, race, cls));
                this.onClose();
                return true;
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
