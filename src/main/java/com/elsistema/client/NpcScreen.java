package com.elsistema.client;

import com.elsistema.CityNpc;
import com.elsistema.CityShop;
import com.elsistema.Forge;
import com.elsistema.ModItems;
import com.elsistema.Net;
import com.elsistema.SideQuests;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ventana de un tendero de la ciudad: la herrería de Bruno (mejoras y habilidades ocultas con esencias), las tiendas
 * (Selene, Ezequiel, Tomás, Irene, Paco), la Estación de Puertas de Hugo, la Arena de Vera y los contratos de la
 * Asociación y del gremio (misiones secundarias).
 */
public class NpcScreen extends Screen {
    private static final int ROW = 26;
    private final int role;
    private final int entityId;
    private final long openTime = System.currentTimeMillis();
    private int sel = 0;

    public NpcScreen(int role, int entityId) {
        super(Component.literal(CityNpc.NAME[Math.max(0, Math.min(CityNpc.ROLES - 1, role))]));
        this.role = Math.max(0, Math.min(CityNpc.ROLES - 1, role));
        this.entityId = entityId;
        Hud.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.6F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // Las tiendas sólo se usan en persona: si te alejas, la ventana se cierra
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) return;
        if (entityId < 0) return;
        Entity e = this.minecraft.level.getEntity(entityId);
        if (e == null || e.distanceToSqr(this.minecraft.player) > 10.0 * 10.0) this.onClose();
    }

    private int pw() {
        return Math.min(420, this.width - 12);
    }

    private int ph() {
        return Math.min(250, this.height - 10);
    }

    private int px() {
        return (this.width - pw()) / 2;
    }

    private int py() {
        return (this.height - ph()) / 2;
    }

    private int accent() {
        return switch (role) {
            case CityNpc.SMITH, CityNpc.COOK -> 0xFFFF9A4A;
            case CityNpc.ALCHEMIST, CityNpc.SHOPKEEPER -> 0xFF6BFF9A;
            case CityNpc.MERCHANT, CityNpc.GATEKEEPER -> Ui.PURPLE;
            case CityNpc.GUILD, CityNpc.HEALER -> 0xFFFF6A5A;
            case CityNpc.TRAINER -> Ui.GOLD;
            default -> Ui.CYAN;
        };
    }

    private boolean isShop() {
        return role != CityNpc.SMITH && CityShop.offers(role).length > 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        long now = System.currentTimeMillis();
        float p = Ui.easeOut((now - openTime) / 260F);
        g.fill(0, 0, this.width, this.height, Ui.fade(0xB0000208, p));
        int x1 = px();
        int y1 = py();
        int x2 = x1 + pw();
        int y2 = y1 + ph();
        int cy = (y1 + y2) / 2;
        int hh = Math.max(1, Math.round((y2 - y1) * p / 2F));
        Ui.panel(g, x1, cy - hh, x2, cy + hh, accent(), Math.min(1F, p * 1.5F));
        float a = Ui.clamp01((p - 0.55F) / 0.45F);
        if (a <= 0F) return;
        Font f = this.font;
        Ui.shine(g, x1, y1, x2, y1 + 16, now, a);
        String who = CityNpc.NAME[role].toUpperCase(Locale.ROOT);
        Ui.glow(g, f, who, x1 + 10, y1 + 7, Ui.WHITE, Ui.GLOW, a);
        Ui.text(g, f, CityNpc.TITLE[role], x1 + 14 + f.width(who), y1 + 7, Ui.GRAY, a);
        String money = "ORO " + CData.coins() + "   ◆ " + CData.essences();
        Ui.text(g, f, money, x2 - 10 - f.width(money), y1 + 7, Hud.ESSENCE, a);
        Ui.divider(g, x1, x2, y1 + 20, a);
        List<String> tip = role == CityNpc.SMITH ? forge(g, f, x1, y1, x2, y2, mx, my, a)
                : isShop() ? shop(g, f, x1, y1, x2, y2, mx, my, a)
                : contracts(g, f, x1, y1, x2, y2, mx, my, a, now);
        if (tip != null && a >= 1F) Ui.tooltip(g, f, tip, mx, my, this.width, this.height);
    }

    private static void hint(GuiGraphics g, Font f, String s, int x1, int x2, int y2, float a) {
        Ui.center(g, f, s, (x1 + x2) / 2, y2 - 12, 0xFF5F7387, a);
    }

    private static String fit(Font f, String s, int w) {
        if (f.width(s) <= w) return s;
        String r = s;
        while (r.length() > 1 && f.width(r + "..") > w) {
            r = r.substring(0, r.length() - 1);
        }
        return r.trim() + "..";
    }

    private static void send(int action, int a, int b) {
        ClientPlayNetworking.send(new Net.ActionPayload(action, a, b));
    }

    // ================================================================ herrería

    private ItemStack slot(int i) {
        if (this.minecraft == null || this.minecraft.player == null) return ItemStack.EMPTY;
        return this.minecraft.player.getItemBySlot(Forge.SLOTS[i]);
    }

    private int listX2() {
        return px() + pw() * 46 / 100;
    }

    private int rowY(int i) {
        return py() + 26 + i * ROW;
    }

    private int[] upgradeButton() {
        int x = listX2() + 14;
        return new int[]{x, py() + ph() - 62, px() + pw() - 12 - x, 16};
    }

    private int[] hiddenButton() {
        int x = listX2() + 14;
        return new int[]{x, py() + ph() - 40, px() + pw() - 12 - x, 16};
    }

    private List<String> forge(GuiGraphics g, Font f, int x1, int y1, int x2, int y2, int mx, int my, float a) {
        List<String> tip = null;
        int lx2 = listX2();
        for (int i = 0; i < Forge.SLOTS.length; i++) {
            ItemStack s = slot(i);
            int ry = rowY(i);
            boolean hover = Ui.inside(mx, my, x1 + 8, ry, lx2 - x1 - 8, ROW - 3);
            boolean on = i == sel;
            boolean eq = Forge.isEquipment(s);
            g.fill(x1 + 8, ry, lx2, ry + ROW - 3, Ui.fade(on ? 0xB0302010 : hover ? 0x90182438 : 0x70101A28, a));
            Ui.border(g, x1 + 8, ry, lx2, ry + ROW - 3, Ui.fade(on ? accent() : 0xFF24364A, a));
            if (!s.isEmpty() && a >= 0.99F) g.renderItem(s, x1 + 11, ry + 3);
            String name = s.isEmpty() ? Forge.SLOT_NAME[i] + ": vacío" : s.getHoverName().getString();
            int lvl = Forge.level(s);
            String plus = Forge.isWeapon(s) && lvl > 0 ? " +" + lvl : "";
            Ui.text(g, f, fit(f, name + plus, lx2 - x1 - 38), x1 + 31, ry + 3, s.isEmpty() ? Ui.MUTED : eq ? Ui.WHITE : Ui.GRAY, a);
            String sub;
            int subCol;
            if (s.isEmpty()) {
                sub = "Ponte algo en este hueco";
                subCol = 0xFF56626E;
            } else if (!eq) {
                sub = "No se puede forjar";
                subCol = 0xFF56626E;
            } else {
                int h = Forge.hidden(s);
                sub = "☾ " + (h < 0 ? "Habilidad oculta: ???" : Forge.abilityName(s, h));
                subCol = h < 0 ? 0xFF7A5AA8 : Ui.PURPLE;
            }
            Ui.text(g, f, fit(f, sub, lx2 - x1 - 38), x1 + 31, ry + 13, subCol, a * 0.9F);
            if (hover && eq) {
                List<Component> lines = new ArrayList<>();
                Forge.tooltip(s, lines);
                List<String> t = new ArrayList<>();
                t.add(s.getHoverName().getString());
                for (Component c : lines) {
                    t.add(c.getString());
                }
                tip = t;
            }
        }
        // Detalle del objeto elegido
        int dx1 = lx2 + 8;
        int dy1 = y1 + 26;
        int dx2 = x2 - 8;
        int dy2 = y2 - 18;
        g.fill(dx1, dy1, dx2, dy2, Ui.fade(0x90070E1A, a));
        Ui.border(g, dx1, dy1, dx2, dy2, Ui.fade((accent() & 0x00FFFFFF) | 0x90000000, a));
        Ui.corners(g, dx1 - 1, dy1 - 1, dx2 + 1, dy2 + 1, 6, Ui.fade(accent(), a));
        ItemStack s = slot(sel);
        int tx = dx1 + 8;
        int w = dx2 - dx1 - 16;
        int y = dy1 + 7;
        if (!Forge.isEquipment(s)) {
            Ui.center(g, f, "Elige un arma o una pieza", (dx1 + dx2) / 2, dy1 + 40, Ui.GRAY, a);
            Ui.center(g, f, "de armadura que lleves puesta", (dx1 + dx2) / 2, dy1 + 52, Ui.GRAY, a);
            hint(g, f, "Las esencias se ganan matando monstruos con rango y completando mazmorras", x1, x2, y2, a);
            return tip;
        }
        boolean weapon = Forge.isWeapon(s);
        int lvl = Forge.level(s);
        Ui.glow(g, f, fit(f, s.getHoverName().getString() + (weapon && lvl > 0 ? " +" + lvl : ""), w), tx, y, Ui.WHITE, Ui.GLOW, a);
        y += 14;
        if (weapon) {
            Ui.text(g, f, "MEJORA  +" + lvl + " / +" + Forge.MAX_LEVEL, tx, y, Ui.CYAN, a);
            y += 11;
            int segW = Math.max(3, (w - 9) / Forge.MAX_LEVEL);
            for (int i = 0; i < Forge.MAX_LEVEL; i++) {
                int sx = tx + i * (segW + 1);
                g.fill(sx, y, sx + segW, y + 4, Ui.fade(i < lvl ? Ui.CYAN : 0x60304050, a));
            }
            y += 9;
            if (lvl < Forge.MAX_LEVEL) {
                Ui.text(g, f, fit(f, "Siguiente: +" + (lvl + 1) + " → +" + Math.round(Forge.DMG_PER_LEVEL * (lvl + 1) * 100)
                        + " % de daño", w), tx, y, Ui.GRAY, a);
            } else {
                Ui.text(g, f, "¡Mejora máxima!", tx, y, Ui.GOLD, a);
            }
        } else {
            Ui.text(g, f, fit(f, "Las armaduras no se mejoran:", w), tx, y, Ui.GRAY, a);
            y += 11;
            Ui.text(g, f, fit(f, "sólo tienen habilidad oculta.", w), tx, y, Ui.GRAY, a);
        }
        y = dy1 + 76;
        Ui.text(g, f, "HABILIDAD OCULTA", tx, y, Ui.PURPLE, a);
        y += 11;
        int h = Forge.hidden(s);
        if (h < 0) {
            Ui.text(g, f, "? ? ?", tx, y, 0xFF7A5AA8, a);
            y += 10;
            Ui.text(g, f, fit(f, "Despiértala para descubrirla", w), tx, y, Ui.MUTED, a);
        } else {
            Ui.text(g, f, Forge.abilityName(s, h), tx, y, Ui.WHITE, a);
            y += 10;
            for (String line : Ui.wrap(f, Forge.abilityDesc(s, h), w)) {
                Ui.text(g, f, line, tx, y, Ui.GRAY, a);
                y += 10;
            }
        }
        int[] ub = upgradeButton();
        boolean canUp = weapon && lvl < Forge.MAX_LEVEL;
        int upCost = canUp ? Forge.UPGRADE_COST[lvl] : 0;
        Ui.button(g, f, canUp ? "MEJORAR  (◆ " + upCost + ")" : weapon ? "MEJORA MÁXIMA" : "NO SE MEJORA", ub[0], ub[1], ub[2], ub[3],
                mx, my, CData.essences() >= upCost ? Ui.CYAN : Ui.RED, canUp, a);
        int[] hb = hiddenButton();
        int hCost = h < 0 ? Forge.AWAKEN_COST : Forge.REROLL_COST;
        Ui.button(g, f, (h < 0 ? "DESPERTAR" : "CAMBIAR") + "  (◆ " + hCost + ")", hb[0], hb[1], hb[2], hb[3], mx, my,
                CData.essences() >= hCost ? Ui.PURPLE : Ui.RED, true, a);
        hint(g, f, "Las esencias se ganan matando monstruos con rango y completando mazmorras", x1, x2, y2, a);
        return tip;
    }

    // ================================================================ tiendas

    private static ItemStack offerIcon(int role, int i) {
        switch (role) {
            case CityNpc.ALCHEMIST -> {
                return switch (i) {
                    case 0 -> PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING);
                    case 1 -> PotionContents.createItemStack(Items.POTION, Potions.LONG_STRENGTH);
                    case 2 -> PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS);
                    case 3 -> PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE);
                    case 4 -> new ItemStack(Items.LAPIS_LAZULI);
                    default -> new ItemStack(Items.GOLDEN_APPLE);
                };
            }
            case CityNpc.COOK -> {
                return new ItemStack(switch (i) {
                    case 0 -> Items.COOKED_BEEF;
                    case 1 -> Items.BREAD;
                    case 2 -> Items.RABBIT_STEW;
                    case 3 -> Items.CAKE;
                    case 4 -> Items.GOLDEN_APPLE;
                    default -> Items.COOKED_CHICKEN;
                });
            }
            case CityNpc.HEALER -> {
                return switch (i) {
                    case 0 -> PotionContents.createItemStack(Items.POTION, Potions.HEALING);
                    case 1 -> new ItemStack(Items.MILK_BUCKET);
                    case 2 -> PotionContents.createItemStack(Items.POTION, Potions.REGENERATION);
                    default -> new ItemStack(Items.TOTEM_OF_UNDYING);
                };
            }
            case CityNpc.SHOPKEEPER -> {
                return new ItemStack(switch (i) {
                    case 0 -> Items.TORCH;
                    case 1 -> Items.ARROW;
                    case 2 -> Items.ENDER_PEARL;
                    case 3 -> Items.FIREWORK_ROCKET;
                    case 4 -> Items.EXPERIENCE_BOTTLE;
                    default -> Items.FILLED_MAP;
                });
            }
            case CityNpc.GATEKEEPER -> {
                int r = Math.max(0, Math.min(5, i));
                return ModItems.KEYS[r] == null ? new ItemStack(Items.ENDER_EYE) : new ItemStack(ModItems.KEYS[r]);
            }
            case CityNpc.TRAINER -> {
                return new ItemStack(switch (i) {
                    case 0 -> Items.WOODEN_SWORD;
                    case 1 -> Items.STONE_SWORD;
                    case 2 -> Items.IRON_SWORD;
                    case 3 -> Items.GOLDEN_SWORD;
                    case 4 -> Items.DIAMOND_SWORD;
                    default -> Items.NETHERITE_SWORD;
                });
            }
            default -> {
            }
        }
        return switch (i) {
            case 0 -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case 1 -> ModItems.RUNE == null ? new ItemStack(Items.BOOK) : new ItemStack(ModItems.RUNE);
            case 2 -> new ItemStack(Items.ECHO_SHARD);
            case 3 -> ModItems.KEYS[2] == null ? new ItemStack(Items.TRIPWIRE_HOOK) : new ItemStack(ModItems.KEYS[2]);
            case 4 -> ModItems.KEYS[4] == null ? new ItemStack(Items.TRIPWIRE_HOOK) : new ItemStack(ModItems.KEYS[4]);
            default -> new ItemStack(Items.GOLD_INGOT);
        };
    }

    private int[] buyButton(int i) {
        int ry = rowY(i) + 2;
        return new int[]{px() + pw() - 86, ry + 2, 76, 16};
    }

    private List<String> shop(GuiGraphics g, Font f, int x1, int y1, int x2, int y2, int mx, int my, float a) {
        CityShop.Offer[] offers = CityShop.offers(role);
        List<String> tip = null;
        for (int i = 0; i < offers.length; i++) {
            CityShop.Offer o = offers[i];
            int ry = rowY(i) + 2;
            g.fill(x1 + 8, ry, x2 - 8, ry + ROW - 4, Ui.fade(0x70101A28, a));
            Ui.border(g, x1 + 8, ry, x2 - 8, ry + ROW - 4, Ui.fade(0xFF24364A, a));
            if (a >= 0.99F) g.renderItem(offerIcon(role, i), x1 + 11, ry + 2);
            Ui.text(g, f, o.name(), x1 + 31, ry + 3, Ui.WHITE, a);
            Ui.text(g, f, fit(f, o.desc(), x2 - x1 - 140), x1 + 31, ry + 12, Ui.GRAY, a * 0.9F);
            int need = CityShop.rankNeeded(role, i);
            boolean rankOk = need < 0 || CData.hunterRank() >= need;
            boolean ok = (o.essences() ? CData.essences() >= o.price() : CData.coins() >= o.price()) && rankOk;
            String price = o.essences() ? "◆ " + o.price() : o.price() + " oro";
            int[] b = buyButton(i);
            Ui.text(g, f, price, b[0] - 8 - f.width(price), ry + 7, o.essences() ? Hud.ESSENCE : Ui.GOLD, a);
            String label = switch (role) {
                case CityNpc.GATEKEEPER -> rankOk ? "ABRIR" : "RANGO " + com.elsistema.Defs.RANK[Math.max(0, need)];
                case CityNpc.TRAINER -> rankOk ? "LUCHAR" : "RANGO " + com.elsistema.Defs.RANK[Math.max(0, need)];
                case CityNpc.HEALER -> "PEDIR";
                default -> "COMPRAR";
            };
            boolean hover = Ui.button(g, f, label, b[0], b[1], b[2], b[3], mx, my, ok ? accent() : Ui.RED, true, a);
            if (hover && !ok) {
                tip = List.of(o.name(), !rankOk ? "Necesitas ser cazador de rango " + com.elsistema.Defs.RANK[Math.max(0, need)]
                        : o.essences() ? "Te faltan esencias" : "Te falta oro");
            }
        }
        String h = switch (role) {
            case CityNpc.MERCHANT -> "Las esencias son el alma de los monstruos: gánalas cazando";
            case CityNpc.COOK -> "La comida va a tu inventario; el banquete hace efecto al momento";
            case CityNpc.HEALER -> "Las curas hacen efecto al momento; el tótem va a tu inventario";
            case CityNpc.SHOPKEEPER -> "Todo va a tu inventario. ¡Vuelve cuando quieras!";
            case CityNpc.GATEKEEPER -> "La puerta se abre delante del anillo: crúzala para entrar en la mazmorra";
            case CityNpc.TRAINER -> "Una persona a la vez · la piedra imán del foso te sube si te rindes";
            default -> "Las pociones van a tu inventario; los elixires hacen efecto al momento";
        };
        hint(g, f, h, x1, x2, y2, a);
        return tip;
    }

    // ================================================================ contratos (misiones secundarias)

    private List<Integer> myQuests() {
        List<Integer> l = new ArrayList<>();
        for (int q = 0; q < SideQuests.COUNT; q++) {
            if (SideQuests.GIVER[q] == role) l.add(q);
        }
        return l;
    }

    private int questRowY(int i) {
        return py() + 26 + i * 52;
    }

    private int[] questButton(int i) {
        return new int[]{px() + pw() - 92, questRowY(i) + 16, 82, 16};
    }

    private List<String> contracts(GuiGraphics g, Font f, int x1, int y1, int x2, int y2, int mx, int my, float a, long now) {
        List<Integer> qs = myQuests();
        if (qs.isEmpty()) {
            Ui.center(g, f, "«" + CityNpc.LINES[role][0] + "»", (x1 + x2) / 2, y1 + 60, Ui.GRAY, a);
            return null;
        }
        List<String> tip = null;
        float pulse = 0.65F + 0.35F * (float) Math.sin(now / 260.0);
        for (int i = 0; i < qs.size(); i++) {
            int q = qs.get(i);
            int st = CData.side(q);
            int ry = questRowY(i);
            int col = st == SideQuests.CLAIMED ? Ui.GREEN : st == SideQuests.READY ? Ui.GOLD : st == SideQuests.ACTIVE ? Ui.CYAN : Ui.GRAY;
            g.fill(x1 + 8, ry, x2 - 8, ry + 48, Ui.fade(0x80101A28, a));
            Ui.border(g, x1 + 8, ry, x2 - 8, ry + 48, Ui.fade((col & 0x00FFFFFF) | 0xA0000000, a));
            g.fill(x1 + 8, ry, x1 + 10, ry + 48, Ui.fade(col, a));
            Ui.text(g, f, SideQuests.NAME[q], x1 + 15, ry + 4, Ui.WHITE, a);
            Ui.text(g, f, fit(f, SideQuests.GOAL_TEXT[q], x2 - x1 - 130), x1 + 15, ry + 15, Ui.GRAY, a);
            int goal = SideQuests.GOAL[q];
            int prog = Math.min(goal, CData.sideProg(q));
            if (st == SideQuests.ACTIVE || st == SideQuests.READY) {
                int bw = x2 - x1 - 130;
                Ui.bar(g, x1 + 15, ry + 27, bw, 4, prog / (float) goal, col, a);
                String ptxt = prog + "/" + goal;
                Ui.text(g, f, ptxt, x1 + 19 + bw, ry + 25, col, a);
            }
            String reward = st == SideQuests.CLAIMED ? "Recompensa: " + SideQuests.REWARD[q] : "Recompensa: ? ? ?";
            Ui.text(g, f, fit(f, reward, x2 - x1 - 30), x1 + 15, ry + 36, st == SideQuests.CLAIMED ? Ui.GREEN : 0xFF8A7A50, a);
            int[] b = questButton(i);
            String label = switch (st) {
                case SideQuests.OPEN -> "ACEPTAR";
                case SideQuests.ACTIVE -> "EN CURSO";
                case SideQuests.READY -> "COBRAR";
                default -> "COBRADA";
            };
            boolean enabled = st == SideQuests.OPEN || st == SideQuests.READY;
            Ui.button(g, f, label, b[0], b[1], b[2], b[3], mx, my, st == SideQuests.READY ? Ui.GOLD : accent(), enabled,
                    st == SideQuests.READY ? a * pulse : a);
            if (Ui.inside(mx, my, x1 + 8, ry, x2 - x1 - 108, 48)) {
                tip = List.of(SideQuests.NAME[q], SideQuests.GOAL_TEXT[q],
                        st == SideQuests.CLAIMED ? "Cobrada: " + SideQuests.REWARD[q] : "La recompensa está oculta hasta que la cobres");
            }
        }
        hint(g, f, "Acepta un contrato, cúmplelo donde sea y vuelve aquí a cobrarlo", x1, x2, y2, a);
        return tip;
    }

    // ================================================================ ratón

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (role == CityNpc.SMITH) {
            int lx2 = listX2();
            for (int i = 0; i < Forge.SLOTS.length; i++) {
                if (Ui.inside(mx, my, px() + 8, rowY(i), lx2 - px() - 8, ROW - 3)) {
                    sel = i;
                    Hud.click();
                    return true;
                }
            }
            ItemStack s = slot(sel);
            int[] ub = upgradeButton();
            if (Ui.inside(mx, my, ub[0], ub[1], ub[2], ub[3]) && Forge.isWeapon(s) && Forge.level(s) < Forge.MAX_LEVEL) {
                send(Net.ACT_FORGE, sel, 0);
                Hud.click();
                return true;
            }
            int[] hb = hiddenButton();
            if (Ui.inside(mx, my, hb[0], hb[1], hb[2], hb[3]) && Forge.isEquipment(s)) {
                send(Net.ACT_FORGE, sel, 1);
                Hud.click();
                return true;
            }
        } else if (isShop()) {
            CityShop.Offer[] offers = CityShop.offers(role);
            for (int i = 0; i < offers.length; i++) {
                int[] b = buyButton(i);
                if (Ui.inside(mx, my, b[0], b[1], b[2], b[3])) {
                    send(Net.ACT_CITY_BUY, role, i);
                    Hud.click();
                    // la puerta y la arena se ven mejor sin la ventana delante
                    if (role == CityNpc.GATEKEEPER || role == CityNpc.TRAINER) this.onClose();
                    return true;
                }
            }
        } else {
            List<Integer> qs = myQuests();
            for (int i = 0; i < qs.size(); i++) {
                int q = qs.get(i);
                int st = CData.side(q);
                int[] b = questButton(i);
                if (Ui.inside(mx, my, b[0], b[1], b[2], b[3]) && (st == SideQuests.OPEN || st == SideQuests.READY)) {
                    send(Net.ACT_SIDE, q, st == SideQuests.OPEN ? 0 : 1);
                    Hud.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Prueba automática: elige el hueco de la herrería. */
    public void selfTestSelect(int i) {
        this.sel = Math.max(0, Math.min(Forge.SLOTS.length - 1, i));
    }

    /** Prueba automática: recorre los textos de todas las ventanas. */
    public static int selfCheck() {
        int n = 0;
        for (int r = 0; r < CityNpc.ROLES; r++) {
            for (int i = 0; i < CityShop.offers(r).length; i++) {
                offerIcon(r, i);
                n++;
            }
        }
        for (int i = 0; i < 8; i++) {
            n += Forge.WEAPON_NAME[i].length() > 0 && Forge.ARMOR_DESC[i].length() > 0 ? 1 : 0;
        }
        for (int q = 0; q < SideQuests.COUNT; q++) {
            n += SideQuests.REWARD[q].isEmpty() ? 0 : 1;
        }
        return n;
    }
}
