package com.elsistema;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La Arena del Sistema (1.9.0): Vera te deja combatir en el foso contra tres oleadas de monstruos de un rango
 * (la última con un campeón). Cuesta oro entrar; si ganas te llevas oro y esencias (además de lo normal por cada
 * monstruo). Sólo combate una persona a la vez. La piedra imán del foso te sube si te rindes.
 */
public final class Arena {
    private Arena() {}

    public static final String TAG = "es_arena";
    public static final int WAVES = 3;
    /** Entrada (oro) y premio por rango E, D, C, B, A y S. */
    public static final int[] FEE = {100, 250, 600, 1300, 3000, 7000};
    public static final int[] PRIZE_COINS = {300, 700, 1600, 3500, 8000, 18000};
    public static final int[] PRIZE_ESS = {10, 18, 30, 45, 70, 120};

    private static UUID fighter;
    private static ServerLevel level;
    private static int rank;
    private static int wave;
    private static int timer;
    private static int age;
    private static int waveSize = 1;
    private static final List<UUID> MOBS = new ArrayList<>();
    private static ServerBossEvent bar;

    public static boolean busy() {
        return fighter != null;
    }

    public static boolean fighting(ServerPlayer p) {
        return fighter != null && fighter.equals(p.getUUID());
    }

    public static int wave() {
        return wave;
    }

    public static int alive() {
        return MOBS.size();
    }

    /** Empieza un combate (el oro ya está cobrado). Devuelve false si la arena está ocupada. */
    public static boolean start(ServerPlayer p, int r) {
        if (fighter != null || !City.inCity(p)) return false;
        level = (ServerLevel) p.level();
        fighter = p.getUUID();
        rank = Math.max(0, Math.min(5, r));
        wave = 0;
        timer = 60;
        age = 0;
        MOBS.clear();
        bar = new ServerBossEvent(Component.literal("Arena del Sistema · Rango " + Defs.RANK[rank] + " · ¡Prepárate!"),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(1.0F);
        bar.addPlayer(p);
        p.teleportTo(level, CityBuilder.ARENA_CX + 0.5, CityBuilder.ARENA_FLOOR, CityBuilder.ARENA_CZ + 4.5, 180.0F, 0.0F);
        p.resetFallDistance();
        level.playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        SystemLogic.notify(p, Net.N_ARENA, 0, rank);
        return true;
    }

    /** Sales de la arena (portal, /spawn, viaje, piedra imán...): si estabas combatiendo, te rindes. */
    public static void leave(ServerPlayer p) {
        if (fighting(p) && p.getServer() != null) end(p.getServer(), p, false, 0);
    }

    public static void reset() {
        fighter = null;
        level = null;
        MOBS.clear();
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    private static boolean inPit(Entity e) {
        return e.getY() < City.G - 0.5 && Math.abs(e.getX() - (CityBuilder.ARENA_CX + 0.5)) < 8.0
                && Math.abs(e.getZ() - (CityBuilder.ARENA_CZ + 0.5)) < 8.0;
    }

    public static void tick(MinecraftServer server) {
        if (fighter == null) return;
        age++;
        ServerPlayer p = server.getPlayerList().getPlayer(fighter);
        if (p == null || !p.isAlive() || p.level() != level || !inPit(p)) {
            end(server, p, false, p != null && !p.isAlive() ? 1 : 0);
            return;
        }
        MOBS.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        for (UUID id : MOBS) {
            if (!(level.getEntity(id) instanceof Mob m)) continue;
            if (!inPit(m)) {
                m.teleportTo(CityBuilder.ARENA_CX + 0.5, CityBuilder.ARENA_FLOOR, CityBuilder.ARENA_CZ - 3.5);
                m.resetFallDistance();
            }
            if (m.getTarget() != p) m.setTarget(p);
        }
        if (wave > 0 && bar != null) bar.setProgress(Math.max(0.0F, Math.min(1.0F, MOBS.size() / (float) waveSize)));
        if (timer > 0) {
            if (--timer == 0) spawnWave(p);
            return;
        }
        if (wave > 0 && MOBS.isEmpty()) {
            if (wave >= WAVES) {
                end(server, p, true, 0);
                return;
            }
            timer = 60;
            if (bar != null) {
                bar.setName(Component.literal("Arena del Sistema · Rango " + Defs.RANK[rank] + " · Oleada " + (wave + 1) + "/" + WAVES
                        + " en 3 s"));
                bar.setProgress(1.0F);
            }
            level.playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        if (age > 6000) end(server, p, false, 2);
    }

    private static void spawnWave(ServerPlayer p) {
        wave++;
        boolean champ = wave >= WAVES;
        RandomSource rnd = level.getRandom();
        List<EntityType<? extends Mob>> pool = Dungeons.pool(rank);
        int n = champ ? 1 + rank / 2 : 2 + rank / 2 + wave;
        Data d = Store.get(p);
        int min = rank == 0 ? 1 : Defs.RANK_MAX[rank - 1] + 1;
        int max = Defs.RANK_MAX[rank];
        int lvl = Math.max(min, Math.min(max, d.level));
        List<UUID> list = new ArrayList<>();
        Ranks.spawning = true;
        try {
            for (int i = 0; i < n; i++) {
                Mob m = spawn(pool.get(rnd.nextInt(pool.size())), rnd, lvl + rnd.nextInt(5) - 2, false, p);
                if (m != null) list.add(m.getUUID());
            }
            if (champ) {
                Mob b = spawn(Dungeons.boss(rank), rnd, lvl + 4, true, p);
                if (b != null) list.add(b.getUUID());
            }
        } finally {
            Ranks.spawning = false;
        }
        MOBS.clear();
        MOBS.addAll(list);
        waveSize = Math.max(1, list.size());
        if (bar != null) {
            bar.setName(Component.literal((champ ? "★ CAMPEÓN DE LA ARENA" : "Arena del Sistema") + " · Rango " + Defs.RANK[rank]
                    + " · Oleada " + wave + "/" + WAVES));
            bar.setColor(champ ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
            bar.setProgress(1.0F);
        }
        SystemLogic.notify(p, Net.N_ARENA, 1, wave);
        if (champ) p.playNotifySound(SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    private static Mob spawn(EntityType<? extends Mob> type, RandomSource rnd, int lvl, boolean champ, ServerPlayer target) {
        int x = CityBuilder.ARENA_CX - 5 + rnd.nextInt(11);
        int z = CityBuilder.ARENA_CZ - 6 + rnd.nextInt(5);
        if (champ) {
            x = CityBuilder.ARENA_CX;
            z = CityBuilder.ARENA_CZ - 5;
        }
        Mob m = type.spawn(level, new BlockPos(x, CityBuilder.ARENA_FLOOR, z), MobSpawnType.MOB_SUMMONED);
        if (m == null) return null;
        m.setPersistenceRequired();
        m.addTag(TAG);
        lvl = Math.max(1, Math.min(Defs.MAX_LEVEL, lvl));
        Ranks.apply(m, rank, lvl, champ);
        if (champ) {
            m.setGlowingTag(true);
            MutableComponent name = Component.literal("★ Campeón de la Arena ").withStyle(Ranks.COLOR[rank], ChatFormatting.BOLD);
            name.append(Component.literal("[" + Defs.RANK[rank] + "] Nv." + lvl).withStyle(ChatFormatting.WHITE));
            m.setCustomName(name);
            m.setCustomNameVisible(true);
        }
        m.setTarget(target);
        Fx.send(level, ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1.0, m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
        return m;
    }

    /**
     * Fin del combate. reason (si se pierde): 0 te rindes o sales del foso, 1 has caído, 2 se acabó el tiempo.
     */
    private static void end(MinecraftServer server, ServerPlayer p, boolean won, int reason) {
        if (level != null) {
            for (UUID id : MOBS) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
        }
        MOBS.clear();
        if (bar != null) bar.removeAllPlayers();
        bar = null;
        int r = rank;
        ServerLevel l = level;
        fighter = null;
        level = null;
        if (p == null) return;
        Data d = Store.get(p);
        if (won) {
            SystemLogic.addCoins(p, PRIZE_COINS[r]);
            SystemLogic.addEssences(p, PRIZE_ESS[r]);
            SystemLogic.notify(p, Net.N_ARENA, 2, r);
            if (l != null) {
                Fx.send(l, ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.6, 1.0, 0.6, 0.3);
                l.playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        } else {
            SystemLogic.notify(p, Net.N_ARENA, 3, reason);
        }
        if (p.isAlive() && l != null && p.level() == l && inPit(p)) {
            p.teleportTo(l, CityBuilder.ARENA_CX + 0.5, City.G, CityBuilder.ARENA_CZ + 12.5, 0.0F, 0.0F);
            p.resetFallDistance();
        }
        SystemLogic.sync(p, d);
    }

    /** Prueba automática: da el combate por ganado. */
    public static void selfTestWin(ServerPlayer p) {
        if (fighting(p) && p.getServer() != null) end(p.getServer(), p, true, 0);
    }
}
