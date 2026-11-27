package com.elsistema.client;

import com.elsistema.CityNpc;
import com.elsistema.Defs;
import com.elsistema.ModArmor;
import com.elsistema.ModItems;
import com.elsistema.Net;
import com.elsistema.Quests;
import com.elsistema.ShadowTree;
import com.elsistema.SideQuests;
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

/** Ventana del Sistema (tecla V): Estado, Habilidades (3 árboles), Sombras, Tienda, Misiones, Rango y Ajustes. */
public class SystemScreen extends Screen {
    private static int lastTab = 0;
    private static int lastSub = 0;
    private static int lastCat = 0;
    private static int lastQSub = 0;
    private static final String[] TABS = {"ESTADO", "HABILIDADES", "SOMBRAS", "TIENDA", "MISIONES", "RANGO"};
    private static final int TAB_RANK = 5;
    /** Pestaña de AJUSTES: el engranaje a la derecha de las pestañas (tecla 7). */
    private static final int TAB_CONFIG = 6;
    private static final int GEAR_W = 18;
    private static final String[] GEAR = {
            "....#....",
            ".#.###.#.",
            "..#####..",
            ".###.###.",
            "###...###",
            ".###.###.",
            "..#####..",
            ".#.###.#.",
            "....#...."};
    private static final String[] SETTING_TITLE = {"PARTÍCULAS DEL MOD", "MODO RENDIMIENTO"};
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
    private static final int QUEST_ROW = 24;
    private static final String[] QUEST_SUBS = {"MISIONES OCULTAS", "HITOS DE NIVEL", "CIUDAD"};
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
    private int selQuest = -1;
    private int qscroll = 0;
    private int releaseId = -1;
    private long releaseTime = 0L;
    private LivingEntity preview;
    private String previewType = "";
    private boolean prestigeArmed = false;
    private long prestigeTime = 0L;
    /** Pestaña Sombras: true = se ve el árbol propio de la sombra elegida. */
    private boolean shadowTreeView = false;

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
        int n = TABS.length;
        return Math.min(98, (pw() - 20 - 4 * (n - 1) - GEAR_W - 4) / n);
    }

    /** X de una pestaña; la última (TAB_CONFIG) es el engranaje de los ajustes. */
    private int tabX(int i) {
        int tw = tabW();
        int n = TABS.length;
        int group = n * tw + 4 * (n - 1) + 4 + GEAR_W;
        return px() + pw() / 2 - group / 2 + Math.min(i, n) * (tw + 4);
    }

    private static int tabAccent(int i) {
        return i == 2 ? Ui.SHADOW : i == 4 ? Ui.GOLD : i == TAB_RANK ? CData.rankColor() : Ui.CYAN;
    }

    // ================================================================== dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        long now = System.currentTimeMillis();
        float p = Ui.easeOut((now - openTime) / 280F);
        g.fill(0, 0, this.width, this.height, Ui.fade(0xC0000208, p));
        int x1 = px();
        int y1 = py();
        int x2 = x1 + pw();
        int y2 = y1 + ph();
        int cy = (y1 + y2) / 2;
        int hh = Math.max(1, Math.round((y2 - y1) * p / 2F));
        int accent = tabAccent(tab);
        Ui.panel(g, x1, cy - hh, x2, cy + hh, accent, Math.min(1F, p * 1.5F));
        float a = Ui.clamp01((p - 0.55F) / 0.45F);
        if (a <= 0F) return;
        g.enableScissor(x1, cy - hh, x2, cy + hh);
        Ui.shine(g, x1, y1, x2, y1 + 16, now, a);
        List<String> tabTip = tabs(g, x1, y1, x2, mx, my, a);
        float ca = a * Ui.clamp01((now - tabTime) / 160F);
        List<String> tip = switch (tab) {
            case 1 -> trees(g, x1, y1, x2, y2, mx, my, ca, now);
            case 2 -> shadows(g, x1, y1, x2, y2, mx, my, ca, now);
            case 3 -> shop(g, x1, y1, x2, y2, mx, my, ca);
            case 4 -> quests(g, x1, y1, x2, y2, mx, my, ca, now);
            case TAB_RANK -> rankTab(g, x1, y1, x2, y2, mx, my, ca, now);
            case TAB_CONFIG -> settings(g, x1, y1, x2, y2, mx, my, ca, now);
            default -> status(g, x1, y1, x2, y2, mx, my, ca, now);
        };
        if (tabTip != null) tip = tabTip;
        g.disableScissor();
        if (tip != null && ca >= 1F) {
            Ui.tooltip(g, this.font, tip, mx, my, this.width, this.height);
        }
    }

    private List<String> tabs(GuiGraphics g, int x1, int y1, int x2, int mx, int my, float a) {
        Font f = this.font;
        int tw = tabW();
        List<String> tip = null;
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabX(i);
            int ty = y1 + TAB_Y;
            boolean on = i == tab;
            boolean hover = Ui.inside(mx, my, tx, ty, tw, TAB_H);
            int acc = tabAccent(i);
            int bg = on ? (i == 2 ? 0xA05A2D9F : i == 4 ? 0xA08A6A1A : i == TAB_RANK ? 0xA0000000 | ((acc >> 1) & 0x7F7F7F)
                    : 0xA03A8DFF) : hover ? 0x60305070 : 0x40101A28;
            g.fill(tx, ty, tx + tw, ty + TAB_H, Ui.fade(bg, a));
            Ui.border(g, tx, ty, tx + tw, ty + TAB_H, Ui.fade(on ? acc : 0xFF3C5A74, a));
            String label = TABS[i];
            if ((i == 0 && CData.points() > 0) || (i == 1 && CData.skillPoints() > 0)
                    || (i == 2 && ClientShadows.anyPoints())) label += " *";
            boolean alert = i == 4 && CData.quest(Quests.Q_CLASS) == Quests.ACTIVE
                    || i == TAB_RANK && (CData.examReady() || CData.prestigeReady());
            if (alert) label += " !";
            Ui.center(g, f, fit(f, label, tw - 4), tx + tw / 2, ty + 3, on ? Ui.WHITE : alert ? Ui.GOLD : Ui.GRAY, a);
        }
        // Engranaje: pestaña de AJUSTES
        int gx = tabX(TAB_CONFIG);
        int gy = y1 + TAB_Y;
        boolean gOn = tab == TAB_CONFIG;
        boolean gHover = Ui.inside(mx, my, gx, gy, GEAR_W, TAB_H);
        g.fill(gx, gy, gx + GEAR_W, gy + TAB_H, Ui.fade(gOn ? 0xA03A8DFF : gHover ? 0x60305070 : 0x40101A28, a));
        Ui.border(g, gx, gy, gx + GEAR_W, gy + TAB_H, Ui.fade(gOn ? Ui.CYAN : 0xFF3C5A74, a));
        int gc = Ui.fade(gOn || gHover ? Ui.WHITE : Ui.GRAY, a);
        int ox = gx + (GEAR_W - 9) / 2;
        int oy = gy + (TAB_H - 9) / 2;
        for (int r = 0; r < GEAR.length; r++) {
            for (int c = 0; c < GEAR[r].length(); c++) {
                if (GEAR[r].charAt(c) == '#') g.fill(ox + c, oy + r, ox + c + 1, oy + r + 1, gc);
            }
        }
        if (gHover) tip = List.of("Ajustes (tecla 7)", "Partículas y modo rendimiento");
        Ui.divider(g, x1, x2, y1 + TAB_Y + TAB_H + 3, a);
        return tip;
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
        label(g, f, "RANGO", CData.rankLabel(), lx, y1 + Y_RANK, CData.rankColor(), a);
        label(g, f, "CLASE", CData.className(), mid, y1 + Y_RANK, CData.mutated() ? Ui.GOLD : Ui.WHITE, a);
        label(g, f, "RAZA", Hud.name(Defs.RACE_NAME, CData.race()), lx, y1 + Y_RACE, Ui.WHITE, a);
        label(g, f, "ORO", String.valueOf(CData.coins()), mid, y1 + Y_RACE, Ui.GOLD, a);
        String ess = "◆ " + CData.essences();
        Ui.glow(g, f, ess, rx - f.width(ess), y1 + Y_RACE, Hud.ESSENCE, 0x407FE8D8, a);
        if (Ui.inside(mx, my, rx - f.width(ess) - 2, y1 + Y_RACE - 1, f.width(ess) + 4, 10)) {
            tip = List.of("Esencias: " + CData.essences(), "Se ganan matando monstruos con rango y completando mazmorras",
                    "Se gastan en la herrería y en el mercader de la ciudad");
        }
        if (Ui.inside(mx, my, mid, y1 + Y_RANK - 1, rx - mid, 10) && CData.cls() >= 0) {
            tip = classTip(CData.cls(), CData.mutated());
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
            boolean mut = CData.mutated();
            String k = "HABILIDAD [" + Hud.key(ElSistemaClient.SKILL) + "]:";
            Ui.text(g, f, k, lx, y1 + Y_SKILL, Ui.GRAY, a);
            Ui.glow(g, f, Defs.skillName(c, mut), lx + f.width(k) + 5, y1 + Y_SKILL, mut ? Ui.GOLD : Ui.CYAN,
                    mut ? 0x50FFC857 : 0x5050B4FF, a);
            String cost = Defs.SKILL_COST[c] + " MP  ·  " + (Defs.SKILL_CD[c] / 20) + " s";
            Ui.text(g, f, cost, rx - f.width(cost), y1 + Y_SKILL, Ui.MUTED, a);
            int yy = y1 + Y_SKILL + 12;
            for (String l : Ui.wrap(f, Defs.skillDesc(c, mut), rx - lx)) {
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
        hint(g, "Esc: cerrar   ·   1-6: pestañas   ·   ratón encima: detalles", x1, x2, y2, a);
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

    private static List<String> classTip(int c, boolean mut) {
        List<String> l = new ArrayList<>();
        l.add("CLASE: " + Defs.className(c, mut).toUpperCase(Locale.ROOT));
        if (mut) l.add("Mutación de " + Defs.CLASS_NAME[c] + " (prueba superada)");
        for (String s : Defs.CLASS_BONUS[c]) {
            l.add(s);
        }
        if (mut) {
            for (String s : Defs.MUTATION_BONUS[c]) {
                l.add("★ " + s);
            }
        }
        l.add("Habilidad: " + Defs.skillName(c, mut));
        return l;
    }

    // ------------------------------------------------------------------ pestaña HABILIDADES

    private static int sub() {
        return lastSub < 0 || lastSub > 2 ? 0 : lastSub;
    }

    private int chipW() {
        return Math.min(120, (pw() - 16 - 8 - 56) / 3);
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
            String who = i == 0 ? Hud.name(Defs.RACE_NAME, CData.race()) : i == 1 ? CData.className() : "Monarca";
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
                : kind == 1 ? "Árbol de clase · " + CData.className() : "Árbol compartido por todos";
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
            case ClientShadows.RECOVERING -> "Caída · vuelve en " + ClientShadows.downText(e);
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
            shadowTreeView = false;
        }
        if (shadowTreeView) {
            List<String> ttip = shadowTree(g, f, lb, ib, sel, mx, my, a, now);
            hint(g, "Cada nivel de la sombra le da " + ShadowTree.POINTS_PER_LEVEL + " punto · ganan tu misma EXP", x1, x2, y2, a);
            return ttip;
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
            String ptsTag = e.pts() > 0 ? "+" + e.pts() : "";
            if (!ptsTag.isEmpty()) {
                Ui.text(g, f, ptsTag, right - f.width(lv) - 6 - f.width(rt) - 6 - f.width(ptsTag), ry + 3, Ui.GOLD, a);
            }
            int nameW = right - f.width(lv) - f.width(rt) - 12 - (rx1 + 6) - (e.boss() ? f.width("JEFE") + 4 : 0)
                    - (ptsTag.isEmpty() ? 0 : f.width(ptsTag) + 6);
            String nm = fit(f, e.name(), nameW);
            Ui.text(g, f, nm, rx1 + 6, ry + 3, Ui.WHITE, a);
            if (e.boss()) Ui.text(g, f, "JEFE", rx1 + 10 + f.width(nm), ry + 3, Ui.GOLD, a);
            Ui.text(g, f, stateText(e), rx1 + 6, ry + 12, stateColor(e.state()), a * 0.9F);
            if (e.state() == ClientShadows.OUT) {
                Ui.bar(g, right - 40, ry + 14, 40, 3, e.hp() / 100F, Ui.HP_RED, a);
            }
            if (hover) {
                List<String> l = new ArrayList<>();
                l.add(e.name());
                l.add("Rango " + rt + " · nivel " + e.level() + " · " + e.kills() + " bajas");
                l.add("Ataque: " + e.attackText() + " por golpe");
                if (e.pts() > 0) l.add("¡" + e.pts() + (e.pts() == 1 ? " punto" : " puntos") + " para su árbol!");
                l.add("Clic: seleccionar");
                tip = l;
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
                "    pulsa " + arise + ": ¡Levántate! (3 intentos)",
                "3. Más rango = más maná y menos %.",
                "    Sube la Perspicacia en el árbol."};
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
        int h = Math.max(40, Math.min(110, ib.h() - 100));
        return new Box(ib.x1() + 4, ib.y1() + 4, ib.x2() - 4, ib.y1() + 4 + h);
    }

    private Box treeButton(Box ib) {
        return new Box(ib.x1() + 6, ib.y2() - 37, ib.x2() - 6, ib.y2() - 23);
    }

    /** Barra de EXP de una sombra con el texto "EXP 45%". */
    private static void shadowXpBar(GuiGraphics g, Font f, ClientShadows.Entry e, int x, int y, int w, float a) {
        boolean max = e.level() >= Defs.MAX_LEVEL;
        String t = max ? "MÁX" : (e.xp() / 10) + "%";
        Ui.text(g, f, "EXP", x, y, Ui.GRAY, a);
        int bx = x + f.width("EXP") + 4;
        int bw = Math.max(10, w - (bx - x) - f.width(t) - 4);
        Ui.bar(g, bx, y + 2, bw, 4, max ? 1F : e.xp() / 1000F, Ui.SHADOW, a);
        Ui.text(g, f, t, x + w - f.width(t), y, Ui.WHITE, a);
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
        shadowXpBar(g, f, e, lx, y, w, a);
        y += 10;
        Ui.text(g, f, fit(f, e.kills() + " bajas" + (e.boss() ? "   ·   JEFE" : ""), w), lx, y, e.boss() ? Ui.GOLD : Ui.GRAY, a);
        String atk = "Ataque " + e.attackText();
        Ui.text(g, f, atk, ib.x2() - 7 - f.width(atk), y, Ui.HP_RED, a);
        y += 10;
        if (y < ib.y2() - 47) {
            Ui.text(g, f, fit(f, stateText(e), w), lx, y, stateColor(e.state()), a);
        }
        Box tb = treeButton(ib);
        if (e.pts() > 0) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(now / 240.0);
            Ui.border(g, tb.x1() - 2, tb.y1() - 2, tb.x2() + 2, tb.y2() + 2, Ui.fade(Ui.GOLD, a * 0.6F * pulse));
        }
        Ui.button(g, f, e.pts() > 0 ? "ÁRBOL PROPIO  (+" + e.pts() + ")" : "ÁRBOL PROPIO", tb.x1(), tb.y1(), tb.w(), tb.h(),
                mx, my, e.pts() > 0 ? Ui.GOLD : Ui.SHADOW, true, a);
        Box act = actionButton(ib);
        Box rel = releaseButton(ib);
        boolean out = e.state() == ClientShadows.OUT;
        boolean recovering = e.state() == ClientShadows.RECOVERING;
        Ui.button(g, f, out ? "GUARDAR" : "INVOCAR", act.x1(), act.y1(), act.w(), act.h(), mx, my, Ui.SHADOW, !recovering, a);
        boolean armed = releaseId == e.id() && System.currentTimeMillis() - releaseTime < 3000L;
        Ui.button(g, f, armed ? "¿SEGURO?" : "LIBERAR", rel.x1(), rel.y1(), rel.w(), rel.h(), mx, my, Ui.RED, true, a);
    }

    // ------------------------------------------------------------------ árbol propio de una sombra

    private int treeRowH(Box lb) {
        return Math.max(18, (lb.h() - 24) / ShadowTree.SIZE);
    }

    private Box treePlus(Box lb, int k) {
        int rh = treeRowH(lb);
        int ry = lb.y1() + 20 + k * rh;
        return new Box(lb.x2() - 20, ry + (rh - 3) / 2 - 7, lb.x2() - 6, ry + (rh - 3) / 2 + 7);
    }

    private Box backButton(Box ib) {
        return new Box(ib.x1() + 6, ib.y2() - 20, ib.x2() - 6, ib.y2() - 6);
    }

    private static List<String> shadowNodeTip(ClientShadows.Entry e, int k) {
        ShadowTree.Node n = ShadowTree.NODES[k];
        int r = e.treeRank(k);
        List<String> l = new ArrayList<>();
        l.add(n.name());
        l.add(n.desc());
        l.add("Rango " + r + "/" + n.max() + (r > 0 ? " · ahora " + ShadowTree.effect(k, r) : ""));
        if (r < n.max()) {
            l.add("Siguiente: " + ShadowTree.effect(k, r + 1) + " · cuesta " + n.cost() + (n.cost() == 1 ? " punto" : " puntos"));
        } else {
            l.add("¡Rango máximo!");
        }
        return l;
    }

    /** Árbol propio de la sombra elegida: los 7 nudos a la izquierda y la sombra a la derecha. */
    private List<String> shadowTree(GuiGraphics g, Font f, Box lb, Box ib, ClientShadows.Entry e, int mx, int my, float a, long now) {
        List<String> tip = null;
        int pts = e.pts();
        String title = "ÁRBOL DE " + e.name().toUpperCase(Locale.ROOT);
        String ptsText = pts + (pts == 1 ? " punto" : " puntos");
        Ui.glow(g, f, fit(f, title, lb.w() - 16 - f.width(ptsText)), lb.x1() + 6, lb.y1() + 6, Ui.WHITE, Ui.SHADOW_GLOW, a);
        Ui.text(g, f, ptsText, lb.x2() - 6 - f.width(ptsText), lb.y1() + 6, pts > 0 ? Ui.GOLD : Ui.MUTED, a);
        int rh = treeRowH(lb);
        for (int k = 0; k < ShadowTree.SIZE; k++) {
            ShadowTree.Node n = ShadowTree.NODES[k];
            int r = e.treeRank(k);
            int ry = lb.y1() + 20 + k * rh;
            int rx1 = lb.x1() + 4;
            int rx2 = lb.x2() - 24;
            boolean can = ShadowTree.canUp(e.tree(), pts, k);
            boolean maxed = r >= n.max();
            boolean hover = Ui.inside(mx, my, rx1, ry, lb.x2() - 4 - rx1, rh - 3);
            int accent = maxed ? Ui.GOLD : r > 0 ? Ui.SHADOW : 0xFF24364A;
            g.fill(rx1, ry, rx2, ry + rh - 2, Ui.fade(hover ? 0x90182438 : r > 0 ? 0x80180E2A : 0x70101A28, a));
            Ui.border(g, rx1, ry, rx2, ry + rh - 2, Ui.fade(accent, a));
            String rank = r + "/" + n.max();
            Ui.text(g, f, fit(f, n.name(), rx2 - rx1 - 12 - f.width(rank)), rx1 + 5, ry + 2, r > 0 ? Ui.WHITE : Ui.GRAY, a);
            Ui.text(g, f, rank, rx2 - 4 - f.width(rank), ry + 2, maxed ? Ui.GOLD : Ui.WHITE, a);
            if (rh >= 22) {
                String eff = r > 0 ? ShadowTree.effect(k, r) : n.desc();
                Ui.text(g, f, fit(f, eff, rx2 - rx1 - 10), rx1 + 5, ry + 11, r > 0 ? 0xFFC9A7FF : Ui.MUTED, a * 0.9F);
            }
            Ui.bar(g, rx1 + 1, ry + rh - 4, rx2 - rx1 - 2, 1, r / (float) n.max(), maxed ? Ui.GOLD : Ui.SHADOW, a);
            Box plus = treePlus(lb, k);
            if (can) {
                float pulse = 0.5F + 0.5F * (float) Math.sin(now / 240.0 + k);
                Ui.border(g, plus.x1() - 1, plus.y1() - 1, plus.x2() + 1, plus.y2() + 1, Ui.fade(Ui.GOLD, a * 0.5F * pulse));
            }
            Ui.button(g, f, n.cost() > 1 ? "+" + n.cost() : "+", plus.x1(), plus.y1(), plus.w(), plus.h(), mx, my,
                    maxed ? Ui.GOLD : Ui.GREEN, can, a);
            if (hover) tip = shadowNodeTip(e, k);
        }

        // Panel derecho: la sombra
        Box pb = previewBox(ib);
        g.fillGradient(pb.x1(), pb.y1(), pb.x2(), pb.y2(), Ui.fade(0x30100020, a), Ui.fade(0x902A0F4A, a));
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
        shadowXpBar(g, f, e, lx, y, w, a);
        y += 10;
        String atk = "Ataque " + e.attackText();
        Ui.text(g, f, atk, lx, y, Ui.HP_RED, a);
        String spent = e.spent() + "/" + ShadowTree.totalCost();
        Ui.text(g, f, spent, ib.x2() - 7 - f.width(spent), y, Ui.MUTED, a);
        y += 10;
        if (y < ib.y2() - 30) {
            Ui.text(g, f, fit(f, pts > 0 ? "Elige dónde gastar sus puntos" : "Gana puntos al subir de nivel", w), lx, y,
                    pts > 0 ? Ui.GOLD : Ui.MUTED, a);
        }
        Box back = backButton(ib);
        Ui.button(g, f, "VOLVER AL EJÉRCITO", back.x1(), back.y1(), back.w(), back.h(), mx, my, Ui.SHADOW, true, a);
        return tip;
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
        int myRank = CData.hunterRank();
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
        hint(g, "Clic: comprar   ·   Mayús + clic: comprar 5", x1, x2, y2, a);
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
        l.add(myRank >= o.minRank() ? "Clic: comprar   ·   Mayús + clic: comprar 5" : "Exclusivo: necesitas rango " + Defs.RANK[Math.max(0, Math.min(5, o.minRank()))] + " (pestaña Rango)");
        return l;
    }

    // ------------------------------------------------------------------ pestaña MISIONES

    private static int qsub() {
        return lastQSub >= 0 && lastQSub < QUEST_SUBS.length ? lastQSub : 0;
    }

    private int qchipW() {
        return Math.min(124, (pw() - 16 - 8) / 3);
    }

    private int qchipX(int i) {
        return px() + 8 + i * (qchipW() + 4);
    }

    private Box questList() {
        int x1 = px();
        int y1 = py();
        return new Box(x1 + 8, y1 + 45, x1 + pw() * 52 / 100, y1 + ph() - 17);
    }

    private Box questInfo() {
        Box lb = questList();
        return new Box(lb.x2() + 6, lb.y1(), px() + pw() - 8, lb.y2());
    }

    private int questRows() {
        return Math.max(1, (questList().h() - 4) / QUEST_ROW);
    }

    private Box trialButton(Box ib) {
        return new Box(ib.x1() + 8, ib.y2() - 22, ib.x2() - 8, ib.y2() - 6);
    }

    private static int questColor(int q) {
        int st = CData.quest(q);
        return st == Quests.DONE ? Ui.GREEN : st == Quests.ACTIVE ? Ui.GOLD : 0xFF56626E;
    }

    private static String questMark(int q) {
        return q >= Quests.Q_ECLIPSE ? "☠" : q == Quests.Q_CLASS ? "✦" : "◆";
    }

    private static int firstQuest() {
        for (int q = 0; q < Quests.COUNT; q++) {
            if (CData.quest(q) == Quests.ACTIVE) return q;
        }
        return 0;
    }

    private static List<String> questTip(int q, int st) {
        List<String> l = new ArrayList<>();
        if (st == Quests.HIDDEN) {
            l.add("MISIÓN OCULTA");
            l.add("El Sistema todavía no te la ha revelado.");
            l.add("Pista: " + Quests.CLUE[q]);
            return l;
        }
        l.add(Quests.NAME[q].toUpperCase(Locale.ROOT));
        l.add(Quests.GOAL_TEXT[q]);
        if (Quests.GOAL[q] > 1) l.add("Progreso: " + (st == Quests.DONE ? Quests.GOAL[q] : CData.questProg(q)) + "/" + Quests.GOAL[q]);
        l.add(st == Quests.DONE ? "Completada" : "Clic: ver detalles");
        return l;
    }

    /** Recompensas que se muestran (la del cambio de clase dice a qué clase mutas). */
    private static List<String> rewardLines(int q) {
        List<String> l = new ArrayList<>();
        for (String r : Quests.REWARD[q]) {
            l.add(r);
        }
        int c = CData.cls();
        if (q == Quests.Q_CLASS && c >= 0 && c < Defs.MUTATION_NAME.length) {
            l.set(0, "Clase: " + Defs.CLASS_NAME[c] + " » " + Defs.MUTATION_NAME[c]);
            l.set(1, "Habilidad: " + Defs.MUT_SKILL_NAME[c]);
        }
        return l;
    }

    private List<String> quests(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        int sub = qsub();
        int cw = qchipW();
        for (int i = 0; i < QUEST_SUBS.length; i++) {
            int cx = qchipX(i);
            chip(g, f, QUEST_SUBS[i], cx, y1 + 27, cw, i == sub, Ui.inside(mx, my, cx, y1 + 27, cw, 13), Ui.GOLD, a);
        }
        int found = 0;
        int done = 0;
        for (int q = 0; q < Quests.COUNT; q++) {
            if (CData.quest(q) != Quests.HIDDEN) found++;
            if (CData.quest(q) == Quests.DONE) done++;
        }
        String sum = sub == 0 ? "Descubiertas " + found + "/" + Quests.COUNT + "  ·  hechas " + done
                : sub == 1 ? "Cobrados " + Integer.bitCount(CData.milestones()) + "/" + Quests.MS_LEVEL.length
                : "◆ " + CData.essences();
        sum = fit(f, sum, x2 - 10 - (qchipX(QUEST_SUBS.length - 1) + cw + 8));
        Ui.text(g, f, sum, x2 - 10 - f.width(sum), y1 + 30, sub == 2 ? Hud.ESSENCE : Ui.GRAY, a);
        if (sub == 1) return milestones(g, x1, y1, x2, y2, mx, my, a, now);
        if (sub == 2) return cityTab(g, x1, y1, x2, y2, mx, my, a, now);

        Box lb = questList();
        Box ib = questInfo();
        g.fill(lb.x1(), lb.y1(), lb.x2(), lb.y2(), Ui.fade(0x70040812, a));
        Ui.border(g, lb.x1(), lb.y1(), lb.x2(), lb.y2(), Ui.fade(0xFF2A3F5A, a));
        g.fill(ib.x1(), ib.y1(), ib.x2(), ib.y2(), Ui.fade(0x90070E1A, a));
        Ui.border(g, ib.x1(), ib.y1(), ib.x2(), ib.y2(), Ui.fade(0x90FFC857, a));
        Ui.corners(g, ib.x1() - 1, ib.y1() - 1, ib.x2() + 1, ib.y2() + 1, 6, Ui.fade(Ui.GOLD, a));
        int rows = questRows();
        qscroll = Math.max(0, Math.min(qscroll, Quests.COUNT - rows));
        if (selQuest < 0 || selQuest >= Quests.COUNT) selQuest = firstQuest();
        List<String> tip = null;
        float pulse = 0.65F + 0.35F * (float) Math.sin(now / 260.0);
        g.enableScissor(lb.x1() + 1, lb.y1() + 1, lb.x2() - 1, lb.y2() - 1);
        for (int i = 0; i < rows && qscroll + i < Quests.COUNT; i++) {
            int q = qscroll + i;
            int st = CData.quest(q);
            int ry = lb.y1() + 3 + i * QUEST_ROW;
            int rx1 = lb.x1() + 3;
            int rx2 = lb.x2() - (Quests.COUNT > rows ? 8 : 3);
            boolean hover = Ui.inside(mx, my, rx1, ry, rx2 - rx1, QUEST_ROW - 3);
            boolean on = q == selQuest;
            int col = questColor(q);
            g.fill(rx1, ry, rx2, ry + QUEST_ROW - 3, Ui.fade(on ? 0xB0302408 : hover ? 0x90182438 : 0x70101A28, a));
            Ui.border(g, rx1, ry, rx2, ry + QUEST_ROW - 3, Ui.fade(on ? Ui.GOLD : 0xFF24364A, a));
            g.fill(rx1, ry, rx1 + 2, ry + QUEST_ROW - 3, Ui.fade(col, a));
            String mark = questMark(q);
            Ui.text(g, f, mark, rx1 + 6, ry + 3, col, a);
            String right = st == Quests.DONE ? "✔" : st == Quests.ACTIVE && Quests.GOAL[q] > 1
                    ? CData.questProg(q) + "/" + Quests.GOAL[q] : "";
            int nameX = rx1 + 8 + f.width(mark);
            String name = st == Quests.HIDDEN ? "? ? ?" : Quests.NAME[q];
            int nameColor = st == Quests.HIDDEN ? Ui.MUTED : st == Quests.DONE ? Ui.GREEN : Ui.WHITE;
            Ui.text(g, f, fit(f, name, rx2 - 8 - f.width(right) - nameX), nameX, ry + 3, nameColor, a);
            if (!right.isEmpty()) Ui.text(g, f, right, rx2 - 4 - f.width(right), ry + 3, col, a);
            String line2;
            int c2;
            float a2 = a * 0.9F;
            if (st == Quests.HIDDEN) {
                line2 = Quests.CLUE[q];
                c2 = 0xFF5A6A7A;
            } else if (st == Quests.DONE) {
                line2 = "Completada";
                c2 = Ui.GREEN;
            } else if (q == Quests.Q_CLASS) {
                line2 = "¡Prueba disponible!";
                c2 = Ui.GOLD;
                a2 = a * pulse;
            } else {
                line2 = Quests.GOAL_TEXT[q];
                c2 = Ui.GRAY;
            }
            Ui.text(g, f, fit(f, line2, rx2 - rx1 - 12), rx1 + 6, ry + 12, c2, a2);
            if (hover) tip = questTip(q, st);
        }
        g.disableScissor();
        if (Quests.COUNT > rows) {
            int trackY1 = lb.y1() + 3;
            int trackY2 = lb.y2() - 3;
            int th = Math.max(10, (trackY2 - trackY1) * rows / Quests.COUNT);
            int ty = trackY1 + (trackY2 - trackY1 - th) * qscroll / Math.max(1, Quests.COUNT - rows);
            g.fill(lb.x2() - 6, trackY1, lb.x2() - 3, trackY2, Ui.fade(0x60101A28, a));
            g.fill(lb.x2() - 6, ty, lb.x2() - 3, ty + th, Ui.fade(Ui.GOLD, a));
        }
        questInfo(g, f, ib, selQuest, mx, my, a, now);
        hint(g, "Las misiones ocultas aparecen cuando cumples su condición secreta", x1, x2, y2, a);
        return tip;
    }

    private void questInfo(GuiGraphics g, Font f, Box ib, int q, int mx, int my, float a, long now) {
        int st = CData.quest(q);
        int lx = ib.x1() + 8;
        int w = ib.w() - 16;
        int y = ib.y1() + 7;
        int col = questColor(q);
        String state = st == Quests.DONE ? "COMPLETADA" : st == Quests.ACTIVE ? "DESCUBIERTA" : "OCULTA";
        int sw = f.width(state) + 8;
        g.fill(ib.x2() - 8 - sw, y - 2, ib.x2() - 8, y + 9, Ui.fade((col & 0x00FFFFFF) | 0x40000000, a));
        Ui.border(g, ib.x2() - 8 - sw, y - 2, ib.x2() - 8, y + 9, Ui.fade(col, a));
        Ui.center(g, f, state, ib.x2() - 8 - sw / 2, y, col, a);
        String title = st == Quests.HIDDEN ? "MISIÓN OCULTA" : Quests.NAME[q];
        Ui.glow(g, f, fit(f, title, w - sw - 6), lx, y, Ui.WHITE, 0x60FFC857, a);
        y += 13;
        g.fill(lx, y, ib.x2() - 8, y + 1, Ui.fade(0x60FFC857, a));
        y += 6;
        float pulse = 0.65F + 0.35F * (float) Math.sin(now / 400.0);
        if (st == Quests.HIDDEN) {
            Ui.bigCenter(g, f, "? ? ?", ib.cx(), y + 16, 2.6F, Ui.MUTED, 0x40FFC857, a * pulse);
            y += 36;
            for (String l : Ui.wrap(f, "El Sistema todavía no te ha revelado esta misión.", w)) {
                Ui.text(g, f, l, lx, y, Ui.MUTED, a);
                y += 10;
            }
            y += 5;
            Ui.text(g, f, "PISTA:", lx, y, Ui.GRAY, a);
            y += 10;
            for (String l : Ui.wrap(f, Quests.CLUE[q], w)) {
                Ui.text(g, f, l, lx, y, 0xFFC8D6E5, a);
                y += 10;
            }
            y += 5;
            Ui.text(g, f, "RECOMPENSA:", lx, y, Ui.GRAY, a);
            Ui.text(g, f, "? ? ?", lx + f.width("RECOMPENSA:") + 5, y, Ui.GOLD, a * pulse);
            return;
        }
        List<String> rewards = rewardLines(q);
        boolean trial = q == Quests.Q_CLASS;
        int reserved = 27 + rewards.size() * 10 + (Quests.GOAL[q] > 1 ? 12 : 0) + (trial ? 36 : 0);
        int limit = ib.y2() - 6 - reserved;
        for (String l : Ui.wrap(f, Quests.DESC[q], w)) {
            if (y > limit) break;
            Ui.text(g, f, l, lx, y, 0xFFC8D6E5, a);
            y += 10;
        }
        y += 4;
        Ui.text(g, f, "OBJETIVO:", lx, y, Ui.GRAY, a);
        y += 10;
        Ui.text(g, f, fit(f, Quests.GOAL_TEXT[q], w), lx, y, Ui.WHITE, a);
        y += 11;
        if (Quests.GOAL[q] > 1) {
            int pr = st == Quests.DONE ? Quests.GOAL[q] : CData.questProg(q);
            String pt = pr + " / " + Quests.GOAL[q];
            Ui.bar(g, lx, y + 1, w - f.width(pt) - 6, 5, pr / (float) Quests.GOAL[q], col, a);
            Ui.text(g, f, pt, ib.x2() - 8 - f.width(pt), y, Ui.WHITE, a);
            y += 12;
        }
        y += 2;
        Ui.text(g, f, st == Quests.DONE ? "RECOMPENSA (RECIBIDA):" : "RECOMPENSA:", lx, y, Ui.GRAY, a);
        y += 10;
        for (String r : rewards) {
            Ui.text(g, f, fit(f, "• " + r, w), lx, y, st == Quests.DONE ? Ui.GREEN : Ui.GOLD, a);
            y += 10;
        }
        if (trial) {
            Box b = trialButton(ib);
            if (st == Quests.ACTIVE) {
                String info = "Mazmorra privada · rango B · 4 oleadas + jefe";
                if (CData.trialTries() > 0) info += " · intentos: " + CData.trialTries();
                Ui.center(g, f, fit(f, info, w), ib.cx(), b.y1() - 11, Ui.MUTED, a);
                g.fill(b.x1() - 2, b.y1() - 2, b.x2() + 2, b.y2() + 2, Ui.fade(0x30FFC857, a * pulse));
                Ui.button(g, f, "INICIAR PRUEBA", b.x1(), b.y1(), b.w(), b.h(), mx, my, Ui.GOLD, true, a);
            } else {
                Ui.glow(g, f, fit(f, "Tu clase: " + CData.className(), w), lx, b.y1() + 4, Ui.GOLD, 0x50FFC857, a);
            }
        }
    }

    private List<String> milestones(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        int gx1 = x1 + 8;
        int gy1 = y1 + 45;
        int gx2 = x2 - 8;
        int gy2 = y2 - 17;
        int gap = 5;
        int cw = (gx2 - gx1 - gap * 2) / 3;
        int ch = (gy2 - gy1 - gap * 2) / 3;
        int lvl = CData.level();
        int next = -1;
        for (int i = 0; i < Quests.MS_LEVEL.length; i++) {
            if (!CData.milestone(i)) {
                next = i;
                break;
            }
        }
        float pulse = 0.65F + 0.35F * (float) Math.sin(now / 300.0);
        List<String> tip = null;
        for (int i = 0; i < Quests.MS_LEVEL.length; i++) {
            int cx = gx1 + (i % 3) * (cw + gap);
            int cy = gy1 + (i / 3) * (ch + gap);
            boolean got = CData.milestone(i);
            boolean isNext = i == next;
            boolean hover = Ui.inside(mx, my, cx, cy, cw, ch);
            int col = got ? Ui.GREEN : isNext ? Ui.GOLD : 0xFF3C4A58;
            g.fill(cx, cy, cx + cw, cy + ch, Ui.fade(got ? 0x70102418 : isNext ? 0x80302408 : 0x60080C12, a));
            Ui.border(g, cx, cy, cx + cw, cy + ch, Ui.fade(hover ? Ui.GOLD : col, a));
            if (isNext) Ui.corners(g, cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, 5, Ui.fade(Ui.GOLD, a * pulse));
            String lv = "NV. " + Quests.MS_LEVEL[i];
            int bw = f.width(lv) + 8;
            g.fill(cx + 4, cy + 4, cx + 4 + bw, cy + 15, Ui.fade((col & 0x00FFFFFF) | 0x50000000, a));
            Ui.text(g, f, lv, cx + 8, cy + 6, got ? Ui.GREEN : isNext ? Ui.GOLD : Ui.MUTED, a);
            String state = got ? "✔ COBRADO" : isNext ? lvl + "/" + Quests.MS_LEVEL[i] : "BLOQUEADO";
            Ui.text(g, f, state, cx + cw - 5 - f.width(state), cy + 6, got ? Ui.GREEN : isNext ? Ui.GOLD : 0xFF56626E, a);
            int ty = cy + 19;
            if (got) {
                Ui.text(g, f, fit(f, Quests.MS_NAME[i], cw - 10), cx + 5, ty, Ui.WHITE, a);
                ty += 11;
                for (String r : Quests.MS_REWARD[i]) {
                    if (ty > cy + ch - 9) break;
                    Ui.text(g, f, fit(f, r, cw - 10), cx + 5, ty, 0xFFB8E8C8, a);
                    ty += 10;
                }
            } else {
                Ui.text(g, f, "? ? ?", cx + 5, ty, Ui.MUTED, a * (isNext ? pulse : 1F));
                ty += 11;
                Ui.text(g, f, fit(f, "Recompensa oculta", cw - 10), cx + 5, ty, 0xFF5A6A7A, a);
                if (isNext && ch > 40) {
                    Ui.bar(g, cx + 5, cy + ch - 8, cw - 10, 3, Math.min(1F, lvl / (float) Quests.MS_LEVEL[i]), Ui.GOLD, a);
                }
            }
            if (hover) {
                List<String> l = new ArrayList<>();
                if (got) {
                    l.add(Quests.MS_NAME[i].toUpperCase(Locale.ROOT) + "  (nivel " + Quests.MS_LEVEL[i] + ")");
                    for (String r : Quests.MS_REWARD[i]) {
                        l.add("+ " + r);
                    }
                } else {
                    l.add("HITO OCULTO");
                    l.add("Llega al nivel " + Quests.MS_LEVEL[i] + " para descubrir su recompensa.");
                }
                tip = l;
            }
        }
        hint(g, "Recompensas ocultas del Sistema al llegar a ciertos niveles", x1, x2, y2, a);
        return tip;
    }

    private boolean clickQuests(double mx, double my, int y1) {
        int cw = qchipW();
        for (int i = 0; i < QUEST_SUBS.length; i++) {
            if (Ui.inside(mx, my, qchipX(i), y1 + 27, cw, 13)) {
                if (lastQSub != i) {
                    lastQSub = i;
                    tabTime = System.currentTimeMillis();
                    Hud.click();
                }
                return true;
            }
        }
        if (qsub() == 2) {
            if (cityButton().in(mx, my)) {
                send(Net.ACT_CITY, CData.inCity() ? 1 : 0, 0);
                Hud.sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
                this.onClose();
                return true;
            }
            return false;
        }
        if (qsub() != 0) return false;
        Box lb = questList();
        int rows = questRows();
        for (int i = 0; i < rows && qscroll + i < Quests.COUNT; i++) {
            int ry = lb.y1() + 3 + i * QUEST_ROW;
            if (Ui.inside(mx, my, lb.x1() + 3, ry, lb.w() - 6, QUEST_ROW - 3)) {
                selQuest = qscroll + i;
                Hud.click();
                return true;
            }
        }
        if (selQuest == Quests.Q_CLASS && CData.quest(Quests.Q_CLASS) == Quests.ACTIVE && trialButton(questInfo()).in(mx, my)) {
            send(Net.ACT_QUEST, Quests.Q_CLASS, 0);
            Hud.sound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.2F);
            this.onClose();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ MISIONES > CIUDAD

    private Box cityInfo() {
        int x1 = px();
        int y1 = py();
        return new Box(x1 + 8, y1 + 45, x1 + pw() * 45 / 100, y1 + ph() - 17);
    }

    private Box cityQuests() {
        Box l = cityInfo();
        return new Box(l.x2() + 6, l.y1(), px() + pw() - 8, l.y2());
    }

    private Box cityButton() {
        Box b = cityInfo();
        return new Box(b.x1() + 8, b.y2() - 24, b.x2() - 8, b.y2() - 7);
    }

    private static final String[][] CITY_PLACES = {
            {"Asociación de Cazadores", "Lucía: contratos"},
            {"Herrería", "Bruno: mejoras y habilidades ocultas"},
            {"Alquimia", "Selene: pociones y elixires"},
            {"Esencias", "Ezequiel: rarezas por esencias"},
            {"Gremio de cazadores", "Darío: contratos difíciles"}};

    private List<String> cityTab(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        List<String> tip = null;
        Box ib = cityInfo();
        card(g, ib, Ui.CYAN, a);
        int lx = ib.x1() + 8;
        int y = ib.y1() + 7;
        Ui.glow(g, f, fit(f, "CIUDAD DE LOS CAZADORES", ib.w() - 16), lx, y, Ui.WHITE, Ui.GLOW, a);
        y += 13;
        Ui.text(g, f, fit(f, CData.inCity() ? "Estás en la ciudad" : "Un mapa aparte con tiendas en persona", ib.w() - 16), lx, y,
                CData.inCity() ? Ui.GREEN : Ui.GRAY, a);
        y += 14;
        for (String[] p : CITY_PLACES) {
            if (y > ib.y2() - 40) break;
            Ui.text(g, f, fit(f, "◆ " + p[0], ib.w() - 16), lx, y, Ui.CYAN, a);
            y += 10;
            Ui.text(g, f, fit(f, p[1], ib.w() - 24), lx + 8, y, Ui.MUTED, a);
            y += 12;
        }
        Box b = cityButton();
        float pulse = 0.75F + 0.25F * (float) Math.sin(now / 300.0);
        boolean hover = Ui.button(g, f, CData.inCity() ? "VOLVER A CASA" : "VIAJAR A LA CIUDAD", b.x1(), b.y1(), b.w(), b.h(), mx, my,
                Ui.CYAN, true, a * pulse);
        if (hover) tip = CData.inCity() ? List.of("Vuelves al sitio desde el que viajaste", "También puedes cruzar el Portal de regreso de la plaza")
                : List.of("Te lleva a la plaza de la ciudad", "No se puede viajar desde una mazmorra");
        // Contratos
        Box qb = cityQuests();
        card(g, qb, Ui.GOLD, a);
        Ui.glow(g, f, "CONTRATOS", qb.x1() + 8, qb.y1() + 7, Ui.WHITE, 0x60FFC857, a);
        int row = Math.max(18, (qb.h() - 24) / SideQuests.COUNT);
        for (int q = 0; q < SideQuests.COUNT; q++) {
            int ry = qb.y1() + 21 + q * row;
            if (ry + row > qb.y2() + 2) break;
            int st = CData.side(q);
            int col = st == SideQuests.CLAIMED ? Ui.GREEN : st == SideQuests.READY ? Ui.GOLD : st == SideQuests.ACTIVE ? Ui.CYAN : 0xFF56626E;
            String state = switch (st) {
                case SideQuests.OPEN -> "habla con " + CityNpc.NAME[SideQuests.GIVER[q]];
                case SideQuests.ACTIVE -> CData.sideProg(q) + "/" + SideQuests.GOAL[q];
                case SideQuests.READY -> "¡cóbrala!";
                default -> "cobrada";
            };
            int sw = f.width(state);
            Ui.text(g, f, fit(f, SideQuests.NAME[q], qb.w() - 24 - sw), qb.x1() + 8, ry, st == SideQuests.OPEN ? Ui.GRAY : Ui.WHITE, a);
            Ui.text(g, f, state, qb.x2() - 8 - sw, ry, col, a);
            if (Ui.inside(mx, my, qb.x1() + 4, ry - 2, qb.w() - 8, row - 2)) {
                tip = List.of(SideQuests.NAME[q], SideQuests.GOAL_TEXT[q], "Lo da: " + CityNpc.NAME[SideQuests.GIVER[q]] + " ("
                        + CityNpc.TITLE[SideQuests.GIVER[q]] + ")", st == SideQuests.CLAIMED ? "Recompensa: " + SideQuests.REWARD[q]
                        : "Recompensa: ? ? ?");
            }
        }
        hint(g, "Los contratos se aceptan y se cobran en persona, en la ciudad", x1, x2, y2, a);
        return tip;
    }

    // ------------------------------------------------------------------ pestaña RANGO

    private Box rankBox() {
        int x1 = px();
        int y1 = py();
        return new Box(x1 + 8, y1 + 28, x1 + pw() * 42 / 100, y1 + ph() - 17);
    }

    private Box examBox() {
        Box l = rankBox();
        int h = (l.h() - 6) * 54 / 100;
        return new Box(l.x2() + 6, l.y1(), px() + pw() - 8, l.y1() + h);
    }

    private Box prestigeBox() {
        Box l = rankBox();
        Box e = examBox();
        return new Box(e.x1(), e.y2() + 6, e.x2(), l.y2());
    }

    private static Box bottomButton(Box b) {
        return new Box(b.x1() + 8, b.y2() - 20, b.x2() - 8, b.y2() - 6);
    }

    private static void card(GuiGraphics g, Box b, int accent, float a) {
        g.fill(b.x1(), b.y1(), b.x2(), b.y2(), Ui.fade(0x90070E1A, a));
        Ui.border(g, b.x1(), b.y1(), b.x2(), b.y2(), Ui.fade((accent & 0x00FFFFFF) | 0x90000000, a));
        Ui.corners(g, b.x1() - 1, b.y1() - 1, b.x2() + 1, b.y2() + 1, 6, Ui.fade(accent, a));
    }

    /** Etiqueta de estado arriba a la derecha de una tarjeta. */
    private static void stateTag(GuiGraphics g, Font f, Box b, String state, int col, float a) {
        int y = b.y1() + 7;
        int sw = f.width(state) + 8;
        g.fill(b.x2() - 8 - sw, y - 2, b.x2() - 8, y + 9, Ui.fade((col & 0x00FFFFFF) | 0x40000000, a));
        Ui.border(g, b.x2() - 8 - sw, y - 2, b.x2() - 8, y + 9, Ui.fade(col, a));
        Ui.center(g, f, state, b.x2() - 8 - sw / 2, y, col, a);
    }

    private static List<String> rankTip(int i) {
        List<String> l = new ArrayList<>();
        l.add("RANGO " + Defs.RANK[i] + (i == 0 ? "  (el de partida)" : "  (examen desde el nivel " + Defs.RANK_MIN[i] + ")"));
        List<String> bf = Defs.rankBuffs(i);
        if (bf.isEmpty()) {
            l.add("Sin bonus");
        } else {
            l.addAll(bf);
        }
        return l;
    }

    private static List<String> prestigeTip(int next) {
        List<String> l = new ArrayList<>();
        l.add("PRESTIGIO " + next + ": " + Defs.prestigeName(next));
        l.add("Vuelves al nivel 1 y tus estadísticas vuelven a 10.");
        l.add("Conservas raza, clase, árboles, sombras, oro, objetos y rango.");
        l.add("Recibes el arma del Monarca de tu clase.");
        l.add("Bonus totales con este prestigio:");
        for (String s : Defs.prestigeBuffs(next)) {
            l.add("  " + s);
        }
        return l;
    }

    private List<String> rankTab(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        List<String> tip = null;
        int rank = CData.hunterRank();
        int pres = CData.prestige();
        int col = CData.rankColor();
        int glowCol = 0x50000000 | (col & 0xFFFFFF);
        float pulse = 0.65F + 0.35F * (float) Math.sin(now / 300.0);

        // ---- izquierda: insignia, escalera de rangos y bonus actuales
        Box lb = rankBox();
        card(g, lb, col, a);
        int cx = lb.cx();
        int y = lb.y1() + 7;
        Ui.center(g, f, pres > 0 ? "RANGO ÚNICO" : "RANGO DE CAZADOR", cx, y, Ui.GRAY, a);
        y += 12;
        int bs = 40;
        int bx1 = cx - bs / 2;
        int bx2 = cx + bs / 2;
        g.fill(bx1, y, bx2, y + bs, Ui.fade(0xA0000000 | ((col >> 2) & 0x3F3F3F), a));
        Ui.border(g, bx1, y, bx2, y + bs, Ui.fade(col, a));
        Ui.corners(g, bx1 - 2, y - 2, bx2 + 2, y + bs + 2, 5, Ui.fade(col, a * pulse));
        Ui.bigCenter(g, f, pres > 0 ? "★" : Defs.RANK[rank], cx, y + bs / 2F + 1F, 3.0F, col, glowCol, a);
        if (Ui.inside(mx, my, bx1, y, bs, bs)) tip = pres > 0 ? prestigeTip(pres) : rankTip(rank);
        y += bs + 6;
        String title = fit(f, pres > 0 ? Defs.prestigeName(pres) : "Cazador de rango " + Defs.RANK[rank], lb.w() - 12);
        Ui.glow(g, f, title, cx - f.width(title) / 2, y, col, glowCol, a);
        y += 13;
        int cell = Math.min(22, (lb.w() - 16) / 6);
        int lx0 = cx - cell * 3;
        for (int i = 0; i < 6; i++) {
            int qx = lx0 + i * cell;
            boolean got = i <= rank;
            boolean cur = i == rank;
            int rc = Defs.RANK_COLOR[i];
            g.fill(qx + 1, y, qx + cell - 1, y + 13, Ui.fade(got ? 0x60000000 | (rc & 0xFFFFFF) : 0x50101A28, a));
            Ui.border(g, qx + 1, y, qx + cell - 1, y + 13, Ui.fade(cur ? Ui.WHITE : got ? rc : 0xFF2E4458, a));
            Ui.center(g, f, Defs.RANK[i], qx + cell / 2, y + 3, got ? Ui.WHITE : 0xFF56626E, a);
            if (Ui.inside(mx, my, qx, y, cell, 13)) tip = rankTip(i);
        }
        y += 19;
        Ui.text(g, f, "BONUS ACTUALES:", lb.x1() + 8, y, Ui.GRAY, a);
        y += 11;
        List<String> buffs = new ArrayList<>(Defs.rankBuffs(rank));
        for (String s : Defs.prestigeBuffs(pres)) {
            buffs.add("★ " + s);
        }
        if (buffs.isEmpty()) buffs.add("Ninguno: ¡haz el examen!");
        for (String s : buffs) {
            if (y > lb.y2() - 13) break;
            boolean star = s.startsWith("★");
            Ui.text(g, f, fit(f, (star ? "" : "• ") + s, lb.w() - 16), lb.x1() + 8, y,
                    star ? Defs.MONARCA_COLOR : buffs.size() == 1 && rank == 0 && pres == 0 ? Ui.MUTED : Ui.GREEN, a);
            y += 10;
        }

        // ---- derecha arriba: examen de ascenso
        Box eb = examBox();
        boolean maxRank = rank >= 5;
        int next = Math.min(5, rank + 1);
        int ecol = maxRank ? Ui.GOLD : Defs.RANK_COLOR[next];
        boolean ready = CData.examReady();
        card(g, eb, ecol, a);
        int ex = eb.x1() + 8;
        int ew = eb.w() - 16;
        stateTag(g, f, eb, maxRank ? "COMPLETADO" : ready ? "DISPONIBLE" : "BLOQUEADO",
                maxRank ? Ui.GREEN : ready ? Ui.GOLD : 0xFF56626E, a);
        y = eb.y1() + 7;
        Ui.glow(g, f, "EXAMEN DE ASCENSO", ex, y, Ui.WHITE, 0x405FB8FF, a);
        y += 13;
        g.fill(ex, y, eb.x2() - 8, y + 1, Ui.fade((ecol & 0x00FFFFFF) | 0x60000000, a));
        y += 5;
        Box eBtn = bottomButton(eb);
        if (maxRank) {
            for (String l : Ui.wrap(f, "Eres un cazador de rango S: lo más alto de la Asociación. Por encima solo queda "
                    + "el prestigio en el nivel " + Defs.MAX_LEVEL + ".", ew)) {
                if (y > eb.y2() - 12) break;
                Ui.text(g, f, l, ex, y, 0xFFC8D6E5, a);
                y += 10;
            }
        } else {
            String k = "Siguiente: ";
            Ui.text(g, f, k, ex, y, Ui.GRAY, a);
            Ui.glow(g, f, "RANGO " + Defs.RANK[next], ex + f.width(k), y, ecol, 0x50000000 | (ecol & 0xFFFFFF), a);
            String req = "Nivel " + Defs.RANK_MIN[next];
            Ui.text(g, f, req, eb.x2() - 8 - f.width(req), y, CData.level() >= Defs.RANK_MIN[next] ? Ui.GREEN : Ui.RED, a);
            y += 11;
            String lt = Math.min(CData.level(), Defs.RANK_MIN[next]) + "/" + Defs.RANK_MIN[next];
            Ui.bar(g, ex, y + 1, ew - f.width(lt) - 6, 4, CData.level() / (float) Defs.RANK_MIN[next], ecol, a);
            Ui.text(g, f, lt, eb.x2() - 8 - f.width(lt), y, Ui.WHITE, a);
            y += 11;
            Ui.text(g, f, fit(f, Defs.EXAM_WAVES + " oleadas + examinador · nivel " + Defs.examLevel(next, CData.level()),
                    ew), ex, y, Ui.MUTED, a);
            y += 11;
            Ui.text(g, f, "AL ASCENDER:", ex, y, Ui.GRAY, a);
            y += 10;
            for (String l : pack(f, Defs.rankBuffs(next), ew)) {
                if (y > eBtn.y1() - 11) break;
                Ui.text(g, f, l, ex, y, Ui.GOLD, a);
                y += 10;
            }
            if (ready) {
                g.fill(eBtn.x1() - 2, eBtn.y1() - 2, eBtn.x2() + 2, eBtn.y2() + 2, Ui.fade(0x30FFC857, a * pulse));
            }
            String lbl = ready ? "HACER EL EXAMEN" : "NECESITAS NIVEL " + Defs.RANK_MIN[next];
            if (ready && CData.examTries() > 0) lbl += "  ·  intentos: " + CData.examTries();
            Ui.button(g, f, fit(f, lbl, eBtn.w() - 6), eBtn.x1(), eBtn.y1(), eBtn.w(), eBtn.h(), mx, my, ecol, ready, a);
        }

        // ---- derecha abajo: prestigio
        Box pb = prestigeBox();
        int pcol = Defs.MONARCA_COLOR;
        boolean pReady = CData.prestigeReady();
        card(g, pb, pcol, a);
        stateTag(g, f, pb, pReady ? "DISPONIBLE" : pres > 0 ? "PRESTIGIO " + pres : "NIVEL " + Defs.MAX_LEVEL,
                pReady ? Ui.GOLD : pres > 0 ? pcol : 0xFF56626E, a);
        y = pb.y1() + 7;
        Ui.glow(g, f, "PRESTIGIO", ex, y, Ui.WHITE, 0x40C77DFF, a);
        y += 13;
        String plt = CData.level() + "/" + Defs.MAX_LEVEL;
        Ui.bar(g, ex, y + 1, ew - f.width(plt) - 6, 4, CData.level() / (float) Defs.MAX_LEVEL, pcol, a);
        Ui.text(g, f, plt, pb.x2() - 8 - f.width(plt), y, Ui.WHITE, a);
        y += 12;
        Box pBtn = bottomButton(pb);
        String pdesc = "Vuelves al nivel 1 (conservas lo demás) y ganas: rango " + Defs.prestigeName(pres + 1)
                + ", arma del Monarca, +" + Defs.P_XP + "% EXP, +" + Defs.P_DMG + "% daño y vida, +"
                + Defs.P_SLOTS + " sombras y +" + Defs.P_POINTS + " punto por nivel.";
        for (String l : Ui.wrap(f, pdesc, ew)) {
            if (y > pBtn.y1() - 11) break;
            Ui.text(g, f, l, ex, y, 0xFFC8D6E5, a);
            y += 10;
        }
        if (Ui.inside(mx, my, pb.x1(), pb.y1(), pb.w(), pBtn.y1() - pb.y1())) tip = prestigeTip(pres + 1);
        boolean armed = prestigeArmed && now - prestigeTime < 3000L;
        if (pReady) {
            g.fill(pBtn.x1() - 2, pBtn.y1() - 2, pBtn.x2() + 2, pBtn.y2() + 2, Ui.fade(0x30C77DFF, a * pulse));
        }
        String plbl = !pReady ? "NECESITAS NIVEL " + Defs.MAX_LEVEL : armed ? "¿SEGURO? VUELVES AL NIVEL 1" : "HACER PRESTIGIO";
        Ui.button(g, f, fit(f, plbl, pBtn.w() - 6), pBtn.x1(), pBtn.y1(), pBtn.w(), pBtn.h(), mx, my, armed ? Ui.RED : pcol, pReady, a);

        hint(g, "El rango sube con el examen · el prestigio, en el nivel " + Defs.MAX_LEVEL, x1, x2, y2, a);
        return tip;
    }

    /** Junta textos cortos en líneas separadas por " · " sin partir ninguno por la mitad. */
    private static List<String> pack(Font f, List<String> items, int w) {
        List<String> out = new ArrayList<>();
        String line = "";
        for (String it : items) {
            String test = line.isEmpty() ? it : line + "  ·  " + it;
            if (!line.isEmpty() && f.width(test) > w) {
                out.add(line);
                line = it;
            } else {
                line = test;
            }
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private boolean clickRank(double mx, double my) {
        if (CData.hunterRank() < 5 && bottomButton(examBox()).in(mx, my)) {
            if (CData.examReady()) {
                send(Net.ACT_RANK, 0, 0);
                Hud.sound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.2F);
                this.onClose();
            } else {
                Hud.error();
            }
            return true;
        }
        if (bottomButton(prestigeBox()).in(mx, my)) {
            if (!CData.prestigeReady()) {
                Hud.error();
                return true;
            }
            long now = System.currentTimeMillis();
            if (prestigeArmed && now - prestigeTime < 3000L) {
                prestigeArmed = false;
                send(Net.ACT_RANK, 1, 0);
                Hud.sound(SoundEvents.BEACON_ACTIVATE, 1.0F, 0.6F);
            } else {
                prestigeArmed = true;
                prestigeTime = now;
                Hud.error();
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ pestaña AJUSTES

    private Box settingRow(int i) {
        int x1 = px();
        int top = py() + 44;
        int h = Math.min(48, (ph() - 44 - 22 - 8) / 2);
        return new Box(x1 + 12, top + i * (h + 8), x1 + pw() - 12, top + i * (h + 8) + h);
    }

    private static Box toggleBox(Box row) {
        int h = 16;
        int y = row.y1() + (row.h() - h) / 2;
        return new Box(row.x2() - 62, y, row.x2() - 10, y + h);
    }

    private static boolean settingOn(int i) {
        return i == 0 ? ClientConfig.particles() : ClientConfig.performance();
    }

    private static String settingDesc(int i, boolean on) {
        if (i == 0) {
            return on ? "Se ven los efectos de las habilidades, las sombras, los jefes y las mazmorras."
                    : "Sin partículas del mod. Las puertas de mazmorra y los cadáveres se ven con unas pocas.";
        }
        return on ? "Activado: 1 de cada 3 partículas, sin auras ni remolinos y sin brillo en los monstruos."
                : "Todo más ligero para el PC: menos partículas, sin auras y sin brillo en los monstruos.";
    }

    private static List<String> settingTip(int i) {
        if (i == 0) {
            return List.of("Partículas del mod",
                    "Habilidades, golpes, ¡Levántate!, sombras, jefes y mazmorras.",
                    "Sin ellas, las puertas de mazmorra, los cadáveres que se pueden",
                    "levantar y las nubes de veneno se siguen viendo (con menos).",
                    "Las partículas de Minecraft se cambian en Opciones > Vídeo.");
        }
        return List.of("Modo rendimiento",
                "• Sólo 1 de cada 3 partículas del mod.",
                "• Sin auras de las sombras ni remolinos de las puertas.",
                "• Los ojos, runas y magma de los monstruos no brillan",
                "  (una pasada de dibujo menos por monstruo).",
                "Útil si el juego va lento con muchas sombras o en las mazmorras.");
    }

    private List<String> settings(GuiGraphics g, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        Font f = this.font;
        List<String> tip = null;
        Ui.glow(g, f, "AJUSTES DEL SISTEMA", x1 + 14, y1 + 29, Ui.WHITE, Ui.GLOW, a);
        String sub = "Sólo cambian lo que ves tú";
        Ui.text(g, f, sub, x2 - 14 - f.width(sub), y1 + 29, Ui.MUTED, a);
        for (int i = 0; i < SETTING_TITLE.length; i++) {
            Box r = settingRow(i);
            boolean on = settingOn(i);
            boolean hover = r.in(mx, my);
            card(g, r, on ? (i == 0 ? Ui.CYAN : Ui.GREEN) : 0xFF3C5A74, a);
            if (hover) g.fill(r.x1() + 1, r.y1() + 1, r.x2() - 1, r.y2() - 1, Ui.fade(0x18FFFFFF, a));
            Ui.text(g, f, SETTING_TITLE[i], r.x1() + 8, r.y1() + 7, on ? Ui.WHITE : Ui.GRAY, a);
            List<String> lines = Ui.wrap(f, settingDesc(i, on), r.w() - 84);
            for (int k = 0; k < Math.min(2, lines.size()); k++) {
                Ui.text(g, f, lines.get(k), r.x1() + 8, r.y1() + 20 + k * 10, Ui.MUTED, a);
            }
            toggle(g, f, toggleBox(r), on, hover, a);
            if (hover) tip = settingTip(i);
        }
        hint(g, "Se guardan en config/elsistema.json y valen para todos tus mundos", x1, x2, y2, a);
        return tip;
    }

    /** Interruptor NO | SÍ. */
    private static void toggle(GuiGraphics g, Font f, Box t, boolean on, boolean hover, float a) {
        int mid = t.cx();
        g.fill(t.x1(), t.y1(), t.x2(), t.y2(), Ui.fade(0xA0080F1A, a));
        if (on) {
            g.fill(mid, t.y1() + 1, t.x2() - 1, t.y2() - 1, Ui.fade(0x9035B060, a));
        } else {
            g.fill(t.x1() + 1, t.y1() + 1, mid, t.y2() - 1, Ui.fade(0x90902838, a));
        }
        Ui.border(g, t.x1(), t.y1(), t.x2(), t.y2(), Ui.fade(hover ? Ui.WHITE : on ? Ui.GREEN : 0xFF5F7387, a));
        int ty = t.y1() + (t.h() - 8) / 2;
        Ui.center(g, f, "NO", (t.x1() + mid) / 2, ty, on ? 0xFF56626E : Ui.WHITE, a);
        Ui.center(g, f, "SÍ", (mid + t.x2()) / 2, ty, on ? Ui.WHITE : 0xFF56626E, a);
    }

    private boolean clickSettings(double mx, double my) {
        for (int i = 0; i < SETTING_TITLE.length; i++) {
            if (!settingRow(i).in(mx, my)) continue;
            boolean on = !settingOn(i);
            if (i == 0) {
                ClientConfig.setParticles(on);
            } else {
                ClientConfig.setPerformance(on);
            }
            Hud.click();
            Hud.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, on ? 2.0F : 0.7F);
            return true;
        }
        return false;
    }

    // ================================================================== ratón y teclado

    private void switchTab(int i) {
        if (tab != i) {
            tab = i;
            lastTab = i;
            tabTime = System.currentTimeMillis();
            shadowTreeView = false;
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
            if (Ui.inside(mx, my, tabX(TAB_CONFIG), y1 + TAB_Y, GEAR_W, TAB_H)) {
                switchTab(TAB_CONFIG);
                return true;
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
        if (tab == 4 && button == 0 && clickQuests(mx, my, y1)) return true;
        if (tab == TAB_RANK && button == 0 && clickRank(mx, my)) return true;
        if (tab == TAB_CONFIG && button == 0 && clickSettings(mx, my)) return true;
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
        if (shadowTreeView) {
            ClientShadows.Entry sel = ClientShadows.byId(selShadow);
            if (sel == null) {
                shadowTreeView = false;
                return false;
            }
            for (int k = 0; k < ShadowTree.SIZE; k++) {
                if (treePlus(lb, k).in(mx, my)) {
                    if (ShadowTree.canUp(sel.tree(), sel.pts(), k)) {
                        send(Net.ACT_SHADOW_TREE, sel.id(), k);
                        Hud.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.4F);
                    } else {
                        Hud.error();
                    }
                    return true;
                }
            }
            if (backButton(infoBox()).in(mx, my)) {
                shadowTreeView = false;
                Hud.click();
                return true;
            }
            return false;
        }
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
        if (treeButton(ib).in(mx, my)) {
            shadowTreeView = true;
            Hud.click();
            return true;
        }
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
        int myRank = CData.hunterRank();
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
        if (tab == 4 && qsub() == 0 && questList().in(mx, my)) {
            int max = Math.max(0, Quests.COUNT - questRows());
            qscroll = Math.max(0, Math.min(max, qscroll - (int) Math.signum(scrollY)));
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
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_1 + TAB_CONFIG) {
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
        if (newTab == 4) {
            lastQSub = newSub;
        } else {
            lastSub = newSub;
        }
        lastCat = newCat;
        this.selNode = -1;
        this.tabTime = 0L;
        this.shadowTreeView = newTab == 2 && newSub == 1;
        if (this.shadowTreeView) {
            for (ClientShadows.Entry e : ClientShadows.list()) {
                if (e.pts() > 0 || e.spent() > 0) {
                    this.selShadow = e.id();
                    break;
                }
            }
        }
    }

    /** Recorre todos los textos de la ventana con todas las razas y clases (para detectar errores). */
    public static int selfCheck() {
        int n = 0;
        for (int r = 0; r < 6; r++) {
            rankTip(r);
            Defs.rankBuffs(r);
            Defs.examLevel(Math.max(1, r), 40 + r * 60);
            n++;
        }
        for (int p = 0; p <= 12; p++) {
            Defs.prestigeName(p);
            Defs.prestigeBuffs(p);
            prestigeTip(p + 1);
            Defs.rankLabel(p % 6, p);
            n++;
        }
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
            classTip(c, false);
            classTip(c, true);
        }
        for (int q = 0; q < Quests.COUNT; q++) {
            questTip(q, Quests.HIDDEN);
            questTip(q, Quests.ACTIVE);
            questTip(q, Quests.DONE);
            n++;
        }
        for (int i = 0; i < 5; i++) {
            statTip(i);
        }
        n += NpcScreen.selfCheck();
        for (int i = 0; i < SETTING_TITLE.length; i++) {
            settingDesc(i, true);
            settingDesc(i, false);
            settingTip(i);
            n++;
        }
        int[] ranks = new int[ShadowTree.SIZE];
        for (int k = 0; k < ShadowTree.SIZE; k++) {
            for (int r = 0; r <= ShadowTree.NODES[k].max(); r++) {
                ranks[k] = r;
                ShadowTree.effect(k, r);
                shadowNodeTip(new ClientShadows.Entry(1, 0, 1, 0, 100, 0, 0, false, 10, 500, 3, ranks.clone(), "minecraft:zombie",
                        "entity.minecraft.zombie"), k);
                n++;
            }
        }
        return n;
    }
}
