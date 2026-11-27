package com.elsistema;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La Ciudad de los Cazadores: una dimensión propia (data/elsistema/dimension/ciudad.json). Desde la 1.9.0 es una
 * ciudad entera de 7x7 manzanas (ver CityBuilder) que se construye poco a poco (sin congelar el servidor) la primera
 * vez que arranca el mundo: plaza con el Cristal del Sistema y su haz de luz, Torre de la Asociación con ascensor al
 * mirador, Arena, Estación de Puertas, tiendas, Centro Médico, Comedor, Gremio, parques, río con puentes y tu
 * apartamento. Las tiendas sólo se usan en persona (hablando con su tendero).
 */
public final class City {
    private City() {}

    public static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "ciudad"));
    /** Altura del suelo (donde se pisa). */
    public static final int G = 64;
    /** Versión de la ciudad: si cambia, se vuelve a construir. */
    private static final int VERSION = 2;
    private static final BlockPos MARKER = new BlockPos(VERSION, 1, 0);
    private static final double SPAWN_X = 0.5;
    private static final double SPAWN_Z = 8.5;
    /** Toda la ciudad (para buscar a sus habitantes). */
    private static final AABB AREA = new AABB(-CityBuilder.OUT - 4, G - 10, -CityBuilder.OUT - 4,
            CityBuilder.OUT + 4, G + 110, CityBuilder.SOUTH + 4);

    private static final TicketType<ChunkPos> TICKET = TicketType.create("elsistema_ciudad", Comparator.comparingLong(ChunkPos::toLong));

    /** La obra en curso (null si no se está construyendo). */
    private static CityBuilder job;
    private static long jobStart;
    private static int jobTicks;
    /** ¿Está la ciudad de esta versión? (se mira una vez y se recuerda) */
    private static volatile boolean ready = false;
    /** Para la prueba automática: la ciudad construida (la mira el cliente de prueba desde su hilo). */
    public static volatile boolean builtFlag = false;
    /** Milisegundos de obra por tick (la prueba del servidor usa más para acabar antes). */
    public static long budgetMs = 25;
    /** Para la prueba automática: oficios distintos en la ciudad al acabar la obra y bloques que no existían. */
    public static int lastRoles = 0;
    public static List<String> missingBlocks = List.of();
    /** Quien quiso ir a la ciudad mientras se construía: va en cuanto esté. */
    private static final Set<UUID> WAITING = new LinkedHashSet<>();

    public static boolean inCity(Player p) {
        return p.level().dimension() == CITY;
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(CITY);
    }

    // ================================================================ la obra

    /** ¿Está construida la ciudad de esta versión? */
    public static boolean isReady(ServerLevel city) {
        if (ready) return true;
        if (job != null) return false;
        if (city.getBlockState(MARKER).is(Blocks.LODESTONE)) {
            ready = true;
            builtFlag = true;
        }
        return ready;
    }

    public static boolean building() {
        return job != null;
    }

    public static int buildPercent() {
        return job == null ? 100 : job.percent();
    }

    /** Al arrancar el servidor: si falta la ciudad (o es de una versión vieja) se empieza a construir. */
    public static void onServerStarted(MinecraftServer server) {
        ready = false;
        builtFlag = false;
        job = null;
        WAITING.clear();
        ServerLevel city = level(server);
        if (city != null && !isReady(city)) startBuild(city, false);
    }

    public static void onServerStopping(MinecraftServer server) {
        if (job != null) {
            ElSistema.LOGGER.warn("[ciudad] se cierra el servidor con la obra a medias ({} %): se terminará la próxima vez", job.percent());
            releaseTickets(job.l);
        }
        job = null;
        ready = false;
        WAITING.clear();
        Arena.reset();
    }

    /** Empieza la obra (force: aunque ya esté construida, para /sistema ciudad reconstruir). */
    public static boolean startBuild(ServerLevel city, boolean force) {
        if (job != null) return false;
        if (!force && isReady(city)) return false;
        ready = false;
        builtFlag = false;
        if (force) city.setBlock(MARKER, Blocks.STONE.defaultBlockState(), 2);
        job = new CityBuilder(city);
        job.plan();
        jobStart = System.currentTimeMillis();
        jobTicks = 0;
        addTickets(city);
        ElSistema.LOGGER.info("[ciudad] empieza la obra de la Ciudad de los Cazadores v{} ({} pasos)", VERSION, job.steps.size());
        return true;
    }

    private static void addTickets(ServerLevel city) {
        for (int cx = (-CityBuilder.HX) >> 4; cx <= CityBuilder.HX >> 4; cx++) {
            for (int cz = CityBuilder.HZ1 >> 4; cz <= CityBuilder.HZ2 >> 4; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                city.getChunkSource().addRegionTicket(TICKET, cp, 0, cp);
            }
        }
    }

    private static void releaseTickets(ServerLevel city) {
        for (int cx = (-CityBuilder.HX) >> 4; cx <= CityBuilder.HX >> 4; cx++) {
            for (int cz = CityBuilder.HZ1 >> 4; cz <= CityBuilder.HZ2 >> 4; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                city.getChunkSource().removeRegionTicket(TICKET, cp, 0, cp);
            }
        }
    }

    private static void tickJob(MinecraftServer server) {
        if (job == null) return;
        ServerLevel city = job.l;
        jobTicks++;
        // Nadie puede estar en la ciudad mientras se construye: vuelven a casa y regresan al acabar
        if (jobTicks % 10 == 1) {
            for (ServerPlayer p : List.copyOf(city.players())) {
                if (p.getY() > G + 150) continue; // dentro de una mazmorra (en el cielo de la ciudad)
                Data d = Store.get(p);
                back(p, d);
                WAITING.add(p.getUUID());
                SystemLogic.notify(p, Net.N_CITY, 10, job.percent());
            }
        }
        boolean done = job.run(budgetMs * 1_000_000L);
        if (jobTicks % 200 == 0) {
            for (UUID id : WAITING) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) SystemLogic.notify(p, Net.N_CITY, 6, job.percent());
            }
            ElSistema.LOGGER.info("[ciudad] obra al {} % ({} bloques)", job.percent(), job.count);
        }
        if (done) finishBuild(server, city);
    }

    private static void finishBuild(MinecraftServer server, ServerLevel city) {
        CityBuilder b = job;
        // Fuera los vecinos de la ciudad vieja: cada uno vuelve a aparecer en su sitio nuevo
        int gone = 0;
        for (CityNpc n : city.getEntitiesOfClass(CityNpc.class, AREA, n -> true)) {
            n.discard();
            gone++;
        }
        city.setBlock(MARKER, Blocks.LODESTONE.defaultBlockState(), 2);
        job = null;
        ready = true;
        builtFlag = true;
        int made = checkNpcs(city);
        int animals = spawnAnimals(city);
        Set<Integer> roles = new HashSet<>();
        for (CityNpc n : city.getEntitiesOfClass(CityNpc.class, AREA, n -> n.isAlive())) roles.add(n.role());
        lastRoles = roles.size();
        missingBlocks = List.copyOf(b.missing);
        releaseTickets(city);
        long ms = System.currentTimeMillis() - jobStart;
        ElSistema.LOGGER.info("Ciudad de los Cazadores v{} construida: {} bloques en {} s ({} ticks); {} vecinos nuevos ({} viejos fuera), {} animales{}",
                VERSION, b.count, ms / 1000, jobTicks, made, gone, animals, b.missing.isEmpty() ? "" : "; faltan bloques: " + b.missing);
        for (UUID id : List.copyOf(WAITING)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive()) continue;
            Data d = Store.get(p);
            if (d.inDungeon == 1 || Dungeons.inDungeon(p)) continue;
            travel(p, d);
        }
        WAITING.clear();
    }

    // ================================================================ animales de la ciudad

    public static final String PET_TAG = "es_ciudad_animal";
    public static int lastAnimals = 0;

    /** Gatos en las plazas y parques, loros en el jardín, conejos y zorros en la orilla y peces en el río y la fuente. */
    private static int spawnAnimals(ServerLevel city) {
        for (net.minecraft.world.entity.Entity e : city.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, AREA.inflate(60),
                e -> e.getTags().contains(PET_TAG))) {
            e.discard();
        }
        int rz = CityBuilder.RIVER_Z1 + 6;
        Object[][] list = {
                {net.minecraft.world.entity.EntityType.CAT, -6, 4}, {net.minecraft.world.entity.EntityType.CAT, 5, -6},
                {net.minecraft.world.entity.EntityType.CAT, -26, 4}, {net.minecraft.world.entity.EntityType.CAT, 2, 52},
                {net.minecraft.world.entity.EntityType.CAT, -52, 54}, {net.minecraft.world.entity.EntityType.CAT, -30, 24},
                {net.minecraft.world.entity.EntityType.PARROT, -26, -24}, {net.minecraft.world.entity.EntityType.PARROT, -32, -32},
                {net.minecraft.world.entity.EntityType.PARROT, 4, 48}, {net.minecraft.world.entity.EntityType.PARROT, 26, 6},
                {net.minecraft.world.entity.EntityType.RABBIT, -20, 140}, {net.minecraft.world.entity.EntityType.RABBIT, 30, 136},
                {net.minecraft.world.entity.EntityType.RABBIT, -64, 142}, {net.minecraft.world.entity.EntityType.RABBIT, 70, 146},
                {net.minecraft.world.entity.EntityType.FOX, 112, -112}, {net.minecraft.world.entity.EntityType.FOX, -114, 40},
                {net.minecraft.world.entity.EntityType.TROPICAL_FISH, -20, rz}, {net.minecraft.world.entity.EntityType.TROPICAL_FISH, 6, rz + 2},
                {net.minecraft.world.entity.EntityType.TROPICAL_FISH, 30, rz - 1}, {net.minecraft.world.entity.EntityType.TROPICAL_FISH, 60, rz + 1},
                {net.minecraft.world.entity.EntityType.TROPICAL_FISH, -50, rz}, {net.minecraft.world.entity.EntityType.AXOLOTL, -80, rz + 3},
                {net.minecraft.world.entity.EntityType.AXOLOTL, 90, rz - 2}, {net.minecraft.world.entity.EntityType.SALMON, 0, rz + 4}};
        int n = 0;
        for (Object[] o : list) {
            @SuppressWarnings("unchecked")
            net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type =
                    (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>) o[0];
            int x = (Integer) o[1];
            int z = (Integer) o[2];
            boolean water = z >= CityBuilder.RIVER_Z1 && z <= CityBuilder.RIVER_Z2;
            BlockPos at = water ? new BlockPos(x, G - 3, z) : freeSpot(city, new BlockPos(x, G, z));
            try {
                net.minecraft.world.entity.Mob m = type.spawn(city, at, net.minecraft.world.entity.MobSpawnType.EVENT);
                if (m == null) continue;
                m.setPersistenceRequired();
                m.setInvulnerable(true);
                m.addTag(PET_TAG);
                if (m instanceof net.minecraft.world.entity.animal.AbstractFish f) f.setFromBucket(true);
                if (m instanceof net.minecraft.world.entity.animal.axolotl.Axolotl a) a.setFromBucket(true);
                n++;
            } catch (Exception e) {
                ElSistema.LOGGER.warn("[ciudad] no pude poner un {}: {}", type.getDescriptionId(), e.toString());
            }
        }
        lastAnimals = n;
        return n;
    }

    // ================================================================ viajar

    /** ACT_CITY: a = 0 ir a la ciudad, 1 volver al sitio de donde viniste, 10 + i ir al sitio i de la ciudad. */
    public static void action(ServerPlayer p, int op) {
        Data d = Store.get(p);
        if (d.awakened != 1) {
            SystemLogic.notify(p, Net.N_NOT_AWAKENED, 0, 0);
            return;
        }
        if (op >= 10) goTo(p, d, op - 10);
        else if (op == 0) travel(p, d);
        else back(p, d);
    }

    public static void travel(ServerPlayer p, Data d) {
        if (inCity(p)) {
            SystemLogic.notify(p, Net.N_CITY, 3, 0);
            return;
        }
        if (d.inDungeon == 1 || Dungeons.inDungeon(p)) {
            SystemLogic.notify(p, Net.N_CITY, 2, 0);
            return;
        }
        ServerLevel city = p.getServer() == null ? null : level(p.getServer());
        if (city == null) {
            SystemLogic.notify(p, Net.N_CITY, 4, 0);
            return;
        }
        if (!isReady(city)) {
            if (job == null) startBuild(city, false);
            WAITING.add(p.getUUID());
            SystemLogic.notify(p, Net.N_CITY, 6, buildPercent());
            return;
        }
        d.cityDim = p.level().dimension().location().toString();
        d.cityX = p.getX();
        d.cityY = p.getY();
        d.cityZ = p.getZ();
        d.cityYaw = p.getYRot();
        d.cityRet = 1;
        p.teleportTo(city, SPAWN_X, G, SPAWN_Z, 180.0F, 0.0F);
        p.resetFallDistance();
        city.playSound((Player) null, SPAWN_X, G, SPAWN_Z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        Fx.send(city, net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, SPAWN_X, G + 1.0, SPAWN_Z, 40, 0.4, 0.9, 0.4, 0.05);
        SystemLogic.notify(p, Net.N_CITY, 0, 0);
        d.cityCheck = 100;
        d.liftLock = 0;
        SystemLogic.sync(p, d);
    }

    /** /spawn: lleva a la plaza de la ciudad (si ya estás en la ciudad, vuelves a la plaza sin perder el sitio de vuelta). */
    public static void toSpawn(ServerPlayer p, Data d) {
        if (!inCity(p)) {
            travel(p, d);
            return;
        }
        if (p.getY() > G + 150) {
            SystemLogic.notify(p, Net.N_CITY, 2, 0);
            return;
        }
        Arena.leave(p);
        ServerLevel city = (ServerLevel) p.level();
        p.teleportTo(city, SPAWN_X, G, SPAWN_Z, 180.0F, 0.0F);
        p.resetFallDistance();
        city.playSound((Player) null, SPAWN_X, G, SPAWN_Z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** Ir al momento a un sitio de la ciudad (sólo estando en ella). */
    public static void goTo(ServerPlayer p, Data d, int i) {
        if (i < 0 || i >= CityPlaces.count()) return;
        if (!inCity(p) || p.getY() > G + 150 || d.inDungeon == 1) {
            SystemLogic.notify(p, Net.N_CITY, 5, 0);
            return;
        }
        if (Arena.fighting(p)) {
            SystemLogic.notify(p, Net.N_CITY, 9, 0);
            return;
        }
        CityPlaces.Place pl = CityPlaces.LIST[i];
        ServerLevel city = (ServerLevel) p.level();
        Fx.send(city, net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
        p.teleportTo(city, pl.x(), pl.y(), pl.z(), pl.yaw(), 0.0F);
        p.resetFallDistance();
        d.liftLock = 1;
        city.playSound((Player) null, pl.x(), pl.y(), pl.z(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.3F);
        Fx.send(city, net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, pl.x(), pl.y() + 1.0, pl.z(), 30, 0.3, 0.8, 0.3, 0.05);
        SystemLogic.notify(p, Net.N_CITY, 8, i);
    }

    public static void back(ServerPlayer p, Data d) {
        if (!inCity(p) || p.getServer() == null) {
            SystemLogic.notify(p, Net.N_CITY, 5, 0);
            return;
        }
        Arena.leave(p);
        ServerLevel to = null;
        if (d.cityRet == 1 && d.cityDim != null && !d.cityDim.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(d.cityDim);
            if (id != null) to = p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        double x = d.cityX;
        double y = d.cityY;
        double z = d.cityZ;
        float yaw = d.cityYaw;
        if (to == null || to.dimension() == CITY) {
            to = p.getServer().overworld();
            BlockPos s = to.getSharedSpawnPos();
            x = s.getX() + 0.5;
            y = to.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.getX(), s.getZ());
            z = s.getZ() + 0.5;
            yaw = 0.0F;
        }
        d.cityRet = 0;
        p.teleportTo(to, x, y, z, yaw, 0.0F);
        p.resetFallDistance();
        SystemLogic.notify(p, Net.N_CITY, 1, 0);
        SystemLogic.sync(p, d);
    }

    // ================================================================ cada tick

    /** Cada tick: la obra, la arena, el Portal de regreso, el ascensor de la Asociación y los vecinos. */
    public static void tick(MinecraftServer server) {
        tickJob(server);
        Arena.tick(server);
        ServerLevel city = level(server);
        if (city == null || city.players().isEmpty() || job != null) return;
        boolean check = server.getTickCount() % 10 == 0;
        for (ServerPlayer p : List.copyOf(city.players())) {
            Data d = Store.get(p);
            if (d.cityCheck > 0 && --d.cityCheck == 0) checkNpcs(city);
            if (!p.isAlive() || p.isSpectator()) continue;
            int bx = p.getBlockX();
            int by = p.getBlockY();
            int bz = p.getBlockZ();
            lift(p, d, city, bx, by, bz);
            if (!check) continue;
            // El portal de regreso: el hueco del arco del lado oeste de la plaza
            if (p.getX() >= -10.0 && p.getX() < -8.0 && p.getZ() >= -2.0 && p.getZ() < 3.0 && p.getY() >= G - 0.5 && p.getY() < G + 3) {
                back(p, d);
                continue;
            }
            // La piedra imán del foso de la arena te sube
            if (bx == CityBuilder.ARENA_PAD_X && bz == CityBuilder.ARENA_PAD_Z && by == CityBuilder.ARENA_FLOOR) {
                Arena.leave(p);
                p.teleportTo(city, CityBuilder.ARENA_CX + 0.5, G, CityBuilder.ARENA_CZ + 12.5, 0.0F, 0.0F);
                p.resetFallDistance();
                city.playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        }
        long t = server.getTickCount();
        fireworks(city, t);
        if (t % 8 == 0) music(city);
        if (t % 40 == 0) {
            Fx.ambient(city, net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, -9.0, G + 1.5, 0.5, 10, 0.4, 1.0, 1.6, 0.02);
            Fx.ambient(city, net.minecraft.core.particles.ParticleTypes.END_ROD, 0.5, G + 16.0, 0.5, 3, 0.4, 0.6, 0.4, 0.01);
            Fx.ambient(city, net.minecraft.core.particles.ParticleTypes.END_ROD, CityBuilder.GATE_X + 0.5, G + 6.0, CityBuilder.GATE_Z - 0.5,
                    4, 2.0, 2.0, 0.2, 0.01);
        }
        if (t % 20 == 0) {
            // la fuente de la plaza
            for (int k = 0; k < 4; k++) {
                double a = (t / 20 + k) * Math.PI / 2.0;
                Fx.ambient(city, net.minecraft.core.particles.ParticleTypes.SPLASH, 0.5 + Math.cos(a) * 4.0, G - 0.1, 0.5 + Math.sin(a) * 4.0,
                        6, 0.4, 0.1, 0.4, 0.1);
            }
        }
    }

    /** Al anochecer hay fuegos artificiales sobre la plaza (medio minuto, si hay alguien cerca para verlos). */
    private static void fireworks(ServerLevel city, long t) {
        long time = city.getDayTime() % 24000L;
        if (time < 13200L || time > 13800L || t % 12 != 0) return;
        boolean near = false;
        for (ServerPlayer p : city.players()) {
            if (p.distanceToSqr(0.5, G, 0.5) < 160 * 160) near = true;
        }
        if (!near) return;
        net.minecraft.util.RandomSource r = city.getRandom();
        int[] colors = {0x5FD7FF, 0x3A8DFF, 0xB57BFF, 0xFFFFFF, 0xFFC857, 0xFF6A9A};
        net.minecraft.world.item.component.FireworkExplosion.Shape[] shapes = {
                net.minecraft.world.item.component.FireworkExplosion.Shape.LARGE_BALL,
                net.minecraft.world.item.component.FireworkExplosion.Shape.SMALL_BALL,
                net.minecraft.world.item.component.FireworkExplosion.Shape.STAR,
                net.minecraft.world.item.component.FireworkExplosion.Shape.BURST};
        net.minecraft.world.item.component.FireworkExplosion boom = new net.minecraft.world.item.component.FireworkExplosion(
                shapes[r.nextInt(shapes.length)],
                it.unimi.dsi.fastutil.ints.IntList.of(colors[r.nextInt(colors.length)], colors[r.nextInt(colors.length)]),
                it.unimi.dsi.fastutil.ints.IntList.of(colors[r.nextInt(colors.length)]), r.nextBoolean(), r.nextBoolean());
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
        stack.set(net.minecraft.core.component.DataComponents.FIREWORKS,
                new net.minecraft.world.item.component.Fireworks(2 + r.nextInt(2), List.of(boom)));
        double a = r.nextDouble() * Math.PI * 2.0;
        double d = 6.0 + r.nextDouble() * 4.0;
        city.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(city, 0.5 + Math.cos(a) * d, G + 1.0,
                0.5 + Math.sin(a) * d, stack));
    }

    /** Mateo, el músico callejero, toca (una melodía en escala pentatónica) si hay alguien cerca. */
    private static int note = 7;

    private static void music(ServerLevel city) {
        BlockPos home = npcHome(CityNpc.MUSICIAN);
        boolean near = false;
        for (ServerPlayer p : city.players()) {
            if (p.distanceToSqr(home.getX(), home.getY(), home.getZ()) < 20 * 20) near = true;
        }
        if (!near) return;
        List<CityNpc> l = city.getEntitiesOfClass(CityNpc.class, new AABB(home).inflate(18), n -> n.role() == CityNpc.MUSICIAN);
        if (l.isEmpty()) return;
        CityNpc m = l.get(0);
        net.minecraft.util.RandomSource r = city.getRandom();
        if (r.nextInt(5) == 0) return; // silencios
        int[] scale = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24};
        note = Math.max(0, Math.min(scale.length - 1, note + r.nextInt(5) - 2));
        float pitch = (float) Math.pow(2.0, (scale[note] - 12) / 12.0);
        city.playSound((Player) null, m.getX(), m.getY() + 1.0, m.getZ(),
                r.nextInt(4) == 0 ? SoundEvents.NOTE_BLOCK_BASS.value() : SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.RECORDS, 0.7F, pitch);
        if (r.nextInt(3) == 0) {
            Fx.ambient(city, net.minecraft.core.particles.ParticleTypes.NOTE, m.getX(), m.getY() + 2.2, m.getZ(), 1, 0.2, 0.1, 0.2, 1.0);
        }
    }

    /** El ascensor de la Torre de la Asociación: quédate un momento en la cabina y sube (o baja). */
    private static void lift(ServerPlayer p, Data d, ServerLevel city, int bx, int by, int bz) {
        int cab = -1;
        if (by == G && bz >= CityBuilder.LIFT_LOBBY_Z && bz <= CityBuilder.LIFT_LOBBY_Z + 1) {
            if (bx >= CityBuilder.LIFT_LOBBY_X1 && bx <= CityBuilder.LIFT_LOBBY_X1 + 1) cab = 0;
            else if (bx >= CityBuilder.LIFT_LOBBY_X2 && bx <= CityBuilder.LIFT_LOBBY_X2 + 1) cab = 1;
        } else if (by == CityBuilder.DECK_Y && bz >= CityBuilder.LIFT_DECK_Z && bz <= CityBuilder.LIFT_DECK_Z + 1
                && bx >= CityBuilder.LIFT_DECK_X && bx <= CityBuilder.LIFT_DECK_X + 1) {
            cab = 2;
        }
        if (cab < 0) {
            d.liftTicks = 0;
            d.liftLock = 0;
            return;
        }
        if (d.liftLock != 0) return;
        d.liftTicks++;
        if (d.liftTicks == 1) {
            p.displayClientMessage(Component.literal(cab == 2 ? "▼ Ascensor: bajando al vestíbulo..." : "▲ Ascensor: subiendo al mirador...")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            city.playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
        }
        if (d.liftTicks < 30) return;
        d.liftTicks = 0;
        d.liftLock = 1;
        if (cab == 2) {
            p.teleportTo(city, CityBuilder.LIFT_LOBBY_X1 + 1.0, G, CityBuilder.LIFT_LOBBY_Z + 1.0, 0.0F, 0.0F);
        } else {
            p.teleportTo(city, CityBuilder.LIFT_DECK_X + 1.0, CityBuilder.DECK_Y, CityBuilder.LIFT_DECK_Z + 1.0, 0.0F, 0.0F);
        }
        p.resetFallDistance();
        city.playSound((Player) null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        p.displayClientMessage(Component.literal(cab == 2 ? "Vestíbulo de la Asociación" : "Mirador de la Asociación · 150 m")
                .withStyle(net.minecraft.ChatFormatting.AQUA), true);
    }

    // ================================================================ tenderos

    /** ¿Hay un tendero de este oficio a menos de 8 bloques? (las tiendas sólo se usan en persona) */
    public static boolean nearRole(ServerPlayer p, int role) {
        if (!inCity(p)) return false;
        AABB box = p.getBoundingBox().inflate(8.0);
        return !p.level().getEntitiesOfClass(CityNpc.class, box, n -> n.role() == role && n.isAlive()).isEmpty();
    }

    /** Clic derecho sobre un habitante de la ciudad. */
    public static void interact(ServerPlayer p, CityNpc npc) {
        Data d = Store.get(p);
        int role = npc.role();
        SideQuests.onTalk(p, d, role);
        boolean menu = role <= CityNpc.GUILD || CityShop.offers(role).length > 0;
        if (!menu) {
            String[] lines = CityNpc.LINES[role];
            int i = p.getRandom().nextInt(lines.length);
            SystemLogic.notify(p, Net.N_NPC_TALK, role, i);
            return;
        }
        if (d.awakened != 1) {
            SystemLogic.notify(p, Net.N_NPC_TALK, role, 0);
            SystemLogic.notify(p, Net.N_NOT_AWAKENED, 0, 0);
            return;
        }
        SystemLogic.sync(p, d);
        SystemLogic.notify(p, Net.N_NPC_OPEN, role, npc.getId());
    }

    /** Pone en su sitio a los vecinos que falten (sólo si su zona está cargada). */
    public static int checkNpcs(ServerLevel city) {
        Set<Integer> present = new HashSet<>();
        for (CityNpc n : city.getEntitiesOfClass(CityNpc.class, AREA, n -> n.isAlive())) {
            present.add(n.role());
        }
        int made = 0;
        for (int r = 0; r < CityNpc.ROLES; r++) {
            if (present.contains(r)) continue;
            BlockPos at = npcHome(r);
            if (!city.isLoaded(at) || !city.areEntitiesLoaded(ChunkPos.asLong(at))) continue;
            if (spawnNpc(city, r) != null) made++;
        }
        return made;
    }

    private static CityNpc spawnNpc(ServerLevel city, int role) {
        CityNpc n = ModEntities.CIUDADANO.create(city);
        if (n == null) return null;
        BlockPos h = CityNpc.keeper(role) ? npcHome(role) : freeSpot(city, npcHome(role));
        n.moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, npcYaw(role), 0.0F);
        n.setYHeadRot(npcYaw(role));
        n.setYBodyRot(npcYaw(role));
        n.setRole(role);
        n.setHome(h);
        city.addFreshEntity(n);
        return n;
    }

    /** Un sitio libre (dos de aire encima de suelo firme) cerca del que se pide. */
    private static BlockPos freeSpot(ServerLevel city, BlockPos want) {
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos p = want.offset(dx, 0, dz);
                    if (city.getBlockState(p).getCollisionShape(city, p).isEmpty()
                            && city.getBlockState(p.above()).getCollisionShape(city, p.above()).isEmpty()
                            && !city.getBlockState(p.below()).getCollisionShape(city, p.below()).isEmpty()
                            && city.getFluidState(p).isEmpty()) {
                        return p;
                    }
                }
            }
        }
        return want;
    }

    /** Dónde vive cada habitante (los tenderos, justo detrás de su mostrador). */
    public static BlockPos npcHome(int role) {
        return switch (role) {
            case CityNpc.RECEPTION -> new BlockPos(0, G, -31);
            case CityNpc.SMITH -> new BlockPos(28, G, -27);
            case CityNpc.ALCHEMIST -> new BlockPos(56, G, -27);
            case CityNpc.MERCHANT -> new BlockPos(28, G, 27);
            case CityNpc.GUILD -> new BlockPos(-56, G, -62);
            case CityNpc.HUNTER -> new BlockPos(6, G, -6);
            case CityNpc.STUDENT -> new BlockPos(4, G, 32);
            case CityNpc.CLERK -> new BlockPos(28, G, 4);
            case CityNpc.NEIGHBOR -> new BlockPos(-24, G, 4);
            case CityNpc.HEALER -> new BlockPos(-56, G, -28);
            case CityNpc.COOK -> new BlockPos(84, G, -31);
            case CityNpc.GATEKEEPER -> new BlockPos(4, G, -82);
            case CityNpc.TRAINER -> new BlockPos(-3, G, -47);
            case CityNpc.SHOPKEEPER -> new BlockPos(60, G, 27);
            case CityNpc.NOVICE -> new BlockPos(-6, G, -15);
            case CityNpc.REPORTER -> new BlockPos(8, G, -42);
            case CityNpc.GRANDMA -> new BlockPos(-4, G, 58);
            case CityNpc.COURIER -> new BlockPos(64, G, -14);
            case CityNpc.POLICE -> new BlockPos(-14, G, 6);
            case CityNpc.MUSICIAN -> new BlockPos(-4, G, 24);
            case CityNpc.GARDENER -> new BlockPos(-24, G, -26);
            default -> new BlockPos(12, G, 104);
        };
    }

    /** ¿Mira este tendero al sur (y se le habla desde el sur)? Los de la acera sur de la avenida miran al norte. */
    public static boolean facesSouth(int role) {
        return role != CityNpc.MERCHANT && role != CityNpc.SHOPKEEPER;
    }

    private static float npcYaw(int role) {
        return facesSouth(role) ? 0.0F : 180.0F;
    }
}
