package com.elsistema.client;

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

    /** Una sombra del ejército tal y como la manda el servidor. */
    public record Entry(int id, int rank, int level, int state, int hp, int recover, int kills, boolean boss,
                        String type, String key) {
        public String name() {
            if (key == null || key.isEmpty()) return type;
            return Component.translatable(key).getString();
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
            int n = Math.min(nums.size() / 7, strs.size() / 2);
            for (int i = 0; i < n; i++) {
                int b = i * 7;
                int killsBoss = nums.get(b + 6);
                out.add(new Entry(nums.get(b), nums.get(b + 1), nums.get(b + 2), nums.get(b + 3), nums.get(b + 4),
                        nums.get(b + 5), killsBoss & 0xFFFFFF, (killsBoss >> 24) != 0, strs.get(i * 2), strs.get(i * 2 + 1)));
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

    /** Segundos que le quedan a una sombra para recuperarse (cuenta atrás en el cliente). */
    public static int recoverLeft(Entry e) {
        if (e.state() != RECOVERING) return 0;
        long passed = (System.currentTimeMillis() - listTime) / 1000L;
        return (int) Math.max(0L, e.recover() - passed);
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
