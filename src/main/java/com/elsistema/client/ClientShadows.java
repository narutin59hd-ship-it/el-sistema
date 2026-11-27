package com.elsistema.client;

import com.elsistema.Net;
import com.elsistema.ShadowTree;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lo que el cliente sabe de las sombras: qué criaturas hay que pintar oscuras y la lista del ejército. */
public final class ClientShadows {
    private ClientShadows() {}

    public static final int STORED = 0;
    public static final int OUT = 1;
    public static final int RECOVERING = 2;

    /**
     * Una sombra del ejército tal y como la manda el servidor (recover = ticks de juego, attack = daño x10,
     * xp = EXP hacia el siguiente nivel en milésimas, pts = puntos sin gastar, tree = rangos de su árbol propio).
     */
    public record Entry(int id, int rank, int level, int state, int hp, int recover, int kills, boolean boss,
                        int attack, int xp, int pts, int[] tree, String type, String key) {
        public String name() {
            if (key == null || key.isEmpty()) return type;
            return Component.translatable(key).getString();
        }

        /** Daño por golpe con un decimal. */
        public String attackText() {
            return attack % 10 == 0 ? String.valueOf(attack / 10) : (attack / 10) + "," + (attack % 10);
        }

        public int treeRank(int node) {
            return ShadowTree.rank(tree, node);
        }

        /** Puntos ya gastados en su árbol propio. */
        public int spent() {
            int s = 0;
            for (int i = 0; i < ShadowTree.SIZE; i++) {
                s += treeRank(i) * ShadowTree.NODES[i].cost();
            }
            return s;
        }
    }

    private static final Set<Integer> MARKED = new HashSet<>();
    /** Criatura de muestra que se dibuja en la pestaña Sombras (también se pinta oscura). */
    public static Entity preview;
    private static List<Entry> list = List.of();
    private static long listTime = 0L;

    public static void mark(int entityId) {
        MARKED.add(entityId);
    }

    public static boolean isShadow(int entityId) {
        return MARKED.contains(entityId);
    }

    public static boolean isShadow(Entity entity) {
        return entity != null && (entity == preview || MARKED.contains(entity.getId()));
    }

    /** Olvida las criaturas que ya no existen en el mundo del cliente. */
    public static void prune(ClientLevel level) {
        if (level == null) {
            MARKED.clear();
            return;
        }
        MARKED.removeIf(id -> level.getEntity(id) == null);
    }

    public static void set(List<Integer> nums, List<String> strs) {
        List<Entry> out = new ArrayList<>();
        if (nums != null && strs != null) {
            int st = Net.SHADOW_STRIDE;
            int n = Math.min(nums.size() / st, strs.size() / 2);
            for (int i = 0; i < n; i++) {
                int b = i * st;
                int killsBoss = nums.get(b + 6);
                int[] tree = new int[ShadowTree.SIZE];
                for (int k = 0; k < ShadowTree.SIZE; k++) {
                    tree[k] = nums.get(b + 10 + k);
                }
                out.add(new Entry(nums.get(b), nums.get(b + 1), nums.get(b + 2), nums.get(b + 3), nums.get(b + 4),
                        nums.get(b + 5), killsBoss & 0xFFFFFF, (killsBoss >> 24) != 0, nums.get(b + 7),
                        nums.get(b + 8), nums.get(b + 9), tree, strs.get(i * 2), strs.get(i * 2 + 1)));
            }
        }
        list = Collections.unmodifiableList(out);
        listTime = System.currentTimeMillis();
    }

    public static List<Entry> list() {
        return list;
    }

    public static Entry byId(int id) {
        for (Entry e : list) {
            if (e.id() == id) return e;
        }
        return null;
    }

    /** Ticks de juego que le quedan a una sombra caída (cuenta atrás en el cliente). */
    public static int recoverLeft(Entry e) {
        if (e.state() != RECOVERING) return 0;
        long passed = (System.currentTimeMillis() - listTime) / 50L;
        return (int) Math.max(0L, e.recover() - passed);
    }

    /** "2 días 5 h", "7 h" o "menos de 1 h" (días y horas de Minecraft). */
    public static String downText(Entry e) {
        int left = recoverLeft(e);
        int days = left / 24000;
        int hours = (left % 24000) / 1000;
        if (days > 0) return days + (days == 1 ? " día " : " días ") + hours + " h";
        if (hours > 0) return hours + " h";
        return "menos de 1 h";
    }

    /** ¿Alguna sombra tiene puntos sin gastar en su árbol? */
    public static boolean anyPoints() {
        for (Entry e : list) {
            if (e.pts() > 0) return true;
        }
        return false;
    }

    public static int out() {
        int n = 0;
        for (Entry e : list) {
            if (e.state() == OUT) n++;
        }
        return n;
    }

    public static void reset() {
        MARKED.clear();
        list = List.of();
        preview = null;
    }
}
