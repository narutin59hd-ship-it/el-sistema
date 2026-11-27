package com.elsistema;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * La Ciudad de los Cazadores: una dimensión propia (data/elsistema/dimension/ciudad.json) con una ciudad que se
 * construye la primera vez que alguien viaja a ella. En la plaza está el Cristal del Sistema; al norte la Asociación
 * de Cazadores; por la avenida del este la herrería, la alquimia, el mercader de esencias y el gremio. Las tiendas
 * sólo se pueden usar en persona (hablando con su tendero).
 */
public final class City {
    private City() {}

    public static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "ciudad"));
    /** Altura del suelo (donde se pisa). */
    public static final int G = 64;
    /** Versión de la ciudad: si cambia, se vuelve a construir. */
    private static final int VERSION = 1;
    private static final int HALF = 62;
    /** Lotes de 20x20 separados por calles de 6: lote k va de k*26-10 a k*26+9. */
    private static final int CELL = 26;
    private static final double SPAWN_X = 0.0;
    private static final double SPAWN_Z = 6.5;

    public static boolean inCity(Player p) {
        return p.level().dimension() == CITY;
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(CITY);
    }

    // ================================================================ viajar

    /** ACT_CITY: a = 0 ir a la ciudad, 1 volver al sitio de donde viniste. */
    public static void action(ServerPlayer p, int op) {
        Data d = Store.get(p);
        if (d.awakened != 1) {
            SystemLogic.notify(p, Net.N_NOT_AWAKENED, 0, 0);
            return;
        }
        if (op == 0) travel(p, d);
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
        ensureBuilt(city);
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
        SystemLogic.sync(p, d);
    }

    public static void back(ServerPlayer p, Data d) {
        if (!inCity(p) || p.getServer() == null) {
            SystemLogic.notify(p, Net.N_CITY, 5, 0);
            return;
        }
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

    /** Cada tick: el Portal de regreso de la plaza y comprobar que están todos los vecinos. */
    public static void tick(MinecraftServer server) {
        ServerLevel city = level(server);
        if (city == null || city.players().isEmpty()) return;
        boolean check = server.getTickCount() % 10 == 0;
        for (ServerPlayer p : List.copyOf(city.players())) {
            Data d = Store.get(p);
            if (d.cityCheck > 0 && --d.cityCheck == 0) checkNpcs(city);
            if (!check || !p.isAlive() || p.isSpectator()) continue;
            // El portal de regreso: el hueco del arco de la esquina suroeste de la plaza
            if (p.getX() > -7.9 && p.getX() < -5.1 && p.getZ() > 7.8 && p.getZ() < 9.2 && p.getY() >= G - 0.5 && p.getY() < G + 3) {
                back(p, d);
            }
        }
        if (server.getTickCount() % 40 == 0) {
            Fx.ambient(city, net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, -7.0, G + 1.4, 8.0, 6, 0.7, 0.9, 0.1, 0.02);
            Fx.ambient(city, net.minecraft.core.particles.ParticleTypes.END_ROD, 0.0, G + 13.0, 0.0, 3, 0.4, 0.6, 0.4, 0.01);
        }
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
        if (role >= CityNpc.HUNTER) {
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
        AABB all = new AABB(-HALF - 4, G - 8, -HALF - 4, HALF + 4, G + 40, HALF + 4);
        for (CityNpc n : city.getEntitiesOfClass(CityNpc.class, all, n -> n.isAlive())) {
            present.add(n.role());
        }
        int made = 0;
        for (int r = 0; r < CityNpc.ROLES; r++) {
            if (present.contains(r)) continue;
            BlockPos at = npcHome(r);
            if (!city.isLoaded(at) || !city.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(at))) continue;
            if (spawnNpc(city, r) != null) made++;
        }
        return made;
    }

    private static CityNpc spawnNpc(ServerLevel city, int role) {
        CityNpc n = ModEntities.CIUDADANO.create(city);
        if (n == null) return null;
        BlockPos h = npcHome(role);
        n.moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, npcYaw(role), 0.0F);
        n.setYHeadRot(npcYaw(role));
        n.setYBodyRot(npcYaw(role));
        n.setRole(role);
        n.setHome(h);
        city.addFreshEntity(n);
        return n;
    }

    /** Dónde vive cada habitante. */
    public static BlockPos npcHome(int role) {
        return switch (role) {
            case CityNpc.RECEPTION -> new BlockPos(-1, G, -31);
            case CityNpc.SMITH -> new BlockPos(25, G, -28);
            case CityNpc.ALCHEMIST -> new BlockPos(51, G, -28);
            case CityNpc.MERCHANT -> new BlockPos(25, G, 27);
            case CityNpc.GUILD -> new BlockPos(51, G, 29);
            case CityNpc.HUNTER -> new BlockPos(5, G, 4);
            case CityNpc.STUDENT -> new BlockPos(-6, G, -4);
            case CityNpc.CLERK -> new BlockPos(-26, G, -26);
            default -> new BlockPos(-52, G, -24);
        };
    }

    private static float npcYaw(int role) {
        // Los tenderos miran a su puerta (al sur los del norte de la avenida y al norte los del sur)
        return switch (role) {
            case CityNpc.RECEPTION, CityNpc.SMITH, CityNpc.ALCHEMIST -> 0.0F;
            case CityNpc.MERCHANT, CityNpc.GUILD -> 180.0F;
            default -> 0.0F;
        };
    }

    // ================================================================ construir la ciudad

    /** Construye la ciudad si todavía no existe (o si es de una versión anterior). Devuelve true si la ha construido. */
    public static boolean ensureBuilt(ServerLevel city) {
        BlockPos marker = new BlockPos(VERSION, 1, 0);
        if (city.getBlockState(marker).is(Blocks.LODESTONE)) return false;
        long t0 = System.currentTimeMillis();
        for (int cx = -5; cx <= 4; cx++) {
            for (int cz = -5; cz <= 4; cz++) {
                city.getChunk(cx, cz);
            }
        }
        Builder b = new Builder(city);
        b.build();
        b.set(marker.getX(), marker.getY(), marker.getZ(), Blocks.LODESTONE.defaultBlockState());
        checkNpcs(city);
        ElSistema.LOGGER.info("Ciudad de los Cazadores construida: {} bloques en {} ms", b.count, System.currentTimeMillis() - t0);
        return true;
    }

    /** Lote (i, j) de la cuadrícula: esquina noroeste. */
    static int lot(int k) {
        return k * CELL - 10;
    }

    private static final class Builder {
        final ServerLevel l;
        final RandomSource rnd = RandomSource.create(1717L);
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int count = 0;

        Builder(ServerLevel l) {
            this.l = l;
        }

        void set(int x, int y, int z, BlockState s) {
            m.set(x, y, z);
            l.setBlock(m, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            count++;
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        set(x, y, z, s);
                    }
                }
            }
        }

        /** Paredes de una caja (sin techo ni suelo). */
        void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            fill(x1, y1, z1, x2, y2, z1, s);
            fill(x1, y1, z2, x2, y2, z2, s);
            fill(x1, y1, z1, x1, y2, z2, s);
            fill(x2, y1, z1, x2, y2, z2, s);
        }

        static BlockState st(Block b) {
            return b.defaultBlockState();
        }

        void build() {
            ground();
            plaza();
            association();
            smithy();
            alchemy();
            essenceShop();
            guildHall();
            park(lot(-1), lot(-1));
            park(lot(-2), lot(-1));
            returnPortal();
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if (special(i, j)) continue;
                    tower(lot(i), lot(j), i * 7 + j * 13);
                }
            }
            lamps();
        }

        /** Lotes que no son torres. */
        boolean special(int i, int j) {
            return (i == 0 && j == 0) || (i == 0 && j == -1) || (i == 1 && j == -1) || (i == 2 && j == -1)
                    || (i == 1 && j == 1) || (i == 2 && j == 1) || (i == -1 && j == -1) || (i == -2 && j == -1);
        }

        // ------------------------------------------------------------ suelo, calles y aceras

        void ground() {
            BlockState asphalt = st(Blocks.GRAY_CONCRETE);
            BlockState line = st(Blocks.WHITE_CONCRETE);
            BlockState walk = st(Blocks.SMOOTH_STONE);
            BlockState curb = st(Blocks.STONE_BRICKS);
            for (int x = -HALF - 2; x <= HALF + 2; x++) {
                for (int z = -HALF - 2; z <= HALF + 2; z++) {
                    int rx = Math.floorMod(x + 10, CELL);
                    int rz = Math.floorMod(z + 10, CELL);
                    boolean roadX = rx >= 20;
                    boolean roadZ = rz >= 20;
                    BlockState s;
                    if (roadX || roadZ) {
                        boolean edge = (roadX && (rx == 20 || rx == 25)) || (roadZ && (rz == 20 || rz == 25));
                        if (edge && !(roadX && roadZ)) {
                            s = curb;
                        } else {
                            s = asphalt;
                            boolean mid = roadX && !roadZ && (rx == 22 || rx == 23) && Math.floorMod(z, 6) < 3 && rx == 22;
                            boolean midZ = roadZ && !roadX && rz == 22 && Math.floorMod(x, 6) < 3;
                            if (mid || midZ) s = line;
                        }
                    } else {
                        boolean border = rx == 0 || rx == 19 || rz == 0 || rz == 19;
                        s = border ? walk : st(Blocks.GRASS_BLOCK);
                    }
                    set(x, G - 1, z, s);
                    // Limpia lo que hubiera encima (por si se reconstruye)
                    for (int y = G; y < G + 4; y++) {
                        m.set(x, y, z);
                        if (!l.getBlockState(m).isAir()) set(x, y, z, st(Blocks.AIR));
                    }
                }
            }
        }

        // ------------------------------------------------------------ plaza y Cristal del Sistema

        void plaza() {
            int x1 = lot(0);
            int z1 = lot(0);
            for (int x = x1; x < x1 + 20; x++) {
                for (int z = z1; z < z1 + 20; z++) {
                    double dx = x + 0.5;
                    double dz = z + 0.5;
                    double r = Math.sqrt(dx * dx + dz * dz);
                    BlockState s;
                    if (r < 2.5) s = st(Blocks.POLISHED_BLACKSTONE);
                    else if (r < 5.5) s = st(Blocks.QUARTZ_BRICKS);
                    else if (r < 6.6) s = st(Blocks.CYAN_TERRACOTTA);
                    else if (r < 9.6) s = (Math.floorMod(x + z, 2) == 0) ? st(Blocks.POLISHED_ANDESITE) : st(Blocks.SMOOTH_STONE);
                    else s = st(Blocks.POLISHED_ANDESITE);
                    set(x, G - 1, z, s);
                }
            }
            // Cristal del Sistema: pilar de cristal azul con faroles marinos que brilla de noche
            fill(-2, G, -2, 1, G, 1, st(Blocks.POLISHED_BLACKSTONE_BRICKS));
            fill(-1, G + 1, -1, 0, G + 1, 0, st(Blocks.SEA_LANTERN));
            for (int y = G + 2; y <= G + 11; y++) {
                boolean lamp = (y - G) % 3 == 0;
                fill(-1, y, -1, 0, y, 0, lamp ? st(Blocks.SEA_LANTERN) : st(Blocks.LIGHT_BLUE_STAINED_GLASS));
            }
            set(-1, G + 12, -1, st(Blocks.SEA_LANTERN));
            set(0, G + 12, 0, st(Blocks.SEA_LANTERN));
            set(-1, G + 12, 0, st(Blocks.LIGHT_BLUE_STAINED_GLASS));
            set(0, G + 12, -1, st(Blocks.LIGHT_BLUE_STAINED_GLASS));
            // Bancos alrededor
            BlockState benchN = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            BlockState benchS = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
            BlockState benchE = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            BlockState benchW = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
            fill(-3, G, -8, 2, G, -8, benchN);
            fill(-3, G, 7, 2, G, 7, benchS);
            fill(7, G, -3, 7, G, 2, benchE);
            fill(-8, G, -3, -8, G, 2, benchW);
            // Jardineras en las esquinas
            for (int[] c : new int[][]{{-9, -9}, {7, -9}, {7, 7}}) {
                fill(c[0], G, c[1], c[0] + 1, G, c[1] + 1, st(Blocks.POLISHED_ANDESITE));
                tree(c[0], G + 1, c[1], 4);
            }
            sign(-1, G, -3, Direction.NORTH, "CRISTAL DEL", "SISTEMA", "", "");
        }

        /** Árbol pequeño (tronco y copa de hojas que no se caen). */
        void tree(int x, int y, int z, int h) {
            BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            for (int dy = 0; dy < h; dy++) {
                set(x, y + dy, z, st(Blocks.OAK_LOG));
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = h - 2; dy <= h + 1; dy++) {
                        int r = Math.abs(dx) + Math.abs(dz) + Math.max(0, dy - h);
                        if (r > 3 || (dx == 0 && dz == 0 && dy < h)) continue;
                        m.set(x + dx, y + dy, z + dz);
                        if (l.getBlockState(m).isAir()) set(x + dx, y + dy, z + dz, leaves);
                    }
                }
            }
        }

        void sign(int x, int y, int z, Direction facing, String l1, String l2, String l3, String l4) {
            BlockState s = Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
            set(x, y, z, s);
            m.set(x, y, z);
            if (l.getBlockEntity(m) instanceof SignBlockEntity sign) {
                SignText t = sign.getFrontText()
                        .setMessage(0, Component.literal(l1))
                        .setMessage(1, Component.literal(l2))
                        .setMessage(2, Component.literal(l3))
                        .setMessage(3, Component.literal(l4))
                        .setColor(DyeColor.LIGHT_BLUE)
                        .setHasGlowingText(true);
                sign.setText(t, true);
                sign.setWaxed(true);
                sign.setChanged();
                BlockPos p = m.immutable();
                l.sendBlockUpdated(p, s, s, Block.UPDATE_CLIENTS);
            }
        }

        // ------------------------------------------------------------ Asociación de Cazadores (norte)

        void association() {
            int x1 = lot(0);
            int z1 = lot(-1);
            int x2 = x1 + 19;
            int z2 = z1 + 19;
            int h = 38;
            BlockState frame = st(Blocks.WHITE_CONCRETE);
            BlockState glass = st(Blocks.LIGHT_BLUE_STAINED_GLASS);
            BlockState floor = st(Blocks.SMOOTH_QUARTZ);
            // Fachada: marco blanco y cristal azul; cada 5 bloques una planta
            for (int y = G; y <= G + h; y++) {
                boolean band = (y - G) % 5 == 0;
                for (int x = x1; x <= x2; x++) {
                    for (int z = z1; z <= z2; z++) {
                        boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                        if (!wall) continue;
                        boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                        boolean pillar = (z == z1 || z == z2) ? (x - x1) % 4 == 0 : (z - z1) % 4 == 0;
                        set(x, y, z, band || corner || pillar || y == G + h ? frame : glass);
                    }
                }
                if (band && y > G) fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, floor);
            }
            fill(x1, G - 1, z1, x2, G - 1, z2, st(Blocks.POLISHED_DIORITE));
            fill(x1 + 1, G + h, z1 + 1, x2 - 1, G + h, z2 - 1, frame);
            // Corona con el emblema del Sistema
            walls(x1 + 6, G + h + 1, z1 + 6, x2 - 6, G + h + 3, z2 - 6, st(Blocks.CYAN_CONCRETE));
            fill(x1 + 9, G + h + 4, z1 + 9, x1 + 10, G + h + 6, z1 + 10, st(Blocks.SEA_LANTERN));
            // Vestíbulo: planta baja de 5 bloques, entrada al sur
            fill(x1 + 1, G, z1 + 1, x2 - 1, G + 4, z2 - 1, st(Blocks.AIR));
            fill(-2, G, z2, 1, G + 3, z2, st(Blocks.AIR));
            fill(-3, G + 4, z2 + 1, 2, G + 4, z2 + 2, st(Blocks.WHITE_CONCRETE));
            // Mostrador de recepción y tablón de misiones
            fill(-5, G, -29, 4, G, -29, st(Blocks.QUARTZ_BLOCK));
            fill(-5, G + 1, -29, 4, G + 1, -29, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            fill(-4, G + 1, z1 + 1, 3, G + 3, z1 + 1, st(Blocks.SPRUCE_PLANKS));
            sign(-2, G + 2, z1 + 2, Direction.SOUTH, "TABLÓN DE", "CONTRATOS", "Habla con", "Lucía");
            sign(1, G + 2, z1 + 2, Direction.SOUTH, "ASOCIACIÓN", "DE CAZADORES", "", "");
            fill(x1 + 2, G, z1 + 2, x1 + 2, G + 3, z1 + 2, st(Blocks.SEA_LANTERN));
            fill(x2 - 2, G, z1 + 2, x2 - 2, G + 3, z1 + 2, st(Blocks.SEA_LANTERN));
            // Alfombra azul de la entrada al mostrador
            fill(-1, G, -28, 0, G, z2 - 1, st(Blocks.BLUE_CARPET));
            sign(2, G + 2, z2 + 1, Direction.SOUTH, "ASOCIACIÓN", "DE CAZADORES", "Contratos y", "misiones");
        }

        // ------------------------------------------------------------ tiendas de la avenida del este

        /** Tienda: caja de dos plantas con escaparate hacia la calle (al sur si front=true, al norte si no). */
        void shop(int x1, int z1, int w, int d, boolean southFront, BlockState wall, BlockState trim, BlockState floor,
                  BlockState glass, String name1, String name2) {
            int x2 = x1 + w - 1;
            int z2 = z1 + d - 1;
            int h = 9;
            fill(x1, G - 1, z1, x2, G - 1, z2, floor);
            for (int y = G; y <= G + h; y++) {
                for (int x = x1; x <= x2; x++) {
                    for (int z = z1; z <= z2; z++) {
                        if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                        boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                        boolean band = y == G + 4 || y == G + h;
                        set(x, y, z, corner || band ? trim : wall);
                    }
                }
            }
            fill(x1 + 1, G + 4, z1 + 1, x2 - 1, G + 4, z2 - 1, floor);
            fill(x1, G + h, z1, x2, G + h, z2, trim);
            int fz = southFront ? z2 : z1;
            int out = southFront ? 1 : -1;
            // Escaparates y puerta
            fill(x1 + 1, G + 1, fz, x2 - 1, G + 3, fz, glass);
            int mid = x1 + w / 2;
            fill(mid - 1, G, fz, mid, G + 2, fz, st(Blocks.AIR));
            fill(x1 + 1, G + 5, fz, x2 - 1, G + 7, fz, glass);
            // Toldo y letrero
            BlockState awning = Blocks.DARK_OAK_SLAB.defaultBlockState();
            fill(x1, G + 4, fz + out, x2, G + 4, fz + out, awning);
            sign(mid - 1, G + 3, fz + out, southFront ? Direction.SOUTH : Direction.NORTH, name1, name2, "", "");
            // Luces dentro
            set(x1 + 1, G + 3, z1 + 1, st(Blocks.SEA_LANTERN));
            set(x2 - 1, G + 3, z1 + 1, st(Blocks.SEA_LANTERN));
            set(x1 + 1, G + 3, z2 - 1, st(Blocks.SEA_LANTERN));
            set(x2 - 1, G + 3, z2 - 1, st(Blocks.SEA_LANTERN));
        }

        void smithy() {
            int x1 = lot(1) + 2;
            int z1 = lot(-1) + 4;
            shop(x1, z1, 16, 14, true, st(Blocks.STONE_BRICKS), st(Blocks.DEEPSLATE_BRICKS), st(Blocks.POLISHED_DEEPSLATE),
                    st(Blocks.GLASS), "HERRERÍA", "de Bruno");
            // Yunques, forja y mesas
            set(x1 + 4, G, z1 + 6, st(Blocks.ANVIL));
            set(x1 + 9, G, z1 + 6, st(Blocks.SMITHING_TABLE));
            set(x1 + 2, G, z1 + 2, st(Blocks.BLAST_FURNACE));
            set(x1 + 3, G, z1 + 2, st(Blocks.BLAST_FURNACE));
            set(x1 + 12, G, z1 + 2, st(Blocks.GRINDSTONE));
            fill(x1 + 6, G, z1 + 1, x1 + 9, G, z1 + 1, st(Blocks.MAGMA_BLOCK));
            fill(x1 + 6, G + 1, z1 + 1, x1 + 9, G + 2, z1 + 1, st(Blocks.IRON_BARS));
            fill(x1 + 5, G, z1 + 5, x1 + 10, G, z1 + 5, st(Blocks.POLISHED_BLACKSTONE));
        }

        void alchemy() {
            int x1 = lot(2) + 2;
            int z1 = lot(-1) + 4;
            shop(x1, z1, 16, 14, true, st(Blocks.DARK_OAK_PLANKS), st(Blocks.STRIPPED_DARK_OAK_LOG), st(Blocks.MOSS_BLOCK),
                    st(Blocks.GREEN_STAINED_GLASS), "ALQUIMIA", "de Selene");
            set(x1 + 3, G, z1 + 4, st(Blocks.BREWING_STAND));
            set(x1 + 5, G, z1 + 4, st(Blocks.CAULDRON));
            fill(x1 + 1, G, z1 + 1, x1 + 14, G + 2, z1 + 1, st(Blocks.BOOKSHELF));
            set(x1 + 10, G, z1 + 4, st(Blocks.BREWING_STAND));
            fill(x1 + 6, G, z1 + 6, x1 + 10, G, z1 + 6, st(Blocks.SPRUCE_PLANKS));
            set(x1 + 12, G, z1 + 3, st(Blocks.FLOWERING_AZALEA));
        }

        void essenceShop() {
            int x1 = lot(1) + 2;
            int z1 = lot(1) + 2;
            shop(x1, z1, 16, 14, false, st(Blocks.PURPUR_BLOCK), st(Blocks.PURPUR_PILLAR), st(Blocks.POLISHED_BLACKSTONE),
                    st(Blocks.PURPLE_STAINED_GLASS), "ESENCIAS", "de Ezequiel");
            set(x1 + 3, G, z1 + 10, st(Blocks.AMETHYST_BLOCK));
            set(x1 + 3, G + 1, z1 + 10, st(Blocks.AMETHYST_CLUSTER));
            set(x1 + 12, G, z1 + 10, st(Blocks.AMETHYST_BLOCK));
            set(x1 + 12, G + 1, z1 + 10, st(Blocks.AMETHYST_CLUSTER));
            fill(x1 + 5, G, z1 + 8, x1 + 10, G, z1 + 8, st(Blocks.CRYING_OBSIDIAN));
            set(x1 + 7, G, z1 + 11, st(Blocks.ENCHANTING_TABLE));
        }

        void guildHall() {
            int x1 = lot(2) + 1;
            int z1 = lot(1) + 2;
            shop(x1, z1, 18, 16, false, st(Blocks.SPRUCE_PLANKS), st(Blocks.STRIPPED_SPRUCE_LOG), st(Blocks.SPRUCE_PLANKS),
                    st(Blocks.GLASS), "GREMIO DE", "CAZADORES");
            // Mesas largas y estandartes de color
            fill(x1 + 3, G, z1 + 5, x1 + 7, G, z1 + 5, Blocks.SPRUCE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            fill(x1 + 10, G, z1 + 5, x1 + 14, G, z1 + 5, Blocks.SPRUCE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            fill(x1 + 1, G + 2, z1 + 14, x1 + 16, G + 3, z1 + 14, st(Blocks.RED_WOOL));
            fill(x1 + 6, G, z1 + 12, x1 + 11, G, z1 + 12, st(Blocks.STRIPPED_SPRUCE_LOG));
            set(x1 + 2, G, z1 + 13, st(Blocks.BARREL));
            set(x1 + 15, G, z1 + 13, st(Blocks.BARREL));
        }

        // ------------------------------------------------------------ parques y portal

        void park(int x1, int z1) {
            for (int x = x1 + 1; x < x1 + 19; x++) {
                for (int z = z1 + 1; z < z1 + 19; z++) {
                    set(x, G - 1, z, st(Blocks.GRASS_BLOCK));
                }
            }
            // Caminos en cruz y estanque
            fill(x1 + 9, G - 1, z1 + 1, x1 + 10, G - 1, z1 + 18, st(Blocks.DIRT_PATH));
            fill(x1 + 1, G - 1, z1 + 9, x1 + 18, G - 1, z1 + 10, st(Blocks.DIRT_PATH));
            fill(x1 + 3, G - 1, z1 + 3, x1 + 6, G - 1, z1 + 6, st(Blocks.WATER));
            for (int[] t : new int[][]{{3, 13}, {14, 3}, {15, 14}, {6, 16}, {13, 8}}) {
                tree(x1 + t[0], G, z1 + t[1], 4 + rnd.nextInt(2));
            }
            for (int i = 0; i < 18; i++) {
                int x = x1 + 1 + rnd.nextInt(18);
                int z = z1 + 1 + rnd.nextInt(18);
                m.set(x, G, z);
                if (l.getBlockState(m).isAir() && l.getBlockState(m.below()).is(Blocks.GRASS_BLOCK)) {
                    set(x, G, z, rnd.nextBoolean() ? st(Blocks.POPPY) : st(Blocks.CORNFLOWER));
                }
            }
        }

        void returnPortal() {
            // Arco de la esquina suroeste de la plaza, mirando al norte: el hueco lleva de vuelta a casa
            BlockState frame = st(Blocks.POLISHED_BLACKSTONE_BRICKS);
            fill(-9, G, 8, -9, G + 3, 8, frame);
            fill(-5, G, 8, -5, G + 3, 8, frame);
            fill(-9, G + 4, 8, -5, G + 4, 8, st(Blocks.CRYING_OBSIDIAN));
            set(-7, G + 5, 8, st(Blocks.SEA_LANTERN));
            fill(-8, G - 1, 8, -6, G - 1, 8, st(Blocks.AMETHYST_BLOCK));
            sign(-9, G + 2, 7, Direction.NORTH, "PORTAL DE", "REGRESO", "Pasa por el", "arco");
        }

        // ------------------------------------------------------------ torres

        void tower(int lx, int lz, int seed) {
            RandomSource r = RandomSource.create(9000L + seed * 31L);
            int inset = 2 + r.nextInt(2);
            int x1 = lx + inset;
            int z1 = lz + inset;
            int x2 = lx + 19 - inset;
            int z2 = lz + 19 - inset;
            int h = 16 + r.nextInt(5) * 6 + (Math.abs(lx) + Math.abs(lz) < 40 ? 8 : 0);
            BlockState[][] styles = {
                    {st(Blocks.WHITE_CONCRETE), st(Blocks.LIGHT_BLUE_STAINED_GLASS)},
                    {st(Blocks.GRAY_CONCRETE), st(Blocks.GRAY_STAINED_GLASS)},
                    {st(Blocks.SMOOTH_SANDSTONE), st(Blocks.GLASS)},
                    {st(Blocks.DEEPSLATE_TILES), st(Blocks.CYAN_STAINED_GLASS)},
                    {st(Blocks.QUARTZ_BLOCK), st(Blocks.BLUE_STAINED_GLASS)},
                    {st(Blocks.BRICKS), st(Blocks.GLASS)}};
            BlockState[] s = styles[r.nextInt(styles.length)];
            BlockState frame = s[0];
            BlockState glass = s[1];
            int step = 3 + r.nextInt(2);
            for (int y = G; y <= G + h; y++) {
                boolean band = (y - G) % 4 == 0;
                for (int x = x1; x <= x2; x++) {
                    for (int z = z1; z <= z2; z++) {
                        if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                        boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                        boolean pillar = (x == x1 || x == x2) ? (z - z1) % step == 0 : (x - x1) % step == 0;
                        set(x, y, z, band || corner || pillar || y == G + h ? frame : glass);
                    }
                }
                if (band && y > G && y < G + h) {
                    fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, st(Blocks.SMOOTH_STONE));
                    // Algunas plantas con luz (de noche se ven las ventanas encendidas)
                    if (r.nextInt(3) == 0) {
                        set((x1 + x2) / 2, y, (z1 + z2) / 2, st(Blocks.SEA_LANTERN));
                        set(x1 + 2, y, z1 + 2, st(Blocks.SEA_LANTERN));
                        set(x2 - 2, y, z2 - 2, st(Blocks.SEA_LANTERN));
                    }
                }
            }
            fill(x1 + 1, G + h, z1 + 1, x2 - 1, G + h, z2 - 1, frame);
            // Azotea: barandilla, aire acondicionado y antena con luz roja
            walls(x1, G + h + 1, z1, x2, G + h + 1, z2, frame);
            set(x1 + 3, G + h + 1, z1 + 3, st(Blocks.IRON_BLOCK));
            set(x1 + 4, G + h + 1, z1 + 3, st(Blocks.IRON_BLOCK));
            int ax = (x1 + x2) / 2;
            int az = (z1 + z2) / 2;
            fill(ax, G + h + 1, az, ax, G + h + 4, az, st(Blocks.IRON_BARS));
            set(ax, G + h + 5, az, st(Blocks.REDSTONE_LAMP).setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
            set(ax, G + h + 4, az + 0, st(Blocks.REDSTONE_BLOCK));
            // Puerta en la planta baja hacia la calle más cercana
            int dx = (x1 + x2) / 2;
            fill(dx - 1, G, z2, dx, G + 2, z2, st(Blocks.AIR));
            fill(dx - 1, G, z1, dx, G + 2, z1, st(Blocks.AIR));
        }

        // ------------------------------------------------------------ farolas

        void lamps() {
            BlockState post = st(Blocks.POLISHED_BLACKSTONE_WALL);
            for (int k = -2; k <= 2; k++) {
                for (int t = -HALF; t <= HALF; t += 9) {
                    // A los dos lados de cada calle horizontal y vertical
                    int roadStart = k * CELL + 10;
                    for (int side : new int[]{roadStart - 1, roadStart + 6}) {
                        lamp(t, side, post);
                        lamp(side, t, post);
                    }
                }
            }
        }

        void lamp(int x, int z, BlockState post) {
            if (Math.abs(x) > HALF || Math.abs(z) > HALF) return;
            m.set(x, G - 1, z);
            BlockState below = l.getBlockState(m);
            if (!below.is(Blocks.SMOOTH_STONE) && !below.is(Blocks.GRASS_BLOCK)) return;
            for (int y = G; y <= G + 3; y++) {
                m.set(x, y, z);
                if (!l.getBlockState(m).isAir()) return;
            }
            fill(x, G, z, x, G + 2, z, post);
            set(x, G + 3, z, st(Blocks.SEA_LANTERN));
        }
    }
}
