package com.elsistema;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plano y obra de la Ciudad de los Cazadores (versión 2, El Sistema 1.9.0). Todo es diseño propio.
 * <p>
 * La ciudad es una cuadrícula de 7x7 manzanas de 21x21 bloques separadas por calles de 7 (con aceras, pasos de cebra,
 * coches aparcados, farolas y semáforos). En el centro está la plaza del Cristal del Sistema (con su haz de luz), al
 * norte la Torre de la Asociación (con ascensor al mirador), la Arena y la Estación de Puertas; por la avenida
 * comercial las tiendas, el Comedor y el Centro Médico; al oeste el Gremio, el jardín y tu apartamento; al sur el
 * paseo, el parque central, la estación y el río con sus puentes. Alrededor, un bosque.
 * <p>
 * La obra se hace por pasos (cada uno pequeño) para repartirla entre varios ticks del servidor sin congelarlo.
 */
final class CityBuilder {
    static final int G = City.G;
    static final int LOT = 21;
    static final int CELL = 28;
    static final int N = 3;
    /** Primera y última fila/columna de la cuadrícula (incluidas las calles de fuera). */
    static final int MIN = lot(-N) - 7;
    static final int MAX = lotEnd(N) + 7;
    /** El río va al sur de la ciudad. */
    static final int RIVER_Z1 = MAX + 7;
    static final int RIVER_Z2 = RIVER_Z1 + 13;
    /** Hasta dónde llega lo construido (el bosque de alrededor). */
    static final int OUT = 132;
    static final int SOUTH = RIVER_Z2 + 32;
    /** Las colinas que rodean la ciudad (un valle): hasta 56 bloques más allá del bosque. */
    static final int HILLS = 56;
    static final int HX = OUT + HILLS;
    static final int HZ1 = -OUT - HILLS;
    static final int HZ2 = SOUTH + HILLS;

    /** Qué hay en cada manzana (filas de norte a sur, columnas de oeste a este). */
    private static final String[] PLAN = {
            "RTTXTTR",
            "RUTATHT",
            "TMZCFLK",
            "TTPQSTT",
            "RROBE4T",
            "RYRPTRT",
            "RRTVTRR"};

    static int lot(int k) {
        return CELL * k - 10;
    }

    // ------------------------------------------------------------ sitios que City necesita saber (ascensor, arena, puerta)

    /** Esquina noroeste del lote de la Asociación (0, -1). */
    static final int AX0 = -10;
    static final int AZ0 = -38;
    /** Cabinas del ascensor: x/z de la esquina noroeste de su hueco de 2x2. */
    static final int LIFT_LOBBY_X1 = AX0 + 3;
    static final int LIFT_LOBBY_X2 = AX0 + 16;
    static final int LIFT_LOBBY_Z = AZ0 + 4;
    static final int LIFT_DECK_X = AX0 + 6;
    static final int LIFT_DECK_Z = AZ0 + 7;
    /** La Arena (lote 0, -2): centro del foso y la piedra imán de salida. */
    static final int ARENA_CX = 0;
    static final int ARENA_CZ = -56;
    static final int ARENA_PAD_X = ARENA_CX - 6;
    static final int ARENA_PAD_Z = ARENA_CZ + 6;
    /** La Estación de Puertas (lote 0, -3): dónde se abre la puerta (delante del anillo). */
    static final int GATE_X = 0;
    static final int GATE_Z = -94 + 7 + 1;

    static int lotEnd(int k) {
        return CELL * k + 10;
    }

    static char kind(int i, int j) {
        return PLAN[j + N].charAt(i + N);
    }

    final ServerLevel l;
    final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
    final List<Runnable> steps = new ArrayList<>();
    int next = 0;
    long count = 0;
    final List<String> missing = new ArrayList<>();

    CityBuilder(ServerLevel l) {
        this.l = l;
    }

    // ================================================================ bloques por su nombre

    private static final Map<String, BlockState> CACHE = new HashMap<>();

    /** Bloque de Minecraft por su nombre (si no existiera, aire y se apunta en el registro). */
    BlockState b(String id) {
        BlockState s = CACHE.get(id);
        if (s != null) return s;
        ResourceLocation rl = ResourceLocation.withDefaultNamespace(id);
        if (!BuiltInRegistries.BLOCK.containsKey(rl)) {
            if (!missing.contains(id)) {
                missing.add(id);
                ElSistema.LOGGER.error("[ciudad] no existe el bloque minecraft:{}", id);
            }
            return Blocks.AIR.defaultBlockState();
        }
        s = BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
        CACHE.put(id, s);
        return s;
    }

    static <T extends Comparable<T>> BlockState w(BlockState s, Property<T> p, T v) {
        return s.hasProperty(p) ? s.setValue(p, v) : s;
    }

    BlockState face(String id, Direction d) {
        BlockState s = b(id);
        // las muelas (y botones o palancas) van en el suelo, no colgadas de una pared
        s = w(s, BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR);
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && d.getAxis().isHorizontal()) {
            return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
        }
        return w(s, BlockStateProperties.FACING, d);
    }

    BlockState stairs(String id, Direction facing, boolean top) {
        return w(face(id, facing), BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    BlockState slab(String id, boolean top) {
        return w(b(id), BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    /** Cristal fino, barrotes o valla unidos a lo largo de un eje. */
    BlockState pane(String id, boolean alongX) {
        BlockState s = b(id);
        if (alongX) return w(w(s, BlockStateProperties.EAST, true), BlockStateProperties.WEST, true);
        return w(w(s, BlockStateProperties.NORTH, true), BlockStateProperties.SOUTH, true);
    }

    BlockState light(int level) {
        return w(b("light"), BlockStateProperties.LEVEL, Math.max(0, Math.min(15, level)));
    }

    BlockState leaves(String id) {
        return w(b(id), BlockStateProperties.PERSISTENT, true);
    }

    BlockState axis(String id, Direction.Axis a) {
        return w(b(id), BlockStateProperties.AXIS, a);
    }

    BlockState hanging(String id) {
        return w(b(id), BlockStateProperties.HANGING, true);
    }

    // ================================================================ herramientas de obra

    void set(int x, int y, int z, BlockState s) {
        m.set(x, y, z);
        l.setBlock(m, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        count++;
    }

    void set(int x, int y, int z, String id) {
        set(x, y, z, b(id));
    }

    BlockState get(int x, int y, int z) {
        m.set(x, y, z);
        return l.getBlockState(m);
    }

    boolean air(int x, int y, int z) {
        return get(x, y, z).isAir();
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

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String id) {
        fill(x1, y1, z1, x2, y2, z2, b(id));
    }

    /** Las cuatro paredes de una caja. */
    void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        fill(x1, y1, z1, x2, y2, z1, s);
        fill(x1, y1, z2, x2, y2, z2, s);
        fill(x1, y1, z1, x1, y2, z2, s);
        fill(x2, y1, z1, x2, y2, z2, s);
    }

    void walls(int x1, int y1, int z1, int x2, int y2, int z2, String id) {
        walls(x1, y1, z1, x2, y2, z2, b(id));
    }

    void door(int x, int y, int z, String id, Direction facing, boolean right) {
        BlockState d = w(face(id, facing), BlockStateProperties.DOOR_HINGE, right ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        set(x, y, z, w(d, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        set(x, y + 1, z, w(d, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** Cama: los pies en (x, y, z) y la cabeza hacia «facing». */
    void bed(int x, int y, int z, String id, Direction facing) {
        BlockState s = face(id, facing);
        set(x, y, z, w(s, BlockStateProperties.BED_PART, BedPart.FOOT));
        set(x + facing.getStepX(), y, z + facing.getStepZ(), w(s, BlockStateProperties.BED_PART, BedPart.HEAD));
    }

    void tall(int x, int y, int z, String id) {
        set(x, y, z, w(b(id), BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        set(x, y + 1, z, w(b(id), BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    void sign(int x, int y, int z, Direction facing, DyeColor color, String... lines) {
        BlockState s = face("dark_oak_wall_sign", facing);
        set(x, y, z, s);
        m.set(x, y, z);
        if (l.getBlockEntity(m) instanceof SignBlockEntity sign) {
            SignText t = sign.getFrontText();
            for (int i = 0; i < 4; i++) {
                t = t.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
            }
            t = t.setColor(color).setHasGlowingText(true);
            sign.setText(t, true);
            sign.setWaxed(true);
            sign.setChanged();
            l.sendBlockUpdated(m.immutable(), s, s, Block.UPDATE_CLIENTS);
        }
    }

    void sign(int x, int y, int z, Direction facing, String... lines) {
        sign(x, y, z, facing, DyeColor.LIGHT_BLUE, lines);
    }

    /** Letrero de pie (sobre un poste o el suelo) mirando hacia «facing». */
    void post(int x, int y, int z, Direction facing, DyeColor color, String... lines) {
        int rot = switch (facing) {
            case NORTH -> 8;
            case WEST -> 4;
            case EAST -> 12;
            default -> 0;
        };
        BlockState s = w(b("dark_oak_sign"), BlockStateProperties.ROTATION_16, rot);
        set(x, y, z, s);
        m.set(x, y, z);
        if (l.getBlockEntity(m) instanceof SignBlockEntity sign) {
            SignText t = sign.getFrontText();
            for (int i = 0; i < 4; i++) {
                t = t.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
            }
            t = t.setColor(color).setHasGlowingText(true);
            sign.setText(t, true);
            sign.setWaxed(true);
            sign.setChanged();
            l.sendBlockUpdated(m.immutable(), s, s, Block.UPDATE_CLIENTS);
        }
    }

    /** Muro (de piedra, ladrillo...) unido a lo largo de un eje. */
    BlockState wallLine(String id, boolean alongX) {
        BlockState s = b(id);
        s = w(s, BlockStateProperties.UP, false);
        if (alongX) {
            s = w(s, BlockStateProperties.EAST_WALL, net.minecraft.world.level.block.state.properties.WallSide.LOW);
            return w(s, BlockStateProperties.WEST_WALL, net.minecraft.world.level.block.state.properties.WallSide.LOW);
        }
        s = w(s, BlockStateProperties.NORTH_WALL, net.minecraft.world.level.block.state.properties.WallSide.LOW);
        return w(s, BlockStateProperties.SOUTH_WALL, net.minecraft.world.level.block.state.properties.WallSide.LOW);
    }

    /** Barandilla (barrotes, cristal fino o valla) alrededor de un rectángulo, bien unida en cada lado. */
    void rail(int x1, int y, int z1, int x2, int z2, String id) {
        for (int x = x1; x <= x2; x++) {
            set(x, y, z1, pane(id, true));
            set(x, y, z2, pane(id, true));
        }
        for (int z = z1 + 1; z < z2; z++) {
            set(x1, y, z, pane(id, false));
            set(x2, y, z, pane(id, false));
        }
        BlockState c = b(id);
        set(x1, y, z1, w(w(c, BlockStateProperties.EAST, true), BlockStateProperties.SOUTH, true));
        set(x2, y, z1, w(w(c, BlockStateProperties.WEST, true), BlockStateProperties.SOUTH, true));
        set(x1, y, z2, w(w(c, BlockStateProperties.EAST, true), BlockStateProperties.NORTH, true));
        set(x2, y, z2, w(w(c, BlockStateProperties.WEST, true), BlockStateProperties.NORTH, true));
    }

    static long hash(int x, int z) {
        long h = x * 341873128712L + z * 132897987541L;
        h ^= (h >>> 29);
        h *= 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 32);
    }

    static RandomSource rnd(int i, int j, int salt) {
        return RandomSource.create(hash(i * 31 + salt, j * 17 - salt) ^ 0x5157454D41L);
    }

    // ================================================================ el plan por pasos

    /** Prepara todos los pasos de la obra. */
    void plan() {
        int c1 = (-OUT) >> 4;
        int c2 = OUT >> 4;
        int cz2 = SOUTH >> 4;
        // 1) cargar los chunks de la ciudad y de las colinas (City les pone un ticket para que no se descarguen)
        for (int cx = (-HX) >> 4; cx <= HX >> 4; cx++) {
            final int fx = cx;
            steps.add(() -> {
                for (int cz = HZ1 >> 4; cz <= HZ2 >> 4; cz++) {
                    l.getChunk(fx, cz);
                }
            });
        }
        // 2) quitar lo que hubiera (la ciudad de la versión anterior)
        for (int cx = c1; cx <= c2; cx++) {
            for (int cz = c1; cz <= cz2; cz++) {
                final int fx = cx;
                final int fz = cz;
                steps.add(() -> clearChunk(fx, fz));
            }
        }
        // 3) suelo: calles, aceras, río y alrededores
        for (int x = -OUT; x <= OUT; x++) {
            final int fx = x;
            steps.add(() -> groundLine(fx));
        }
        // 4) manzanas
        for (int j = -N; j <= N; j++) {
            for (int i = -N; i <= N; i++) {
                final int fi = i;
                final int fj = j;
                addLot(fi, fj);
            }
        }
        // 5) el río, los puentes y la orilla sur
        steps.add(this::riverWalls);
        for (int k = -N - 1; k <= N; k++) {
            final int fk = k;
            steps.add(() -> bridge(CELL * fk + 14));
        }
        steps.add(this::riverPromenade);
        steps.add(this::southPark);
        // 6) el bosque de alrededor y las colinas del valle (con la garganta por la que sale el río)
        for (int a = 0; a < 8; a++) {
            final int fa = a;
            steps.add(() -> forest(fa));
        }
        for (int x = -HX; x <= HX; x++) {
            final int fx = x;
            steps.add(() -> hillLine(fx));
        }
        for (int a = 0; a < 6; a++) {
            final int fa = a;
            steps.add(() -> hillTrees(fa));
        }
        // 7) mobiliario urbano: paradas, farolas, semáforos y coches
        steps.add(this::busStops);
        for (int k = -N; k <= N; k++) {
            final int fk = k;
            steps.add(() -> streetLamps(fk));
        }
        for (int k = -N - 1; k <= N; k++) {
            final int fk = k;
            steps.add(() -> parkedCars(CELL * fk + 14, fk));
        }
        steps.add(this::trafficLights);
    }

    /** Ejecuta pasos durante como mucho «nanos». Devuelve true si ya está todo. */
    boolean run(long nanos) {
        long end = System.nanoTime() + nanos;
        while (next < steps.size()) {
            Runnable r = steps.get(next++);
            try {
                r.run();
            } catch (Exception e) {
                ElSistema.LOGGER.error("[ciudad] falló un paso de la obra ({}): {}", next, e.toString());
            }
            if (System.nanoTime() > end) break;
        }
        return next >= steps.size();
    }

    int percent() {
        return steps.isEmpty() ? 100 : Math.min(100, next * 100 / steps.size());
    }

    /** Hasta dónde se limpia hacia arriba (las salas de las mazmorras van mucho más arriba y no se tocan). */
    static final int CLEAR_TOP = G + 120;

    private void clearChunk(int cx, int cz) {
        LevelChunk c = l.getChunk(cx, cz);
        LevelChunkSection[] sections = c.getSections();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null || sections[i].hasOnlyAir()) continue;
            int y0 = (c.getMinSection() + i) << 4;
            if (y0 + 15 < G || y0 > CLEAR_TOP) continue;
            for (int y = Math.max(G, y0); y < Math.min(y0 + 16, CLEAR_TOP + 1); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int wx = (cx << 4) + x;
                        int wz = (cz << 4) + z;
                        if (!air(wx, y, wz)) set(wx, y, wz, air);
                    }
                }
            }
        }
    }

    // ================================================================ suelo

    private BlockState asphalt(int x, int z) {
        long h = hash(x, z);
        if ((h & 31) == 0) return b("gray_concrete_powder");
        if ((h & 255) == 7) return b("cyan_terracotta");
        return b("gray_concrete");
    }

    /** Suelo (y = G - 1) de un punto de la cuadrícula (calles, pasos de cebra y aceras; dentro de las manzanas, hierba). */
    BlockState groundAt(int x, int z) {
        int rx = Math.floorMod(x + 10, CELL);
        int rz = Math.floorMod(z + 10, CELL);
        boolean roadNS = rx >= LOT;
        boolean roadEW = rz >= LOT;
        if (roadNS || roadEW) {
            if (roadNS && roadEW) return asphalt(x, z);
            int across = roadNS ? rx - LOT : rz - LOT;
            int along = roadNS ? rz : rx;
            int coord = roadNS ? z : x;
            // paso de cebra junto a cada cruce (rayas a lo largo de la calle) y línea discontinua en el centro
            if (along >= 1 && along <= 3 || along >= LOT - 4 && along <= LOT - 2) {
                return across % 2 == 0 ? b("white_concrete") : asphalt(x, z);
            }
            if (across == 3 && Math.floorMod(coord, 6) < 3) return b("white_concrete");
            return asphalt(x, z);
        }
        boolean curb = rx == 0 || rx == LOT - 1 || rz == 0 || rz == LOT - 1;
        if (curb) return b("smooth_stone");
        boolean walk = rx == 1 || rx == LOT - 2 || rz == 1 || rz == LOT - 2;
        if (walk) return (Math.floorMod(x + z, 3) == 0) ? b("polished_andesite") : b("andesite");
        return b("grass_block");
    }

    private void groundLine(int x) {
        for (int z = -OUT; z <= SOUTH; z++) {
            BlockState s;
            boolean grid = x >= MIN && x <= MAX && z >= MIN && z <= MAX;
            if (grid) {
                s = groundAt(x, z);
            } else if (z > MAX && z <= SOUTH && x >= -OUT && x <= OUT) {
                s = riverGround(x, z);
                if (s == null) continue;
            } else {
                s = b("grass_block");
            }
            set(x, G - 1, z, s);
        }
    }

    /** El sur: paseo, río y orilla. Devuelve null si ya lo hace otro paso. */
    private BlockState riverGround(int x, int z) {
        if (z < RIVER_Z1) {
            if (z == RIVER_Z1 - 1) return b("stone_bricks");
            return (Math.floorMod(x, 5) == 0 || z == MAX + 1) ? b("polished_andesite") : b("smooth_stone");
        }
        if (z <= RIVER_Z2) {
            // el cauce: agua 4 de hondo y fondo de arena y grava
            for (int y = G - 5; y <= G - 2; y++) set(x, y, z, b("water"));
            set(x, G - 6, z, (hash(x, z) & 3) == 0 ? b("gravel") : b("sand"));
            set(x, G - 1, z, Blocks.AIR.defaultBlockState());
            return null;
        }
        if (z == RIVER_Z2 + 1) return b("stone_bricks");
        if (z <= RIVER_Z2 + 6) return (Math.floorMod(x, 5) == 0) ? b("polished_andesite") : b("smooth_stone");
        return b("grass_block");
    }

    // ================================================================ manzanas

    private void addLot(int i, int j) {
        char k = kind(i, j);
        int x0 = lot(i);
        int z0 = lot(j);
        switch (k) {
            case 'Q' -> {
                steps.add(() -> plazaGround(x0, z0));
                steps.add(this::crystal);
                steps.add(() -> plazaDecor(x0, z0));
            }
            case 'C' -> {
                steps.add(() -> associationLower(x0, z0));
                steps.add(() -> associationUpper(x0, z0));
                steps.add(() -> associationLobby(x0, z0));
            }
            case 'A' -> steps.add(() -> arena(x0, z0));
            case 'X' -> steps.add(() -> gateStation(x0, z0));
            case 'F' -> steps.add(() -> smithy(x0, z0));
            case 'L' -> steps.add(() -> alchemy(x0, z0));
            case 'K' -> steps.add(() -> restaurant(x0, z0));
            case 'M' -> steps.add(() -> hospital(x0, z0));
            case 'Z' -> steps.add(() -> garden(x0, z0));
            case 'U' -> steps.add(() -> guildHall(x0, z0));
            case 'S' -> steps.add(() -> mall(x0, z0));
            case 'O' -> {
                steps.add(() -> residence(x0, z0, i, j, true));
                steps.add(() -> apartment(x0, z0));
            }
            case 'B' -> steps.add(() -> boulevard(x0, z0));
            case 'E' -> steps.add(() -> essenceShop(x0, z0));
            case '4' -> steps.add(() -> store24(x0, z0));
            case 'P' -> steps.add(() -> park(x0, z0, i, j, j > 0));
            case 'Y' -> steps.add(() -> playground(x0, z0, i, j));
            case 'V' -> steps.add(() -> station(x0, z0));
            case 'H' -> steps.add(() -> tower(x0, z0, i, j, 2, "HOTEL MIRAMAR"));
            case 'R' -> steps.add(() -> residence(x0, z0, i, j, false));
            default -> steps.add(() -> tower(x0, z0, i, j, -1, null));
        }
    }

    // ------------------------------------------------------------ fachadas y plantas

    /**
     * Fachada genérica de un edificio. kind: 0 muro cortina (cristal con montantes), 1 oficinas (ventanas corridas),
     * 2 viviendas (ventanas sueltas), 3 torre oscura del Sistema (cristal azul y franjas de luz).
     */
    private BlockState facade(int kind, int u, int y, int y0, BlockState frame, BlockState glass, BlockState band, int step) {
        int fy = Math.floorMod(y - y0, 4);
        switch (kind) {
            case 1 -> {
                if (fy == 0 || fy == 1) return band;
                return Math.floorMod(u, step * 3) == 0 ? frame : glass;
            }
            case 2 -> {
                if (fy == 0) return band;
                int uu = Math.floorMod(u, 4);
                return (uu == 1 || uu == 2) && fy >= 1 && fy <= 2 ? glass : frame;
            }
            case 3 -> {
                if (fy == 0) return band;
                return Math.floorMod(u, step) == 0 ? frame : glass;
            }
            default -> {
                if (fy == 0) return band;
                return Math.floorMod(u, step) == 0 ? frame : glass;
            }
        }
    }

    /** Caja de edificio con fachada, plantas cada 4 y luces dentro (de noche se ven las ventanas encendidas). */
    private void shell(int x1, int z1, int x2, int z2, int y1, int y2, int kind, BlockState frame, BlockState glass,
                       BlockState band, int step, RandomSource r, boolean floors) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                set(x, y, z1, facade(kind, x - x1, y, y1, frame, glass, band, step));
                set(x, y, z2, facade(kind, x - x1, y, y1, frame, glass, band, step));
            }
            for (int z = z1 + 1; z < z2; z++) {
                set(x1, y, z, facade(kind, z - z1, y, y1, frame, glass, band, step));
                set(x2, y, z, facade(kind, z - z1, y, y1, frame, glass, band, step));
            }
            set(x1, y, z1, frame);
            set(x2, y, z1, frame);
            set(x1, y, z2, frame);
            set(x2, y, z2, frame);
        }
        if (!floors) return;
        BlockState floor = b("smooth_stone");
        for (int y = y1 + 4; y < y2; y += 4) {
            fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, floor);
            if (r.nextInt(10) < 6) {
                BlockState lamp = light(12 + r.nextInt(4));
                for (int x = x1 + 2; x < x2 - 1; x += 4) {
                    for (int z = z1 + 2; z < z2 - 1; z += 4) {
                        set(x, y + 3, z, lamp);
                    }
                }
            }
        }
    }

    private static final String[][] TOWER_STYLES = {
            {"white_concrete", "light_blue_stained_glass", "light_gray_concrete"},
            {"light_gray_concrete", "gray_stained_glass", "white_concrete"},
            {"smooth_quartz", "blue_stained_glass", "quartz_bricks"},
            {"black_concrete", "cyan_stained_glass", "gray_concrete"},
            {"polished_andesite", "light_gray_stained_glass", "smooth_stone"},
            {"white_terracotta", "light_blue_stained_glass", "smooth_sandstone"},
            {"deepslate_tiles", "blue_stained_glass", "polished_deepslate"},
            {"calcite", "cyan_stained_glass", "smooth_quartz"}};

    private static final String[] TOWER_NAMES = {"TORRE AURORA", "EDIFICIO MERIDIANO", "TORRE CELESTE", "CENTRO NEXO",
            "TORRE VÉRTICE", "EDIFICIO ATLAS", "TORRE ZAFIRO", "EDIFICIO HORIZONTE", "TORRE ÍNDIGO", "CENTRO PRISMA",
            "TORRE CÚSPIDE", "EDIFICIO CENIT", "TORRE ÓRBITA", "EDIFICIO FARO", "TORRE ESTELA", "EDIFICIO LUMEN"};

    /** Torre de oficinas o de cristal con su vestíbulo, retranqueo, azotea y antena. */
    private void tower(int x0, int z0, int i, int j, int forceStyle, String name) {
        RandomSource r = rnd(i, j, 1);
        int dist = Math.max(Math.abs(i), Math.abs(j));
        int h = 58 - dist * 9 + r.nextInt(5) * 4;
        int kind = r.nextInt(4);
        int si = forceStyle >= 0 ? forceStyle : r.nextInt(TOWER_STYLES.length);
        if (kind == 3) si = 6 + r.nextInt(2) == 6 ? 3 : 6;
        String[] st = TOWER_STYLES[si];
        BlockState frame = b(st[0]);
        BlockState glass = b(st[1]);
        BlockState band = b(st[2]);
        int step = 3 + r.nextInt(2);
        int inset = 2 + r.nextInt(2);
        int x1 = x0 + inset;
        int z1 = z0 + inset;
        int x2 = x0 + 20 - inset;
        int z2 = z0 + 20 - inset;
        // patio delantero de piedra y jardineras
        fill(x0 + 2, G - 1, z0 + 2, x0 + 18, G - 1, z0 + 18, b("polished_andesite"));
        int setback = h > 36 ? G + h * 2 / 3 : G + h + 1;
        shell(x1, z1, x2, z2, G, Math.min(setback - 1, G + h), kind, frame, glass, band, step, r, true);
        int top = G + h;
        int ox1 = x1;
        int oz1 = z1;
        int ox2 = x2;
        int oz2 = z2;
        if (setback <= G + h) {
            fill(x1 + 1, setback - 1, z1 + 1, x2 - 1, setback - 1, z2 - 1, band);
            shell(x1 + 2, z1 + 2, x2 - 2, z2 - 2, setback, top, kind, frame, glass, band, step, r, true);
            walls(x1, setback, z1, x2, setback, z2, band);
            x1 += 2;
            z1 += 2;
            x2 -= 2;
            z2 -= 2;
        }
        if (kind == 3) {
            // franjas de luz azul en las esquinas (las de abajo y, si hay retranqueo, las de arriba)
            for (int y = G + 1; y < top; y++) {
                if (Math.floorMod(y - G, 2) == 0 || y == setback || y == setback - 1) continue;
                boolean up = y > setback;
                set(up ? x1 : ox1, y, up ? z1 : oz1, b("sea_lantern"));
                set(up ? x2 : ox2, y, up ? z2 : oz2, b("sea_lantern"));
                set(up ? x1 : ox1, y, up ? z2 : oz2, b("sea_lantern"));
                set(up ? x2 : ox2, y, up ? z1 : oz1, b("sea_lantern"));
            }
        }
        fill(x1, top, z1, x2, top, z2, band);
        roof(x1, z1, x2, z2, top + 1, band, r, h > 48 && r.nextInt(3) == 0 ? 1 : r.nextInt(4) == 0 ? 2 : 0);
        lobbyFront(x0, z0, i, j, frame, r, name != null ? name : TOWER_NAMES[(int) Math.floorMod(hash(i, j), TOWER_NAMES.length)]);
    }

    /**
     * Azotea con pretil. kind: 0 máquinas, depósito y antena con luz roja; 1 helipuerto; 2 piscina con hamacas;
     * 3 jardín con árboles, flores y bancos.
     */
    private void roof(int x1, int z1, int x2, int z2, int y, BlockState band, RandomSource r, int kind) {
        walls(x1, y, z1, x2, y, z2, band);
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        if (kind == 2 && x2 - x1 >= 10) {
            // piscina de 7x5 con borde de cuarzo, luces bajo el agua, hamacas y sombrilla
            fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, slab("smooth_quartz_slab", false));
            for (int x = cx - 4; x <= cx + 4; x++) {
                for (int z = cz - 3; z <= cz + 3; z++) {
                    boolean edge = Math.abs(x - cx) == 4 || Math.abs(z - cz) == 3;
                    set(x, y, z, edge ? b("smooth_quartz") : b("water"));
                    if (!edge) set(x, y - 1, z, (Math.floorMod(x + z, 3) == 0) ? b("sea_lantern") : b("light_blue_concrete"));
                }
            }
            for (int x = cx - 3; x <= cx + 3; x += 2) {
                if (z2 - 1 > cz + 4) set(x, y + 1, cz + 5, stairs("quartz_stairs", Direction.SOUTH, false));
            }
            if (x2 - 2 > cx + 5) {
                set(x2 - 2, y + 1, z1 + 2, b("spruce_fence"));
                set(x2 - 2, y + 2, z1 + 2, b("spruce_fence"));
                fill(x2 - 3, y + 3, z1 + 1, x2 - 1, y + 3, z1 + 3, b("orange_wool"));
            }
            rail(x1, y + 1, z1, x2, z2, "glass_pane");
            return;
        }
        if (kind == 3) {
            // jardín: césped, caminos de tablas, arbolitos, flores y bancos
            fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, b("grass_block"));
            for (int x = x1 + 1; x < x2; x++) set(x, y, cz, b("spruce_planks"));
            for (int z = z1 + 1; z < z2; z++) set(cx, y, z, b("spruce_planks"));
            rail(x1, y + 1, z1, x2, z2, "spruce_fence");
            for (int[] c : new int[][]{{x1 + 3, z1 + 3}, {x2 - 3, z2 - 3}, {x1 + 3, z2 - 3}, {x2 - 3, z1 + 3}}) {
                if (r.nextBoolean()) {
                    tree(c[0], y + 1, c[1], 3, "oak_log", "azalea_leaves");
                } else {
                    set(c[0], y + 1, c[1], b("flowering_azalea"));
                }
            }
            String[] fl = {"poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "pink_tulip"};
            for (int k = 0; k < 14; k++) {
                int x = x1 + 1 + r.nextInt(Math.max(1, x2 - x1 - 1));
                int z = z1 + 1 + r.nextInt(Math.max(1, z2 - z1 - 1));
                if (x != cx && z != cz && air(x, y + 1, z)) set(x, y + 1, z, b(fl[r.nextInt(fl.length)]));
            }
            set(cx + 1, y + 1, cz + 2, stairs("spruce_stairs", Direction.EAST, false));
            set(cx - 1, y + 1, cz - 2, stairs("spruce_stairs", Direction.WEST, false));
            set(cx, y + 1, cz, b("lantern"));
            return;
        }
        if (kind == 1 && x2 - x1 >= 10) {
            fill(cx - 4, y, cz - 4, cx + 4, y, cz + 4, b("gray_concrete"));
            for (int d = -2; d <= 2; d++) {
                set(cx - 2, y, cz + d, b("white_concrete"));
                set(cx + 2, y, cz + d, b("white_concrete"));
            }
            set(cx - 1, y, cz, b("white_concrete"));
            set(cx, y, cz, b("white_concrete"));
            set(cx + 1, y, cz, b("white_concrete"));
            for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
                set(cx + c[0], y, cz + c[1], b("verdant_froglight"));
            }
            return;
        }
        // máquinas del aire acondicionado
        for (int k = 0; k < 3; k++) {
            int ax = x1 + 2 + r.nextInt(Math.max(1, x2 - x1 - 4));
            int az = z1 + 2 + r.nextInt(Math.max(1, z2 - z1 - 4));
            set(ax, y, az, b("light_gray_concrete"));
            set(ax, y + 1, az, slab("smooth_stone_slab", false));
        }
        // depósito de agua
        if (r.nextBoolean()) {
            int tx = x1 + 2;
            int tz = z2 - 3;
            fill(tx, y, tz, tx + 1, y + 2, tz + 1, b("spruce_planks"));
            fill(tx, y + 3, tz, tx + 1, y + 3, tz + 1, slab("spruce_slab", false));
        }
        // antena con luz roja
        fill(cx, y, cz, cx, y + 5, cz, b("iron_bars"));
        set(cx, y + 6, cz, b("lightning_rod"));
        set(cx, y + 5, cz, w(b("redstone_lamp"), BlockStateProperties.LIT, true));
        set(cx, y + 4, cz, b("redstone_block"));
    }

    /** Planta baja a la calle: puerta de cristal, marquesina, nombre, jardineras y faroles. */
    private void lobbyFront(int x0, int z0, int i, int j, BlockState frame, RandomSource r, String name) {
        // la fachada principal mira a la calle más cercana a la plaza
        Direction out = Math.abs(j) >= Math.abs(i) ? (j > 0 ? Direction.NORTH : Direction.SOUTH) : (i > 0 ? Direction.WEST : Direction.EAST);
        if (i == 0 && j == 0) out = Direction.SOUTH;
        int cx = x0 + 10;
        int cz = z0 + 10;
        int fx = cx + out.getStepX() * 10;
        int fz = cz + out.getStepZ() * 10;
        // busca la fachada (primer bloque que no es aire desde la acera hacia dentro)
        int dx = -out.getStepX();
        int dz = -out.getStepZ();
        int wx = fx;
        int wz = fz;
        for (int k = 0; k < 8 && air(wx, G + 1, wz); k++) {
            wx += dx;
            wz += dz;
        }
        Direction side = out.getClockWise();
        // puerta de 3 de ancho y 3 de alto
        for (int s = -1; s <= 1; s++) {
            int px = wx + side.getStepX() * s;
            int pz = wz + side.getStepZ() * s;
            fill(px, G, pz, px, G + 2, pz, Blocks.AIR.defaultBlockState());
            set(px, G - 1, pz, b("polished_diorite"));
        }
        // marquesina
        for (int s = -3; s <= 3; s++) {
            int px = wx + side.getStepX() * s + out.getStepX();
            int pz = wz + side.getStepZ() * s + out.getStepZ();
            set(px, G + 3, pz, slab("smooth_quartz_slab", true));
        }
        // letrero con el nombre junto a la puerta, mirando a la calle
        sign(wx + side.getStepX() * 2 + out.getStepX(), G + 2, wz + side.getStepZ() * 2 + out.getStepZ(), out, DyeColor.WHITE, "", name, "", "");
        // luz del vestíbulo
        set(wx + dx * 2, G + 2, wz + dz * 2, light(15));
        set(wx + dx * 6, G + 2, wz + dz * 6, light(14));
        // vestíbulo amueblado: mostrador con plantas y dos sofás a los lados
        for (int s = -1; s <= 1; s++) {
            int px = wx + dx * 5 + side.getStepX() * s;
            int pz = wz + dz * 5 + side.getStepZ() * s;
            if (air(px, G, pz)) {
                set(px, G, pz, b("quartz_bricks"));
                set(px, G + 1, pz, slab("smooth_quartz_slab", false));
            }
        }
        for (int s : new int[]{-2, 2}) {
            int px = wx + dx * 5 + side.getStepX() * s;
            int pz = wz + dz * 5 + side.getStepZ() * s;
            if (air(px, G, pz)) set(px, G, pz, b(s < 0 ? "potted_bamboo" : "potted_fern"));
        }
        for (int s : new int[]{-5, 5}) {
            Direction back = s > 0 ? side : side.getOpposite();
            for (int k = 1; k <= 3; k++) {
                int px = wx + dx * k + side.getStepX() * s;
                int pz = wz + dz * k + side.getStepZ() * s;
                if (air(px, G, pz) && air(px, G + 1, pz)) set(px, G, pz, stairs("dark_oak_stairs", back, false));
            }
        }
        // jardineras a los lados
        for (int s : new int[]{-5, 5}) {
            int px = wx + side.getStepX() * s + out.getStepX() * 2;
            int pz = wz + side.getStepZ() * s + out.getStepZ() * 2;
            if (air(px, G, pz) && air(px, G + 1, pz)) {
                set(px, G, pz, b("potted_azalea_bush"));
            }
        }
    }

    /** Edificio de viviendas: ladrillo o terracota, balcones, ventanas encendidas y depósito en la azotea. */
    private void residence(int x0, int z0, int i, int j, boolean home) {
        RandomSource r = rnd(i, j, 2);
        String[][] styles = {
                {"bricks", "glass", "white_concrete"},
                {"terracotta", "glass", "smooth_sandstone"},
                {"light_gray_terracotta", "light_blue_stained_glass", "white_terracotta"},
                {"mud_bricks", "glass", "packed_mud"},
                {"white_terracotta", "glass", "light_blue_terracotta"},
                {"tuff_bricks", "glass", "polished_tuff"}};
        String[] st = home ? styles[0] : styles[r.nextInt(styles.length)];
        BlockState wall = b(st[0]);
        BlockState glass = b(st[1]);
        BlockState band = b(st[2]);
        int floors = home ? 6 : 5 + r.nextInt(6);
        int x1 = x0 + 2;
        int z1 = z0 + 2;
        int x2 = x0 + 18;
        int z2 = z0 + 18;
        int top = G + floors * 4;
        fill(x0 + 2, G - 1, z0 + 2, x0 + 18, G - 1, z0 + 18, b("polished_andesite"));
        shell(x1, z1, x2, z2, G, top, 2, wall, glass, band, 4, r, true);
        fill(x1, top, z1, x2, top, z2, band);
        // balcones en las fachadas norte y sur
        for (int y = G + 4; y < top; y += 4) {
            for (int x = x1 + 1; x < x2; x++) {
                int uu = Math.floorMod(x - x1, 4);
                if (uu != 1 && uu != 2) continue;
                if (r.nextInt(3) != 0) continue;
                for (int[] zz : new int[][]{{z1 - 1, 0}, {z2 + 1, 1}}) {
                    set(x, y, zz[0], slab("smooth_stone_slab", true));
                    set(x, y + 1, zz[0], pane("iron_bars", true));
                }
            }
        }
        roof(x1, z1, x2, z2, top + 1, band, r, r.nextInt(3) == 0 ? 3 : 0);
        if (!home) {
            lobbyFront(x0, z0, i, j, wall, r, "RESIDENCIAL " + (char) ('A' + Math.floorMod(i + 3 + (j + 3) * 7, 26)));
        }
    }

    // ================================================================ la plaza del Cristal

    private void plazaGround(int x0, int z0) {
        for (int x = x0; x <= x0 + 20; x++) {
            for (int z = z0; z <= z0 + 20; z++) {
                double r = Math.sqrt(x * x + z * z);
                BlockState s;
                if (r < 2.6) s = b("polished_blackstone_bricks");
                else if (r < 5.3) s = null;
                else if (r < 6.3) s = b("smooth_quartz");
                else if (r < 7.0) s = b("cyan_terracotta");
                else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) s = b("quartz_bricks");
                else if (Math.abs(Math.abs(x) - Math.abs(z)) <= 0) s = b("light_blue_terracotta");
                else s = ((int) Math.floor(r) % 2 == 0) ? b("polished_andesite") : b("smooth_stone");
                if (s == null) {
                    // la fuente: agua de un bloque sobre prismarina
                    set(x, G - 2, z, b("dark_prismarine"));
                    set(x, G - 1, z, b("water"));
                    continue;
                }
                set(x, G - 1, z, s);
                if (r >= 5.3 && r < 6.3) set(x, G, z, slab("smooth_quartz_slab", false));
            }
        }
    }

    /**
     * El Cristal del Sistema: un pedestal de cuarzo con el faro dentro y, flotando encima, un gran cristal en forma de
     * octaedro (cian y azul claro, iluminado por dentro). El haz azul del faro lo atraviesa y se ve desde toda la ciudad.
     */
    private void crystal() {
        fill(-1, G - 1, -1, 1, G - 1, 1, b("iron_block"));
        // pedestal de 3x3 y 2 de alto con el faro en el centro
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                boolean corner = dx != 0 && dz != 0;
                set(dx, G, dz, b(corner ? "chiseled_quartz_block" : "quartz_bricks"));
                set(dx, G + 1, dz, corner ? axis("quartz_pillar", Direction.Axis.Y) : b("cyan_stained_glass"));
                set(dx, G + 2, dz, slab("smooth_quartz_slab", false));
            }
        }
        set(0, G, 0, b("beacon"));
        set(0, G + 1, 0, b("light_blue_stained_glass"));
        set(0, G + 2, 0, b("light_blue_stained_glass"));
        // escalones de cuarzo alrededor del pedestal
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2 && dx * dx + dz * dz <= 5) set(dx, G, dz, slab("smooth_quartz_slab", false));
            }
        }
        // el cristal flotante: octaedro de G+4 a G+16 (radio 3 en el centro)
        int cy = G + 10;
        for (int y = G + 4; y <= G + 16; y++) {
            int r = 3 - Math.abs(y - cy) / 2;
            if (r < 0) continue;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d = Math.abs(dx) + Math.abs(dz);
                    if (d > r) continue;
                    String id = dx == 0 && dz == 0 ? "light_blue_stained_glass" : d == r ? "cyan_stained_glass" : "light_blue_stained_glass";
                    set(dx, y, dz, b(id));
                }
            }
        }
        // luz dentro del cristal (de noche brilla) y la columna del haz hasta arriba
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) set(c[0], cy, c[1], light(15));
        set(0, G + 3, 0, b("light_blue_stained_glass"));
        // chispas alrededor
        for (int[] c : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            set(c[0], G + 6, c[1], face("end_rod", Direction.UP));
        }
        // luz para la plaza de noche
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) set(c[0], G + 3, c[1], light(14));
        sign(0, G + 1, -2, Direction.NORTH, DyeColor.CYAN, "CRISTAL DEL", "SISTEMA", "", "");
        sign(0, G + 1, 2, Direction.SOUTH, DyeColor.CYAN, "CRISTAL DEL", "SISTEMA", "", "");
    }

    private void plazaDecor(int x0, int z0) {
        // cerezos en las esquinas
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    set(c[0] + dx, G, c[1] + dz, slab("smooth_stone_slab", false));
                }
            }
            set(c[0], G - 1, c[1], b("grass_block"));
            cherry(c[0], G, c[1], 5);
        }
        // bancos mirando a la fuente
        for (int s : new int[]{-4, -3, 3, 4}) {
            set(s, G, -8, stairs("dark_oak_stairs", Direction.NORTH, false));
            set(s, G, 8, stairs("dark_oak_stairs", Direction.SOUTH, false));
            set(8, G, s, stairs("dark_oak_stairs", Direction.EAST, false));
            set(-8, G, s, stairs("dark_oak_stairs", Direction.WEST, false));
        }
        // farolas de la plaza
        for (int[] c : new int[][]{{-9, -5}, {9, -5}, {-5, -9}, {5, -9}, {9, 5}, {6, 9}, {-6, 9}, {-9, 5}}) {
            lampPost(c[0], c[1], true);
        }
        // Portal de regreso (lado oeste de la plaza): arco con el paso entre z -2 y 2
        BlockState frame = b("polished_blackstone_bricks");
        fill(-10, G, -3, -9, G + 4, -3, frame);
        fill(-10, G, 3, -9, G + 4, 3, frame);
        fill(-10, G + 5, -3, -9, G + 5, 3, b("crying_obsidian"));
        fill(-10, G + 6, -1, -9, G + 6, 1, b("amethyst_block"));
        set(-10, G + 7, 0, face("end_rod", Direction.UP));
        set(-9, G + 7, 0, face("end_rod", Direction.UP));
        fill(-10, G - 1, -2, -9, G - 1, 2, b("amethyst_block"));
        fill(-10, G, -2, -9, G + 4, 2, Blocks.AIR.defaultBlockState());
        set(-9, G + 4, -2, light(12));
        set(-9, G + 4, 2, light(12));
        sign(-8, G + 4, -3, Direction.EAST, DyeColor.PURPLE, "PORTAL DE", "REGRESO", "Crúzalo para", "volver a casa");
        sign(-8, G + 4, 3, Direction.EAST, DyeColor.PURPLE, "PORTAL DE", "REGRESO", "Crúzalo para", "volver a casa");
        // tablón de bienvenida junto a la salida
        fill(2, G, 10, 4, G + 1, 10, b("spruce_planks"));
        set(2, G + 2, 10, slab("spruce_slab", false));
        set(3, G + 2, 10, slab("spruce_slab", false));
        set(4, G + 2, 10, slab("spruce_slab", false));
        sign(3, G + 1, 9, Direction.NORTH, DyeColor.CYAN, "BIENVENIDO A LA", "CIUDAD DE LOS", "CAZADORES", "");
        sign(2, G, 9, Direction.NORTH, DyeColor.WHITE, "V > Misiones >", "Ciudad: IR a", "cada sitio", "");
        sign(4, G, 9, Direction.NORTH, DyeColor.WHITE, "/spawn: vuelve", "a esta plaza", "", "");
        sign(2, G + 1, 9, Direction.NORTH, DyeColor.WHITE, "", "Portal de", "regreso: al", "oeste ←");
        fill(-4, G, 10, -2, G + 1, 10, b("spruce_planks"));
        set(-2, G + 2, 10, slab("spruce_slab", false));
        set(-3, G + 2, 10, slab("spruce_slab", false));
        set(-4, G + 2, 10, slab("spruce_slab", false));
        sign(-3, G + 1, 9, Direction.NORTH, DyeColor.CYAN, "AL NORTE", "Asociación,", "Arena y", "Puertas");
        sign(-2, G, 9, Direction.NORTH, DyeColor.WHITE, "AL ESTE", "Herrería,", "Alquimia y", "Comedor");
        sign(-4, G, 9, Direction.NORTH, DyeColor.WHITE, "AL OESTE", "Gremio y", "Centro Médico", "");
        sign(-4, G + 1, 9, Direction.NORTH, DyeColor.WHITE, "AL SUR", "Tu apartamento,", "parque y río", "");
        sign(4, G + 1, 9, Direction.NORTH, DyeColor.WHITE, "", "Tienda 24 h y", "Esencias:", "sureste");
        // máquinas expendedoras
        vending(9, -2, Direction.WEST);
        vending(9, 2, Direction.WEST);
    }

    private void vending(int x, int z, Direction facing) {
        set(x, G, z, b("white_concrete"));
        set(x, G + 1, z, b("light_blue_stained_glass"));
        set(x, G + 2, z, b("sea_lantern"));
    }

    // ================================================================ árboles, farolas y bancos

    void tree(int x, int y, int z, int h, String log, String leaf) {
        BlockState lv = leaves(leaf);
        for (int dy = 0; dy < h; dy++) set(x, y + dy, z, b(log));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = h - 2; dy <= h + 1; dy++) {
                    int r = Math.abs(dx) + Math.abs(dz) + Math.max(0, dy - h);
                    if (r > 3 || (dx == 0 && dz == 0 && dy < h)) continue;
                    if (air(x + dx, y + dy, z + dz)) set(x + dx, y + dy, z + dz, lv);
                }
            }
        }
    }

    /** Cerezo: tronco con ramas y una copa rosa ancha. */
    void cherry(int x, int y, int z, int h) {
        BlockState lv = leaves("cherry_leaves");
        for (int dy = 0; dy < h; dy++) set(x, y + dy, z, b("cherry_log"));
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = h - 2; dy <= h + 1; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz) + Math.max(0, dy - h + 1) * 1.2;
                    if (d > 3.3 || (dx == 0 && dz == 0 && dy < h)) continue;
                    if ((hash(x + dx * 7, z + dz * 13 + dy) & 7) == 0 && d > 2.5) continue;
                    if (air(x + dx, y + dy, z + dz)) set(x + dx, y + dy, z + dz, lv);
                }
            }
        }
    }

    /** Farola: poste negro y luz cálida (o azul en la plaza). */
    void lampPost(int x, int z, boolean blue) {
        for (int y = G; y <= G + 3; y++) {
            if (!air(x, y, z)) return;
        }
        fill(x, G, z, x, G + 2, z, b("polished_blackstone_wall"));
        set(x, G + 3, z, b(blue ? "sea_lantern" : "ochre_froglight"));
        set(x, G + 4, z, slab("polished_blackstone_slab", false));
    }

    void bench(int x, int z, Direction back, int len, boolean alongX) {
        for (int k = 0; k < len; k++) {
            int bx = alongX ? x + k : x;
            int bz = alongX ? z : z + k;
            if (air(bx, G, bz)) set(bx, G, bz, stairs("spruce_stairs", back, false));
        }
    }

    // ================================================================ calles: farolas, semáforos, paradas y coches

    /**
     * Farolas en los bordillos de las manzanas de la fila j: una por lado y al tresbolillo (así cada calle tiene una a
     * cada lado cada 14 bloques), nunca en las esquinas ni delante de una puerta (están en el centro).
     */
    private void streetLamps(int j) {
        for (int i = -N; i <= N; i++) {
            int x0 = lot(i);
            int z0 = lot(j);
            lampAt(x0 + 6, z0);
            lampAt(x0 + 14, z0 + 20);
            lampAt(x0, z0 + 14);
            lampAt(x0 + 20, z0 + 6);
        }
    }

    private void lampAt(int x, int z) {
        BlockState below = get(x, G - 1, z);
        if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.WATER) || below.isAir()) return;
        lampPost(x, z, false);
    }

    /** Semáforos en dos esquinas de cada cruce. */
    private void trafficLights() {
        for (int a = -N - 1; a <= N; a++) {
            for (int c = -N - 1; c <= N; c++) {
                int ix = CELL * a + 14;
                int iz = CELL * c + 14;
                for (int[] k : new int[][]{{-4, -4}, {4, 4}}) {
                    int x = ix + k[0];
                    int z = iz + k[1];
                    if (x < MIN || x > MAX || z < MIN || z > MAX) continue;
                    if (!air(x, G, z) || !air(x, G + 4, z)) continue;
                    fill(x, G, z, x, G + 1, z, b("polished_blackstone_wall"));
                    set(x, G + 2, z, b("verdant_froglight"));
                    set(x, G + 3, z, b("black_concrete"));
                    set(x, G + 4, z, w(b("redstone_lamp"), BlockStateProperties.LIT, true));
                    set(x, G + 5, z, slab("polished_blackstone_slab", false));
                }
            }
        }
    }

    /** Paradas de autobús por toda la ciudad (sólo donde la acera está libre). */
    private void busStops() {
        String[] lines = {"Línea 1", "Línea 3", "Línea 7", "Nocturno"};
        int n = 0;
        for (int k = -N; k <= N; k++) {
            for (int j = -N; j <= N; j++) {
                if ((k + j) % 2 != 0) continue;
                int x = lot(k) + 2; // cerca de la esquina: nunca delante de una puerta (están en el centro)
                if (busStop(x, lot(j), true, lines[Math.floorMod(k + j, 4)])) n++;
                if (busStop(x, lotEnd(j), false, lines[Math.floorMod(k - j, 4)])) n++;
            }
        }
        ElSistema.LOGGER.info("[ciudad] {} paradas de autobús", n);
    }

    /** Marquesina de 5 de largo: el banco en el bordillo (z) mirando a la calle y el cristal detrás (en la acera). */
    private boolean busStop(int x, int z, boolean north, String line) {
        int back = north ? z + 1 : z - 1;
        for (int dx = 0; dx <= 4; dx++) {
            for (int y = G; y <= G + 3; y++) {
                if (!air(x + dx, y, z) || !air(x + dx, y, back)) return false;
            }
        }
        Direction face = north ? Direction.NORTH : Direction.SOUTH;
        for (int dx = 1; dx <= 3; dx++) {
            for (int y = G; y <= G + 2; y++) set(x + dx, y, back, pane("light_blue_stained_glass_pane", true));
        }
        for (int dx = 0; dx <= 4; dx++) {
            set(x + dx, G + 3, z, slab("smooth_quartz_slab", true));
            set(x + dx, G + 3, back, slab("smooth_quartz_slab", true));
        }
        fill(x + 1, G, z, x + 3, G, z, stairs("dark_oak_stairs", face.getOpposite(), false));
        for (int dx : new int[]{0, 4}) {
            fill(x + dx, G, back, x + dx, G + 2, back, b("polished_blackstone_wall"));
        }
        set(x + 4, G + 1, z, b("polished_blackstone_wall"));
        set(x + 4, G + 2, z, b("yellow_concrete"));
        set(x + 2, G + 2, z, light(13));
        post(x, G, z, face, DyeColor.YELLOW, "PARADA", line, "Plaza · Río", "Estación");
        return true;
    }

    /** Coches aparcados en los dos lados de una calle norte-sur (x = c) y este-oeste (z = c). */
    private void parkedCars(int c, int k) {
        RandomSource r = RandomSource.create(hash(c, k) ^ 77L);
        for (int t = MIN + 2; t <= MAX - 6; t++) {
            int rt = Math.floorMod(t + 10, CELL);
            // sólo junto a las manzanas, lejos de los cruces y los pasos de cebra
            if (rt < 4 || rt > LOT - 8) continue;
            if (r.nextInt(14) != 0) continue;
            boolean east = r.nextBoolean();
            if (r.nextBoolean()) car(east ? c + 2 : c - 3, t, false, r);
            else car(t, east ? c + 2 : c - 3, true, r);
            t += 6;
        }
    }

    private static final String[] CAR_COLORS = {"red", "white", "black", "gray", "light_gray", "blue", "yellow", "cyan", "white"};

    /** Coche de 4x2: cuerpo de hormigón, ruedas negras, cabina de cristal oscuro, capó y maletero. */
    void car(int x, int z, boolean alongX, RandomSource r) {
        int lx = alongX ? 4 : 2;
        int lz = alongX ? 2 : 4;
        for (int dx = 0; dx < lx; dx++) {
            for (int dz = 0; dz < lz; dz++) {
                if (!air(x + dx, G, z + dz) || !air(x + dx, G + 1, z + dz)) return;
                BlockState g = get(x + dx, G - 1, z + dz);
                if (!g.is(Blocks.GRAY_CONCRETE) && !g.is(Blocks.GRAY_CONCRETE_POWDER) && !g.is(Blocks.LIGHT_GRAY_CONCRETE)
                        && !g.is(Blocks.CYAN_TERRACOTTA)) return;
            }
        }
        String col = CAR_COLORS[r.nextInt(CAR_COLORS.length)];
        boolean taxi = col.equals("yellow");
        for (int dx = 0; dx < lx; dx++) {
            for (int dz = 0; dz < lz; dz++) {
                int along = alongX ? dx : dz;
                boolean end = along == 0 || along == 3;
                set(x + dx, G, z + dz, b(end ? "black_concrete" : col + "_concrete"));
                if (end) set(x + dx, G + 1, z + dz, slab(col.equals("light_gray") ? "smooth_stone_slab" : slabOf(col), false));
                else set(x + dx, G + 1, z + dz, b(taxi ? "yellow_stained_glass" : "black_stained_glass"));
            }
        }
    }

    private static String slabOf(String col) {
        return switch (col) {
            case "red" -> "red_nether_brick_slab";
            case "white" -> "smooth_quartz_slab";
            case "black" -> "polished_blackstone_slab";
            case "gray" -> "polished_andesite_slab";
            case "blue" -> "prismarine_brick_slab";
            case "yellow" -> "smooth_sandstone_slab";
            case "cyan" -> "prismarine_slab";
            default -> "smooth_stone_slab";
        };
    }

    // ================================================================ el río

    private void riverWalls() {
        for (int x = -OUT; x <= OUT; x++) {
            for (int z : new int[]{RIVER_Z1 - 1, RIVER_Z2 + 1}) {
                fill(x, G - 6, z, x, G - 1, z, b("stone_bricks"));
                set(x, G, z, pane("iron_bars", true));
            }
        }
    }

    /** Puente sobre el río para la calle norte-sur de la x dada. */
    private void bridge(int c) {
        if (c - 3 < -OUT || c + 3 > OUT) return;
        // la calle cruza el paseo hasta el puente
        for (int z = MAX + 1; z < RIVER_Z1 - 1; z++) {
            for (int x = c - 3; x <= c + 3; x++) {
                set(x, G - 1, z, (x == c && Math.floorMod(z, 6) < 3) ? b("white_concrete") : b("gray_concrete"));
                set(x, G, z, Blocks.AIR.defaultBlockState());
            }
        }
        for (int z = RIVER_Z1 - 1; z <= RIVER_Z2 + 1; z++) {
            for (int x = c - 3; x <= c + 3; x++) {
                set(x, G - 1, z, (x == c && Math.floorMod(z, 6) < 3) ? b("white_concrete") : b("gray_concrete"));
                set(x, G, z, Blocks.AIR.defaultBlockState());
            }
            set(c - 4, G - 1, z, b("stone_bricks"));
            set(c + 4, G - 1, z, b("stone_bricks"));
            set(c - 4, G, z, wallLine("stone_brick_wall", false));
            set(c + 4, G, z, wallLine("stone_brick_wall", false));
            // el arco de piedra bajo el tablero
            set(c - 4, G - 2, z, slab("stone_brick_slab", true));
            set(c + 4, G - 2, z, slab("stone_brick_slab", true));
            if (Math.floorMod(z - RIVER_Z1, 7) == 3) {
                set(c - 4, G, z, b("stone_bricks"));
                set(c + 4, G, z, b("stone_bricks"));
                set(c - 4, G + 1, z, b("polished_blackstone_wall"));
                set(c - 4, G + 2, z, b("polished_blackstone_wall"));
                set(c - 4, G + 3, z, b("ochre_froglight"));
                set(c + 4, G + 1, z, b("polished_blackstone_wall"));
                set(c + 4, G + 2, z, b("polished_blackstone_wall"));
                set(c + 4, G + 3, z, b("ochre_froglight"));
            }
        }
        // pilar en medio del río
        int mz = (RIVER_Z1 + RIVER_Z2) / 2;
        fill(c - 2, G - 6, mz - 1, c + 2, G - 2, mz + 1, b("stone_bricks"));
        // la calle sigue hasta la orilla sur
        for (int z = RIVER_Z2 + 2; z <= RIVER_Z2 + 6; z++) {
            for (int x = c - 3; x <= c + 3; x++) set(x, G - 1, z, b("gray_concrete"));
        }
    }

    private void riverPromenade() {
        for (int x = -OUT + 2; x <= OUT - 2; x++) {
            int rx = Math.floorMod(x + 10, CELL);
            boolean road = rx >= LOT;
            if (road) continue;
            if (Math.floorMod(x, 9) == 4) {
                lampPost(x, RIVER_Z1 - 2, false);
                lampPost(x, RIVER_Z2 + 2, false);
            } else if (Math.floorMod(x, 9) == 0) {
                set(x, G, MAX + 2, stairs("spruce_stairs", Direction.NORTH, false));
                set(x + 1, G, MAX + 2, stairs("spruce_stairs", Direction.NORTH, false));
            } else if (Math.floorMod(x, 9) == 7) {
                set(x, G - 1, MAX + 3, b("grass_block"));
                if (air(x, G, MAX + 3)) tree(x, G, MAX + 3, 4, "birch_log", "birch_leaves");
            }
        }
        // nenúfares
        for (int x = -OUT + 4; x <= OUT - 4; x += 3) {
            long h = hash(x, 99);
            if ((h & 7) != 0) continue;
            int z = RIVER_Z1 + 1 + (int) Math.floorMod(h >> 8, 3);
            if (get(x, G - 2, z).is(Blocks.WATER) && air(x, G - 1, z)) set(x, G - 1, z, b("lily_pad"));
        }
    }

    /** Parque de la orilla sur: paseo con bancos y farolas, caminos desde los puentes, quiosco, cerezos y flores. */
    private void southPark() {
        int z1 = RIVER_Z2 + 7;
        // paseo de la orilla (de este a oeste) y caminos que siguen a los puentes
        for (int x = -OUT + 4; x <= OUT - 4; x++) {
            for (int z = z1; z <= z1 + 2; z++) set(x, G - 1, z, (Math.floorMod(x + z, 7) == 0) ? b("mossy_cobblestone") : b("dirt_path"));
        }
        for (int k = -N - 1; k <= N; k++) {
            int c = CELL * k + 14;
            for (int z = z1 + 3; z <= SOUTH - 2; z++) {
                for (int x = c - 1; x <= c + 1; x++) set(x, G - 1, z, b("dirt_path"));
            }
        }
        for (int x = -OUT + 8; x <= OUT - 8; x += 12) {
            int rx = Math.floorMod(x + 10, CELL);
            if (rx >= LOT - 1) continue;
            lampPost(x, z1 + 3, false);
            set(x + 2, G, z1 + 3, stairs("spruce_stairs", Direction.SOUTH, false));
            set(x + 3, G, z1 + 3, stairs("spruce_stairs", Direction.SOUTH, false));
        }
        // quiosco de la música en el centro
        int qz = z1 + 14;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz <= 18) set(dx, G - 1, qz + dz, b("smooth_stone"));
            }
        }
        fill(-3, G, qz - 3, 3, G, qz + 3, slab("smooth_quartz_slab", false));
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            fill(c[0], G + 1, qz + c[1], c[0], G + 3, qz + c[1], b("white_concrete"));
        }
        for (int k = 0; k <= 4; k++) {
            for (int dx = -4 + k; dx <= 4 - k; dx++) {
                for (int dz = -4 + k; dz <= 4 - k; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == 4 - k) set(dx, G + 4 + k, qz + dz, b(k % 2 == 0 ? "cyan_terracotta" : "white_terracotta"));
                }
            }
        }
        set(0, G + 3, qz, hanging("lantern"));
        fill(0, G + 4, qz, 0, G + 7, qz, b("chain"));
        post(0, G, qz + 5, Direction.SOUTH, DyeColor.WHITE, "QUIOSCO DEL", "PARQUE DEL RÍO", "Conciertos los", "domingos");
        // árboles y macizos de flores
        for (int k = 0; k < 140; k++) {
            long h = hash(k * 37, 5011);
            int x = -OUT + 6 + (int) Math.floorMod(h, 2 * OUT - 12);
            int z = z1 + 4 + (int) Math.floorMod(h >> 12, SOUTH - z1 - 6);
            if (!get(x, G - 1, z).is(Blocks.GRASS_BLOCK) || !air(x, G, z)) continue;
            if (Math.abs(x) <= 6 && Math.abs(z - qz) <= 6) continue;
            int t = (int) ((h >> 20) & 3);
            if (t == 0) cherry(x, G, z, 4 + (int) ((h >> 24) & 1));
            else if (t == 1) tree(x, G, z, 5, "birch_log", "birch_leaves");
            else flowers(x, z, h);
        }
        // mirador sobre el agua
        int cx = 0;
        fill(cx - 3, G - 1, RIVER_Z2 + 2, cx + 3, G - 1, RIVER_Z2 + 4, b("spruce_planks"));
        rail(cx - 3, G, RIVER_Z2 + 2, cx + 3, RIVER_Z2 + 4, "spruce_fence");
        fill(cx - 1, G, RIVER_Z2 + 4, cx + 1, G, RIVER_Z2 + 4, Blocks.AIR.defaultBlockState());
        post(cx + 2, G, RIVER_Z2 + 5, Direction.SOUTH, DyeColor.WHITE, "PASEO DEL RÍO", "Ciudad de los", "Cazadores", "");
    }

    private void flowers(int x, int z, long h) {
        String[] fl = {"poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "azure_bluet", "lily_of_the_valley", "red_tulip"};
        for (int k = 0; k < 6; k++) {
            int fx = x + (int) ((h >> (k * 3)) & 3) - 1;
            int fz = z + (int) ((h >> (k * 3 + 2)) & 3) - 1;
            if (get(fx, G - 1, fz).is(Blocks.GRASS_BLOCK) && air(fx, G, fz)) set(fx, G, fz, b(fl[(int) Math.floorMod(h >> k, fl.length)]));
        }
    }

    // ================================================================ las colinas del valle

    /** Ruido suave (0..1) a partir de una rejilla de valores al azar. */
    private static double noise(int x, int z, int scale, int salt) {
        double fx = (double) x / scale;
        double fz = (double) z / scale;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = (hash(ix * 7 + salt, iz * 13) & 0xFFFF) / 65535.0;
        double b2 = (hash(ix * 7 + 7 + salt, iz * 13) & 0xFFFF) / 65535.0;
        double c = (hash(ix * 7 + salt, iz * 13 + 13) & 0xFFFF) / 65535.0;
        double d = (hash(ix * 7 + 7 + salt, iz * 13 + 13) & 0xFFFF) / 65535.0;
        return (a + (b2 - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz;
    }

    /** Altura de las colinas (en bloques sobre el suelo) en una columna: 0 dentro de la ciudad y del bosque. */
    static double hillHeight(int x, int z) {
        double dx = Math.max(0, Math.abs(x) - OUT);
        double dz = z < -OUT ? -OUT - z : z > SOUTH ? z - SOUTH : 0;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= 0) return 0;
        double t = Math.min(1.0, d / 44.0);
        double ease = t * t * (3 - 2 * t);
        double n = noise(x, z, 26, 11) * 0.65 + noise(x, z, 9, 23) * 0.25 + 0.1;
        double h = ease * (12 + 30 * n);
        // la garganta del río: las colinas se abren donde sale el agua
        if (Math.abs(x) > OUT - 4) {
            double rz = z < RIVER_Z1 - 3 ? RIVER_Z1 - 3 - z : z > RIVER_Z2 + 3 ? z - RIVER_Z2 - 3 : 0;
            double g = Math.min(1.0, rz / 14.0);
            h *= g * g * (3 - 2 * g);
        }
        return h;
    }

    private int hillTop(int x, int z) {
        return G - 1 + (int) Math.round(hillHeight(x, z));
    }

    private void hillLine(int x) {
        for (int z = HZ1; z <= HZ2; z++) {
            boolean inside = Math.abs(x) <= OUT && z >= -OUT && z <= SOUTH;
            if (inside) continue;
            // el río sigue por la garganta hasta perderse entre las colinas
            if (z >= RIVER_Z1 && z <= RIVER_Z2 && Math.abs(x) > OUT) {
                for (int y = G - 5; y <= G - 2; y++) set(x, y, z, b("water"));
                set(x, G - 6, z, (hash(x, z) & 3) == 0 ? b("gravel") : b("sand"));
                set(x, G - 1, z, Blocks.AIR.defaultBlockState());
                continue;
            }
            int top = hillTop(x, z);
            if (top < G) continue;
            int low = Math.min(Math.min(hillTop(x + 1, z), hillTop(x - 1, z)), Math.min(hillTop(x, z + 1), hillTop(x, z - 1)));
            int from = Math.max(G, Math.min(top, low) - 1);
            boolean cliff = top - low >= 3;
            double h = top - G + 1;
            for (int y = from; y <= top; y++) {
                BlockState st;
                if (y == top) {
                    st = h > 34 ? b("snow_block") : cliff ? ((hash(x, z) & 1) == 0 ? b("stone") : b("andesite"))
                            : (hash(x * 3, z) & 15) == 0 ? b("coarse_dirt") : b("grass_block");
                } else if (cliff || y < top - 3) {
                    st = (hash(x, y * 7 + z) & 3) == 0 ? b("andesite") : b("stone");
                } else {
                    st = b("dirt");
                }
                set(x, y, z, st);
            }
            if (!cliff && h < 30 && (hash(x * 11, z * 5) & 63) == 0) {
                set(x, top + 1, z, b((hash(x, z * 3) & 1) == 0 ? "short_grass" : "fern"));
            }
        }
    }

    /** Árboles en las laderas (abetos arriba, robles y abedules abajo). */
    private void hillTrees(int part) {
        for (int k = 0; k < 260; k++) {
            long h = hash(part * 977 + k, 4243);
            int x = -HX + (int) Math.floorMod(h, 2 * HX + 1);
            int z = HZ1 + (int) Math.floorMod(h >> 16, HZ2 - HZ1 + 1);
            if (Math.abs(x) <= OUT + 2 && z >= -OUT - 2 && z <= SOUTH + 2) continue;
            int top = hillTop(x, z);
            if (top < G || !get(x, top, z).is(Blocks.GRASS_BLOCK) || !air(x, top + 1, z)) continue;
            int hh = top - G + 1;
            if (hh > 22) tree(x, top + 1, z, 7 + (int) ((h >> 40) & 3), "spruce_log", "spruce_leaves");
            else if (((h >> 33) & 3) == 0) tree(x, top + 1, z, 6, "birch_log", "birch_leaves");
            else tree(x, top + 1, z, 5 + (int) ((h >> 36) & 1), "oak_log", "oak_leaves");
        }
    }

    // ================================================================ el bosque de alrededor

    private void forest(int part) {
        for (int k = 0; k < 120; k++) {
            long h = hash(part * 1000 + k, 7331);
            int x = -OUT + (int) Math.floorMod(h, 2 * OUT + 1);
            int z = -OUT + (int) Math.floorMod(h >> 16, OUT + RIVER_Z1 + 1);
            boolean inside = x > MIN - 4 && x < MAX + 4 && z > MIN - 4;
            if (inside) continue;
            if (!get(x, G - 1, z).is(Blocks.GRASS_BLOCK) || !air(x, G, z)) continue;
            int t = (int) ((h >> 32) & 7);
            if (t < 3) tree(x, G, z, 5 + (t & 1), "oak_log", "oak_leaves");
            else if (t < 5) tree(x, G, z, 6, "birch_log", "birch_leaves");
            else if (t < 6) tree(x, G, z, 7, "spruce_log", "spruce_leaves");
            else if (t < 7) cherry(x, G, z, 5);
            else flowers(x, z, h);
        }
    }

    // ================================================================ Torre de la Asociación de Cazadores

    /** Huecos de la torre: planta baja de 8 de alto, retranqueos a G+48 y G+70, mirador en G+72. */
    static final int ASSOC_TOP = G + 86;
    static final int DECK_Y = G + 73;

    private void associationLower(int x0, int z0) {
        RandomSource r = rnd(0, -1, 9);
        int x1 = x0 + 1;
        int x2 = x0 + 19;
        int z1 = z0 + 2;
        int z2 = z0 + 18;
        fill(x0, G - 1, z0, x0 + 20, G - 1, z0 + 20, b("polished_diorite"));
        BlockState frame = b("white_concrete");
        BlockState glass = b("light_blue_stained_glass");
        BlockState band = b("smooth_quartz");
        // planta baja (vestíbulo) de cristal
        for (int y = G; y <= G + 7; y++) {
            for (int x = x1; x <= x2; x++) {
                boolean col = Math.floorMod(x - x1, 3) == 0;
                set(x, y, z2, col || y == G + 7 ? frame : glass);
                set(x, y, z1, col || y == G + 7 ? frame : glass);
            }
            for (int z = z1; z <= z2; z++) {
                boolean col = Math.floorMod(z - z1, 3) == 0;
                set(x1, y, z, col || y == G + 7 ? frame : glass);
                set(x2, y, z, col || y == G + 7 ? frame : glass);
            }
        }
        fill(x1 + 1, G + 8, z1 + 1, x2 - 1, G + 8, z2 - 1, band);
        // torre hasta el primer retranqueo
        shell(x1, z1, x2, z2, G + 8, G + 47, 0, frame, glass, band, 3, r, true);
        // aletas azules en las esquinas
        for (int y = G; y <= G + 47; y++) {
            set(x1, y, z1, b("cyan_concrete"));
            set(x2, y, z1, b("cyan_concrete"));
            set(x1, y, z2, b("cyan_concrete"));
            set(x2, y, z2, b("cyan_concrete"));
        }
    }

    private void associationUpper(int x0, int z0) {
        RandomSource r = rnd(0, -1, 10);
        BlockState frame = b("white_concrete");
        BlockState glass = b("light_blue_stained_glass");
        BlockState band = b("smooth_quartz");
        int x1 = x0 + 3;
        int x2 = x0 + 17;
        int z1 = z0 + 4;
        int z2 = z0 + 16;
        fill(x0 + 1, G + 48, z0 + 2, x0 + 19, G + 48, z0 + 18, band);
        shell(x1, z1, x2, z2, G + 48, G + 69, 0, frame, glass, band, 3, r, true);
        for (int y = G + 48; y <= G + 69; y++) {
            set(x1, y, z1, b("cyan_concrete"));
            set(x2, y, z1, b("cyan_concrete"));
            set(x1, y, z2, b("cyan_concrete"));
            set(x2, y, z2, b("cyan_concrete"));
        }
        // emblema en la fachada sur: escudo con «!» de luz
        int ez = z2 + 1;
        int ex = x0 + 10;
        for (int y = G + 56; y <= G + 66; y++) {
            for (int x = ex - 4; x <= ex + 4; x++) {
                boolean edge = Math.abs(x - ex) == 4 || y == G + 66 || (y < G + 59 && Math.abs(x - ex) >= 5 - (y - G - 55));
                if (y < G + 58 && Math.abs(x - ex) > 1 + (y - G - 56)) continue;
                set(x, y, ez, edge ? b("cyan_concrete") : b("blue_concrete"));
            }
        }
        for (int y = G + 61; y <= G + 64; y++) set(ex, y, ez, b("sea_lantern"));
        set(ex, G + 59, ez, b("sea_lantern"));
        // mirador al aire libre: suelo, barandilla de cristal de 2 de alto (no se puede saltar), columnas y corona
        int dx1 = x0 + 5;
        int dx2 = x0 + 15;
        int dz1 = z0 + 6;
        int dz2 = z0 + 14;
        fill(x1, G + 70, z1, x2, G + 70, z2, band);
        fill(dx1, G + 71, dz1, dx2, G + 71, dz2, frame);
        fill(dx1 + 1, G + 72, dz1 + 1, dx2 - 1, G + 72, dz2 - 1, b("polished_diorite"));
        fill(dx1 + 1, G + 73, dz1 + 1, dx2 - 1, G + 79, dz2 - 1, Blocks.AIR.defaultBlockState());
        walls(dx1, G + 72, dz1, dx2, G + 72, dz2, frame);
        rail(dx1, G + 73, dz1, dx2, dz2, "glass_pane");
        rail(dx1, G + 74, dz1, dx2, dz2, "glass_pane");
        for (int x = dx1 + 2; x < dx2 - 1; x += 3) {
            for (int z = dz1 + 2; z < dz2 - 1; z += 3) set(x, G + 72, z, b("sea_lantern"));
        }
        // columnas en las esquinas y en el centro de cada lado
        int mx = (dx1 + dx2) / 2;
        int mz = (dz1 + dz2) / 2;
        for (int[] c : new int[][]{{dx1, dz1}, {dx2, dz1}, {dx1, dz2}, {dx2, dz2}, {dx1, mz}, {dx2, mz}}) {
            fill(c[0], G + 73, c[1], c[0], G + 79, c[1], axis("quartz_pillar", Direction.Axis.Y));
        }
        // techo: anillo de cuarzo con luces y un lucernario de cristal
        walls(dx1, G + 80, dz1, dx2, G + 80, dz2, band);
        fill(dx1 + 1, G + 80, dz1 + 1, dx2 - 1, G + 80, dz2 - 1, glass);
        for (int x = dx1 + 1; x < dx2; x += 2) {
            set(x, G + 79, dz1, b("sea_lantern"));
            set(x, G + 79, dz2, b("sea_lantern"));
        }
        // telescopios y un cartel
        for (int[] c : new int[][]{{dx2 - 2, dz2 - 1}, {dx1 + 4, dz2 - 1}, {dx2 - 1, mz + 2}}) {
            set(c[0], G + 73, c[1], b("polished_blackstone_wall"));
            set(c[0], G + 74, c[1], b("lightning_rod"));
        }
        set(dx2 - 1, G + 73, dz1 + 1, b("potted_flowering_azalea_bush"));
        sign(mx, G + 74, dz1 + 1, Direction.SOUTH, DyeColor.CYAN, "MIRADOR DE LA", "ASOCIACIÓN", "73 bloques", "de altura");
        // ascensor del mirador (baja al vestíbulo)
        elevatorCabin(dx1 + 1, G + 73, dz1 + 1, false);
        // corona y aguja
        for (int y = G + 81; y <= G + 86; y++) {
            int in = (y - G - 81) / 2;
            walls(dx1 + in, y, dz1 + in, dx2 - in, y, dz2 - in, (y & 1) == 0 ? b("sea_lantern") : glass);
        }
        fill(x0 + 10, G + 87, z0 + 10, x0 + 10, G + 98, z0 + 10, b("iron_bars"));
        set(x0 + 10, G + 99, z0 + 10, face("end_rod", Direction.UP));
        set(x0 + 10, G + 92, z0 + 10, b("sea_lantern"));
    }

    private void bench2(int x, int y, int z, Direction back, int len) {
        for (int k = 0; k < len; k++) set(x + k, y, z, stairs("quartz_stairs", back, false));
    }

    /** Cabina de ascensor de 2x2 (paredes de hierro al oeste, norte y este; abierta al sur) con su letrero dentro. */
    private void elevatorCabin(int x, int y, int z, boolean up) {
        fill(x, y - 1, z, x + 1, y - 1, z + 1, b("pearlescent_froglight"));
        fill(x - 1, y, z - 1, x - 1, y + 2, z + 1, b("iron_block"));
        fill(x + 2, y, z - 1, x + 2, y + 2, z + 1, b("iron_block"));
        fill(x - 1, y, z - 1, x + 2, y + 2, z - 1, b("iron_block"));
        fill(x - 1, y + 3, z - 1, x + 2, y + 3, z + 1, b("iron_block"));
        fill(x, y, z, x + 1, y + 2, z + 1, Blocks.AIR.defaultBlockState());
        set(x - 1, y + 3, z + 2, slab("smooth_quartz_slab", true));
        set(x + 2, y + 3, z + 2, slab("smooth_quartz_slab", true));
        set(x, y + 3, z + 2, b("sea_lantern"));
        set(x + 1, y + 3, z + 2, b("sea_lantern"));
        sign(x, y + 1, z, Direction.SOUTH, DyeColor.WHITE, "ASCENSOR", up ? "Sube al" : "Baja al", up ? "MIRADOR" : "VESTÍBULO",
                "Quédate dentro");
        sign(x + 1, y + 1, z, Direction.SOUTH, DyeColor.CYAN, "", up ? "▲" : "▼", "", "");
    }

    private void associationLobby(int x0, int z0) {
        int x1 = x0 + 2;
        int x2 = x0 + 18;
        int z1 = z0 + 3;
        int z2 = z0 + 17;
        // suelo con dibujo y alfombra hasta el mostrador
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean border = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, G - 1, z, border ? b("cyan_terracotta") : (Math.floorMod(x + z, 2) == 0 ? b("polished_diorite") : b("smooth_quartz")));
            }
        }
        int cx = x0 + 10;
        fill(cx - 1, G, z0 + 9, cx + 1, G, z2, b("light_blue_carpet"));
        // entrada: puerta de cristal abierta, marquesina y el nombre encima
        fill(cx - 2, G, z0 + 18, cx + 2, G + 3, z0 + 18, Blocks.AIR.defaultBlockState());
        fill(cx - 4, G + 4, z0 + 19, cx + 4, G + 4, z0 + 20, slab("smooth_quartz_slab", true));
        set(cx - 3, G + 3, z0 + 19, light(15));
        set(cx + 3, G + 3, z0 + 19, light(15));
        sign(cx, G + 5, z0 + 19, Direction.SOUTH, DyeColor.CYAN, "ASOCIACIÓN", "DE CAZADORES", "Contratos, rangos", "y mirador");
        sign(cx - 2, G + 5, z0 + 19, Direction.SOUTH, DyeColor.WHITE, "", "RECEPCIÓN", "Lucía", "");
        sign(cx + 2, G + 5, z0 + 19, Direction.SOUTH, DyeColor.WHITE, "", "ASCENSOR AL", "MIRADOR", "");
        // mostrador de recepción (Lucía detrás)
        fill(cx - 4, G, z0 + 8, cx + 4, G, z0 + 8, b("quartz_bricks"));
        fill(cx - 4, G + 1, z0 + 8, cx + 4, G + 1, z0 + 8, slab("smooth_quartz_slab", false));
        set(cx - 4, G + 2, z0 + 8, b("potted_flowering_azalea_bush"));
        set(cx + 4, G + 2, z0 + 8, b("potted_fern"));
        // tablón de contratos y emblema en la pared del fondo
        fill(cx - 4, G + 1, z1, cx + 4, G + 3, z1, b("spruce_planks"));
        sign(cx - 2, G + 2, z1 + 1, Direction.SOUTH, DyeColor.WHITE, "TABLÓN DE", "CONTRATOS", "Habla con", "Lucía");
        sign(cx + 2, G + 2, z1 + 1, Direction.SOUTH, DyeColor.WHITE, "RANGOS", "E · D · C", "B · A · S", "");
        for (int y = G + 4; y <= G + 6; y++) {
            for (int x = cx - 1; x <= cx + 1; x++) set(x, y, z1, b("cyan_concrete"));
        }
        set(cx, G + 5, z1, b("sea_lantern"));
        set(cx, G + 6, z1, b("sea_lantern"));
        // tablero de rangos en la pared este
        String[] ranks = {"white_wool", "lime_wool", "cyan_wool", "purple_wool", "orange_wool", "red_wool"};
        for (int k = 0; k < 6; k++) {
            set(x2, G + 2, z0 + 7 + k * 2, b(ranks[k]));
            sign(x2 - 1, G + 2, z0 + 7 + k * 2, Direction.WEST, DyeColor.WHITE, "RANGO " + "EDCBAS".charAt(k),
                    new String[]{"Despertado", "Novato", "Profesional", "Veterano", "Élite", "Nacional"}[k], "", "");
        }
        // sala de espera al oeste
        for (int z = z0 + 10; z <= z0 + 14; z += 2) {
            set(x1 + 1, G, z, stairs("quartz_stairs", Direction.WEST, false));
            set(x1 + 1, G, z + 1, stairs("quartz_stairs", Direction.WEST, false));
        }
        set(x1 + 1, G, z0 + 16, b("potted_azalea_bush"));
        set(x1 + 1, G, z0 + 9, b("potted_bamboo"));
        // pantalla
        fill(x1, G + 2, z0 + 10, x1, G + 4, z0 + 14, b("black_concrete"));
        fill(x1 + 1, G + 3, z0 + 11, x1 + 1, G + 3, z0 + 13, b("light_blue_stained_glass"));
        // luces del techo (en el suelo de la planta de arriba)
        for (int x = x1 + 1; x < x2; x += 4) {
            for (int z = z1 + 1; z < z2; z += 4) set(x, G + 8, z, b("sea_lantern"));
        }
        // ascensores al mirador (esquinas del fondo)
        elevatorCabin(x1 + 1, G, z1 + 1, true);
        elevatorCabin(x2 - 2, G, z1 + 1, true);
        // banderas en la entrada
        for (int x : new int[]{x0 + 3, x0 + 17}) {
            fill(x, G, z0 + 20, x, G + 3, z0 + 20, b("polished_blackstone_wall"));
            set(x, G + 4, z0 + 20, w(b("light_blue_banner"), BlockStateProperties.ROTATION_16, 0));
        }
    }

    // ================================================================ la Arena (centro de entrenamiento)

    static final int ARENA_FLOOR = G - 5;

    private void arena(int x0, int z0) {
        int cx = x0 + 10;
        int cz = z0 + 10;
        fill(x0, G - 1, z0, x0 + 20, G - 1, z0 + 20, b("polished_andesite"));
        // foso de 15x15 y 5 de hondo
        int x1 = cx - 7;
        int x2 = cx + 7;
        int z1 = cz - 7;
        int z2 = cz + 7;
        fill(x1 - 1, ARENA_FLOOR - 1, z1 - 1, x2 + 1, G - 1, z2 + 1, b("deepslate_tiles"));
        fill(x1, ARENA_FLOOR, z1, x2, G - 1, z2, Blocks.AIR.defaultBlockState());
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                double r = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                set(x, ARENA_FLOOR - 1, z, Math.abs(r - 5.5) < 0.5 ? b("cyan_terracotta") : (r < 1.5 ? b("chiseled_stone_bricks") : b("packed_mud")));
            }
        }
        // borde y barandilla
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            set(x, G - 1, z1 - 1, b("polished_blackstone"));
            set(x, G - 1, z2 + 1, b("polished_blackstone"));
            set(x, G, z1 - 1, pane("iron_bars", true));
            set(x, G, z2 + 1, pane("iron_bars", true));
        }
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            set(x1 - 1, G - 1, z, b("polished_blackstone"));
            set(x2 + 1, G - 1, z, b("polished_blackstone"));
            set(x1 - 1, G, z, pane("iron_bars", false));
            set(x2 + 1, G, z, pane("iron_bars", false));
        }
        // luces en las paredes del foso
        for (int k = -6; k <= 6; k += 4) {
            set(cx + k, ARENA_FLOOR + 3, z1 - 1, b("sea_lantern"));
            set(cx + k, ARENA_FLOOR + 3, z2 + 1, b("sea_lantern"));
            set(x1 - 1, ARENA_FLOOR + 3, cz + k, b("sea_lantern"));
            set(x2 + 1, ARENA_FLOOR + 3, cz + k, b("sea_lantern"));
        }
        // gradas al norte, este y oeste
        for (int x = x0; x <= x0 + 20; x++) {
            set(x, G, z0 + 1, stairs("stone_brick_stairs", Direction.NORTH, false));
            set(x, G, z0, b("stone_bricks"));
            set(x, G + 1, z0, stairs("stone_brick_stairs", Direction.NORTH, false));
        }
        for (int z = z0 + 2; z <= z0 + 17; z++) {
            set(x0 + 1, G, z, stairs("stone_brick_stairs", Direction.WEST, false));
            set(x0, G, z, b("stone_bricks"));
            set(x0, G + 1, z, stairs("stone_brick_stairs", Direction.WEST, false));
            set(x0 + 19, G, z, stairs("stone_brick_stairs", Direction.EAST, false));
            set(x0 + 20, G, z, b("stone_bricks"));
            set(x0 + 20, G + 1, z, stairs("stone_brick_stairs", Direction.EAST, false));
        }
        // banderas y luces en las esquinas
        for (int[] c : new int[][]{{x0, z0}, {x0 + 20, z0}, {x0, z0 + 20}, {x0 + 20, z0 + 20}}) {
            fill(c[0], G + 2, c[1], c[0], G + 5, c[1], b("polished_blackstone_wall"));
            set(c[0], G + 6, c[1], b("ochre_froglight"));
        }
        // salida del foso: piedra imán en la esquina suroeste
        set(x1 + 1, ARENA_FLOOR - 1, z2 - 1, b("lodestone"));
        sign(x1, ARENA_FLOOR + 1, z2 - 1, Direction.EAST, DyeColor.WHITE, "SALIDA", "Pisa la piedra", "imán para subir", "");
        // entrada: postes con luz y el cartel de la arena
        fill(cx - 5, G, z0 + 20, cx - 5, G + 1, z0 + 20, b("polished_blackstone_wall"));
        set(cx - 5, G + 2, z0 + 20, b("ochre_froglight"));
        fill(cx + 5, G, z0 + 20, cx + 5, G + 1, z0 + 20, b("polished_blackstone_wall"));
        set(cx + 5, G + 2, z0 + 20, b("ochre_froglight"));
        fill(cx + 3, G, z0 + 19, cx + 3, G + 1, z0 + 19, b("polished_blackstone_wall"));
        post(cx + 3, G + 2, z0 + 19, Direction.SOUTH, DyeColor.ORANGE, "ARENA DEL", "SISTEMA", "Habla con Vera", "para combatir");
        // marcador de rondas sobre las gradas del norte
        fill(cx - 4, G + 3, z0, cx + 4, G + 6, z0, b("black_concrete"));
        fill(cx - 3, G + 4, z0, cx + 3, G + 5, z0, b("light_blue_stained_glass"));
        set(cx - 3, G + 4, z0, b("sea_lantern"));
        set(cx + 3, G + 4, z0, b("sea_lantern"));
        fill(cx - 4, G + 2, z0, cx + 4, G + 2, z0, b("stone_bricks"));
        sign(cx, G + 5, z0 + 1, Direction.SOUTH, DyeColor.ORANGE, "ARENA", "Oleadas por", "rango", "");
    }

    // ================================================================ Estación de Puertas

    private void gateStation(int x0, int z0) {
        int cx = x0 + 10;
        int x1 = x0 + 1;
        int x2 = x0 + 19;
        int z1 = z0 + 2;
        int z2 = z0 + 18;
        fill(x0, G - 1, z0, x0 + 20, G - 1, z0 + 20, b("polished_blackstone"));
        // nave: paredes oscuras y techo de cristal tintado
        for (int y = G; y <= G + 12; y++) {
            for (int x = x1; x <= x2; x++) set(x, y, z1, b(y % 4 == 0 ? "polished_deepslate" : "deepslate_tiles"));
            for (int z = z1; z <= z2; z++) {
                set(x1, y, z, b(y % 4 == 0 ? "polished_deepslate" : "deepslate_tiles"));
                set(x2, y, z, b(y % 4 == 0 ? "polished_deepslate" : "deepslate_tiles"));
            }
        }
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) set(x, G + 13, z, (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) ? b("polished_deepslate") : b("purple_stained_glass"));
        }
        // suelo con dibujo y un camino de luz desde la entrada hasta la puerta
        for (int x = x1 + 1; x < x2; x++) {
            for (int z = z1 + 1; z <= z2; z++) {
                set(x, G - 1, z, Math.floorMod(x - cx, 4) == 0 ? b("polished_blackstone_bricks") : b("polished_deepslate"));
            }
        }
        for (int z = z0 + 11; z <= z2; z++) {
            set(cx - 2, G - 1, z, Math.floorMod(z, 3) == 0 ? b("sea_lantern") : b("purple_concrete"));
            set(cx + 2, G - 1, z, Math.floorMod(z, 3) == 0 ? b("sea_lantern") : b("purple_concrete"));
        }
        // columnas de la fachada abierta al sur
        for (int x = x1; x <= x2; x += 6) fill(x, G, z2, x, G + 12, z2, b("polished_deepslate"));
        fill(x1, G + 12, z2, x2, G + 12, z2, b("polished_deepslate"));
        // el anillo de la puerta (vertical, en el plano z = cz)
        int gz = z0 + 7;
        int gy = G + 6;
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int y = G; y <= G + 12; y++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (y - gy) * (y - gy));
                if (d > 4.5 && d < 5.7) set(x, y, gz, (Math.floorMod(x + y, 3) == 0) ? b("crying_obsidian") : b("obsidian"));
                else if (d <= 4.5) set(x, y, gz, b("purple_stained_glass"));
            }
        }
        // el disco brilla: luces detrás del cristal y un marco de amatista
        for (int x = cx - 4; x <= cx + 4; x += 2) {
            for (int y = G + 2; y <= G + 10; y += 2) {
                if ((x - cx) * (x - cx) + (y - gy) * (y - gy) <= 18) set(x, y, gz - 1, light(14));
            }
        }
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int y = G; y <= G + 12; y++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (y - gy) * (y - gy));
                if (d >= 5.7 && d < 6.4) set(x, y, gz, b("amethyst_block"));
            }
        }
        fill(cx - 2, G, gz, cx + 2, G, gz, b("crying_obsidian"));
        // vallas de seguridad amarillas y negras delante del anillo
        for (int x = cx - 6; x <= cx + 6; x++) {
            if (Math.abs(x - cx) <= 1) continue;
            set(x, G, gz + 3, b((x & 1) == 0 ? "yellow_concrete" : "black_concrete"));
        }
        // la ventanilla de Hugo (él está detrás, en gz + 5)
        fill(cx + 3, G, gz + 6, cx + 5, G, gz + 6, b("polished_blackstone"));
        set(cx + 3, G + 1, gz + 6, b("lantern"));
        set(cx + 5, G + 1, gz + 6, b("purple_stained_glass"));
        // el suelo bajo la puerta brilla
        for (int x = cx - 1; x <= cx + 1; x++) {
            set(x, G - 1, gz + 1, b("crying_obsidian"));
            set(x, G - 1, gz + 2, b("polished_blackstone_bricks"));
        }
        set(cx, G - 1, gz + 1, b("amethyst_block"));
        // pantallas de vigilancia en las paredes
        for (int z = z1 + 3; z <= z2 - 3; z += 5) {
            fill(x1 + 1, G + 2, z, x1 + 1, G + 4, z + 2, b("black_concrete"));
            fill(x1 + 1, G + 3, z, x1 + 1, G + 3, z + 2, b("light_blue_stained_glass"));
            fill(x2 - 1, G + 2, z, x2 - 1, G + 4, z + 2, b("black_concrete"));
            fill(x2 - 1, G + 3, z, x2 - 1, G + 3, z + 2, b("purple_stained_glass"));
        }
        for (int x = x1 + 2; x < x2; x += 4) {
            for (int z = z1 + 2; z < z2; z += 4) {
                set(x, G + 11, z, light(15));
                set(x, G + 5, z, light(13));
            }
        }
        // lámparas colgantes
        for (int x = cx - 6; x <= cx + 6; x += 6) {
            fill(x, G + 9, z0 + 13, x, G + 12, z0 + 13, b("chain"));
            set(x, G + 8, z0 + 13, hanging("soul_lantern"));
        }
        sign(cx + 3, G + 3, z2 + 1, Direction.SOUTH, DyeColor.PURPLE, "ESTACIÓN DE", "PUERTAS", "Hugo abre puertas", "de tu rango");
        sign(cx - 3, G + 3, z2 + 1, Direction.SOUTH, DyeColor.WHITE, "ASOCIACIÓN DE", "CAZADORES", "Zona vigilada", "");
    }

    // ================================================================ tiendas de la avenida comercial

    /**
     * Tienda de varias plantas con escaparate a la calle (al sur si south, si no al norte). Devuelve la z de la fachada.
     * Deja hueco de puerta (3x3) en el centro (x0 + 10) y un toldo de rayas de dos colores. El mostrador va a 6 de la
     * fachada y el tendero justo detrás (ver City.npcHome).
     */
    private int shopBox(int x0, int z0, boolean south, BlockState wall, BlockState trim, BlockState floor, BlockState glass,
                        String awningA, String awningB, int floors) {
        int x1 = x0 + 2;
        int x2 = x0 + 18;
        int zf = south ? z0 + 18 : z0 + 2;
        int zb = south ? z0 + 3 : z0 + 17;
        int z1 = Math.min(zf, zb);
        int z2 = Math.max(zf, zb);
        int h = floors * 5;
        fill(x0 + 2, G - 1, z0 + 2, x0 + 18, G - 1, z0 + 18, b("polished_andesite"));
        fill(x1, G - 1, z1, x2, G - 1, z2, floor);
        for (int y = G; y <= G + h; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    boolean bandY = (y - G) % 5 == 0;
                    BlockState s = corner || bandY ? trim : wall;
                    // ventanas en las plantas altas
                    if (!corner && !bandY && y > G + 5 && (y - G) % 5 >= 2 && (y - G) % 5 <= 3) {
                        int u = z == z1 || z == z2 ? x - x1 : z - z1;
                        if (Math.floorMod(u, 3) != 0) s = glass;
                    }
                    set(x, y, z, s);
                }
            }
            if ((y - G) % 5 == 0 && y > G) {
                fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, floor);
                // luz en cada planta: de noche se ven las ventanas encendidas
                if (y < G + h) {
                    for (int x = x1 + 2; x < x2; x += 4) {
                        for (int z = z1 + 2; z < z2; z += 4) set(x, y + 3, z, light(13));
                    }
                }
            }
        }
        fill(x1, G + h, z1, x2, G + h, z2, trim);
        walls(x1, G + h + 1, z1, x2, G + h + 1, z2, trim);
        // escaparate de la planta baja y puerta
        fill(x1 + 1, G + 1, zf, x2 - 1, G + 3, zf, glass);
        fill(x0 + 9, G, zf, x0 + 11, G + 2, zf, Blocks.AIR.defaultBlockState());
        set(x0 + 10, G - 1, zf, b("polished_diorite"));
        // toldo a rayas
        int out = south ? 1 : -1;
        for (int x = x1; x <= x2; x++) {
            set(x, G + 4, zf + out, b(Math.floorMod(x, 2) == 0 ? awningA : awningB));
        }
        // luces dentro
        for (int x = x1 + 2; x < x2; x += 4) {
            for (int z = z1 + 2; z < z2; z += 4) set(x, G + 4, z, b("glowstone"));
        }
        // jardineras a los lados de la puerta
        set(x0 + 6, G, zf + out, b("potted_azalea_bush"));
        set(x0 + 14, G, zf + out, b("potted_azalea_bush"));
        return zf;
    }

    private void smithy(int x0, int z0) {
        int zf = shopBox(x0, z0, true, b("stone_bricks"), b("deepslate_bricks"), b("polished_deepslate"), b("glass"),
                "red_wool", "black_wool", 2);
        int cx = x0 + 10;
        sign(cx, G + 3, zf + 1, Direction.SOUTH, DyeColor.ORANGE, "HERRERÍA", "DE BRUNO", "Mejoras y", "habilidades");
        // mostrador (Bruno detrás, en z0 + 11)
        fill(cx - 4, G, z0 + 12, cx + 4, G, z0 + 12, b("polished_blackstone"));
        set(cx - 4, G + 1, z0 + 12, b("lantern"));
        set(cx + 4, G + 1, z0 + 12, face("grindstone", Direction.SOUTH));
        // taller: yunque, mesa de herrería, altos hornos y la fragua al fondo
        set(cx - 3, G, z0 + 9, face("anvil", Direction.EAST));
        set(cx + 3, G, z0 + 9, b("smithing_table"));
        for (int x = x0 + 4; x <= x0 + 6; x++) set(x, G, z0 + 4, face("blast_furnace", Direction.SOUTH));
        set(x0 + 16, G, z0 + 4, face("grindstone", Direction.SOUTH));
        fill(cx - 2, G - 1, z0 + 4, cx + 2, G - 1, z0 + 4, b("magma_block"));
        fill(cx - 2, G, z0 + 4, cx + 2, G, z0 + 4, b("magma_block"));
        fill(cx - 2, G + 1, z0 + 4, cx + 2, G + 2, z0 + 4, pane("iron_bars", true));
        fill(cx - 3, G + 3, z0 + 4, cx + 3, G + 3, z0 + 4, b("bricks"));
        fill(cx - 3, G, z0 + 4, cx - 3, G + 2, z0 + 4, b("bricks"));
        fill(cx + 3, G, z0 + 4, cx + 3, G + 2, z0 + 4, b("bricks"));
        // chimenea con humo
        fill(x0 + 15, G + 11, z0 + 5, x0 + 16, G + 15, z0 + 6, b("bricks"));
        set(x0 + 15, G + 16, z0 + 5, b("campfire"));
        set(x0 + 16, G + 16, z0 + 6, b("campfire"));
        // armería: barriles con el género y armas colgadas
        fill(x0 + 3, G, z0 + 7, x0 + 3, G + 1, z0 + 10, face("barrel", Direction.EAST));
        fill(x0 + 17, G, z0 + 7, x0 + 17, G + 1, z0 + 10, face("barrel", Direction.WEST));
        for (int z = z0 + 14; z <= z0 + 16; z++) {
            set(x0 + 3, G, z, b("polished_blackstone_wall"));
            set(x0 + 3, G + 1, z, b("lightning_rod"));
            set(x0 + 17, G, z, b("polished_blackstone_wall"));
            set(x0 + 17, G + 1, z, b("lightning_rod"));
        }
        set(x0 + 3, G + 2, z0 + 9, b("lantern"));
        set(x0 + 17, G + 2, z0 + 9, b("lantern"));
    }

    private void alchemy(int x0, int z0) {
        int zf = shopBox(x0, z0, true, b("dark_oak_planks"), b("stripped_dark_oak_log"), b("moss_block"), b("green_stained_glass"),
                "green_wool", "white_wool", 3);
        int cx = x0 + 10;
        sign(cx, G + 3, zf + 1, Direction.SOUTH, DyeColor.LIME, "ALQUIMIA", "DE SELENE", "Pociones y", "elixires");
        // mostrador con alambiques (Selene detrás)
        fill(cx - 4, G, z0 + 12, cx + 4, G, z0 + 12, b("spruce_planks"));
        set(cx - 3, G + 1, z0 + 12, b("brewing_stand"));
        set(cx + 3, G + 1, z0 + 12, b("brewing_stand"));
        set(cx, G + 1, z0 + 12, b("potted_crimson_fungus"));
        // estanterías llenas de libros, calderos y plantas raras
        fill(x0 + 3, G, z0 + 4, x0 + 17, G + 3, z0 + 4, b("bookshelf"));
        for (int x = x0 + 5; x <= x0 + 15; x += 5) set(x, G + 1, z0 + 4, face("chiseled_bookshelf", Direction.SOUTH));
        set(x0 + 4, G, z0 + 8, w(b("water_cauldron"), BlockStateProperties.LEVEL_CAULDRON, 3));
        set(x0 + 16, G, z0 + 8, w(b("water_cauldron"), BlockStateProperties.LEVEL_CAULDRON, 3));
        set(x0 + 4, G - 1, z0 + 8, b("magma_block"));
        set(x0 + 16, G - 1, z0 + 8, b("magma_block"));
        String[] pots = {"potted_flowering_azalea_bush", "potted_azalea_bush", "potted_fern", "potted_blue_orchid", "potted_allium",
                "potted_lily_of_the_valley"};
        for (int k = 0; k < 6; k++) set(x0 + 3 + k * 3, G, z0 + 16, b(pots[k]));
        for (int x = x0 + 5; x <= x0 + 15; x += 5) {
            set(x, G + 4, z0 + 9, b("chain"));
            set(x, G + 3, z0 + 9, hanging("soul_lantern"));
            set(x, G + 4, z0 + 14, b("chain"));
            set(x, G + 3, z0 + 14, hanging("lantern"));
        }
    }

    private void restaurant(int x0, int z0) {
        int x1 = x0 + 2;
        int x2 = x0 + 18;
        int z1 = z0 + 3;
        int z2 = z0 + 13;
        RandomSource r = rnd(3, -1, 4);
        fill(x0 + 2, G - 1, z0 + 2, x0 + 18, G - 1, z0 + 18, b("polished_andesite"));
        fill(x1, G - 1, z1, x2, G - 1, z2, b("spruce_planks"));
        shell(x1, z1, x2, z2, G, G + 10, 2, b("bricks"), b("glass"), b("stripped_spruce_log"), 4, r, false);
        fill(x1 + 1, G + 5, z1 + 1, x2 - 1, G + 5, z2 - 1, b("spruce_planks"));
        fill(x1, G + 10, z1, x2, G + 10, z2, b("spruce_planks"));
        walls(x1, G + 11, z1, x2, G + 11, z2, b("stripped_spruce_log"));
        fill(x1 + 1, G + 1, z2, x2 - 1, G + 3, z2, b("glass"));
        fill(x0 + 9, G, z2, x0 + 11, G + 2, z2, Blocks.AIR.defaultBlockState());
        int cx = x0 + 10;
        sign(cx, G + 4, z2 + 1, Direction.SOUTH, DyeColor.ORANGE, "COMEDOR DEL", "CAZADOR", "Cocina de Tomás", "");
        // barra (Tomás detrás, en z0 + 7) y cocina al fondo
        fill(cx - 5, G, z0 + 8, cx + 5, G, z0 + 8, b("stripped_spruce_log"));
        fill(cx - 5, G + 1, z0 + 8, cx + 5, G + 1, z0 + 8, slab("spruce_slab", false));
        set(cx - 5, G + 1, z0 + 8, b("lantern"));
        set(cx + 5, G + 1, z0 + 8, b("lantern"));
        set(x1 + 2, G, z1 + 1, face("smoker", Direction.SOUTH));
        set(x1 + 3, G, z1 + 1, face("smoker", Direction.SOUTH));
        set(x1 + 4, G, z1 + 1, face("furnace", Direction.SOUTH));
        set(x2 - 3, G, z1 + 1, b("campfire"));
        set(x2 - 2, G, z1 + 1, face("barrel", Direction.SOUTH));
        set(x2 - 4, G, z1 + 1, face("barrel", Direction.SOUTH));
        fill(cx - 2, G, z1 + 1, cx + 2, G, z1 + 1, b("smooth_stone"));
        set(cx, G + 1, z1 + 1, b("cake"));
        for (int x = x1 + 2; x < x2 - 1; x += 4) {
            set(x, G + 4, z0 + 10, b("chain"));
            set(x, G + 3, z0 + 10, hanging("lantern"));
        }
        // dos mesas dentro
        for (int x : new int[]{x1 + 3, x2 - 3}) {
            set(x, G, z0 + 11, slab("spruce_slab", true));
            set(x - 1, G, z0 + 11, stairs("spruce_stairs", Direction.WEST, false));
            set(x + 1, G, z0 + 11, stairs("spruce_stairs", Direction.EAST, false));
        }
        // terraza con mesas y sombrillas a rayas
        for (int x = x1 + 2; x <= x2 - 2; x += 6) {
            int z = z0 + 16;
            set(x, G, z, slab("spruce_slab", true));
            set(x - 1, G, z, stairs("spruce_stairs", Direction.WEST, false));
            set(x + 1, G, z, stairs("spruce_stairs", Direction.EAST, false));
            set(x, G + 1, z, b("spruce_fence"));
            set(x, G + 2, z, b("spruce_fence"));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(x + dx, G + 3, z + dz, b(Math.floorMod(dx + dz, 2) == 0 ? "red_wool" : "white_wool"));
                }
            }
        }
        for (int x = x1; x <= x2; x++) set(x, G, z0 + 18, pane("spruce_fence", true));
        fill(cx - 1, G, z0 + 18, cx + 1, G, z0 + 18, Blocks.AIR.defaultBlockState());
        set(x1, G + 1, z0 + 18, b("lantern"));
        set(x2, G + 1, z0 + 18, b("lantern"));
    }

    private void hospital(int x0, int z0) {
        RandomSource r = rnd(-2, -1, 5);
        int x1 = x0 + 2;
        int x2 = x0 + 18;
        int z1 = z0 + 3;
        int z2 = z0 + 17;
        fill(x0 + 2, G - 1, z0 + 2, x0 + 18, G - 1, z0 + 18, b("smooth_quartz"));
        shell(x1, z1, x2, z2, G, G + 16, 2, b("white_concrete"), b("light_blue_stained_glass"), b("light_gray_concrete"), 4, r, true);
        fill(x1, G + 16, z1, x2, G + 16, z2, b("light_gray_concrete"));
        int cx = x0 + 10;
        // cruz roja en la fachada
        for (int y = G + 9; y <= G + 14; y++) set(cx, y, z2 + 1, b("red_concrete"));
        for (int x = cx - 2; x <= cx + 2; x++) {
            set(x, G + 11, z2 + 1, b("red_concrete"));
            set(x, G + 12, z2 + 1, b("red_concrete"));
        }
        set(cx - 1, G + 13, z2 + 1, b("red_concrete"));
        set(cx + 1, G + 13, z2 + 1, b("red_concrete"));
        set(cx - 1, G + 10, z2 + 1, b("red_concrete"));
        set(cx + 1, G + 10, z2 + 1, b("red_concrete"));
        // entrada con marquesina
        fill(cx - 2, G, z2, cx + 2, G + 2, z2, Blocks.AIR.defaultBlockState());
        fill(cx - 3, G + 3, z2 + 1, cx + 3, G + 3, z2 + 2, slab("smooth_quartz_slab", true));
        set(cx - 2, G + 2, z2 + 1, light(15));
        set(cx + 2, G + 2, z2 + 1, light(15));
        sign(cx, G + 4, z2 + 1, Direction.SOUTH, DyeColor.RED, "CENTRO MÉDICO", "Dra. Irene", "Curas y limpieza", "");
        // recepción (Irene detrás, en z0 + 10)
        fill(cx - 3, G, z0 + 11, cx + 3, G, z0 + 11, b("white_concrete"));
        fill(cx - 3, G + 1, z0 + 11, cx + 3, G + 1, z0 + 11, slab("smooth_quartz_slab", false));
        set(cx - 3, G + 1, z0 + 11, b("potted_lily_of_the_valley"));
        fill(cx - 1, G + 1, z1, cx + 1, G + 3, z1, b("red_concrete"));
        fill(cx - 2, G + 2, z1, cx + 2, G + 2, z1, b("red_concrete"));
        // salas con camas y cortinas al oeste y al este
        for (int z = z1 + 2; z <= z1 + 8; z += 3) {
            bed(x1 + 2, G, z, "white_bed", Direction.WEST);
            bed(x2 - 2, G, z, "light_blue_bed", Direction.EAST);
            set(x1 + 1, G, z + 1, pane("white_stained_glass_pane", true));
            set(x1 + 2, G, z + 1, pane("white_stained_glass_pane", true));
            set(x2 - 1, G, z + 1, pane("white_stained_glass_pane", true));
            set(x2 - 2, G, z + 1, pane("white_stained_glass_pane", true));
            set(x1 + 3, G, z, w(b("white_carpet"), BlockStateProperties.FACING, Direction.NORTH));
        }
        // sillas de espera
        for (int x = cx - 5; x <= cx + 5; x++) {
            if (Math.abs(x - cx) <= 1) continue;
            set(x, G, z2 - 2, stairs("quartz_stairs", Direction.SOUTH, false));
        }
        set(x1 + 1, G, z2 - 1, b("potted_fern"));
        set(x2 - 1, G, z2 - 1, b("potted_fern"));
        // helipuerto con la H roja y barandilla
        int hy = G + 16;
        fill(cx - 4, hy, z0 + 6, cx + 4, hy, z0 + 14, b("light_gray_concrete"));
        for (int d = -2; d <= 2; d++) {
            set(cx - 2, hy, z0 + 10 + d, b("red_concrete"));
            set(cx + 2, hy, z0 + 10 + d, b("red_concrete"));
        }
        for (int x = cx - 1; x <= cx + 1; x++) set(x, hy, z0 + 10, b("red_concrete"));
        for (int[] c : new int[][]{{cx - 4, z0 + 6}, {cx + 4, z0 + 6}, {cx - 4, z0 + 14}, {cx + 4, z0 + 14}}) {
            set(c[0], hy, c[1], b("verdant_froglight"));
        }
        rail(x1, hy + 1, z1, x2, z2, "iron_bars");
    }

    private void garden(int x0, int z0) {
        int cx = x0 + 10;
        int cz = z0 + 10;
        for (int x = x0 + 2; x <= x0 + 18; x++) {
            for (int z = z0 + 2; z <= z0 + 18; z++) set(x, G - 1, z, (hash(x, z) & 7) == 0 ? b("moss_block") : b("grass_block"));
        }
        // estanque con nenúfares y puentecito
        for (int x = cx - 5; x <= cx + 5; x++) {
            for (int z = cz - 3; z <= cz + 4; z++) {
                double d = Math.pow((x - cx) / 5.5, 2) + Math.pow((z - cz - 0.5) / 4.0, 2);
                if (d < 1.0) {
                    set(x, G - 2, z, b("clay"));
                    set(x, G - 1, z, b("water"));
                    if ((hash(x, z) & 7) == 0 && x != cx) set(x, G, z, b("lily_pad"));
                } else if (d < 1.4) {
                    set(x, G - 1, z, b("mossy_cobblestone"));
                }
            }
        }
        for (int z = cz - 4; z <= cz + 5; z++) set(cx, G - 1, z, b("spruce_planks"));
        set(cx, G, cz - 5, b("lantern"));
        // caminos de grava
        for (int x = x0 + 2; x <= x0 + 18; x++) {
            if (!get(x, G - 1, z0 + 17).is(Blocks.WATER)) set(x, G - 1, z0 + 17, b("gravel"));
        }
        for (int z = z0 + 2; z <= z0 + 18; z++) {
            if (!get(cx, G - 1, z).is(Blocks.WATER) && !get(cx, G - 1, z).is(Blocks.SPRUCE_PLANKS)) set(cx, G - 1, z, b("gravel"));
        }
        // cerezos, azaleas y faroles de piedra
        for (int[] t : new int[][]{{x0 + 4, z0 + 4}, {x0 + 16, z0 + 5}, {x0 + 4, z0 + 14}, {x0 + 16, z0 + 14}}) {
            set(t[0], G - 1, t[1], b("grass_block"));
            cherry(t[0], G, t[1], 4);
        }
        for (int[] t : new int[][]{{cx - 3, z0 + 16}, {cx + 3, z0 + 16}, {cx - 7, cz}, {cx + 7, cz}}) {
            set(t[0], G, t[1], b("stone_brick_wall"));
            set(t[0], G + 1, t[1], b("lantern"));
        }
        for (int[] t : new int[][]{{x0 + 7, z0 + 3}, {x0 + 13, z0 + 3}, {x0 + 3, z0 + 9}, {x0 + 17, z0 + 9}}) {
            set(t[0], G, t[1], b("flowering_azalea"));
        }
        set(cx + 2, G, z0 + 18, b("stone_brick_wall"));
        post(cx + 2, G + 1, z0 + 18, Direction.SOUTH, DyeColor.PINK, "JARDÍN DE LA", "ASOCIACIÓN", "", "");
    }

    private void guildHall(int x0, int z0) {
        int x1 = x0 + 2;
        int x2 = x0 + 18;
        int z1 = z0 + 2;
        int z2 = z0 + 17;
        fill(x0 + 2, G - 1, z0 + 2, x0 + 18, G - 1, z0 + 18, b("dark_oak_planks"));
        // salón de piedra con vigas de madera oscura, dos plantas
        for (int y = G; y <= G + 10; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                    boolean beam = Math.floorMod(x - x1, 4) == 0 && (z == z1 || z == z2) || Math.floorMod(z - z1, 4) == 0 && (x == x1 || x == x2);
                    boolean win = !beam && (y - G) % 5 >= 2 && (y - G) % 5 <= 3;
                    set(x, y, z, beam ? b("stripped_dark_oak_log") : win ? b("red_stained_glass") : b("stone_bricks"));
                }
            }
        }
        fill(x1 + 1, G + 5, z1 + 1, x2 - 1, G + 5, z2 - 1, b("dark_oak_planks"));
        fill(x1 + 1, G + 6, z1 + 1, x2 - 1, G + 9, z2 - 1, Blocks.AIR.defaultBlockState());
        for (int x = x1 + 2; x < x2; x += 4) {
            for (int z = z1 + 2; z < z2; z += 4) set(x, G + 8, z, light(13));
        }
        // tejado a dos aguas con frontones de madera
        for (int k = 0; ; k++) {
            int y = G + 11 + k;
            int xa = x1 - 1 + k;
            int xb = x2 + 1 - k;
            if (xa > xb) break;
            if (xa == xb) {
                fill(xa, y, z1 - 1, xa, y, z2 + 1, slab("dark_oak_slab", false));
                break;
            }
            fill(xa, y, z1 - 1, xa, y, z2 + 1, stairs("dark_oak_stairs", Direction.EAST, false));
            fill(xb, y, z1 - 1, xb, y, z2 + 1, stairs("dark_oak_stairs", Direction.WEST, false));
            for (int x = xa + 1; x < xb; x++) {
                set(x, y, z1, b("dark_oak_planks"));
                set(x, y, z2, b("dark_oak_planks"));
            }
        }
        fill(x1, G + 11, z1, x2, G + 11, z2, b("dark_oak_planks"));
        // torreones delanteros con almenas y banderas
        for (int tx : new int[]{x1 - 1, x2 - 1}) {
            fill(tx, G, z2 - 1, tx + 2, G + 14, z2 + 1, b("stone_bricks"));
            for (int y = G + 3; y <= G + 12; y += 4) set(tx + 1, y, z2 + 1, b("red_stained_glass"));
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx + dz) % 2 == 0) set(tx + dx, G + 15, z2 + dz, b("stone_bricks"));
                }
            }
            fill(tx + 1, G + 15, z2, tx + 1, G + 16, z2, b("stone_brick_wall"));
            set(tx + 1, G + 17, z2, w(b("red_banner"), BlockStateProperties.ROTATION_16, 0));
        }
        int cx = x0 + 10;
        // portón
        fill(cx - 1, G, z2, cx + 1, G + 3, z2, Blocks.AIR.defaultBlockState());
        set(cx - 2, G + 2, z2 + 1, face("wall_torch", Direction.SOUTH));
        set(cx + 2, G + 2, z2 + 1, face("wall_torch", Direction.SOUTH));
        sign(cx, G + 4, z2 + 1, Direction.SOUTH, DyeColor.RED, "GREMIO DE", "CAZADORES", "Maestro Darío", "");
        // mesas largas con bancos
        for (int tx : new int[]{cx - 4, cx + 4}) {
            fill(tx, G, z1 + 5, tx, G, z2 - 3, slab("dark_oak_slab", true));
            fill(tx - 1, G, z1 + 5, tx - 1, G, z2 - 3, stairs("dark_oak_stairs", Direction.WEST, false));
            fill(tx + 1, G, z1 + 5, tx + 1, G, z2 - 3, stairs("dark_oak_stairs", Direction.EAST, false));
            for (int z = z1 + 6; z <= z2 - 4; z += 4) set(tx, G + 1, z, b("lantern"));
        }
        // la mesa del maestro (Darío detrás, en z1 + 2) y los trofeos de caza
        fill(cx - 2, G, z1 + 3, cx + 2, G, z1 + 3, b("dark_oak_planks"));
        set(cx - 2, G + 1, z1 + 3, b("lantern"));
        set(cx + 2, G + 1, z1 + 3, w(w(b("candle"), BlockStateProperties.CANDLES, 3), BlockStateProperties.LIT, true));
        String[] heads = {"zombie_wall_head", "skeleton_wall_skull", "creeper_wall_head", "wither_skeleton_wall_skull", "piglin_wall_head"};
        for (int k = 0; k < heads.length; k++) set(cx - 4 + k * 2, G + 3, z1 + 1, face(heads[k], Direction.SOUTH));
        for (int z = z1 + 3; z <= z2 - 3; z += 3) {
            set(x1 + 1, G + 3, z, face("red_wall_banner", Direction.EAST));
            set(x2 - 1, G + 3, z, face("red_wall_banner", Direction.WEST));
        }
        for (int x = cx - 6; x <= cx + 6; x += 6) {
            set(x, G + 4, z0 + 10, b("chain"));
            set(x, G + 3, z0 + 10, hanging("lantern"));
        }
    }

    private void mall(int x0, int z0) {
        RandomSource r = rnd(1, 0, 6);
        int x1 = x0 + 2;
        int x2 = x0 + 18;
        int z1 = z0 + 2;
        int z2 = z0 + 18;
        fill(x0 + 2, G - 1, z0 + 2, x0 + 18, G - 1, z0 + 18, b("polished_diorite"));
        shell(x1, z1, x2, z2, G, G + 16, 0, b("white_concrete"), b("light_blue_stained_glass"), b("light_gray_concrete"), 3, r, false);
        fill(x1, G + 16, z1, x2, G + 16, z2, b("white_concrete"));
        // atrio: plantas abiertas en el centro y techo de cristal
        for (int y = G + 4; y < G + 16; y += 4) {
            for (int x = x1 + 1; x < x2; x++) {
                for (int z = z1 + 1; z < z2; z++) {
                    boolean hole = Math.abs(x - (x0 + 10)) <= 3 && Math.abs(z - (z0 + 10)) <= 3;
                    if (!hole) set(x, y, z, b("smooth_quartz"));
                }
            }
            rail(x0 + 6, y + 1, z0 + 6, x0 + 14, z0 + 14, "glass_pane");
            for (int x = x1 + 2; x < x2; x += 4) {
                for (int z = z1 + 2; z < z2; z += 4) set(x, y + 3, z, light(14));
            }
        }
        fill(x0 + 7, G + 16, z0 + 7, x0 + 13, G + 16, z0 + 13, b("glass"));
        // entrada mirando a la plaza (oeste)
        fill(x1, G, z0 + 8, x1, G + 3, z0 + 12, Blocks.AIR.defaultBlockState());
        fill(x1 - 1, G + 4, z0 + 7, x1 - 1, G + 4, z0 + 13, slab("smooth_quartz_slab", true));
        sign(x1 - 1, G + 5, z0 + 10, Direction.WEST, DyeColor.CYAN, "CENTRO", "COMERCIAL", "NEXO", "");
        // fuente y árboles del atrio
        int cx = x0 + 10;
        int cz = z0 + 10;
        fill(cx - 1, G - 1, cz - 1, cx + 1, G - 1, cz + 1, b("water"));
        set(cx, G - 2, cz, b("sea_lantern"));
        set(cx, G - 1, cz, b("water"));
        set(cx - 3, G, cz - 3, b("potted_bamboo"));
        set(cx + 3, G, cz + 3, b("potted_bamboo"));
        tree(cx + 3, G, cz - 3, 6, "jungle_log", "jungle_leaves");
        tree(cx - 3, G, cz + 3, 6, "jungle_log", "jungle_leaves");
        // tiendas en la planta baja (escaparates de colores)
        String[] colors = {"red", "orange", "lime", "magenta", "yellow", "light_blue"};
        for (int k = 0; k < 6; k++) {
            int x = x1 + 2 + (k % 3) * 5;
            int z = k < 3 ? z1 + 1 : z2 - 1;
            fill(x, G, z, x + 2, G, z, b(colors[k] + "_concrete"));
            fill(x, G + 2, z, x + 2, G + 2, z, b(colors[k] + "_wool"));
            set(x + 1, G + 1, z, b(colors[k] + "_stained_glass"));
        }
        for (int x = x1 + 2; x < x2; x += 4) {
            for (int z = z1 + 2; z < z2; z += 4) set(x, G + 3, z, light(15));
        }
        // gran pantalla en la fachada de la avenida
        int sz = z1 - 1;
        fill(x1 + 3, G + 8, sz, x2 - 3, G + 13, sz, b("black_concrete"));
        fill(x1 + 4, G + 9, sz, x2 - 4, G + 12, sz, b("light_blue_stained_glass"));
        fill(x1 + 5, G + 10, sz, x2 - 5, G + 11, sz, b("sea_lantern"));
    }

    private void essenceShop(int x0, int z0) {
        int zf = shopBox(x0, z0, false, b("purpur_block"), b("purpur_pillar"), b("polished_blackstone"), b("purple_stained_glass"),
                "purple_wool", "black_wool", 2);
        int cx = x0 + 10;
        sign(cx, G + 3, zf - 1, Direction.NORTH, DyeColor.PURPLE, "ESENCIAS", "DE EZEQUIEL", "Rarezas por", "esencias");
        // mostrador (Ezequiel detrás, en z0 + 9)
        fill(cx - 4, G, z0 + 8, cx + 4, G, z0 + 8, b("crying_obsidian"));
        set(cx - 4, G + 1, z0 + 8, b("amethyst_cluster"));
        set(cx + 4, G + 1, z0 + 8, b("amethyst_cluster"));
        for (int x : new int[]{x0 + 4, x0 + 16}) {
            set(x, G, z0 + 5, b("amethyst_block"));
            set(x, G + 1, z0 + 5, b("amethyst_cluster"));
            set(x, G, z0 + 12, b("amethyst_block"));
            set(x, G + 1, z0 + 12, b("amethyst_cluster"));
        }
        set(cx, G, z0 + 14, b("enchanting_table"));
        for (int x = cx - 2; x <= cx + 2; x++) set(x, G, z0 + 16, b("bookshelf"));
        set(cx - 2, G + 1, z0 + 16, b("bookshelf"));
        set(cx + 2, G + 1, z0 + 16, b("bookshelf"));
        set(cx - 3, G, z0 + 14, b("bookshelf"));
        set(cx + 3, G, z0 + 14, b("bookshelf"));
        for (int x = x0 + 5; x <= x0 + 15; x += 5) set(x, G + 4, z0 + 11, face("end_rod", Direction.DOWN));
    }

    private void store24(int x0, int z0) {
        int zf = shopBox(x0, z0, false, b("white_concrete"), b("light_gray_concrete"), b("white_concrete"), b("glass"),
                "lime_wool", "white_wool", 4);
        int cx = x0 + 10;
        sign(cx, G + 3, zf - 1, Direction.NORTH, DyeColor.LIME, "TIENDA 24H", "Abierto siempre", "Paco te atiende", "");
        // rótulo luminoso
        fill(x0 + 4, G + 5, zf - 1, x0 + 16, G + 5, zf - 1, b("lime_concrete"));
        for (int x = x0 + 5; x <= x0 + 15; x += 2) set(x, G + 5, zf - 1, b("verdant_froglight"));
        // caja (Paco detrás, en z0 + 9)
        fill(x0 + 12, G, z0 + 8, x0 + 16, G, z0 + 8, b("light_gray_concrete"));
        fill(x0 + 12, G + 1, z0 + 8, x0 + 16, G + 1, z0 + 8, slab("smooth_stone_slab", false));
        // estanterías con género y neveras al fondo
        String[] goods = {"red_wool", "yellow_wool", "orange_wool", "lime_wool", "light_blue_wool", "white_wool"};
        for (int k = 0; k < 2; k++) {
            int x = x0 + 4 + k * 4;
            for (int z = z0 + 6; z <= z0 + 13; z++) {
                set(x, G, z, face("barrel", Direction.UP));
                set(x, G + 1, z, b(goods[(int) Math.floorMod(hash(x, z), goods.length)]));
            }
        }
        for (int x = x0 + 4; x <= x0 + 16; x++) {
            set(x, G, z0 + 16, b("white_concrete"));
            set(x, G + 1, z0 + 16, b("light_blue_stained_glass"));
            set(x, G + 2, z0 + 16, b("white_concrete"));
        }
        for (int x = x0 + 5; x <= x0 + 15; x += 3) set(x, G + 1, z0 + 15, light(12));
    }

    // ------------------------------------------------------------ tu apartamento

    /** Tu apartamento: la planta baja del edificio de ladrillo al suroeste de la plaza (lote -1, 1). */
    static final int HOME_X0 = lot(-1);
    static final int HOME_Z0 = lot(1);

    /**
     * Apartamento de 15x15 con cuatro estancias: biblioteca con la mesa de encantamientos (nivel 30), salón con cocina,
     * dormitorio con dos camas (para reaparecer en la ciudad) y taller con todas las mesas de trabajo.
     */
    private void apartment(int x0, int z0) {
        BlockState air = Blocks.AIR.defaultBlockState();
        fill(x0 + 3, G, z0 + 3, x0 + 17, G + 3, z0 + 17, air);
        fill(x0 + 3, G - 1, z0 + 3, x0 + 9, G - 1, z0 + 9, b("spruce_planks"));
        fill(x0 + 11, G - 1, z0 + 3, x0 + 17, G - 1, z0 + 9, b("oak_planks"));
        fill(x0 + 3, G - 1, z0 + 11, x0 + 9, G - 1, z0 + 17, b("light_blue_wool"));
        fill(x0 + 11, G - 1, z0 + 11, x0 + 17, G - 1, z0 + 17, b("smooth_stone"));
        fill(x0 + 3, G - 1, z0 + 10, x0 + 17, G - 1, z0 + 10, b("oak_planks"));
        fill(x0 + 10, G - 1, z0 + 3, x0 + 10, G - 1, z0 + 17, b("oak_planks"));
        // tabiques con sus puertas
        BlockState wall = b("white_concrete");
        fill(x0 + 3, G, z0 + 10, x0 + 17, G + 3, z0 + 10, wall);
        fill(x0 + 10, G, z0 + 3, x0 + 10, G + 3, z0 + 17, wall);
        for (int[] d : new int[][]{{x0 + 6, z0 + 10}, {x0 + 14, z0 + 10}, {x0 + 10, z0 + 6}, {x0 + 10, z0 + 14}}) {
            fill(d[0], G, d[1], d[0], G + 1, d[1], air);
        }
        // la puerta de la calle (al norte) con su luz y su letrero
        door(x0 + 14, G, z0 + 2, "dark_oak_door", Direction.SOUTH, false);
        set(x0 + 14, G - 1, z0 + 2, b("oak_planks"));
        set(x0 + 14, G - 1, z0 + 1, b("polished_diorite"));
        sign(x0 + 15, G + 2, z0 + 1, Direction.NORTH, DyeColor.CYAN, "TU APARTAMENTO", "Camas, cofres,", "taller y", "encantamientos");
        sign(x0 + 13, G + 2, z0 + 1, Direction.NORTH, DyeColor.WHITE, "Residencial", "Cerezo", "Bajo A", "");
        set(x0 + 14, G + 3, z0 + 1, light(14));
        // biblioteca: mesa de encantamientos rodeada de estanterías (con hueco al sur)
        int ex = x0 + 6;
        int ez = z0 + 6;
        set(ex, G, ez, b("enchanting_table"));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || (dx == 0 && dz == 2)) continue;
                set(ex + dx, G, ez + dz, b("bookshelf"));
                set(ex + dx, G + 1, ez + dz, b("bookshelf"));
            }
        }
        set(ex, G + 3, ez, light(15));
        set(x0 + 3, G, z0 + 3, b("lantern"));
        set(x0 + 9, G, z0 + 3, b("potted_fern"));
        // salón con cocina: sofá mirando a la tele, alfombra, mesita y plantas
        fill(x0 + 13, G, z0 + 5, x0 + 16, G, z0 + 7, b("light_gray_carpet"));
        set(x0 + 14, G, z0 + 6, slab("spruce_slab", true));
        fill(x0 + 12, G, z0 + 5, x0 + 12, G, z0 + 7, stairs("dark_oak_stairs", Direction.WEST, false));
        fill(x0 + 17, G, z0 + 5, x0 + 17, G + 1, z0 + 7, b("black_concrete"));
        set(x0 + 17, G + 1, z0 + 6, b("light_blue_stained_glass"));
        set(x0 + 11, G, z0 + 3, face("smoker", Direction.SOUTH));
        set(x0 + 12, G, z0 + 3, w(b("water_cauldron"), BlockStateProperties.LEVEL_CAULDRON, 3));
        set(x0 + 16, G, z0 + 3, face("barrel", Direction.SOUTH));
        set(x0 + 17, G, z0 + 3, face("furnace", Direction.SOUTH));
        set(x0 + 11, G, z0 + 9, b("potted_bamboo"));
        set(x0 + 17, G, z0 + 9, b("potted_flowering_azalea_bush"));
        set(x0 + 14, G + 3, z0 + 6, light(15));
        // dormitorio: dos camas (ponen tu punto de reaparición), mesita, armario y letrero
        bed(x0 + 4, G, z0 + 16, "blue_bed", Direction.SOUTH);
        bed(x0 + 6, G, z0 + 16, "light_blue_bed", Direction.SOUTH);
        set(x0 + 5, G, z0 + 17, face("barrel", Direction.UP));
        set(x0 + 5, G + 1, z0 + 17, b("lantern"));
        for (int z = z0 + 11; z <= z0 + 14; z++) {
            set(x0 + 3, G, z, face("chest", Direction.EAST));
            set(x0 + 3, G + 1, z, face("barrel", Direction.EAST));
        }
        set(x0 + 9, G, z0 + 11, b("potted_azalea_bush"));
        set(x0 + 9, G, z0 + 17, b("potted_fern"));
        sign(x0 + 3, G + 2, z0 + 16, Direction.EAST, DyeColor.CYAN, "Duerme aquí", "y reaparecerás", "en la ciudad", "");
        set(x0 + 6, G + 3, z0 + 14, light(14));
        // taller: todas las mesas de trabajo
        String[] east = {"crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom", "cartography_table"};
        for (int k = 0; k < east.length; k++) set(x0 + 17, G, z0 + 11 + k, face(east[k], Direction.WEST));
        String[] south = {"barrel", "blast_furnace", "furnace", "brewing_stand", "water_cauldron", "fletching_table"};
        for (int k = 0; k < south.length; k++) {
            BlockState st = south[k].equals("water_cauldron") ? w(b("water_cauldron"), BlockStateProperties.LEVEL_CAULDRON, 3)
                    : face(south[k], Direction.NORTH);
            set(x0 + 11 + k, G, z0 + 17, st);
        }
        set(x0 + 11, G, z0 + 11, b("ender_chest"));
        set(x0 + 12, G, z0 + 11, face("chest", Direction.SOUTH));
        set(x0 + 16, G, z0 + 11, face("chest", Direction.SOUTH));
        set(x0 + 14, G + 3, z0 + 14, light(15));
    }

    // ------------------------------------------------------------ paseo, parques y estación

    private void boulevard(int x0, int z0) {
        int cx = x0 + 10;
        for (int x = x0; x <= x0 + 20; x++) {
            for (int z = z0; z <= z0 + 20; z++) {
                set(x, G - 1, z, (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) ? b("polished_andesite") : b("smooth_stone"));
            }
        }
        // canal de agua con luces en el centro
        for (int z = z0 + 4; z <= z0 + 16; z++) {
            set(cx, G - 2, z, b("prismarine_bricks"));
            set(cx, G - 1, z, b("water"));
            set(cx - 1, G, z, slab("smooth_quartz_slab", false));
            set(cx + 1, G, z, slab("smooth_quartz_slab", false));
            if (Math.floorMod(z, 4) == 0) set(cx, G - 2, z, b("sea_lantern"));
        }
        // hileras de árboles y bancos
        for (int z = z0 + 3; z <= z0 + 17; z += 5) {
            for (int x : new int[]{x0 + 4, x0 + 16}) {
                set(x, G - 1, z, b("grass_block"));
                tree(x, G, z, 5, "oak_log", "oak_leaves");
                bench(x + (x < cx ? 2 : -2), z, x < cx ? Direction.WEST : Direction.EAST, 1, false);
            }
        }
        // boca de metro
        int mx = x0 + 3;
        int mz = z0 + 13;
        fill(mx - 1, G - 6, mz - 1, mx + 3, G - 1, mz + 6, b("stone_bricks"));
        for (int k = 0; k < 5; k++) {
            fill(mx, G - 1 - k, mz + k, mx + 2, G - 1 - k, mz + k, stairs("stone_brick_stairs", Direction.NORTH, false));
            fill(mx, G - k, mz + k, mx + 2, G + 1, mz + k, Blocks.AIR.defaultBlockState());
        }
        fill(mx, G - 5, mz + 5, mx + 2, G - 3, mz + 5, Blocks.AIR.defaultBlockState());
        set(mx + 1, G - 2, mz + 5, b("sea_lantern"));
        fill(mx - 1, G, mz - 1, mx + 3, G, mz - 1, pane("iron_bars", true));
        fill(mx - 1, G, mz, mx - 1, G, mz + 5, pane("iron_bars", false));
        fill(mx + 3, G, mz, mx + 3, G, mz + 5, pane("iron_bars", false));
        fill(mx, G + 2, mz - 1, mx + 2, G + 2, mz - 1, b("blue_concrete"));
        sign(mx + 1, G + 2, mz - 2, Direction.NORTH, DyeColor.WHITE, "METRO", "Plaza del", "Sistema", "");
        sign(cx + 2, G, z0 + 1, Direction.NORTH, DyeColor.WHITE, "PASEO SUR", "Parque Central", "y río", "");
        set(cx + 2, G - 1, z0 + 1, b("polished_andesite"));
        // puestos callejeros: helados y flores
        stall(cx - 6, z0 + 6, "light_blue_wool", "white_wool", "HELADOS", "pink_concrete");
        stall(cx + 3, z0 + 15, "lime_wool", "white_wool", "FLORES", "potted_red_tulip");
    }

    /** Puesto callejero: mostrador, toldo a rayas, cartel y algo a la venta encima. */
    private void stall(int x, int z, String a, String c, String name, String goods) {
        if (!air(x, G, z) || !air(x + 2, G, z)) return;
        fill(x, G, z, x + 2, G, z, b("white_concrete"));
        set(x + 1, G + 1, z, b(goods));
        set(x, G + 1, z, b("spruce_fence"));
        set(x + 2, G + 1, z, b("spruce_fence"));
        set(x, G + 2, z, b("spruce_fence"));
        set(x + 2, G + 2, z, b("spruce_fence"));
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) set(x + dx, G + 3, z + dz, b(Math.floorMod(dx + dz, 2) == 0 ? a : c));
        }
        sign(x + 1, G + 2, z + 1, Direction.SOUTH, DyeColor.WHITE, "", name, "", "");
        set(x + 1, G + 2, z - 1, light(12));
    }

    private void park(int x0, int z0, int i, int j, boolean big) {
        RandomSource r = rnd(i, j, 7);
        int cx = x0 + 10;
        int cz = z0 + 10;
        for (int x = x0 + 2; x <= x0 + 18; x++) {
            for (int z = z0 + 2; z <= z0 + 18; z++) set(x, G - 1, z, b("grass_block"));
        }
        // lago
        double rx = big ? 5.5 : 4.0;
        double rz = big ? 4.0 : 3.0;
        int lx = cx + (big ? 0 : -3);
        int lz = cz + (big ? 2 : -3);
        for (int x = lx - 6; x <= lx + 6; x++) {
            for (int z = lz - 5; z <= lz + 5; z++) {
                double d = Math.pow((x - lx) / rx, 2) + Math.pow((z - lz) / rz, 2);
                if (d < 1.0) {
                    set(x, G - 3, z, b("clay"));
                    set(x, G - 2, z, b("water"));
                    set(x, G - 1, z, b("water"));
                    if ((hash(x, z) & 15) == 0) set(x, G, z, b("lily_pad"));
                } else if (d < 1.35) {
                    set(x, G - 1, z, b("sand"));
                }
            }
        }
        // caminos
        for (int x = x0 + 2; x <= x0 + 18; x++) {
            if (!get(x, G - 1, z0 + 4).is(Blocks.WATER)) set(x, G - 1, z0 + 4, b("dirt_path"));
            if (!get(x, G - 1, z0 + 16).is(Blocks.WATER)) set(x, G - 1, z0 + 16, b("dirt_path"));
        }
        for (int z = z0 + 2; z <= z0 + 18; z++) {
            if (!get(x0 + 16, G - 1, z).is(Blocks.WATER)) set(x0 + 16, G - 1, z, b("dirt_path"));
            if (!get(x0 + 4, G - 1, z).is(Blocks.WATER)) set(x0 + 4, G - 1, z, b("dirt_path"));
        }
        // árboles, flores y bancos
        for (int k = 0; k < 14; k++) {
            int x = x0 + 3 + r.nextInt(15);
            int z = z0 + 3 + r.nextInt(15);
            if (!get(x, G - 1, z).is(Blocks.GRASS_BLOCK) || !air(x, G, z)) continue;
            int t = r.nextInt(5);
            if (t == 0) cherry(x, G, z, 4);
            else if (t == 1) tree(x, G, z, 5, "birch_log", "birch_leaves");
            else if (t == 2) tree(x, G, z, 5, "oak_log", "oak_leaves");
            else flowers(x, z, hash(x, z));
        }
        bench(x0 + 6, z0 + 5, Direction.NORTH, 3, true);
        bench(x0 + 11, z0 + 15, Direction.SOUTH, 3, true);
        for (int[] c : new int[][]{{x0 + 4, z0 + 9}, {x0 + 16, z0 + 9}, {x0 + 10, z0 + 4}, {x0 + 10, z0 + 16}}) {
            if (air(c[0] + 1, G, c[1])) {
                set(c[0] + 1, G, c[1], b("polished_blackstone_wall"));
                set(c[0] + 1, G + 1, c[1], b("lantern"));
            }
        }
        set(x0 + 5, G, z0 + 3, b("cauldron"));
        if (big) sign(cx, G + 1, z0 + 2, Direction.NORTH, DyeColor.LIME, "PARQUE", "CENTRAL", "", "");
        if (big) set(cx, G, z0 + 2, b("oak_fence"));
    }

    private void playground(int x0, int z0, int i, int j) {
        park(x0, z0, i, j, false);
        int x = x0 + 11;
        int z = z0 + 10;
        // arenero
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == 4 || dz == 4;
                set(x + dx, G - 1, z + dz, edge ? b("spruce_planks") : b("sand"));
                if (!edge) set(x + dx, G, z + dz, Blocks.AIR.defaultBlockState());
            }
        }
        // tobogán y columpios
        fill(x0 + 13, G, z0 + 5, x0 + 13, G + 2, z0 + 5, b("yellow_concrete"));
        set(x0 + 13, G + 3, z0 + 5, slab("smooth_quartz_slab", false));
        for (int k = 1; k <= 3; k++) set(x0 + 13, G + 3 - k, z0 + 5 + k, stairs("smooth_quartz_stairs", Direction.NORTH, false));
        fill(x0 + 6, G, z0 + 13, x0 + 6, G + 3, z0 + 13, b("spruce_fence"));
        fill(x0 + 9, G, z0 + 13, x0 + 9, G + 3, z0 + 13, b("spruce_fence"));
        fill(x0 + 6, G + 4, z0 + 13, x0 + 9, G + 4, z0 + 13, b("stripped_spruce_log"));
        set(x0 + 7, G + 3, z0 + 13, b("chain"));
        set(x0 + 8, G + 3, z0 + 13, b("chain"));
        set(x0 + 7, G + 2, z0 + 13, b("chain"));
        set(x0 + 8, G + 2, z0 + 13, b("chain"));
        set(x0 + 7, G + 1, z0 + 13, slab("spruce_slab", false));
        set(x0 + 8, G + 1, z0 + 13, slab("spruce_slab", false));
    }

    private void station(int x0, int z0) {
        int x1 = x0 + 2;
        int x2 = x0 + 18;
        int z1 = z0 + 3;
        int z2 = z0 + 17;
        RandomSource r = rnd(0, 3, 8);
        fill(x0, G - 1, z0, x0 + 20, G - 1, z0 + 20, b("polished_andesite"));
        shell(x1, z1, x2, z2, G, G + 9, 2, b("bricks"), b("glass"), b("stone_bricks"), 4, r, false);
        // bóveda de cristal sobre arcos de hierro
        for (int k = 0; k <= 4; k++) {
            fill(x1 + k, G + 10 + k, z1, x1 + k, G + 10 + k, z2, b("glass"));
            fill(x2 - k, G + 10 + k, z1, x2 - k, G + 10 + k, z2, b("glass"));
            for (int z = z1; z <= z2; z += 4) {
                set(x1 + k, G + 10 + k, z, b("iron_block"));
                set(x2 - k, G + 10 + k, z, b("iron_block"));
            }
        }
        fill(x1 + 5, G + 14, z1, x2 - 5, G + 14, z2, b("glass"));
        for (int z = z1; z <= z2; z += 4) fill(x1 + 5, G + 14, z, x2 - 5, G + 14, z, b("iron_block"));
        fill(x1, G + 10, z1, x2, G + 10, z1, b("stone_bricks"));
        fill(x1, G + 10, z2, x2, G + 10, z2, b("stone_bricks"));
        // reloj sobre la entrada
        int cx = x0 + 10;
        fill(cx - 2, G + 10, z1 - 1, cx + 2, G + 13, z1 - 1, b("stone_bricks"));
        fill(cx - 1, G + 11, z1 - 1, cx + 1, G + 12, z1 - 1, b("white_concrete"));
        set(cx, G + 12, z1 - 1, b("black_concrete"));
        set(cx, G + 11, z1 - 1, b("sea_lantern"));
        // puerta al norte (a la ciudad)
        fill(cx - 2, G, z1, cx + 2, G + 3, z1, Blocks.AIR.defaultBlockState());
        sign(cx, G + 5, z1 - 1, Direction.NORTH, DyeColor.WHITE, "ESTACIÓN", "CENTRAL", "Línea del río", "");
        // andén: bancos, panel de horarios y línea amarilla
        for (int x = x1 + 2; x <= x2 - 4; x += 5) bench(x, z0 + 8, Direction.NORTH, 3, true);
        fill(x1 + 1, G - 1, z0 + 11, x2 - 1, G - 1, z0 + 11, b("yellow_concrete"));
        fill(cx - 3, G + 3, z0 + 4, cx + 3, G + 5, z0 + 4, b("black_concrete"));
        fill(cx - 2, G + 4, z0 + 4, cx + 2, G + 4, z0 + 4, b("orange_stained_glass"));
        sign(cx, G + 3, z0 + 5, Direction.SOUTH, DyeColor.ORANGE, "SALIDAS", "Río · 5 min", "Puertas · 8 min", "Asociación · 3 min");
        // vía y tren parado en el andén
        for (int x = x1 + 1; x <= x2 - 1; x++) {
            fill(x, G - 1, z0 + 13, x, G - 1, z0 + 15, b("gravel"));
            set(x, G - 1, z0 + 14, b("polished_andesite"));
        }
        set(x1 + 1, G, z0 + 14, w(b("rail"), BlockStateProperties.RAIL_SHAPE,
                net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
        set(x2 - 1, G, z0 + 14, w(b("rail"), BlockStateProperties.RAIL_SHAPE,
                net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
        for (int x = x1 + 2; x <= x2 - 2; x++) {
            boolean nose = x == x2 - 2;
            boolean door = Math.floorMod(x - x1, 5) == 4;
            for (int z = z0 + 13; z <= z0 + 15; z++) {
                set(x, G, z, b(nose ? "yellow_concrete" : "gray_concrete"));
                set(x, G + 1, z, b(nose ? "yellow_concrete" : (z == z0 + 13 ? "blue_concrete" : "white_concrete")));
                set(x, G + 2, z, b(nose ? "black_stained_glass" : (z == z0 + 13 && !door ? "light_blue_stained_glass" : "white_concrete")));
                set(x, G + 3, z, b("light_gray_concrete"));
            }
            if (door) {
                set(x, G + 1, z0 + 13, b("gray_stained_glass"));
                set(x, G + 2, z0 + 13, b("gray_stained_glass"));
            }
            set(x, G + 2, z0 + 14, light(12));
        }
        set(x2 - 2, G + 1, z0 + 13, b("ochre_froglight"));
        for (int x = x1 + 2; x < x2; x += 4) set(x, G + 6, z0 + 9, light(15));
    }
}
