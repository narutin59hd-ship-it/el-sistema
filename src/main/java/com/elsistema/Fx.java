package com.elsistema;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Partículas del mod según los AJUSTES de cada jugador (los elige en la ventana del Sistema y su cliente los manda).
 * Cada jugador recibe sólo las partículas que quiere: sin partículas, o menos en el modo rendimiento.
 */
public final class Fx {
    private Fx() {}

    /** Bits de los ajustes que manda el cliente con {@link Net#ACT_SETTINGS}. */
    public static final int NO_PARTICLES = 1;
    public static final int PERFORMANCE = 2;

    private static final int EFFECT = 0;
    private static final int AMBIENT = 1;
    private static final int ESSENTIAL = 2;

    private static final Map<UUID, Integer> SETTINGS = new ConcurrentHashMap<>();
    private static final RandomSource RNG = RandomSource.create();

    public static void set(ServerPlayer p, int bits) {
        SETTINGS.put(p.getUUID(), bits & (NO_PARTICLES | PERFORMANCE));
    }

    public static void forget(UUID id) {
        SETTINGS.remove(id);
    }

    public static int bits(ServerPlayer p) {
        return SETTINGS.getOrDefault(p.getUUID(), 0);
    }

    /** Efecto (habilidades, golpes, sombras, jefes): no sale sin partículas y en modo rendimiento sale 1 de cada 3. */
    public static void send(ServerLevel l, ParticleOptions type, double x, double y, double z, int count,
                            double dx, double dy, double dz, double speed) {
        emit(l, type, x, y, z, count, dx, dy, dz, speed, EFFECT);
    }

    /** Adorno que se repite sin parar (auras, remolinos): sólo con partículas y sin el modo rendimiento. */
    public static void ambient(ServerLevel l, ParticleOptions type, double x, double y, double z, int count,
                               double dx, double dy, double dz, double speed) {
        emit(l, type, x, y, z, count, dx, dy, dz, speed, AMBIENT);
    }

    /**
     * Imprescindible para jugar (puertas de mazmorra, cadáveres que se pueden levantar, nubes de veneno):
     * sale siempre, pero con menos partículas si las has quitado o con el modo rendimiento.
     */
    public static void essential(ServerLevel l, ParticleOptions type, double x, double y, double z, int count,
                                 double dx, double dy, double dz, double speed) {
        emit(l, type, x, y, z, count, dx, dy, dz, speed, ESSENTIAL);
    }

    private static void emit(ServerLevel l, ParticleOptions type, double x, double y, double z, int count,
                             double dx, double dy, double dz, double speed, int kind) {
        for (ServerPlayer p : l.players()) {
            int b = bits(p);
            int n = count;
            if (b != 0) {
                boolean off = (b & NO_PARTICLES) != 0;
                int keep;
                if (kind == ESSENTIAL) {
                    keep = off ? 3 : 2;
                } else if (off || kind == AMBIENT) {
                    continue;
                } else {
                    keep = 3;
                }
                if (count <= 1) {
                    // Una sola partícula (o una con dirección): sale 1 de cada «keep» veces
                    if (RNG.nextInt(keep) != 0) continue;
                } else {
                    n = Math.max(1, (count + keep - 1) / keep);
                }
            }
            l.sendParticles(p, type, false, x, y, z, n, dx, dy, dz, speed);
        }
    }
}
