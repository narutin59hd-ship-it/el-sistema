package com.elsistema.client;

import com.elsistema.Defs;
import com.elsistema.ModItems;
import com.elsistema.Net;
import com.elsistema.Shop;
import com.elsistema.Trees;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ventana del Sistema (tecla V): Estado, Habilidades y Tienda. */
public class SystemScreen extends Screen {
    private static int lastTab = 0;
    private static final String[] TABS = {"ESTADO", "HABILIDADES", "TIENDA"};
    private static final int TAB_W = 92;
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

    private static final int ROW_H = 18;
    private static final int ROWS = 9;

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

    private final long openTime = System.currentTimeMillis();
    private long tabTime = openTime;
    private int tab = lastTab;

    public SystemScreen() {
        super(Component.literal("Sistema"));
        Hud.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 2.0F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int pw() {
        return Math.min(380, this.width - 16);
    }

    private int ph() {
        return Math.min(244, this.height - 12);
    }

    private int px() {
        return (this.width - pw()) / 2;
    }

    private int py() {
        return (this.height - ph()) / 2;
    }

    private static int tabX(int cx, int i) {
        return cx - (3 * TAB_W + 12) / 2 + i * (TAB_W + 6);
    }

    private static int rank(boolean race, int node) {
        return race ? CData.raceRank(node) : CData.classRank(node);
    }

    private static Item icon(boolean race, int index, int node) {
        Item[][] all = race ? RACE_ICONS : CLASS_ICONS;
        return index >= 0 && index < all.length ? all[index][node] : Items.BARRIER;
    }

    private static int[][] nodePos(int cx, int by2) {
        int b = by2 - 26;
        return new int[][]{{cx, b}, {cx - 42, b - 46}, {cx + 42, b - 46}, {cx - 42, b - 92}, {cx + 42, b - 92}};
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            g.fill(x, y, x + 2, y + 2, color);
        }
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
        Ui.panel(g, x1, cy - hh, x2, cy + hh, Ui.CYAN, Math.min(1F, p * 1.5F));
        float a = Ui.clamp01((p - 0.55F) / 0.45F);
        if (a <= 0F) return;
        g.enableScissor(x1, cy - hh, x2, cy + hh);
        Ui.shine(g, x1, y1, x2, y1 + 16, now, a);
        tabs(g, x1, y1, x2, mx, my, a);
        float ca = a * Ui.clamp01((now - tabTime) / 160F);
        List<String> tip;
        if (tab == 0) {
            tip = status(g, x1, y1, x2, y2, mx, my, ca, now);
        } else if (tab == 1) {
            tip = trees(g, x1, y1, x2, y2, mx, my, ca, now);
        } else {
            tip = shop(g, x1, y1, x2, y2, mx, my, ca);
        }
        g.disableScissor();
        if (tip != null && ca >= 1F) {
            Ui.tooltip(g, this.font, tip, mx, my, this.width, this.height);
        }
    }

    private void tabs(GuiGraphics g, int x1, int y1, int x2, int mx, int my, float a) {
        Font f = this.font;
        int cx = (x1 + x2) / 2;
        for (int i = 0; i < 3; i++) {
            int tx = tabX(cx, i);
            int ty = y1 + TAB_Y;
            boolean on = i == tab;
            boolean hover = Ui.inside(mx, my, tx, ty, TAB_W, TAB_H);
            g.fill(tx, ty, tx + TAB_W, ty + TAB_H, Ui.fade(on ? 0xA03A8DFF : hover ? 0x60305070 : 0x40101A28, a));
            Ui.border(g, tx, ty, tx + TAB_W, ty + TAB_H, Ui.fade(on ? Ui.CYAN : 0xFF3C5A74, a));
            String label = TABS[i];
            if ((i == 0 && CData.points() > 0) || (i == 1 && CData.skillPoints() > 0)) label += " *";
            Ui.center(g, f, label, tx + TAB_W / 2, ty + 3, on ? Ui.WHITE : Ui.GRAY, a);
        }
        Ui.divider(g, x1, x2, y1 + TAB_Y + TAB_H + 3, a);
    }

    private static void label(GuiGraphics g, Font f, String key, String value, int x, int y, int color, float a) {
        String k = key + ":";
        Ui.text(g, f, k, x, y, Ui.GRAY, a);
        Ui.glow(g, f, value, x + f.width(k) + 5, y, color, 0x4050B4FF, a);
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

    // ------------------------------------------------------------------ pestaña HABILIDADES

    private List<String> trees(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        int cx = (x1 + x2) / 2;
        int sp = CData.skillPoints();
        String pts = "PUNTOS DE HABILIDAD: " + sp;
        float pulse = sp > 0 ? 0.7F + 0.3F * (float) Math.sin(now / 250.0) : 1F;
        Ui.glow(g, f, pts, cx - f.width(pts) / 2, y1 + 27, sp > 0 ? 0xFFB57BFF : Ui.WHITE, 0x50B57BFF, a * pulse);
        List<String> t1 = tree(g, true, x1 + 8, cx - 4, y1 + 40, y2 - 16, mx, my, a, now);
        List<String> t2 = tree(g, false, cx + 4, x2 - 8, y1 + 40, y2 - 16, mx, my, a, now);
        Ui.center(g, f, "1 punto por nivel o runa  ·  clic en un nudo para mejorarlo", cx, y2 - 12, 0xFF5F7387, a);
        return t1 != null ? t1 : t2;
    }

    private List<String> tree(GuiGraphics g, boolean race, int bx1, int bx2, int by1, int by2,
                              int mx, int my, float a, long now) {
        Font f = this.font;
        int index = race ? CData.race() : CData.cls();
        Trees.Node[] t = Trees.tree(race, index);
        Ui.border(g, bx1, by1, bx2, by2, Ui.fade(0xFF2A4A66, a));
        if (t == null) return null;
        int cx = (bx1 + bx2) / 2;
        String who = race ? Defs.RACE_NAME[index] : Defs.CLASS_NAME[index];
        String title = (race ? "RAZA · " : "CLASE · ") + who.toUpperCase(Locale.ROOT);
        Ui.glow(g, f, title, cx - f.width(title) / 2, by1 + 5, Ui.WHITE, Ui.GLOW, a);
        Ui.center(g, f, race ? Trees.RACE_TREE[index] : Trees.CLASS_TREE[index], cx, by1 + 16, Ui.GRAY, a);
        int[][] pos = nodePos(cx, by2);
        for (int k = 1; k < 5; k++) {
            int par = Trees.PARENT[k];
            boolean lit = rank(race, par) > 0;
            line(g, pos[par][0], pos[par][1], pos[k][0], pos[k][1], Ui.fade(lit ? 0xFF5FD7FF : 0xFF2C3A48, a));
        }
        List<String> tip = null;
        for (int k = 0; k < 5; k++) {
            int nx = pos[k][0];
            int ny = pos[k][1];
            Trees.Node n = t[k];
            int rk = rank(race, k);
            int par = Trees.PARENT[k];
            boolean unlocked = CData.level() >= n.lvl() && (par < 0 || rank(race, par) > 0);
            boolean maxed = rk >= n.max();
            boolean can = unlocked && !maxed && CData.skillPoints() > 0;
            boolean hover = Ui.inside(mx, my, nx - 11, ny - 11, 22, 22);
            int border = !unlocked ? 0xFF3C4A58 : maxed ? Ui.GOLD : can ? Ui.GREEN : Ui.CYAN;
            float pa = can ? 0.6F + 0.4F * (float) Math.sin(now / 220.0) : 1F;
            if (can || hover) {
                Ui.border(g, nx - 13, ny - 13, nx + 13, ny + 13, Ui.fade(border, a * 0.45F * pa));
            }
            g.fill(nx - 11, ny - 11, nx + 11, ny + 11, Ui.fade(hover ? 0xE0203450 : 0xE0101A28, a));
            Ui.border(g, nx - 11, ny - 11, nx + 11, ny + 11, Ui.fade(border, a * pa));
            if (a > 0.9F) {
                g.renderItem(new ItemStack(icon(race, index, k)), nx - 8, ny - 8);
                if (!unlocked) {
                    g.pose().pushPose();
                    g.pose().translate(0.0F, 0.0F, 200.0F);
                    g.fill(nx - 10, ny - 10, nx + 10, ny + 10, 0xA0000000);
                    g.pose().popPose();
                }
            }
            Ui.center(g, f, rk + "/" + n.max(), nx, ny + 13, maxed ? Ui.GOLD : unlocked ? Ui.WHITE : 0xFF6A7580, a);
            if (hover) {
                tip = nodeTip(t, k, rk, unlocked, maxed, race);
            }
        }
        return tip;
    }

    private static List<String> nodeTip(Trees.Node[] t, int k, int rk, boolean unlocked, boolean maxed, boolean race) {
        Trees.Node n = t[k];
        List<String> l = new ArrayList<>();
        l.add(n.name() + "   " + rk + "/" + n.max());
        l.add(n.desc());
        if (CData.level() < n.lvl()) l.add("Requiere nivel " + n.lvl());
        int par = Trees.PARENT[k];
        if (par >= 0 && rank(race, par) <= 0) l.add("Requiere: " + t[par].name());
        if (maxed) {
            l.add("MÁXIMO");
        } else if (unlocked) {
            l.add(CData.skillPoints() > 0 ? "Clic: mejorar (1 punto)" : "No tienes puntos de habilidad");
        }
        return l;
    }

    // ------------------------------------------------------------------ pestaña TIENDA

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

    private String sellLabel(int[] cv) {
        return cv[0] > 0 ? "VENDER NÚCLEOS (" + cv[0] + "): +" + cv[1] : "SIN NÚCLEOS PARA VENDER";
    }

    private static int[] row(int i, int x1, int y1, int colW) {
        int col = i / ROWS;
        int r = i % ROWS;
        return new int[]{x1 + 10 + col * (colW + 10), y1 + 46 + r * ROW_H};
    }

    private List<String> shop(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a) {
        Font f = this.font;
        List<String> tip = null;
        int coins = CData.coins();
        Ui.glow(g, f, "ORO: " + coins, x1 + 12, y1 + 28, Ui.GOLD, 0x50FFC857, a);
        int[] cv = coreCount();
        String sl = sellLabel(cv);
        int bw = f.width(sl) + 16;
        Ui.button(g, f, sl, x2 - 10 - bw, y1 + 25, bw, 14, mx, my, Ui.GOLD, cv[0] > 0, a);
        int colW = (x2 - x1 - 30) / 2;
        for (int i = 0; i < Shop.NAME.length; i++) {
            int[] rr = row(i, x1, y1, colW);
            int rx = rr[0];
            int ry = rr[1];
            boolean hover = Ui.inside(mx, my, rx, ry, colW, ROW_H - 2);
            boolean afford = coins >= Shop.PRICE[i];
            g.fill(rx, ry, rx + colW, ry + ROW_H - 2, Ui.fade(hover ? 0x803A8DFF : 0x50101A28, a));
            Ui.border(g, rx, ry, rx + colW, ry + ROW_H - 2, Ui.fade(hover ? Ui.CYAN : 0xFF2A4A66, a));
            if (a > 0.9F) {
                g.renderItem(Shop.icon(i), rx + 1, ry);
            }
            String price = String.valueOf(Shop.PRICE[i]);
            int pwid = f.width(price);
            Ui.text(g, f, fit(f, Shop.NAME[i], colW - 30 - pwid), rx + 20, ry + 4, Ui.WHITE, a);
            Ui.text(g, f, price, rx + colW - 4 - pwid, ry + 4, afford ? Ui.GOLD : Ui.RED, a);
            if (hover) {
                tip = offerTip(i);
            }
        }
        Ui.center(g, f, "Gana oro matando monstruos, en mazmorras y vendiendo núcleos", (x1 + x2) / 2, y2 - 12, 0xFF5F7387, a);
        return tip;
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

    private static List<String> offerTip(int i) {
        List<String> l = new ArrayList<>();
        l.add(Shop.NAME[i]);
        l.add("Precio: " + Shop.PRICE[i] + " de oro");
        if (i >= 12) l.add("Abre una mazmorra de rango " + Defs.RANK[i - 12]);
        if (i == 11) l.add("+1 punto de habilidad");
        l.add("Clic: comprar   ·   Mayús + clic: comprar 5");
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

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int x1 = px();
        int y1 = py();
        int x2 = x1 + pw();
        int y2 = y1 + ph();
        int cx = (x1 + x2) / 2;
        for (int i = 0; i < 3; i++) {
            if (Ui.inside(mx, my, tabX(cx, i), y1 + TAB_Y, TAB_W, TAB_H)) {
                switchTab(i);
                return true;
            }
        }
        if (tab == 0 && CData.points() > 0) {
            int lx = x1 + 12;
            for (int i = 0; i < 5; i++) {
                int ry = y1 + Y_STATS + i * 14;
                if (Ui.inside(mx, my, lx + PLUS_X, ry - 2, 13, 12)) {
                    ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_STAT, i, hasShiftDown() ? 5 : 1));
                    Hud.click();
                    return true;
                }
            }
        }
        if (tab == 1) {
            for (int side = 0; side < 2; side++) {
                boolean race = side == 0;
                int bx1 = race ? x1 + 8 : cx + 4;
                int bx2 = race ? cx - 4 : x2 - 8;
                int[][] pos = nodePos((bx1 + bx2) / 2, y2 - 16);
                Trees.Node[] t = Trees.tree(race, race ? CData.race() : CData.cls());
                for (int k = 0; k < 5 && t != null; k++) {
                    if (Ui.inside(mx, my, pos[k][0] - 11, pos[k][1] - 11, 22, 22)) {
                        int par = Trees.PARENT[k];
                        boolean can = CData.skillPoints() > 0 && rank(race, k) < t[k].max()
                                && CData.level() >= t[k].lvl() && (par < 0 || rank(race, par) > 0);
                        if (can) {
                            ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_TREE, race ? 0 : 1, k));
                            Hud.click();
                        } else {
                            Hud.error();
                        }
                        return true;
                    }
                }
            }
        }
        if (tab == 2) {
            int[] cv = coreCount();
            int bw = this.font.width(sellLabel(cv)) + 16;
            if (cv[0] > 0 && Ui.inside(mx, my, x2 - 10 - bw, y1 + 25, bw, 14)) {
                ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_SELL, 0, 0));
                Hud.click();
                return true;
            }
            int colW = (x2 - x1 - 30) / 2;
            for (int i = 0; i < Shop.NAME.length; i++) {
                int[] rr = row(i, x1, y1, colW);
                if (Ui.inside(mx, my, rr[0], rr[1], colW, ROW_H - 2)) {
                    int n = hasShiftDown() ? 5 : 1;
                    if (CData.coins() >= (long) Shop.PRICE[i] * n) {
                        ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_BUY, i, n));
                        Hud.click();
                    } else {
                        Hud.error();
                    }
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
        if (keyCode == GLFW.GLFW_KEY_1) {
            switchTab(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_2) {
            switchTab(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_3) {
            switchTab(2);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
