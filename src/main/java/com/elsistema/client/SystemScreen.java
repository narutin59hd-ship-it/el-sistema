package com.elsistema.client;

import com.elsistema.Defs;
import com.elsistema.ModArmor;
import com.elsistema.ModItems;
import com.elsistema.Net;
import com.elsistema.Shop;
import com.elsistema.Trees;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ventana del Sistema (tecla V): Estado, Habilidades (3 árboles), Sombras y Tienda. */
public class SystemScreen extends Screen {
    private static int lastTab = 0;
    private static int lastSub = 0;
    private static int lastCat = 0;
    private static final String[] TABS = {"ESTADO", "HABILIDADES", "SOMBRAS", "TIENDA"};
    private static final int TAB_H = 14;
    private static final int TAB_Y = 6;

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

    private static final int NODE_R = 13;
    private static final int SHADOW_ROW = 24;
    private static final int SHOP_ROW = 22;
    private static final int HINT = 0xFF5F7387;
    private static final int LOCKED = 0xFF3C4A58;

    private static final int[] KIND_COLOR = {Ui.GREEN, Ui.CYAN, Ui.SHADOW};
    private static final String[] KIND_LABEL = {"RAZA", "CLASE", "SOMBRAS"};
    private static final String[] SET_TITLE = {"Cazador", "Archimago", "Caballero negro", "Monarca de las Sombras"};

    private static final Item[][] RACE_ICONS = {
            {Items.EXPERIENCE_BOTTLE, Items.GOLD_INGOT, Items.GOLDEN_APPLE, Items.BOOK, Items.TOTEM_OF_UNDYING},
            {Items.FEATHER, Items.BOW, Items.PHANTOM_MEMBRANE, Items.ENDER_EYE, Items.OAK_SAPLING},
            {Items.IRON_CHESTPLATE, Items.IRON_PICKAXE, Items.ANVIL, Items.GOLD_NUGGET, Items.NETHERITE_INGOT},
            {Items.IRON_AXE, Items.BEEF, Items.REDSTONE, Items.LEATHER_CHESTPLATE, Items.BLAZE_POWDER},
            {Items.LAPIS_LAZULI, Items.FIRE_CHARGE, Items.AMETHYST_SHARD, Items.CACTUS, Items.WITHER_ROSE},
            {Items.SHULKER_SHELL, Items.NETHERITE_SWORD, Items.ELYTRA, Items.FIRE_CHARGE, Items.DRAGON_BREATH}};
    private static final Item[][] CLASS_ICONS = {
            {Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.CLOCK, Items.REDSTONE},
            {Items.ARROW, Items.SPECTRAL_ARROW, Items.LEATHER_BOOTS, Items.CROSSBOW, Items.FIRE_CHARGE},
            {Items.AMETHYST_SHARD, Items.LAPIS_LAZULI, Items.BOOK, Items.CLOCK, Items.HEART_OF_THE_SEA},
            {Items.SPIDER_EYE, Items.ECHO_SHARD, Items.FEATHER, Items.IRON_SWORD, Items.ENDER_PEARL},
            {Items.GOLDEN_APPLE, Items.IRON_CHESTPLATE, Items.CACTUS, Items.SHIELD, Items.ANVIL}};

    private static ItemStack[] shopIcons;

    private final long openTime = System.currentTimeMillis();
    private long tabTime = openTime;
    private int tab = lastTab;
    private int selNode = -1;
    private int selShadow = -1;
    private int scroll = 0;
    private int releaseId = -1;
    private long releaseTime = 0L;
    private LivingEntity preview;
    private String previewType = "";

    /** Rectángulo de la ventana. */
    private record Box(int x1, int y1, int x2, int y2) {
        int w() {
            return x2 - x1;
        }

        int h() {
            return y2 - y1;
        }

        int cx() {
            return (x1 + x2) / 2;
        }

        boolean in(double mx, double my) {
            return mx >= x1 && mx < x2 && my >= y1 && my < y2;
        }
    }

    public SystemScreen() {
        super(Component.literal("Sistema"));
        Hud.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 2.0F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (this.preview != null) this.preview.tickCount++;
    }

    @Override
    public void removed() {
        ClientShadows.preview = null;
        super.removed();
    }

    private int pw() {
        return Math.min(440, this.width - 12);
    }

    private int ph() {
        return Math.min(268, this.height - 10);
    }

    private int px() {
        return (this.width - pw()) / 2;
    }

    private int py() {
        return (this.height - ph()) / 2;
    }

    private int tabW() {
        return Math.min(98, (pw() - 20 - 12) / 4);
    }

    private int tabX(int i) {
        int tw = tabW();
        return px() + pw() / 2 - (4 * tw + 12) / 2 + i * (tw + 4);
    }

    // ================================================================== dibujo

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
        int accent = tab == 2 ? Ui.SHADOW : Ui.CYAN;
        Ui.panel(g, x1, cy - hh, x2, cy + hh, accent, Math.min(1F, p * 1.5F));
        float a = Ui.clamp01((p - 0.55F) / 0.45F);
        if (a <= 0F) return;
        g.enableScissor(x1, cy - hh, x2, cy + hh);
        Ui.shine(g, x1, y1, x2, y1 + 16, now, a);
        tabs(g, x1, y1, x2, mx, my, a);
        float ca = a * Ui.clamp01((now - tabTime) / 160F);
        List<String> tip = switch (tab) {
            case 1 -> trees(g, x1, y1, x2, y2, mx, my, ca, now);
            case 2 -> shadows(g, x1, y1, x2, y2, mx, my, ca, now);
            case 3 -> shop(g, x1, y1, x2, y2, mx, my, ca);
            default -> status(g, x1, y1, x2, y2, mx, my, ca, now);
        };
        g.disableScissor();
        if (tip != null && ca >= 1F) {
            Ui.tooltip(g, this.font, tip, mx, my, this.width, this.height);
        }
    }

    private void tabs(GuiGraphics g, int x1, int y1, int x2, int mx, int my, float a) {
        Font f = this.font;
        int tw = tabW();
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabX(i);
            int ty = y1 + TAB_Y;
            boolean on = i == tab;
            boolean hover = Ui.inside(mx, my, tx, ty, tw, TAB_H);
            int acc = i == 2 ? Ui.SHADOW : Ui.CYAN;
            int bg = on ? (i == 2 ? 0xA05A2D9F : 0xA03A8DFF) : hover ? 0x60305070 : 0x40101A28;
            g.fill(tx, ty, tx + tw, ty + TAB_H, Ui.fade(bg, a));
            Ui.border(g, tx, ty, tx + tw, ty + TAB_H, Ui.fade(on ? acc : 0xFF3C5A74, a));
            String label = TABS[i];
            if ((i == 0 && CData.points() > 0) || (i == 1 && CData.skillPoints() > 0)) label += " *";
            Ui.center(g, f, label, tx + tw / 2, ty + 3, on ? Ui.WHITE : Ui.GRAY, a);
        }
        Ui.divider(g, x1, x2, y1 + TAB_Y + TAB_H + 3, a);
    }

    private static void label(GuiGraphics g, Font f, String key, String value, int x, int y, int color, float a) {
        String k = key + ":";
        Ui.text(g, f, k, x, y, Ui.GRAY, a);
        Ui.glow(g, f, value, x + f.width(k) + 5, y, color, 0x4050B4FF, a);
    }

    private void hint(GuiGraphics g, String s, int x1, int x2, int y2, float a) {
        Ui.center(g, this.font, fit(this.font, s, x2 - x1 - 16), (x1 + x2) / 2, y2 - 12, HINT, a);
    }

    /** Recorta un texto para que quepa en el ancho indicado. */
    private static String fit(Font f, String s, int w) {
        if (f.width(s) <= w) return s;
        String r = s;
        while (r.length() > 1 && f.width(r + "..") > w) {
            r = r.substring(0, r.length() - 1);
        }
        return r.trim() + "..";
    }

    private static void chip(GuiGraphics g, Font f, String text, int x, int y, int w, boolean on, boolean hover, int accent, float a) {
        g.fill(x, y, x + w, y + 13, Ui.fade(on ? (accent & 0x00FFFFFF) | 0x70000000 : hover ? 0x50305070 : 0x40101A28, a));
        Ui.border(g, x, y, x + w, y + 13, Ui.fade(on ? accent : 0xFF2E4458, a));
        if (on) g.fill(x + 2, y + 11, x + w - 2, y + 12, Ui.fade(accent, a));
        Ui.center(g, f, fit(f, text, w - 6), x + w / 2, y + 3, on ? Ui.WHITE : Ui.GRAY, a);
    }

    // ------------------------------------------------------------------ pestaña ESTADO

    private List<String> status(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        List<String> tip = null;
        int lx = x1 + 12;
        int mid = x1 + (x2 - x1) / 2 + 4;
        int rx = x2 - 12;

        String name = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getName().getString() : "?";
        label(g, f, "NOMBRE", name, lx, y1 + Y_NAME, Ui.WHITE, a);
        label(g, f, "NIVEL", String.valueOf(CData.level()), mid, y1 + Y_NAME, Ui.WHITE, a);
        int r = Defs.rank(CData.level());
        label(g, f, "RANGO", Defs.RANK[r], lx, y1 + Y_RANK, Defs.RANK_COLOR[r], a);
        label(g, f, "CLASE", Hud.name(Defs.CLASS_NAME, CData.cls()), mid, y1 + Y_RANK, Ui.WHITE, a);
        label(g, f, "RAZA", Hud.name(Defs.RACE_NAME, CData.race()), lx, y1 + Y_RACE, Ui.WHITE, a);
        label(g, f, "ORO", String.valueOf(CData.coins()), mid, y1 + Y_RACE, Ui.GOLD, a);
        if (Ui.inside(mx, my, mid, y1 + Y_RANK - 1, rx - mid, 10) && CData.cls() >= 0) {
            tip = classTip(CData.cls());
        }
        if (Ui.inside(mx, my, lx, y1 + Y_RACE - 1, mid - lx - 8, 10) && CData.race() >= 0) {
            tip = raceTip(CData.race());
        }
        Ui.divider(g, x1, x2, y1 + Y_DIV1, a);

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

        boolean max = CData.level() >= Defs.MAX_LEVEL;
        String xpText = max ? "NIVEL MÁXIMO" : CData.xp() + " / " + CData.need();
        Ui.text(g, f, "EXP", lx, y1 + Y_EXP, Ui.CYAN, a);
        int ebw = rx - (lx + 24) - f.width(xpText) - 6;
        Ui.bar(g, lx + 24, y1 + Y_EXP + 1, ebw, 5, max ? 1F : CData.xp() / (float) CData.need(), Ui.CYAN, a);
        Ui.text(g, f, xpText, rx - f.width(xpText), y1 + Y_EXP, Ui.WHITE, a);
        Ui.divider(g, x1, x2, y1 + Y_DIV2, a);

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
        Ui.center(g, f, "PUNTOS DE ESTADÍSTICA", bcx, by1 + 7, Ui.GRAY, a);
        float pulse = canAdd ? 0.7F + 0.3F * (float) Math.sin(now / 250.0) : 1F;
        Ui.bigCenter(g, f, String.valueOf(CData.points()), bcx, by1 + 32F, 2.5F,
                canAdd ? Ui.GOLD : Ui.WHITE, 0x60FFC857, a * pulse);
        Ui.center(g, f, canAdd ? "Mayús + clic: +5" : "Sube de nivel para ganar más", bcx, by1 + 54, Ui.MUTED, a);
        Ui.divider(g, x1, x2, y1 + Y_DIV3, a);

        int set = CData.setBonus();
        int limit = set >= 0 ? y2 - 38 : y2 - 24;
        int c = CData.cls();
        if (c >= 0 && c < Defs.SKILL_NAME.length) {
            String k = "HABILIDAD [" + Hud.key(ElSistemaClient.SKILL) + "]:";
            Ui.text(g, f, k, lx, y1 + Y_SKILL, Ui.GRAY, a);
            Ui.glow(g, f, Defs.SKILL_NAME[c], lx + f.width(k) + 5, y1 + Y_SKILL, Ui.CYAN, 0x5050B4FF, a);
            String cost = Defs.SKILL_COST[c] + " MP  ·  " + (Defs.SKILL_CD[c] / 20) + " s";
            Ui.text(g, f, cost, rx - f.width(cost), y1 + Y_SKILL, Ui.MUTED, a);
            int yy = y1 + Y_SKILL + 12;
            for (String l : Ui.wrap(f, Defs.SKILL_DESC[c], rx - lx)) {
                if (yy > limit) break;
                Ui.text(g, f, l, lx, yy, Ui.WHITE, a);
                yy += 10;
            }
        }
        if (set >= 0 && set < SET_TITLE.length) {
            String k = "CONJUNTO:";
            int sy = y2 - 26;
            Ui.text(g, f, k, lx, sy, Ui.GRAY, a);
            String v = SET_TITLE[set];
            Ui.glow(g, f, v, lx + f.width(k) + 5, sy, Ui.GOLD, 0x50FFC857, a);
            String b = fit(f, ModArmor.SET_BONUS[set], rx - (lx + f.width(k) + f.width(v) + 16));
            Ui.text(g, f, b, rx - f.width(b), sy, Ui.WHITE, a);
        }
        hint(g, "Esc: cerrar   ·   1-4: pestañas   ·   ratón encima: detalles", x1, x2, y2, a);
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

    // ------------------------------------------------------------------ pestaña HABILIDADES

    private static int sub() {
        return lastSub < 0 || lastSub > 2 ? 0 : lastSub;
    }

    private int chipW() {
        return Math.min(132, (pw() - 16 - 8) / 3);
    }

    private int chipX(int i) {
        return px() + 8 + i * (chipW() + 4);
    }

    private Box graphBox() {
        int x1 = px();
        int y1 = py();
        return new Box(x1 + 8, y1 + 45, x1 + pw() * 58 / 100, y1 + ph() - 17);
    }

    private Box detailBox() {
        Box gb = graphBox();
        return new Box(gb.x2() + 6, gb.y1(), px() + pw() - 8, gb.y2());
    }

    private static Item icon(int kind, int index, int node) {
        if (kind == 2) {
            Item[] sh = {Items.WITHER_SKELETON_SKULL, Items.ECHO_SHARD, ModItems.GUADANA, Items.SCULK_CATALYST, Items.NETHER_STAR};
            Item it = sh[node];
            return it == null ? Items.BARRIER : it;
        }
        Item[][] all = kind == 0 ? RACE_ICONS : CLASS_ICONS;
        return index >= 0 && index < all.length ? all[index][node] : Items.BARRIER;
    }

    private static Trees.Node[] tree(int kind) {
        return Trees.tree(kind, CData.treeIndex(kind));
    }

    private static boolean unlocked(int kind, Trees.Node[] t, int k) {
        int par = Trees.PARENT[k];
        return CData.level() >= t[k].lvl() && (par < 0 || CData.rank(kind, par) > 0);
    }

    private static boolean canUp(int kind, Trees.Node[] t, int k) {
        return CData.skillPoints() > 0 && CData.rank(kind, k) < t[k].max() && unlocked(kind, t, k);
    }

    private static int[][] nodePos(Box b) {
        int top = b.y1() + 40;
        int bot = b.y2() - 24;
        int mid = (top + bot) / 2;
        int dx = Math.min(74, b.w() * 28 / 100);
        int cx = b.cx();
        return new int[][]{{cx, bot}, {cx - dx, mid}, {cx + dx, mid}, {cx - dx, top}, {cx + dx, top}};
    }

    /** Camino en ángulos rectos del nudo padre al hijo (estilo circuito). */
    private static int[][] path(int[] from, int[] to) {
        int sy = from[1] - NODE_R - 2;
        int ey = to[1] + NODE_R + 6;
        if (from[0] == to[0]) return new int[][]{{from[0], sy}, {to[0], ey}};
        int my = (sy + ey) / 2;
        return new int[][]{{from[0], sy}, {from[0], my}, {to[0], my}, {to[0], ey}};
    }

    private static void pathLine(GuiGraphics g, int[][] pts, int color, int thick) {
        int h = thick / 2;
        for (int i = 0; i + 1 < pts.length; i++) {
            int ax = pts[i][0];
            int ay = pts[i][1];
            int bx = pts[i + 1][0];
            int by = pts[i + 1][1];
            g.fill(Math.min(ax, bx) - h, Math.min(ay, by) - h, Math.max(ax, bx) - h + thick, Math.max(ay, by) - h + thick, color);
        }
    }

    private static int[] along(int[][] pts, float t) {
        int total = 0;
        for (int i = 0; i + 1 < pts.length; i++) {
            total += Math.abs(pts[i + 1][0] - pts[i][0]) + Math.abs(pts[i + 1][1] - pts[i][1]);
        }
        float d = Ui.clamp01(t) * total;
        for (int i = 0; i + 1 < pts.length; i++) {
            int len = Math.abs(pts[i + 1][0] - pts[i][0]) + Math.abs(pts[i + 1][1] - pts[i][1]);
            if (d <= len && len > 0) {
                float u = d / len;
                return new int[]{Math.round(pts[i][0] + (pts[i + 1][0] - pts[i][0]) * u),
                        Math.round(pts[i][1] + (pts[i + 1][1] - pts[i][1]) * u)};
            }
            d -= len;
        }
        return pts[pts.length - 1];
    }

    /** Octógono relleno (los nudos del árbol). */
    private static void octagon(GuiGraphics g, int cx, int cy, int r, int color) {
        int cut = Math.max(2, r * 2 / 5);
        for (int y = -r; y < r; y++) {
            int dy = y < 0 ? -y : y + 1;
            int inset = Math.max(0, dy - (r - cut));
            g.fill(cx - r + inset, cy + y, cx + r - inset, cy + y + 1, color);
        }
    }

    private static int hash(int i) {
        int x = i * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return x & 0x7FFFFFFF;
    }

    /** Fondo del árbol: estrellas que parpadean y círculos mágicos que giran alrededor de la raíz. */
    private static void sky(GuiGraphics g, Box b, int[] root, int accent, float a, long now) {
        g.fill(b.x1(), b.y1(), b.x2(), b.y2(), Ui.fade(0x70040812, a));
        g.fillGradient(b.x1(), b.y1(), b.x2(), b.y2(), Ui.fade(0x00000000, a), Ui.fade((accent & 0x00FFFFFF) | 0x1C000000, a));
        for (int i = 0; i < 46; i++) {
            int sx = b.x1() + 2 + hash(i * 3 + 1) % Math.max(1, b.w() - 4);
            int sy = b.y1() + 2 + hash(i * 3 + 2) % Math.max(1, b.h() - 4);
            float tw = 0.5F + 0.5F * (float) Math.sin(now / 650.0 + i * 1.7);
            int size = i % 7 == 0 ? 2 : 1;
            g.fill(sx, sy, sx + size, sy + size, Ui.fade(i % 3 == 0 ? accent : 0xFFFFFFFF, a * (0.1F + 0.45F * tw)));
        }
        for (int ring = 0; ring < 4; ring++) {
            int r = 30 + ring * 36;
            int dots = r / 2;
            double spin = now / 5000.0 * (ring % 2 == 0 ? 1 : -1);
            for (int k = 0; k < dots; k++) {
                if (ring % 2 == 1 && k % 4 == 3) continue;
                double ang = spin + k * Math.PI * 2.0 / dots;
                int dx = (int) Math.round(Math.cos(ang) * r);
                int dy = (int) Math.round(Math.sin(ang) * r * 0.82);
                g.fill(root[0] + dx, root[1] + dy, root[0] + dx + 1, root[1] + dy + 1, Ui.fade(accent, a * (0.22F - ring * 0.04F)));
            }
        }
        Ui.border(g, b.x1(), b.y1(), b.x2(), b.y2(), Ui.fade(0xFF2A4A66, a));
        Ui.corners(g, b.x1() - 1, b.y1() - 1, b.x2() + 1, b.y2() + 1, 6, Ui.fade(accent, a * 0.8F));
    }

    private List<String> trees(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        int kind = sub();
        int accent = KIND_COLOR[kind];
        int cw = chipW();
        for (int i = 0; i < 3; i++) {
            int cx = chipX(i);
            String who = i == 0 ? Hud.name(Defs.RACE_NAME, CData.race()) : i == 1 ? Hud.name(Defs.CLASS_NAME, CData.cls()) : "Monarca";
            String text = KIND_LABEL[i] + ": " + who.toUpperCase(Locale.ROOT);
            if (i == 2) text = "SOMBRAS";
            chip(g, f, text, cx, y1 + 27, cw, i == kind, Ui.inside(mx, my, cx, y1 + 27, cw, 13), KIND_COLOR[i], a);
        }
        int sp = CData.skillPoints();
        String phText = "PH: " + sp;
        float pp = sp > 0 ? 0.7F + 0.3F * (float) Math.sin(now / 250.0) : 1F;
        Ui.glow(g, f, phText, x2 - 10 - f.width(phText), y1 + 30, sp > 0 ? Ui.PURPLE : Ui.GRAY, 0x50B57BFF, a * pp);

        Box gb = graphBox();
        Box db = detailBox();
        Trees.Node[] t = tree(kind);
        int[][] pos = nodePos(gb);
        g.enableScissor(gb.x1(), gb.y1(), gb.x2(), gb.y2());
        sky(g, gb, pos[0], accent, a, now);
        if (t == null) {
            Ui.center(g, f, "Despierta primero para ver este árbol", gb.cx(), (gb.y1() + gb.y2()) / 2, Ui.GRAY, a);
            g.disableScissor();
            hint(g, "Elige tu raza y tu clase al despertar (tecla " + Hud.key(ElSistemaClient.STATUS) + ")", x1, x2, y2, a);
            return null;
        }
        String title = kind == 0 ? Trees.RACE_TREE[CData.race()] : kind == 1 ? Trees.CLASS_TREE[CData.cls()] : Trees.SHADOW_TREE;
        Ui.glow(g, f, title.toUpperCase(Locale.ROOT), gb.cx() - f.width(title.toUpperCase(Locale.ROOT)) / 2, gb.y1() + 6, Ui.WHITE, (accent & 0x00FFFFFF) | 0x70000000, a);
        String subTitle = kind == 0 ? "Árbol de raza · " + Hud.name(Defs.RACE_NAME, CData.race())
                : kind == 1 ? "Árbol de clase · " + Hud.name(Defs.CLASS_NAME, CData.cls()) : "Árbol compartido por todos";
        Ui.center(g, f, subTitle, gb.cx(), gb.y1() + 17, Ui.MUTED, a);

        // líneas
        for (int k = 1; k < 5; k++) {
            int par = Trees.PARENT[k];
            int[][] pts = path(pos[par], pos[k]);
            boolean lit = CData.rank(kind, par) > 0;
            if (lit) {
                pathLine(g, pts, Ui.fade((accent & 0x00FFFFFF) | 0x40000000, a), 4);
                pathLine(g, pts, Ui.fade(accent, a * 0.9F), 2);
                float tt = ((now + k * 377L) % 1500L) / 1500F;
                for (int s = 0; s < 4; s++) {
                    int[] q = along(pts, tt - s * 0.05F);
                    float qa = a * (1F - s * 0.25F);
                    g.fill(q[0] - 2, q[1] - 2, q[0] + 2, q[1] + 2, Ui.fade(0xFFFFFFFF, qa * 0.9F));
                    g.fill(q[0] - 3, q[1] - 3, q[0] + 3, q[1] + 3, Ui.fade((accent & 0x00FFFFFF) | 0x50000000, qa));
                }
            } else {
                pathLine(g, pts, Ui.fade(0xFF26323E, a), 1);
            }
        }
        // nudos
        int hovered = -1;
        for (int k = 0; k < 5; k++) {
            if (Ui.inside(mx, my, pos[k][0] - NODE_R, pos[k][1] - NODE_R, NODE_R * 2, NODE_R * 2)) hovered = k;
        }
        if (selNode < 0 || selNode > 4) selNode = firstUseful(kind, t);
        for (int k = 0; k < 5; k++) {
            node(g, f, kind, t, k, pos[k][0], pos[k][1], k == hovered, k == selNode, accent, a, now);
        }
        g.disableScissor();
        details(g, f, db, kind, t, hovered >= 0 ? hovered : selNode, mx, my, accent, a, now);
        hint(g, "Clic: ver nudo   ·   clic derecho o MEJORAR: subir de rango (1 PH)", x1, x2, y2, a);
        return null;
    }

    private static int firstUseful(int kind, Trees.Node[] t) {
        for (int k = 0; k < 5; k++) {
            if (canUp(kind, t, k)) return k;
        }
        for (int k = 0; k < 5; k++) {
            if (unlocked(kind, t, k) && CData.rank(kind, k) < t[k].max()) return k;
        }
        return 0;
    }

    private static void node(GuiGraphics g, Font f, int kind, Trees.Node[] t, int k, int nx, int ny, boolean hover,
                             boolean selected, int accent, float a, long now) {
        Trees.Node n = t[k];
        int rk = CData.rank(kind, k);
        boolean open = unlocked(kind, t, k);
        boolean maxed = rk >= n.max();
        boolean can = canUp(kind, t, k);
        int border = !open ? LOCKED : maxed ? Ui.GOLD : can ? Ui.GREEN : accent;
        float pulse = can ? 0.55F + 0.45F * (float) Math.sin(now / 220.0) : 1F;
        if (can || maxed || hover) {
            int glow = (border & 0x00FFFFFF) | 0x30000000;
            octagon(g, nx, ny, NODE_R + 4, Ui.fade(glow, a * pulse));
            octagon(g, nx, ny, NODE_R + 2, Ui.fade(glow, a * pulse));
        }
        octagon(g, nx, ny, NODE_R + 1, Ui.fade(border, a * (can ? 0.6F + 0.4F * pulse : 1F)));
        octagon(g, nx, ny, NODE_R, Ui.fade(hover ? 0xF0203450 : rk > 0 ? 0xF0121E30 : 0xF00A1018, a));
        if (rk > 0) {
            octagon(g, nx, ny, NODE_R - 3, Ui.fade((border & 0x00FFFFFF) | 0x22000000, a));
        }
        if (selected) {
            int o = NODE_R + 5 + Math.round(1.5F + 1.5F * (float) Math.sin(now / 200.0));
            Ui.corners(g, nx - o, ny - o, nx + o, ny + o, 5, Ui.fade(Ui.WHITE, a));
        }
        if (a > 0.9F) {
            g.renderItem(new ItemStack(icon(kind, CData.treeIndex(kind), k)), nx - 8, ny - 8);
            if (!open) {
                g.pose().pushPose();
                g.pose().translate(0.0F, 0.0F, 200.0F);
                octagon(g, nx, ny, NODE_R, 0xA8000000);
                g.pose().popPose();
            }
        }
        int bw = NODE_R * 2 - 4;
        g.fill(nx - bw / 2, ny + NODE_R + 3, nx + bw / 2, ny + NODE_R + 5, Ui.fade(0xC0101A28, a));
        int fw = Math.round(bw * rk / (float) Math.max(1, n.max()));
        if (fw > 0) g.fill(nx - bw / 2, ny + NODE_R + 3, nx - bw / 2 + fw, ny + NODE_R + 5, Ui.fade(maxed ? Ui.GOLD : accent, a));
        String r = open ? rk + "/" + n.max() : "Nv." + n.lvl();
        Ui.text(g, f, r, nx + NODE_R + 4, ny - 4, maxed ? Ui.GOLD : open ? Ui.WHITE : 0xFF6A7580, a);
    }

    private Box upgradeButton(Box db) {
        return new Box(db.x1() + 6, db.y2() - 20, db.x2() - 6, db.y2() - 6);
    }

    private void details(GuiGraphics g, Font f, Box db, int kind, Trees.Node[] t, int k, int mx, int my, int accent, float a, long now) {
        g.fill(db.x1(), db.y1(), db.x2(), db.y2(), Ui.fade(0x90070E1A, a));
        Ui.border(g, db.x1(), db.y1(), db.x2(), db.y2(), Ui.fade((accent & 0x00FFFFFF) | 0x90000000, a));
        Ui.corners(g, db.x1() - 1, db.y1() - 1, db.x2() + 1, db.y2() + 1, 6, Ui.fade(accent, a));
        if (k < 0 || k > 4) return;
        Trees.Node n = t[k];
        int rk = CData.rank(kind, k);
        boolean open = unlocked(kind, t, k);
        boolean maxed = rk >= n.max();
        int lx = db.x1() + 7;
        int w = db.w() - 14;
        int y = db.y1() + 6;
        Ui.glow(g, f, fit(f, n.name(), w), lx, y, Ui.WHITE, (accent & 0x00FFFFFF) | 0x60000000, a);
        y += 13;
        // rangos como cuadraditos
        int pip = n.max() <= 10 ? 6 : Math.max(2, (w - 40) / n.max() - 1);
        for (int i = 0; i < n.max(); i++) {
            int px = lx + i * (pip + 1);
            if (px + pip > db.x2() - 40) break;
            boolean on = i < rk;
            g.fill(px, y, px + pip, y + 5, Ui.fade(on ? (maxed ? Ui.GOLD : accent) : 0xFF1C2834, a));
        }
        String rt = rk + "/" + n.max();
        Ui.text(g, f, rt, db.x2() - 7 - f.width(rt), y - 1, maxed ? Ui.GOLD : Ui.WHITE, a);
        y += 11;
        for (String l : Ui.wrap(f, n.desc(), w)) {
            if (y > db.y2() - 76) break;
            Ui.text(g, f, l, lx, y, 0xFFC8D6E5, a);
            y += 10;
        }
        y += 2;
        Ui.divider(g, db.x1(), db.x2(), y, a);
        y += 5;
        Ui.text(g, f, "AHORA", lx, y, Ui.GRAY, a);
        y += 10;
        Ui.text(g, f, fit(f, rk > 0 ? Trees.effect(n, rk) : "Sin efecto todavía", w), lx + 4, y, rk > 0 ? accent : Ui.MUTED, a);
        y += 12;
        if (!maxed) {
            Ui.text(g, f, "SIGUIENTE RANGO", lx, y, Ui.GRAY, a);
            y += 10;
            Ui.text(g, f, fit(f, Trees.effect(n, rk + 1), w), lx + 4, y, Ui.GREEN, a);
            y += 12;
        }
        boolean lvlOk = CData.level() >= n.lvl();
        if (y < db.y2() - 32) {
            Ui.text(g, f, fit(f, "Nivel " + n.lvl() + (lvlOk ? "  ✔" : "  (tienes " + CData.level() + ")"), w), lx, y, lvlOk ? Ui.MUTED : Ui.RED, a);
            y += 10;
        }
        int par = Trees.PARENT[k];
        if (par >= 0 && y < db.y2() - 32) {
            boolean ok = CData.rank(kind, par) > 0;
            Ui.text(g, f, fit(f, "Requiere: " + t[par].name() + (ok ? "  ✔" : ""), w), lx, y, ok ? Ui.MUTED : Ui.RED, a);
        }
        Box b = upgradeButton(db);
        boolean can = canUp(kind, t, k);
        String label = maxed ? "RANGO MÁXIMO" : !open ? "BLOQUEADO" : CData.skillPoints() <= 0 ? "SIN PUNTOS (PH)" : "MEJORAR  (1 PH)";
        if (can) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(now / 240.0);
            Ui.border(g, b.x1() - 2, b.y1() - 2, b.x2() + 2, b.y2() + 2, Ui.fade(Ui.GREEN, a * 0.5F * pulse));
        }
        Ui.button(g, f, label, b.x1(), b.y1(), b.w(), b.h(), mx, my, maxed ? Ui.GOLD : Ui.GREEN, can, a);
    }

    // ------------------------------------------------------------------ pestaña SOMBRAS

    private Box listBox() {
        int x1 = px();
        int y1 = py();
        return new Box(x1 + 8, y1 + 45, x1 + pw() * 58 / 100, y1 + ph() - 17);
    }

    private Box infoBox() {
        Box lb = listBox();
        return new Box(lb.x2() + 6, lb.y1(), px() + pw() - 8, lb.y2());
    }

    private int visibleRows() {
        return Math.max(1, (listBox().h() - 4) / SHADOW_ROW);
    }

    private Box[] armyButtons() {
        int y = py() + 26;
        int x2 = px() + pw() - 8;
        int w1 = this.font.width("GUARDAR TODAS") + 14;
        int w2 = this.font.width("INVOCAR TODAS") + 14;
        Box store = new Box(x2 - w1, y, x2, y + 14);
        Box summon = new Box(store.x1() - 4 - w2, y, store.x1() - 4, y + 14);
        return new Box[]{summon, store};
    }

    private static int stateColor(int state) {
        return state == ClientShadows.OUT ? Ui.GREEN : state == ClientShadows.RECOVERING ? 0xFFFF8A5A : Ui.SHADOW;
    }

    private static String stateText(ClientShadows.Entry e) {
        return switch (e.state()) {
            case ClientShadows.OUT -> "En el campo";
            case ClientShadows.RECOVERING -> "Recuperándose · " + ClientShadows.recoverLeft(e) + " s";
            default -> "Guardada en tu sombra";
        };
    }

    private LivingEntity previewOf(String type) {
        if (!type.equals(this.previewType)) {
            this.previewType = type;
            this.preview = null;
            if (this.minecraft != null && this.minecraft.level != null) {
                ResourceLocation id = ResourceLocation.tryParse(type);
                if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    Entity e = BuiltInRegistries.ENTITY_TYPE.get(id).create(this.minecraft.level);
                    if (e instanceof LivingEntity le) this.preview = le;
                }
            }
        }
        ClientShadows.preview = this.preview;
        return this.preview;
    }

    private List<String> shadows(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        List<ClientShadows.Entry> list = ClientShadows.list();
        String army = "EJÉRCITO " + CData.shadowCount() + "/" + CData.shadowMax();
        Ui.glow(g, f, army, x1 + 10, y1 + 30, Ui.WHITE, Ui.SHADOW_GLOW, a);
        int out = ClientShadows.out();
        Ui.text(g, f, "·  fuera: " + out, x1 + 16 + f.width(army), y1 + 30, out > 0 ? Ui.GREEN : Ui.MUTED, a);
        Box[] ab = armyButtons();
        boolean anyStored = false;
        for (ClientShadows.Entry e : list) {
            if (e.state() == ClientShadows.STORED) anyStored = true;
        }
        Ui.button(g, f, "INVOCAR TODAS", ab[0].x1(), ab[0].y1(), ab[0].w(), ab[0].h(), mx, my, Ui.SHADOW, anyStored, a);
        Ui.button(g, f, "GUARDAR TODAS", ab[1].x1(), ab[1].y1(), ab[1].w(), ab[1].h(), mx, my, Ui.SHADOW, out > 0, a);

        Box lb = listBox();
        Box ib = infoBox();
        g.fill(lb.x1(), lb.y1(), lb.x2(), lb.y2(), Ui.fade(0x70040812, a));
        Ui.border(g, lb.x1(), lb.y1(), lb.x2(), lb.y2(), Ui.fade(0xFF2A3F5A, a));
        g.fill(ib.x1(), ib.y1(), ib.x2(), ib.y2(), Ui.fade(0x90070E1A, a));
        Ui.border(g, ib.x1(), ib.y1(), ib.x2(), ib.y2(), Ui.fade(0x909B5CFF, a));
        Ui.corners(g, ib.x1() - 1, ib.y1() - 1, ib.x2() + 1, ib.y2() + 1, 6, Ui.fade(Ui.SHADOW, a));
        String keys = "[" + Hud.key(ElSistemaClient.ARISE) + "] ¡Levántate! sobre un cadáver   ·   ["
                + Hud.key(ElSistemaClient.SHADOWS) + "] invocar / guardar todas";
        if (list.isEmpty()) {
            empty(g, f, lb, a, now);
            ClientShadows.preview = null;
            Ui.center(g, f, "Tu ejército está vacío", ib.cx(), ib.y1() + ib.h() / 2 - 4, Ui.MUTED, a);
            hint(g, keys, x1, x2, y2, a);
            return null;
        }
        int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, list.size() - rows));
        ClientShadows.Entry sel = null;
        for (ClientShadows.Entry e : list) {
            if (e.id() == selShadow) sel = e;
        }
        if (sel == null) {
            sel = list.get(0);
            selShadow = sel.id();
        }
        List<String> tip = null;
        g.enableScissor(lb.x1() + 1, lb.y1() + 1, lb.x2() - 1, lb.y2() - 1);
        for (int i = 0; i < rows && scroll + i < list.size(); i++) {
            ClientShadows.Entry e = list.get(scroll + i);
            int ry = lb.y1() + 3 + i * SHADOW_ROW;
            int rx1 = lb.x1() + 3;
            int rx2 = lb.x2() - (list.size() > rows ? 8 : 3);
            boolean hover = Ui.inside(mx, my, rx1, ry, rx2 - rx1, SHADOW_ROW - 3);
            boolean on = e.id() == selShadow;
            g.fill(rx1, ry, rx2, ry + SHADOW_ROW - 3, Ui.fade(on ? 0xB0241238 : hover ? 0x90182438 : 0x70101A28, a));
            Ui.border(g, rx1, ry, rx2, ry + SHADOW_ROW - 3, Ui.fade(on ? Ui.SHADOW : 0xFF24364A, a));
            g.fill(rx1, ry, rx1 + 2, ry + SHADOW_ROW - 3, Ui.fade(stateColor(e.state()), a));
            int rank = Math.max(0, Math.min(5, e.rank()));
            String rt = Defs.RANK[rank];
            String lv = "Nv." + e.level();
            int right = rx2 - 4;
            Ui.text(g, f, lv, right - f.width(lv), ry + 3, Ui.WHITE, a);
            Ui.glow(g, f, rt, right - f.width(lv) - 6 - f.width(rt), ry + 3, Defs.RANK_COLOR[rank], 0x40000000, a);
            int nameW = right - f.width(lv) - f.width(rt) - 12 - (rx1 + 6) - (e.boss() ? f.width("JEFE") + 4 : 0);
            String nm = fit(f, e.name(), nameW);
            Ui.text(g, f, nm, rx1 + 6, ry + 3, Ui.WHITE, a);
            if (e.boss()) Ui.text(g, f, "JEFE", rx1 + 10 + f.width(nm), ry + 3, Ui.GOLD, a);
            Ui.text(g, f, stateText(e), rx1 + 6, ry + 12, stateColor(e.state()), a * 0.9F);
            if (e.state() == ClientShadows.OUT) {
                Ui.bar(g, right - 40, ry + 14, 40, 3, e.hp() / 100F, Ui.HP_RED, a);
            }
            if (hover) {
                tip = List.of(e.name(), "Rango " + rt + " · nivel " + e.level() + " · " + e.kills() + " bajas", "Clic: seleccionar");
            }
        }
        g.disableScissor();
        if (list.size() > rows) {
            int trackY1 = lb.y1() + 3;
            int trackY2 = lb.y2() - 3;
            int th = Math.max(10, (trackY2 - trackY1) * rows / list.size());
            int ty = trackY1 + (trackY2 - trackY1 - th) * scroll / Math.max(1, list.size() - rows);
            g.fill(lb.x2() - 6, trackY1, lb.x2() - 3, trackY2, Ui.fade(0x60101A28, a));
            g.fill(lb.x2() - 6, ty, lb.x2() - 3, ty + th, Ui.fade(Ui.SHADOW, a));
        }
        info(g, f, ib, sel, mx, my, a, now);
        hint(g, keys, x1, x2, y2, a);
        return tip;
    }

    private void empty(GuiGraphics g, Font f, Box lb, float a, long now) {
        int cx = lb.cx();
        int y = lb.y1() + 14;
        float pulse = 0.7F + 0.3F * (float) Math.sin(now / 400.0);
        Ui.bigCenter(g, f, "☾", cx, y + 8, 2.5F, Ui.SHADOW, Ui.SHADOW_GLOW, a * pulse);
        y += 26;
        Ui.center(g, f, "AÚN NO TIENES SOMBRAS", cx, y, Ui.WHITE, a);
        y += 16;
        String arise = Hud.key(ElSistemaClient.ARISE);
        String[] steps = {
                "1. Mata a una criatura.",
                "2. Mientras su cadáver humea (10 s),",
                "    pulsa " + arise + ": ¡Levántate!",
                "3. Si sale bien, se alza como tu sombra",
                "    y te seguirá y luchará por ti."};
        for (String s : steps) {
            Ui.text(g, f, fit(f, s, lb.w() - 16), lb.x1() + 10, y, 0xFFC8D6E5, a);
            y += 11;
        }
        y += 4;
        Ui.text(g, f, fit(f, "Los monstruos de las mazmorras son los mejores.", lb.w() - 16), lb.x1() + 10, y, Ui.MUTED, a);
    }

    private Box actionButton(Box ib) {
        return new Box(ib.x1() + 6, ib.y2() - 20, ib.cx() - 2, ib.y2() - 6);
    }

    private Box releaseButton(Box ib) {
        return new Box(ib.cx() + 2, ib.y2() - 20, ib.x2() - 6, ib.y2() - 6);
    }

    private Box previewBox(Box ib) {
        int h = Math.max(40, Math.min(110, ib.h() - 78));
        return new Box(ib.x1() + 4, ib.y1() + 4, ib.x2() - 4, ib.y1() + 4 + h);
    }

    private void info(GuiGraphics g, Font f, Box ib, ClientShadows.Entry e, int mx, int my, float a, long now) {
        Box pb = previewBox(ib);
        g.fillGradient(pb.x1(), pb.y1(), pb.x2(), pb.y2(), Ui.fade(0x30100020, a), Ui.fade(0x902A0F4A, a));
        int fx = pb.cx();
        int fy = pb.y2() - 8;
        for (int ring = 0; ring < 2; ring++) {
            int r = pb.w() / 3 - ring * 10;
            int dots = Math.max(8, r);
            double spin = now / 2400.0 * (ring == 0 ? 1 : -1);
            for (int k = 0; k < dots; k++) {
                double ang = spin + k * Math.PI * 2.0 / dots;
                int dx = (int) Math.round(Math.cos(ang) * r);
                int dy = (int) Math.round(Math.sin(ang) * r * 0.25);
                g.fill(fx + dx, fy + dy, fx + dx + 1, fy + dy + 1, Ui.fade(Ui.SHADOW, a * 0.6F));
            }
        }
        LivingEntity le = previewOf(e.type());
        if (le != null && a > 0.9F) {
            float hgt = Math.max(0.5F, le.getBbHeight());
            float wid = Math.max(0.5F, le.getBbWidth());
            int scale = (int) Math.max(6, Math.min(Math.min(60, pb.h() * 0.72F / hgt), pb.w() * 0.55F / wid));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, pb.x1(), pb.y1(), pb.x2(), pb.y2(), scale, 0.0625F, mx, my, le);
        }
        Ui.border(g, pb.x1(), pb.y1(), pb.x2(), pb.y2(), Ui.fade(0xFF3A2A5A, a));
        int lx = ib.x1() + 7;
        int w = ib.w() - 14;
        int y = pb.y2() + 5;
        Ui.glow(g, f, fit(f, e.name(), w), lx, y, Ui.WHITE, Ui.SHADOW_GLOW, a);
        y += 11;
        int rank = Math.max(0, Math.min(5, e.rank()));
        Ui.text(g, f, "Rango ", lx, y, Ui.GRAY, a);
        Ui.text(g, f, Defs.RANK[rank], lx + f.width("Rango "), y, Defs.RANK_COLOR[rank], a);
        String lv = "Nivel " + e.level();
        Ui.text(g, f, lv, ib.x2() - 7 - f.width(lv), y, Ui.WHITE, a);
        y += 10;
        Ui.text(g, f, fit(f, e.kills() + " bajas" + (e.boss() ? "   ·   JEFE" : ""), w), lx, y, e.boss() ? Ui.GOLD : Ui.GRAY, a);
        y += 10;
        if (y < ib.y2() - 30) {
            Ui.text(g, f, fit(f, stateText(e), w), lx, y, stateColor(e.state()), a);
        }
        Box act = actionButton(ib);
        Box rel = releaseButton(ib);
        boolean out = e.state() == ClientShadows.OUT;
        boolean recovering = e.state() == ClientShadows.RECOVERING;
        Ui.button(g, f, out ? "GUARDAR" : "INVOCAR", act.x1(), act.y1(), act.w(), act.h(), mx, my, Ui.SHADOW, !recovering, a);
        boolean armed = releaseId == e.id() && System.currentTimeMillis() - releaseTime < 3000L;
        Ui.button(g, f, armed ? "¿SEGURO?" : "LIBERAR", rel.x1(), rel.y1(), rel.w(), rel.h(), mx, my, Ui.RED, true, a);
    }

    // ------------------------------------------------------------------ pestaña TIENDA

    private static ItemStack shopIcon(int i) {
        if (shopIcons == null || shopIcons.length != Shop.OFFERS.size()) {
            shopIcons = new ItemStack[Shop.OFFERS.size()];
        }
        if (shopIcons[i] == null) {
            List<ItemStack> l = Shop.make(i);
            shopIcons[i] = l.isEmpty() ? ItemStack.EMPTY : l.size() >= 2 && Shop.OFFERS.get(i).cat() == 2 ? l.get(1) : l.get(0);
        }
        return shopIcons[i];
    }

    private int[] coreCount() {
        int n = 0;
        int v = 0;
        if (this.minecraft == null || this.minecraft.player == null) return new int[]{0, 0};
        Inventory inv = this.minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            int r = ModItems.coreRank(s);
            if (r >= 0) {
                n += s.getCount();
                v += s.getCount() * ModItems.CORE_VALUE[r];
            }
        }
        return new int[]{n, v};
    }

    private static String sellLabel(int[] cv) {
        return cv[0] > 0 ? "VENDER NÚCLEOS (" + cv[0] + "): +" + cv[1] : "SIN NÚCLEOS PARA VENDER";
    }

    private int catW() {
        return Math.min(100, (pw() - 16 - 12) / 4);
    }

    private int catX(int i) {
        return px() + 8 + i * (catW() + 4);
    }

    private static List<Integer> offersIn(int cat) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < Shop.OFFERS.size(); i++) {
            if (Shop.OFFERS.get(i).cat() == cat) l.add(i);
        }
        return l;
    }

    private int[] shopRow(int k, int count) {
        int x1 = px();
        int colW = (pw() - 16 - 8) / 2;
        int perCol = (count + 1) / 2;
        int col = k / Math.max(1, perCol);
        int r = k % Math.max(1, perCol);
        return new int[]{x1 + 8 + col * (colW + 8), py() + 62 + r * SHOP_ROW, colW};
    }

    private List<String> shop(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a) {
        Font f = this.font;
        List<String> tip = null;
        int cat = Math.max(0, Math.min(Shop.CATS.length - 1, lastCat));
        int cw = catW();
        for (int i = 0; i < Shop.CATS.length; i++) {
            int cx = catX(i);
            chip(g, f, Shop.CATS[i], cx, y1 + 27, cw, i == cat, Ui.inside(mx, my, cx, y1 + 27, cw, 13), Ui.GOLD, a);
        }
        int coins = CData.coins();
        Ui.glow(g, f, "ORO: " + coins, x1 + 10, y1 + 47, Ui.GOLD, 0x50FFC857, a);
        int[] cv = coreCount();
        String sl = sellLabel(cv);
        int bw = f.width(sl) + 16;
        Ui.button(g, f, sl, x2 - 8 - bw, y1 + 44, bw, 14, mx, my, Ui.GOLD, cv[0] > 0, a);
        int myRank = Defs.rank(CData.level());
        List<Integer> ids = offersIn(cat);
        for (int k = 0; k < ids.size(); k++) {
            int i = ids.get(k);
            Shop.Offer o = Shop.OFFERS.get(i);
            int[] rr = shopRow(k, ids.size());
            int rx = rr[0];
            int ry = rr[1];
            int colW = rr[2];
            boolean locked = myRank < o.minRank();
            boolean afford = coins >= o.price();
            boolean hover = Ui.inside(mx, my, rx, ry, colW, SHOP_ROW - 2);
            g.fill(rx, ry, rx + colW, ry + SHOP_ROW - 2, Ui.fade(hover && !locked ? 0x803A8DFF : locked ? 0x50080C12 : 0x60101A28, a));
            Ui.border(g, rx, ry, rx + colW, ry + SHOP_ROW - 2, Ui.fade(hover ? (locked ? Ui.RED : Ui.CYAN) : 0xFF2A4A66, a));
            if (a > 0.9F) {
                g.renderItem(shopIcon(i), rx + 2, ry + 2);
                if (locked) {
                    g.pose().pushPose();
                    g.pose().translate(0.0F, 0.0F, 200.0F);
                    g.fill(rx + 2, ry + 2, rx + 18, ry + 18, 0x90000000);
                    g.pose().popPose();
                }
            }
            String price = String.valueOf(o.price());
            int pwid = f.width(price);
            Ui.text(g, f, fit(f, o.name(), colW - 30 - pwid), rx + 21, ry + 2, locked ? Ui.MUTED : Ui.WHITE, a);
            Ui.text(g, f, price, rx + colW - 4 - pwid, ry + 2, afford && !locked ? Ui.GOLD : Ui.RED, a);
            if (locked) {
                String need = "Requiere rango " + Defs.RANK[o.minRank()];
                Ui.text(g, f, fit(f, need, colW - 26), rx + 21, ry + 11, Defs.RANK_COLOR[o.minRank()], a * 0.9F);
            } else {
                Ui.text(g, f, fit(f, o.desc(), colW - 26), rx + 21, ry + 11, Ui.MUTED, a);
            }
            if (hover) tip = offerTip(i, myRank);
        }
        hint(g, "Clic: comprar   ·   Mayús + clic: comprar 5   ·   gana oro en mazmorras y vendiendo núcleos", x1, x2, y2, a);
        return tip;
    }

    private static List<String> offerTip(int i, int myRank) {
        Shop.Offer o = Shop.OFFERS.get(i);
        List<String> l = new ArrayList<>();
        l.add(o.name());
        l.add(o.desc());
        l.add("Precio: " + o.price() + " de oro");
        if (o.minRank() > 0) {
            l.add("Rango necesario: " + Defs.RANK[o.minRank()] + (myRank >= o.minRank() ? "  ✔" : "  (eres " + Defs.RANK[myRank] + ")"));
        }
        List<ItemStack> items = Shop.make(i);
        if (items.size() > 1) {
            for (ItemStack s : items) {
                l.add(" · " + s.getHoverName().getString());
            }
        }
        l.add(myRank >= o.minRank() ? "Clic: comprar   ·   Mayús + clic: comprar 5" : "Exclusivo: sube de rango para comprarlo");
        return l;
    }

    // ================================================================== ratón y teclado

    private void switchTab(int i) {
        if (tab != i) {
            tab = i;
            lastTab = i;
            tabTime = System.currentTimeMillis();
            Hud.click();
        }
    }

    private static void send(int action, int a, int b) {
        ClientPlayNetworking.send(new Net.ActionPayload(action, a, b));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 && button != 1) return super.mouseClicked(mx, my, button);
        int x1 = px();
        int y1 = py();
        int x2 = x1 + pw();
        int tw = tabW();
        if (button == 0) {
            for (int i = 0; i < TABS.length; i++) {
                if (Ui.inside(mx, my, tabX(i), y1 + TAB_Y, tw, TAB_H)) {
                    switchTab(i);
                    return true;
                }
            }
        }
        if (tab == 0 && button == 0 && CData.points() > 0) {
            int lx = x1 + 12;
            for (int i = 0; i < 5; i++) {
                int ry = y1 + Y_STATS + i * 14;
                if (Ui.inside(mx, my, lx + PLUS_X, ry - 2, 13, 12)) {
                    send(Net.ACT_STAT, i, hasShiftDown() ? 5 : 1);
                    Hud.click();
                    return true;
                }
            }
        }
        if (tab == 1 && clickTree(mx, my, button, y1)) return true;
        if (tab == 2 && button == 0 && clickShadows(mx, my)) return true;
        if (tab == 3 && button == 0 && clickShop(mx, my, y1, x2)) return true;
        return super.mouseClicked(mx, my, button);
    }

    private boolean clickTree(double mx, double my, int button, int y1) {
        int cw = chipW();
        for (int i = 0; i < 3; i++) {
            if (button == 0 && Ui.inside(mx, my, chipX(i), y1 + 27, cw, 13)) {
                if (lastSub != i) {
                    lastSub = i;
                    selNode = -1;
                    tabTime = System.currentTimeMillis();
                    Hud.click();
                }
                return true;
            }
        }
        int kind = sub();
        Trees.Node[] t = tree(kind);
        if (t == null) return false;
        int[][] pos = nodePos(graphBox());
        for (int k = 0; k < 5; k++) {
            if (Ui.inside(mx, my, pos[k][0] - NODE_R, pos[k][1] - NODE_R, NODE_R * 2, NODE_R * 2)) {
                selNode = k;
                if (button == 1) {
                    upgrade(kind, t, k);
                } else {
                    Hud.click();
                }
                return true;
            }
        }
        Box b = upgradeButton(detailBox());
        if (button == 0 && b.in(mx, my) && selNode >= 0) {
            upgrade(kind, t, selNode);
            return true;
        }
        return false;
    }

    private void upgrade(int kind, Trees.Node[] t, int k) {
        if (canUp(kind, t, k)) {
            send(Net.ACT_TREE, kind, k);
            Hud.click();
        } else {
            Hud.error();
        }
    }

    private boolean clickShadows(double mx, double my) {
        Box[] ab = armyButtons();
        if (ab[0].in(mx, my)) {
            send(Net.ACT_SHADOWS_ALL, 1, 0);
            Hud.click();
            return true;
        }
        if (ab[1].in(mx, my)) {
            send(Net.ACT_SHADOWS_ALL, 0, 0);
            Hud.click();
            return true;
        }
        List<ClientShadows.Entry> list = ClientShadows.list();
        Box lb = listBox();
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < list.size(); i++) {
            int ry = lb.y1() + 3 + i * SHADOW_ROW;
            if (Ui.inside(mx, my, lb.x1() + 3, ry, lb.w() - 6, SHADOW_ROW - 3)) {
                selShadow = list.get(scroll + i).id();
                releaseId = -1;
                Hud.click();
                return true;
            }
        }
        ClientShadows.Entry sel = ClientShadows.byId(selShadow);
        if (sel == null) return false;
        Box ib = infoBox();
        if (actionButton(ib).in(mx, my)) {
            if (sel.state() == ClientShadows.RECOVERING) {
                Hud.error();
            } else {
                send(Net.ACT_SHADOW, sel.id(), sel.state() == ClientShadows.OUT ? 1 : 0);
                Hud.click();
            }
            return true;
        }
        if (releaseButton(ib).in(mx, my)) {
            long now = System.currentTimeMillis();
            if (releaseId == sel.id() && now - releaseTime < 3000L) {
                send(Net.ACT_SHADOW, sel.id(), 2);
                releaseId = -1;
                selShadow = -1;
                Hud.sound(SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.4F);
            } else {
                releaseId = sel.id();
                releaseTime = now;
                Hud.error();
            }
            return true;
        }
        return false;
    }

    private boolean clickShop(double mx, double my, int y1, int x2) {
        int cw = catW();
        for (int i = 0; i < Shop.CATS.length; i++) {
            if (Ui.inside(mx, my, catX(i), y1 + 27, cw, 13)) {
                if (lastCat != i) {
                    lastCat = i;
                    tabTime = System.currentTimeMillis();
                    Hud.click();
                }
                return true;
            }
        }
        int[] cv = coreCount();
        int bw = this.font.width(sellLabel(cv)) + 16;
        if (cv[0] > 0 && Ui.inside(mx, my, x2 - 8 - bw, y1 + 44, bw, 14)) {
            send(Net.ACT_SELL, 0, 0);
            Hud.click();
            return true;
        }
        int cat = Math.max(0, Math.min(Shop.CATS.length - 1, lastCat));
        List<Integer> ids = offersIn(cat);
        int myRank = Defs.rank(CData.level());
        for (int k = 0; k < ids.size(); k++) {
            int[] rr = shopRow(k, ids.size());
            if (Ui.inside(mx, my, rr[0], rr[1], rr[2], SHOP_ROW - 2)) {
                int i = ids.get(k);
                Shop.Offer o = Shop.OFFERS.get(i);
                int n = hasShiftDown() ? 5 : 1;
                if (myRank >= o.minRank() && CData.coins() >= (long) o.price() * n) {
                    send(Net.ACT_BUY, i, n);
                    Hud.click();
                } else {
                    Hud.error();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (tab == 2 && listBox().in(mx, my)) {
            int max = Math.max(0, ClientShadows.list().size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ElSistemaClient.STATUS != null && ElSistemaClient.STATUS.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_4) {
            switchTab(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ================================================================== prueba automática

    /** Para la prueba automática del cliente: cambia de pestaña, árbol y categoría. */
    public void selfTestView(int newTab, int newSub, int newCat) {
        this.tab = newTab;
        lastTab = newTab;
        lastSub = newSub;
        lastCat = newCat;
        this.selNode = -1;
        this.tabTime = 0L;
    }

    /** Recorre todos los textos de la ventana con todas las razas y clases (para detectar errores). */
    public static int selfCheck() {
        int n = 0;
        for (int kind = 0; kind < 3; kind++) {
            int count = kind == 0 ? Trees.RACE.length : kind == 1 ? Trees.CLASS.length : 1;
            for (int idx = 0; idx < count; idx++) {
                Trees.Node[] t = Trees.tree(kind, idx);
                for (int k = 0; k < 5; k++) {
                    icon(kind, idx, k);
                    for (int r = 0; r <= t[k].max(); r++) {
                        Trees.effect(t[k], r);
                        n++;
                    }
                }
            }
        }
        for (int i = 0; i < Shop.OFFERS.size(); i++) {
            offerTip(i, 0);
            offerTip(i, 5);
            shopIcon(i);
            n++;
        }
        for (int r = 0; r < Defs.RACE_NAME.length; r++) {
            raceTip(r);
        }
        for (int c = 0; c < Defs.CLASS_NAME.length; c++) {
            classTip(c);
        }
        for (int i = 0; i < 5; i++) {
            statTip(i);
        }
        return n;
    }
}
