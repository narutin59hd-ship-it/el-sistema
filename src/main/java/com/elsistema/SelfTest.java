package com.elsistema;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Prueba automática que se ejecuta en GitHub (variable ELSISTEMA_SELFTEST).
 * <ul>
 *   <li>"server": arranca un servidor, crea todos los monstruos, los convierte en sombras, construye y limpia
 *   todas las mazmorras y apaga el servidor.</li>
 *   <li>"client": lo mismo dentro de un mundo de un jugador, pero además despierta al Jugador, le da un ejército
 *   de sombras, mata a una criatura y la extrae con ¡Levántate! para que el cliente lo pueda dibujar.</li>
 * </ul>
 */
public final class SelfTest {
    private SelfTest() {}

    /** 0 = en curso, 1 = todo bien, 2 = fallo. El cliente lo lee para saber cuándo seguir. */
    public static volatile int state = 0;
    /**
     * El cliente pide los pasos de la prueba del cambio de clase: 1 empezar, 2 saltar al jefe secreto,
     * 3 derrotarlo, 4 comprobar la mutación y usar las habilidades mutadas de las 5 clases.
     * Después, el examen de ascenso y el prestigio: 5 matar una sombra y empezar el examen, 6 saltar al examinador,
     * 7 derrotarlo, 8 comprobar el ascenso y llegar al nivel 500, 9 hacer el prestigio y comprobar la sombra caída.
     */
    public static volatile int trialRequest = 0;
    private static int trialDone = 0;
    private static Data.ShadowRec fallen;

    private static boolean clientMode = false;
    private static int ticks = 0;
    private static int startAt = -1;
    private static int arises = 0;
    private static final List<Mob> SPAWNED = new ArrayList<>();
    private static final List<Mob> SHADOWS = new ArrayList<>();

    public static void register(String mode) {
        clientMode = "client".equalsIgnoreCase(mode);
        Shadows.selfTest = true;
        ServerTickEvents.END_SERVER_TICK.register(SelfTest::tick);
        ElSistema.LOGGER.info("[SELFTEST] activado (modo {})", clientMode ? "cliente" : "servidor");
    }

    private static void fail(MinecraftServer server, String what, Throwable t) {
        state = 2;
        ElSistema.LOGGER.error("[SELFTEST] FALLO en {}", what, t);
        if (!clientMode) server.halt(false);
    }

    private static void tick(MinecraftServer server) {
        if (state == 2) return;
        if (state == 1) {
            if (clientMode) trialTick(server);
            return;
        }
        ticks++;
        ServerPlayer player = null;
        if (clientMode) {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            player = players.get(0);
            for (ServerPlayer p : players) {
                if (!p.isCreative()) p.setGameMode(GameType.CREATIVE);
            }
            if (startAt < 0) startAt = ticks + 40;
        } else if (startAt < 0) {
            startAt = 40;
        }
        try {
            if (ticks == startAt) {
                world(server, player);
            }
            if (player != null && ticks >= startAt + 30 && ticks <= startAt + 90 && ticks % 20 == 0 && arises < 3) {
                arises++;
                Shadows.arise(player);
                ElSistema.LOGGER.info("[SELFTEST] ¡Levántate! intento {}", arises);
            }
            // 1.7.0 / 1.9.0: la Ciudad de los Cazadores se construye poco a poco y aparecen sus vecinos
            if (player == null && ticks == startAt + 5) cityBuild(server);
            if (player == null && cityPhase == 1 && City.isReady(City.level(server))) {
                cityCheck(server);
                cityPhase = 2;
            }
            if (player == null && cityPhase == 1 && ticks > startAt + 30000) {
                throw new IllegalStateException("la ciudad no se terminó de construir (" + City.buildPercent() + " %)");
            }
            if (ticks >= startAt + 220 && (player != null || cityPhase == 2)) {
                finish(server, player);
            }
        } catch (Throwable t) {
            fail(server, "tick " + ticks, t);
        }
    }

    private static void world(MinecraftServer server, ServerPlayer player) {
        ServerLevel lvl = player != null ? (ServerLevel) player.level() : server.overworld();
        BlockPos base = player != null ? player.blockPosition() : lvl.getSharedSpawnPos();
        List<EntityType<? extends Mob>> types = List.of(ModEntities.GOBLIN, ModEntities.LOBO, ModEntities.OSO,
                ModEntities.ORCO, ModEntities.HORMIGA, ModEntities.CABALLERO, ModEntities.COLOSO, EntityType.ZOMBIE,
                EntityType.SKELETON, EntityType.COW);
        int i = 0;
        for (EntityType<? extends Mob> type : types) {
            int x = base.getX() - 12 + (i % 5) * 6;
            int z = base.getZ() + 14 + (i / 5) * 7;
            int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            Mob m = type.spawn(lvl, new BlockPos(x, y, z), MobSpawnType.COMMAND);
            if (m == null) throw new IllegalStateException("no se pudo crear " + type.getDescriptionId());
            Ranks.apply(m, i % 6, 10 + i * 7, i == 6);
            m.setNoAi(clientMode);
            SPAWNED.add(m);
            i++;
        }
        ElSistema.LOGGER.info("[SELFTEST] {} criaturas creadas", SPAWNED.size());
        for (Mob m : new ArrayList<>(SPAWNED)) {
            Mob s = Shadows.testShadow(m, lvl);
            if (s == null) throw new IllegalStateException("no se pudo crear la sombra de " + m.getType().getDescriptionId());
            if (clientMode) s.setNoAi(true);
            SHADOWS.add(s);
        }
        ElSistema.LOGGER.info("[SELFTEST] {} sombras creadas", SHADOWS.size());
        for (int k = 0; k < Shop.OFFERS.size(); k++) {
            List<ItemStack> got = Shop.make(k);
            if (got.isEmpty() || got.get(0).isEmpty()) throw new IllegalStateException("oferta vacía " + k);
        }
        for (int kind = 0; kind < 3; kind++) {
            int n = kind == 0 ? Trees.RACE.length : kind == 1 ? Trees.CLASS.length : 1;
            for (int t = 0; t < n; t++) {
                for (Trees.Node node : Trees.tree(kind, t)) {
                    for (int r = 0; r <= node.max(); r++) {
                        Trees.effect(node, r);
                    }
                }
            }
        }
        Data d = new Data();
        Data.ShadowRec rec = new Data.ShadowRec();
        rec.id = 1;
        rec.type = "minecraft:zombie";
        rec.nbt = "{id:\"minecraft:zombie\"}";
        d.shadows.add(rec);
        d.prestige = 2;
        d.hunterRank = 4;
        Data back = new Gson().fromJson(new Gson().toJson(d), Data.class);
        back.fix();
        if (back.shadows.size() != 1) throw new IllegalStateException("datos de sombras");
        if (back.prestige != 2 || back.hunterRank != 4) throw new IllegalStateException("datos de rango y prestigio");
        // Datos de antes de la 1.4.0 (sin rango): el rango sale del nivel
        Data old = new Gson().fromJson("{\"awakened\":1,\"race\":0,\"cls\":0,\"level\":70}", Data.class);
        old.fix();
        if (old.rank() != 4) throw new IllegalStateException("migración del rango: " + old.rank());
        // Probabilidad de ¡Levántate!: baja con el rango del muerto y sube con la Perspicacia
        Data hunter = new Data();
        hunter.awakened = 1;
        hunter.race = 0;
        hunter.cls = 0;
        hunter.hunterRank = 2;
        int e = Shadows.ariseChance(hunter, 0, false);
        int s = Shadows.ariseChance(hunter, 5, false);
        hunter.shadowTree[1] = 10;
        int s2 = Shadows.ariseChance(hunter, 5, false);
        if (!(e > s && s2 > s)) throw new IllegalStateException("probabilidades: E " + e + ", S " + s + ", S con Perspicacia " + s2);
        if (Shadows.ariseCost(5, true) <= Shadows.ariseCost(0, false)) throw new IllegalStateException("coste de ¡Levántate!");
        Data.ShadowRec weak = new Data.ShadowRec();
        weak.type = "minecraft:zombie";
        weak.rank = 0;
        weak.level = 10;
        Data.ShadowRec strong = new Data.ShadowRec();
        strong.type = "minecraft:zombie";
        strong.rank = 5;
        strong.level = 10;
        Data.ShadowRec cow = new Data.ShadowRec();
        cow.type = "minecraft:cow";
        cow.rank = 0;
        cow.level = 10;
        if (!(Shadows.attackOf(strong, null) > Shadows.attackOf(weak, null) && Shadows.attackOf(cow, null) > 0)) {
            throw new IllegalStateException("daño de las sombras");
        }
        for (int lv = 1; lv < Defs.MAX_LEVEL; lv++) {
            if (Defs.xpNeeded(lv) <= 0 || Defs.xpNeeded(lv + 1) < Defs.xpNeeded(lv)) throw new IllegalStateException("EXP del nivel " + lv);
        }
        ElSistema.LOGGER.info("[SELFTEST] rango, prestigio y sombras: E {}% · S {}% · S con Perspicacia {}% · ataque S {} / E {}",
                e, s, s2, Math.round(Shadows.attackOf(strong, null)), Math.round(Shadows.attackOf(weak, null)));
        balance15(lvl);
        Dungeons.selfTest(lvl, base);
        ElSistema.LOGGER.info("[SELFTEST] mazmorras construidas y limpiadas (también la prueba y los jefes secretos)");
        secretBosses(lvl, base);
        if (player != null) army(player, lvl);
    }

    /** 1.5.0: monstruos más difíciles (las sombras no), EXP x5 y árbol propio de las sombras. */
    private static void balance15(ServerLevel lvl) {
        Mob tough = EntityType.ZOMBIE.create(lvl);
        if (tough == null) throw new IllegalStateException("no se pudo crear el zombi de prueba");
        Ranks.apply(tough, 3, 50, false);
        double hardHp = tough.getMaxHealth();
        Ranks.clearDifficulty(tough);
        double softHp = tough.getMaxHealth();
        if (Math.abs(hardHp / softHp - Defs.MOB_HP_MULT) > 0.02) {
            throw new IllegalStateException("dificultad: vida " + hardHp + " con / " + softHp + " sin");
        }
        Mob plain = EntityType.ZOMBIE.create(lvl);
        if (plain == null) throw new IllegalStateException("no se pudo crear el zombi normal");
        int zxp = Ranks.killXp(plain);
        if (zxp != Xp.forMob("zombie", true) * Defs.KILL_XP_MULT) throw new IllegalStateException("EXP del zombi: " + zxp);
        // Las sombras suben de nivel con la EXP y ganan puntos para su árbol
        Data.ShadowRec lv = new Data.ShadowRec();
        lv.type = "minecraft:zombie";
        lv.level = 10;
        int gained = Shadows.giveXp(null, new Data(), lv, Defs.xpNeeded(10) + Defs.xpNeeded(11) + 5);
        if (gained != 2 || lv.level != 12 || lv.pts != 2 * ShadowTree.POINTS_PER_LEVEL || lv.xp != 5) {
            throw new IllegalStateException("EXP de las sombras: +" + gained + " nivel " + lv.level + " puntos " + lv.pts + " exp " + lv.xp);
        }
        double before = Shadows.attackOf(lv, null);
        lv.tree[ShadowTree.DMG] = 5;
        if (!(Shadows.attackOf(lv, null) > before * 1.19)) throw new IllegalStateException("el árbol propio no sube el daño");
        if (!ShadowTree.canUp(lv.tree, 2, ShadowTree.CRIT) || ShadowTree.canUp(lv.tree, 1, ShadowTree.CRIT)) {
            throw new IllegalStateException("coste de los nudos del árbol propio");
        }
        // Partidas de antes de la 1.5.0: las sombras no tenían árbol
        Data old = new Gson().fromJson("{\"shadows\":[{\"id\":3,\"type\":\"minecraft:zombie\",\"nbt\":\"{}\",\"level\":7}]}",
                Data.class);
        old.fix();
        Data.ShadowRec o = old.shadows.get(0);
        if (o.tree == null || o.tree.length != ShadowTree.SIZE || o.pts != 0 || o.level != 7) {
            throw new IllegalStateException("migración de las sombras sin árbol");
        }
        for (int k = 0; k < ShadowTree.SIZE; k++) {
            for (int r = 0; r <= ShadowTree.NODES[k].max(); r++) {
                ShadowTree.effect(k, r);
            }
        }
        ElSistema.LOGGER.info("[SELFTEST] 1.5.0: vida de monstruo x{} · EXP de un zombi {} · sombra 10 -> {} con {} puntos · árbol de {} puntos",
                Math.round(hardHp / softHp * 100.0) / 100.0, zxp, lv.level, lv.pts, ShadowTree.totalCost());
    }

    /** Crea los 4 jefes secretos (en fila, detrás de los demás monstruos) y les hace usar todas sus habilidades. */
    private static void secretBosses(ServerLevel lvl, BlockPos base) {
        SecretBoss.Kind[] kinds = SecretBoss.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            int x = base.getX() - 12 + i * 8;
            int z = base.getZ() + 30;
            int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            // Solo uno enseña su barra de jefe (los demás se marcan como de mazmorra para no llenar la pantalla)
            SecretBoss b = SecretBoss.spawn(lvl, kinds[i], new BlockPos(x, y, z), 3 + i % 3, 50 + i * 10, i != 3);
            if (b == null) throw new IllegalStateException("no se pudo crear el jefe secreto " + kinds[i]);
            Mob dummy = EntityType.ZOMBIE.spawn(lvl, new BlockPos(x, y, z + 4), MobSpawnType.COMMAND);
            if (dummy == null) throw new IllegalStateException("no se pudo crear el muñeco de prueba");
            dummy.setNoAi(true);
            b.selfTestAbilities(lvl, dummy);
            dummy.discard();
            b.setTarget(null);
            if (clientMode) b.setNoAi(true);
            Mob s = Shadows.testShadow(b, lvl);
            if (s == null) throw new IllegalStateException("no se pudo crear la sombra del jefe secreto " + kinds[i]);
            if (clientMode) s.setNoAi(true);
            SHADOWS.add(s);
            SPAWNED.add(b);
        }
        ElSistema.LOGGER.info("[SELFTEST] {} jefes secretos creados y probados", kinds.length);
    }

    private static void trialTick(MinecraftServer server) {
        int want = trialRequest;
        if (want <= trialDone) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        trialDone = want;
        try {
            trialStep(players.get(0), want);
        } catch (Throwable t) {
            fail(server, "prueba del cambio de clase (paso " + want + ")", t);
        }
    }

    private static void trialStep(ServerPlayer p, int step) {
        Data d = Store.get(p);
        switch (step) {
            case 1 -> {
                if (d.quest[Quests.Q_CLASS] != Quests.ACTIVE) throw new IllegalStateException("la misión del cambio de clase no se descubrió");
                Quests.startTrial(p);
                if (!Dungeons.inDungeon(p)) throw new IllegalStateException("no empezó la prueba");
                ElSistema.LOGGER.info("[SELFTEST] prueba del cambio de clase empezada");
            }
            case 2 -> {
                if (!Dungeons.testSkipToBoss(p)) throw new IllegalStateException("no se pudo saltar al jefe");
                ElSistema.LOGGER.info("[SELFTEST] saltando al jefe secreto de la prueba");
            }
            case 3 -> {
                int n = Dungeons.testWin(p);
                if (n <= 0) throw new IllegalStateException("no había nadie que derrotar en la prueba (" + n + ")");
                ElSistema.LOGGER.info("[SELFTEST] {} enemigos derrotados en la prueba", n);
            }
            case 4 -> {
                if (d.mutated != 1 || d.quest[Quests.Q_CLASS] != Quests.DONE) {
                    throw new IllegalStateException("la clase no mutó tras superar la prueba");
                }
                int cls = d.cls;
                for (int c = 0; c < Defs.CLASS_NAME.length; c++) {
                    d.cls = c;
                    d.cooldown = 0;
                    SystemLogic.applyAll(p, d);
                    SystemLogic.useSkill(p);
                    if (d.cooldown <= 0) throw new IllegalStateException("no se usó la habilidad mutada de " + Defs.MUTATION_NAME[c]);
                }
                d.cls = cls;
                d.cooldown = 0;
                SystemLogic.applyAll(p, d);
                SystemLogic.sync(p, d);
                ElSistema.LOGGER.info("[SELFTEST] OK prueba del cambio de clase: {} y las 5 habilidades mutadas",
                        Defs.MUTATION_NAME[cls]);
            }
            case 5 -> {
                fallen = Shadows.testKillOne(p);
                if (fallen == null || fallen.state != Shadows.RECOVERING) throw new IllegalStateException("la sombra no cayó");
                long left = Shadows.downLeft(fallen, p.getServer().overworld());
                if (left < Defs.SHADOW_DOWN_TICKS - 200) throw new IllegalStateException("la sombra caída no quedó 3 días: " + left);
                ElSistema.LOGGER.info("[SELFTEST] sombra caída: inhabilitada {} ticks", left);
                if (d.rank() != 0 || !Ranking.examReady(d)) throw new IllegalStateException("el examen al rango D no está disponible");
                Ranking.startExam(p);
                if (!Dungeons.inDungeon(p)) throw new IllegalStateException("no empezó el examen de ascenso");
                ElSistema.LOGGER.info("[SELFTEST] examen de ascenso al rango D empezado");
            }
            case 6 -> {
                if (!Dungeons.testSkipToBoss(p)) throw new IllegalStateException("no se pudo saltar al examinador");
            }
            case 7 -> {
                int n = Dungeons.testWin(p);
                if (n <= 0) throw new IllegalStateException("no había nadie en el examen (" + n + ")");
            }
            case 8 -> {
                if (d.rank() != 1) throw new IllegalStateException("no se subió al rango D tras el examen: " + d.rank());
                ElSistema.LOGGER.info("[SELFTEST] OK examen de ascenso: rango {}", Defs.RANK[d.rank()]);
                d.level = Defs.MAX_LEVEL;
                d.xp = 0;
                SystemLogic.applyAll(p, d);
                SystemLogic.sync(p, d);
            }
            case 9 -> {
                if (Dungeons.inDungeon(p) || d.inDungeon == 1) throw new IllegalStateException("sigue dentro del examen");
                int cls = d.cls;
                Ranking.prestige(p);
                if (d.prestige != 1 || d.level != 1) throw new IllegalStateException("el prestigio no se hizo: " + d.prestige + " / nivel " + d.level);
                boolean weapon = false;
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    if (p.getInventory().getItem(i).is(ModItems.SOBERANO[cls])) weapon = true;
                }
                if (!weapon) throw new IllegalStateException("no se recibió el arma del Monarca");
                if (fallen != null) {
                    fallen.recoverTick = 1L;
                    fallen.recoverDay = 0L;
                }
                ElSistema.LOGGER.info("[SELFTEST] OK prestigio: {} con {}", Defs.prestigeName(d.prestige),
                        ModItems.SOBERANO[cls].getDescriptionId());
            }
            case 10 -> {
                if (fallen != null && fallen.state == Shadows.RECOVERING) throw new IllegalStateException("la sombra caída no volvió");
                ElSistema.LOGGER.info("[SELFTEST] OK la sombra caída volvió al pasar el tiempo");
            }
            case 11, 12, 13, 14 -> gallery(p, step - 11);
            case 15 -> animRise(p);
            case 16 -> animArise(p);
            case 17, 18, 19, 20, 21, 22, 23, 24, 25, 26 -> cityStep(p, step);
            case 27, 28, 29 -> lightStep(p, step);
            case 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51 -> tourStep(p, step);
            default -> {
            }
        }
    }

    // ================================================================ 1.7.0: la ciudad

    private static int cityPhase = 0;

    private static void cityBuild(MinecraftServer server) {
        ServerLevel city = City.level(server);
        if (city == null) throw new IllegalStateException("no existe la dimensión de la ciudad");
        if (!City.building()) throw new IllegalStateException("la ciudad no se está construyendo en un mundo nuevo");
        City.budgetMs = 300;
        cityPhase = 1;
        ElSistema.LOGGER.info("[SELFTEST] la ciudad se está construyendo ({} %)", City.buildPercent());
    }

    private static void cityCheck(MinecraftServer server) {
        ServerLevel city = City.level(server);
        if (!City.missingBlocks.isEmpty()) throw new IllegalStateException("bloques que no existen en la ciudad: " + City.missingBlocks);
        int G = City.G;
        Object[][] want = {
                {new BlockPos(0, G, 0), net.minecraft.world.level.block.Blocks.BEACON, "el faro del Cristal del Sistema"},
                {new BlockPos(0, G + 1, 0), net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS, "el haz del Cristal"},
                {new BlockPos(0, G + 72, -28), net.minecraft.world.level.block.Blocks.POLISHED_DIORITE, "el suelo del mirador"},
                {new BlockPos(CityBuilder.LIFT_LOBBY_X1, G - 1, CityBuilder.LIFT_LOBBY_Z), net.minecraft.world.level.block.Blocks.PEARLESCENT_FROGLIGHT,
                        "el ascensor del vestíbulo"},
                {new BlockPos(CityBuilder.ARENA_PAD_X, CityBuilder.ARENA_FLOOR - 1, CityBuilder.ARENA_PAD_Z),
                        net.minecraft.world.level.block.Blocks.LODESTONE, "la salida de la arena"},
                {new BlockPos(0, G - 2, CityBuilder.RIVER_Z1 + 3), net.minecraft.world.level.block.Blocks.WATER, "el río"},
                {new BlockPos(CityBuilder.HOME_X0 + 6, G, CityBuilder.HOME_Z0 + 6), net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE,
                        "la mesa de encantamientos del apartamento"}};
        for (Object[] w : want) {
            BlockPos pos = (BlockPos) w[0];
            if (!city.getBlockState(pos).is((net.minecraft.world.level.block.Block) w[1])) {
                throw new IllegalStateException("falta " + w[2] + " en " + pos.toShortString() + " (hay " + city.getBlockState(pos) + ")");
            }
        }
        if (City.lastRoles != CityNpc.ROLES) {
            throw new IllegalStateException("faltan vecinos en la ciudad: " + City.lastRoles + " de " + CityNpc.ROLES);
        }
        ElSistema.LOGGER.info("[SELFTEST] OK ciudad nueva: {} sitios comprobados y {} vecinos en su sitio", want.length, City.lastRoles);
    }

    private static CityNpc npc(ServerPlayer p, int role) {
        BlockPos h = City.npcHome(role);
        List<CityNpc> l = p.serverLevel().getEntitiesOfClass(CityNpc.class, new net.minecraft.world.phys.AABB(h.getX() - 16,
                h.getY() - 4, h.getZ() - 16, h.getX() + 16, h.getY() + 8, h.getZ() + 16), n -> n.role() == role);
        if (l.isEmpty()) throw new IllegalStateException("no está " + CityNpc.NAME[role] + " en la ciudad");
        return l.get(0);
    }

    /** Pone al Jugador a 3 bloques delante de un tendero, mirándole. */
    private static void faceNpc(ServerPlayer p, int role) {
        BlockPos h = City.npcHome(role);
        boolean south = City.facesSouth(role);
        double z = h.getZ() + 0.5 + (south ? 3.5 : -3.5);
        p.teleportTo(p.serverLevel(), h.getX() + 0.5, City.G, z, south ? 180.0F : 0.0F, 8.0F);
    }

    private static void cityStep(ServerPlayer p, int step) {
        Data d = Store.get(p);
        switch (step) {
            case 17 -> {
                d.essences += 500;
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                City.travel(p, d);
                if (!City.inCity(p)) throw new IllegalStateException("no se viajó a la ciudad");
                ElSistema.LOGGER.info("[SELFTEST] en la Ciudad de los Cazadores");
            }
            case 18 -> {
                int made = City.checkNpcs(p.serverLevel());
                ElSistema.LOGGER.info("[SELFTEST] {} vecinos puestos en la ciudad", made);
                npc(p, CityNpc.SMITH);
                faceNpc(p, CityNpc.SMITH);
            }
            case 19 -> {
                City.interact(p, npc(p, CityNpc.SMITH));
                Forge.action(p, 0, 0);
                Forge.action(p, 0, 0);
                Forge.action(p, 0, 0);
                Forge.action(p, 0, 1);
                Forge.action(p, 3, 1);
                ItemStack sword = p.getMainHandItem();
                if (Forge.level(sword) != 3 || Forge.hidden(sword) < 0) {
                    throw new IllegalStateException("la herrería no mejoró la espada: +" + Forge.level(sword) + " oculta " + Forge.hidden(sword));
                }
                if (Forge.hidden(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) < 0) {
                    throw new IllegalStateException("la herrería no despertó la habilidad de la armadura");
                }
                float before = (float) p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                ElSistema.LOGGER.info("[SELFTEST] OK herrería: espada +3 ({} de daño) con {}", before,
                        Forge.abilityName(sword, Forge.hidden(sword)));
            }
            case 20 -> {
                faceNpc(p, CityNpc.RECEPTION);
                City.interact(p, npc(p, CityNpc.RECEPTION));
                SideQuests.action(p, SideQuests.Q_FIRST, 0);
                SideQuests.action(p, SideQuests.Q_GOBLINS, 0);
                if (d.side[SideQuests.Q_FIRST] != SideQuests.ACTIVE) throw new IllegalStateException("no se aceptó el contrato");
                SideQuests.onDungeonClear(p, d, 0);
                SideQuests.onDungeonClear(p, d, 0);
                SideQuests.onDungeonClear(p, d, 3);
                if (d.side[SideQuests.Q_FIRST] != SideQuests.READY) throw new IllegalStateException("el contrato no se completó");
                SystemLogic.sync(p, d);
            }
            case 21 -> {
                long coins = d.coins;
                SideQuests.action(p, SideQuests.Q_FIRST, 1);
                if (d.side[SideQuests.Q_FIRST] != SideQuests.CLAIMED || d.coins <= coins) {
                    throw new IllegalStateException("no se cobró la recompensa oculta");
                }
                ElSistema.LOGGER.info("[SELFTEST] OK contrato cobrado: {}", SideQuests.REWARD[SideQuests.Q_FIRST]);
                faceNpc(p, CityNpc.ALCHEMIST);
            }
            case 22 -> {
                City.interact(p, npc(p, CityNpc.ALCHEMIST));
                CityShop.buy(p, CityNpc.ALCHEMIST, 0);
            }
            case 23 -> {
                faceNpc(p, CityNpc.MERCHANT);
                int pts = d.points;
                City.interact(p, npc(p, CityNpc.MERCHANT));
                CityShop.buy(p, CityNpc.MERCHANT, 0);
                if (d.points != pts + 1) throw new IllegalStateException("el elixir de estadística no dio el punto");
            }
            case 24 -> {
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), 70.5, City.G + 72, 128.5, 152.0F, 24.0F);
            }
            case 25 -> {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                // la ciudad usa la hora del mundo normal (los demás mundos no pueden cambiarla)
                p.getServer().overworld().setDayTime(18000L);
                p.teleportTo(p.serverLevel(), 0.5, City.G, 9.5, 180.0F, -14.0F);
            }
            case 26 -> {
                City.back(p, d);
                if (City.inCity(p)) throw new IllegalStateException("no se volvió de la ciudad");
                ElSistema.LOGGER.info("[SELFTEST] OK vuelta de la ciudad");
            }
            default -> {
            }
        }
    }

    // ================================================================ 1.9.0: la ciudad nueva (capturas del cliente)

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
        p.resetFallDistance();
    }

    private static void tourStep(ServerPlayer p, int step) {
        Data d = Store.get(p);
        ServerLevel lvl = p.serverLevel();
        int G = City.G;
        switch (step) {
            case 30 -> {
                p.getServer().overworld().setDayTime(5000L);
                p.getServer().overworld().setWeatherParameters(20000, 0, false, false);
                p.getInventory().clearContent();
                if (!City.inCity(p)) City.travel(p, d);
                if (!City.inCity(p)) throw new IllegalStateException("no se viajó a la ciudad nueva");
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                look(p, 13.5, G + 7, 19.5, 146.0F, 14.0F);
                ElSistema.LOGGER.info("[SELFTEST] paseo por la ciudad nueva");
            }
            case 31 -> look(p, 0.5, G + 30, 62.5, 180.0F, 12.0F);
            case 32 -> look(p, 0.5, G, -21.5, 180.0F, 6.0F);
            case 33 -> look(p, CityBuilder.LIFT_LOBBY_X1 + 1.0, G, CityBuilder.LIFT_LOBBY_Z + 1.0, 0.0F, 0.0F);
            case 34 -> {
                if (Math.abs(p.getY() - CityBuilder.DECK_Y) > 0.6) {
                    throw new IllegalStateException("el ascensor no subió al mirador (y = " + p.getY() + ")");
                }
                ElSistema.LOGGER.info("[SELFTEST] OK ascensor: en el mirador a {} bloques de altura", (int) (p.getY() - G));
                look(p, 0.5, CityBuilder.DECK_Y, -26.5, 0.0F, 22.0F);
            }
            case 35 -> {
                p.setHealth(6.0F);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 600, 0));
                faceNpc(p, CityNpc.HEALER);
            }
            case 36 -> {
                City.interact(p, npc(p, CityNpc.HEALER));
                CityShop.buy(p, CityNpc.HEALER, 2);
                if (p.getHealth() < p.getMaxHealth() - 0.01F || p.hasEffect(net.minecraft.world.effect.MobEffects.POISON)) {
                    throw new IllegalStateException("la revisión completa no curó (vida " + p.getHealth() + ")");
                }
                ElSistema.LOGGER.info("[SELFTEST] OK Centro Médico: revisión completa");
            }
            case 37 -> faceNpc(p, CityNpc.COOK);
            case 38 -> {
                City.interact(p, npc(p, CityNpc.COOK));
                CityShop.buy(p, CityNpc.COOK, 0);
                if (p.getInventory().countItem(net.minecraft.world.item.Items.COOKED_BEEF) < 8) throw new IllegalStateException("el Comedor no dio el menú");
                ElSistema.LOGGER.info("[SELFTEST] OK Comedor: menú del cazador");
            }
            case 39 -> faceNpc(p, CityNpc.SHOPKEEPER);
            case 40 -> {
                City.interact(p, npc(p, CityNpc.SHOPKEEPER));
                CityShop.buy(p, CityNpc.SHOPKEEPER, 0);
                if (p.getInventory().countItem(net.minecraft.world.item.Items.TORCH) < 32) throw new IllegalStateException("la Tienda 24 h no dio antorchas");
                ElSistema.LOGGER.info("[SELFTEST] OK Tienda 24 h: antorchas");
                faceNpc(p, CityNpc.GATEKEEPER);
            }
            case 41 -> {
                d.hunterRank = Math.max(d.hunterRank, 2);
                City.interact(p, npc(p, CityNpc.GATEKEEPER));
            }
            case 42 -> {
                CityShop.buy(p, CityNpc.GATEKEEPER, 0);
                if (!Dungeons.gateNear(lvl, CityShop.gatePos(), 2.0)) throw new IllegalStateException("Hugo no abrió la puerta");
                ElSistema.LOGGER.info("[SELFTEST] OK Estación de Puertas: puerta de rango E abierta");
                look(p, CityBuilder.GATE_X + 5.5, G + 1, CityBuilder.GATE_Z + 9.5, 152.0F, 8.0F);
            }
            case 43 -> faceNpc(p, CityNpc.TRAINER);
            case 44 -> {
                City.interact(p, npc(p, CityNpc.TRAINER));
                CityShop.buy(p, CityNpc.TRAINER, 0);
                if (!Arena.fighting(p)) throw new IllegalStateException("no empezó el combate en la arena");
                ElSistema.LOGGER.info("[SELFTEST] OK arena: combate de rango E empezado");
            }
            case 45 -> {
                if (Arena.wave() < 1 || Arena.alive() < 1) {
                    throw new IllegalStateException("no salió la primera oleada (" + Arena.wave() + ", " + Arena.alive() + ")");
                }
                ElSistema.LOGGER.info("[SELFTEST] OK arena: oleada {} con {} monstruos", Arena.wave(), Arena.alive());
            }
            case 46 -> {
                long before = d.coins;
                Arena.selfTestWin(p);
                if (Arena.busy() || d.coins <= before) throw new IllegalStateException("la victoria en la arena no dio el premio");
                ElSistema.LOGGER.info("[SELFTEST] OK arena: victoria (+{} de oro)", d.coins - before);
                City.goTo(p, d, 14);
                if (p.distanceToSqr(CityPlaces.LIST[14].x(), CityPlaces.LIST[14].y(), CityPlaces.LIST[14].z()) > 4.0) {
                    throw new IllegalStateException("no se fue al Parque Central");
                }
                ElSistema.LOGGER.info("[SELFTEST] OK viaje dentro de la ciudad: {}", CityPlaces.LIST[14].name());
                look(p, 0.5, G + 9, 44.5, 0.0F, 26.0F);
            }
            case 47 -> look(p, 30.5, G + 8, 99.5, 45.0F, 12.0F);
            case 48 -> {
                City.goTo(p, d, 12);
                look(p, CityBuilder.HOME_X0 + 16.5, G, CityBuilder.HOME_Z0 + 3.5, 60.0F, 15.0F);
            }
            case 49 -> {
                p.getServer().overworld().setDayTime(12600L);
                look(p, -96.5, G + 64, -104.5, -42.0F, 22.0F);
            }
            case 50 -> {
                p.getServer().overworld().setDayTime(18000L);
                look(p, 52.5, G + 22, 40.5, 135.0F, 12.0F);
            }
            case 51 -> {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.getServer().overworld().setDayTime(6000L);
                City.back(p, d);
                if (City.inCity(p)) throw new IllegalStateException("no se volvió de la ciudad nueva");
                ElSistema.LOGGER.info("[SELFTEST] OK fin del paseo por la ciudad");
            }
            default -> {
            }
        }
    }

    // ================================================================ 1.8.0: tonos de luz (capturas del cliente)

    /** 27: patio de piedra de noche con antorchas, faroles y una hoguera; 28: la luna; 29: el sol. */
    private static void lightStep(ServerPlayer p, int step) {
        ServerLevel lvl = p.getServer().overworld();
        BlockPos spawn = lvl.getSharedSpawnPos();
        BlockPos o = new BlockPos(spawn.getX() - 120, 150, spawn.getZ() + 160);
        switch (step) {
            case 27 -> {
                net.minecraft.world.level.block.state.BlockState floor = net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState();
                net.minecraft.world.level.block.state.BlockState wall = net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                net.minecraft.world.level.block.state.BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                for (int x = -12; x <= 12; x++) {
                    for (int z = -12; z <= 12; z++) {
                        lvl.setBlockAndUpdate(o.offset(x, -1, z), floor);
                        for (int y = 0; y <= 6; y++) {
                            lvl.setBlockAndUpdate(o.offset(x, y, z), air);
                        }
                    }
                }
                for (int x = -7; x <= 7; x++) {
                    for (int y = 0; y <= 4; y++) {
                        lvl.setBlockAndUpdate(o.offset(x, y, -7), wall);
                    }
                }
                for (int z = -6; z <= 0; z++) {
                    for (int y = 0; y <= 3; y++) {
                        lvl.setBlockAndUpdate(o.offset(-7, y, z), wall);
                    }
                }
                net.minecraft.world.level.block.state.BlockState torch = net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, net.minecraft.core.Direction.SOUTH);
                lvl.setBlockAndUpdate(o.offset(-4, 2, -6), torch);
                lvl.setBlockAndUpdate(o.offset(3, 2, -6), torch);
                lvl.setBlockAndUpdate(o.offset(-1, 0, -4), net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());
                lvl.setBlockAndUpdate(o.offset(-5, 0, -2), net.minecraft.world.level.block.Blocks.SOUL_LANTERN.defaultBlockState());
                lvl.setBlockAndUpdate(o.offset(4, 0, -3), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
                lvl.setBlockAndUpdate(o.offset(6, 0, 2), net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState());
                lvl.setDayTime(18000L);
                lvl.setWeatherParameters(12000, 0, false, false);
                p.teleportTo(lvl, o.getX() + 0.5, o.getY(), o.getZ() + 8.5, 180.0F, 10.0F);
                ElSistema.LOGGER.info("[SELFTEST] patio de noche para los tonos de luz");
            }
            case 28 -> {
                lvl.setDayTime(18000L);
                p.teleportTo(lvl, o.getX() + 0.5, o.getY(), o.getZ() + 8.5, 180.0F, -90.0F);
            }
            case 29 -> {
                lvl.setDayTime(6000L);
                p.teleportTo(lvl, o.getX() + 0.5, o.getY(), o.getZ() + 8.5, 180.0F, -90.0F);
            }
            default -> {
            }
        }
    }

    /** Escenario para las capturas de las animaciones de las sombras (suelo liso, de día). */
    private static BlockPos stage(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        BlockPos spawn = lvl.getSharedSpawnPos();
        BlockPos o = new BlockPos(spawn.getX() + 80, 140, spawn.getZ() + 200);
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 12; z++) {
                lvl.setBlockAndUpdate(o.offset(x, -1, z), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        lvl.setDayTime(6000L);
        p.teleportTo(lvl, o.getX() + 0.5, o.getY(), o.getZ() + 6.5, 180.0F, 14.0F);
        return o;
    }

    /** Gira a una sombra recién invocada para que mire a la cámara. */
    private static void faceCamera(ServerPlayer p, Data.ShadowRec rec) {
        Mob m = Shadows.outEntity(p, rec);
        if (m == null) return;
        m.setYRot(0.0F);
        m.setYHeadRot(0.0F);
        m.setYBodyRot(0.0F);
    }

    /** 1.6.0: tres sombras salen del suelo, cada una un poco después que la anterior. */
    private static void animRise(ServerPlayer p) {
        Data d = Store.get(p);
        Shadows.storeAll(p, false);
        BlockPos o = stage(p);
        int i = 0;
        for (Data.ShadowRec rec : d.shadows) {
            if (rec.state != Shadows.STORED) continue;
            net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(o.getX() + 0.5 + (i - 1) * 2.6, o.getY(),
                    o.getZ() + 0.5 - (i == 1 ? 1.0 : 0.0));
            if (Shadows.summon(p, d, rec, at, Shadows.RISE, i * 5)) {
                faceCamera(p, rec);
                i++;
            }
            if (i >= 3) break;
        }
        if (i == 0) throw new IllegalStateException("no salió ninguna sombra para la animación");
        if (!Shadows.animating(Shadows.outEntity(p, d.shadows.stream().filter(r -> r.state == Shadows.OUT).findFirst().orElseThrow()))) {
            throw new IllegalStateException("la sombra invocada no está saliendo del suelo");
        }
        ElSistema.LOGGER.info("[SELFTEST] animación: {} sombras saliendo del suelo", i);
    }

    /** 1.6.0: ¡Levántate! con la animación nueva sobre un caballero maldito recién muerto. */
    private static void animArise(ServerPlayer p) {
        Data d = Store.get(p);
        Shadows.storeAll(p, false);
        BlockPos o = stage(p);
        ServerLevel lvl = p.serverLevel();
        Mob victim = ModEntities.CABALLERO.spawn(lvl, o, MobSpawnType.COMMAND);
        if (victim == null) throw new IllegalStateException("no se pudo crear la víctima de la animación");
        Ranks.apply(victim, 2, 30, false);
        victim.setNoAi(true);
        victim.hurt(p.damageSources().playerAttack(p), 100000.0F);
        if (victim.isAlive()) throw new IllegalStateException("la víctima de la animación no murió");
        int before = d.shadows.size();
        Shadows.forceArise = true;
        try {
            Shadows.arise(p);
        } finally {
            Shadows.forceArise = false;
        }
        if (d.shadows.size() != before + 1) throw new IllegalStateException("¡Levántate! no creó la sombra de la animación");
        Data.ShadowRec rec = d.shadows.get(d.shadows.size() - 1);
        faceCamera(p, rec);
        if (!Shadows.animating(Shadows.outEntity(p, rec))) throw new IllegalStateException("la sombra no está saliendo del suelo");
        ElSistema.LOGGER.info("[SELFTEST] animación de ¡Levántate! empezada (sombra {})", rec.id);
    }

    /** Galería para las capturas: los monstruos en fila, quietos y mirando a la cámara (de día y de noche). */
    private static void gallery(ServerPlayer p, int k) {
        ServerLevel lvl = p.serverLevel();
        Shadows.storeAll(p, false);
        BlockPos spawn = lvl.getSharedSpawnPos();
        int lineup = k == 3 ? 1 : k;
        BlockPos o = new BlockPos(spawn.getX() + 80 + lineup * 48, 140, spawn.getZ() + 80);
        double[] dist = {5.6, 9.5, 5.2};
        lvl.setDayTime(k == 3 ? 18000L : 6000L);
        p.teleportTo(lvl, o.getX() + 0.5, o.getY(), o.getZ() + 0.5 + dist[lineup], 180.0F, k == 1 || k == 3 ? 4.0F : 8.0F);
        if (k < 3) {
            for (int x = -14; x <= 14; x++) {
                for (int z = -12; z <= 14; z++) {
                    lvl.setBlockAndUpdate(o.offset(x, -1, z), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
            List<EntityType<? extends Mob>> types = new ArrayList<>();
            double gap;
            if (k == 0) {
                types.add(ModEntities.GOBLIN);
                types.add(ModEntities.ORCO);
                types.add(ModEntities.CABALLERO);
                types.add(ModEntities.GUARDIAN);
                gap = 2.7;
            } else if (k == 1) {
                types.add(ModEntities.LOBO);
                types.add(ModEntities.COLOSO);
                types.add(ModEntities.PRIMORDIAL);
                types.add(ModEntities.ECLIPSE);
                types.add(ModEntities.OSO);
                gap = 3.9;
            } else {
                types.add(ModEntities.HORMIGA);
                types.add(ModEntities.QUEEN);
                types.add(ModEntities.GOBLIN);
                gap = 3.4;
            }
            for (int i = 0; i < types.size(); i++) {
                double x = o.getX() + 0.5 + (i - (types.size() - 1) / 2.0) * gap;
                Mob m = types.get(i).create(lvl);
                if (m == null) throw new IllegalStateException("galería: no se pudo crear " + types.get(i));
                m.moveTo(x, o.getY(), o.getZ() + 0.5, 0.0F, 0.0F);
                m.setYHeadRot(0.0F);
                m.setYBodyRot(0.0F);
                m.setNoAi(true);
                m.setInvulnerable(true);
                m.setPersistenceRequired();
                lvl.addFreshEntity(m);
                if (k == 2 && i == 2) {
                    // Una sombra (para ver que la capa oscura tapa el brillo)
                    Mob s = Shadows.testShadow(m, lvl);
                    if (s != null) {
                        s.setNoAi(true);
                        s.moveTo(x, o.getY(), o.getZ() + 0.5, 0.0F, 0.0F);
                        s.setYHeadRot(0.0F);
                        s.setYBodyRot(0.0F);
                        SHADOWS.add(s);
                        m.discard();
                    }
                }
            }
        }
        ElSistema.LOGGER.info("[SELFTEST] galería {} preparada", k);
    }

    /** Despierta al Jugador del cliente, le da sombras propias y deja un cadáver para extraer. */
    private static void army(ServerPlayer p, ServerLevel lvl) {
        Data d = Store.get(p);
        d.awakened = 1;
        d.race = 5;
        d.cls = 2;
        d.level = 45;
        d.hunterRank = 0;
        d.points = 3;
        d.skillPoints = 4;
        d.coins = 123456;
        d.raceTree[5][0] = 6;
        d.raceTree[5][1] = 3;
        d.classTree[2][0] = 10;
        d.classTree[2][1] = 2;
        d.classTree[2][2] = 4;
        d.shadowTree[0] = 4;
        d.shadowTree[1] = 2;
        d.shadowTree[2] = 3;
        int id = 1;
        for (int k : new int[]{0, 1, 5, 7}) {
            Data.ShadowRec r = Shadows.recordFrom(SPAWNED.get(k), id++);
            if (r == null) throw new IllegalStateException("registro de sombra");
            d.shadows.add(r);
        }
        d.nextShadow = id;
        // Las sombras suben de nivel contigo y gastan puntos en su árbol propio
        int lvBefore = d.shadows.get(0).level;
        Shadows.gainXp(p, d, 3000);
        Data.ShadowRec first = d.shadows.get(0);
        if (first.level <= lvBefore || first.pts <= 0) throw new IllegalStateException("las sombras no subieron de nivel contigo");
        first.pts += 12;
        int ptsBefore = first.pts;
        Shadows.treeUp(p, first.id, ShadowTree.DMG);
        Shadows.treeUp(p, first.id, ShadowTree.CRIT);
        if (first.tree[ShadowTree.DMG] != 1 || first.tree[ShadowTree.CRIT] != 1
                || first.pts != ptsBefore - 1 - ShadowTree.NODES[ShadowTree.CRIT].cost()) {
            throw new IllegalStateException("árbol propio de la sombra: " + java.util.Arrays.toString(first.tree) + " puntos " + first.pts);
        }
        first.tree[ShadowTree.HP] = 6;
        first.tree[ShadowTree.STEAL] = 3;
        first.tree[ShadowTree.REGEN] = 2;
        ElSistema.LOGGER.info("[SELFTEST] sombras propias: {} subió del nivel {} al {} y tiene {} puntos", first.id, lvBefore, first.level,
                first.pts);
        Item[] hotbar = {ModItems.GUADANA, ModItems.ESPADA, ModItems.HACHA, ModItems.DAGA, ModItems.BACULO,
                ModItems.POCION_MANA, ModItems.ELIXIR, ModItems.PIEDRA_RETORNO, ModItems.RUNE};
        p.getInventory().clearContent();
        for (int k = 0; k < hotbar.length; k++) {
            p.getInventory().setItem(k, new ItemStack(hotbar[k], k >= 5 ? 4 : 1));
        }
        p.getInventory().selected = 0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int k = 0; k < 4; k++) {
            p.setItemSlot(slots[k], new ItemStack(ModItems.ARMOR[ModArmor.SET_MONARCA][k]));
        }
        SystemLogic.applyAll(p, d);
        // Misiones ocultas: el nivel 45 descubre el cambio de clase y da los hitos 10, 20 y 30
        Quests.onLevel(p, d);
        if (d.quest[Quests.Q_CLASS] != Quests.ACTIVE) throw new IllegalStateException("no apareció la misión del cambio de clase");
        if (Integer.bitCount(d.milestones) != 3) throw new IllegalStateException("hitos de nivel: " + d.milestones);
        d.quest[Quests.Q_NIGHT] = Quests.ACTIVE;
        d.questProg[Quests.Q_NIGHT] = 37;
        d.quest[Quests.Q_BOSSES] = Quests.DONE;
        d.questProg[Quests.Q_BOSSES] = Quests.GOAL[Quests.Q_BOSSES];
        d.quest[Quests.Q_ECLIPSE] = Quests.ACTIVE;
        d.questProg[Quests.Q_LEGION] = 4;
        int out = Shadows.summonAll(p);
        if (out <= 0) throw new IllegalStateException("no salió ninguna sombra propia");
        ElSistema.LOGGER.info("[SELFTEST] {} sombras propias invocadas", out);
        Mob victim = ModEntities.ORCO.spawn(lvl, p.blockPosition().offset(0, 0, 3), MobSpawnType.COMMAND);
        if (victim == null) throw new IllegalStateException("no se pudo crear la víctima");
        Ranks.apply(victim, 1, 20, false);
        victim.setNoAi(true);
        victim.hurt(p.damageSources().playerAttack(p), 100000.0F);
        if (victim.isAlive()) throw new IllegalStateException("la víctima no murió");
        SystemLogic.sync(p, d);
        Shadows.sendList(p);
    }

    private static void finish(MinecraftServer server, ServerPlayer player) {
        int alive = 0;
        for (Mob s : SHADOWS) {
            if (s.isAlive()) alive++;
            if (!Shadows.isShadow(s)) throw new IllegalStateException("la sombra perdió la etiqueta");
        }
        if (player != null) {
            Data d = Store.get(player);
            ElSistema.LOGGER.info("[SELFTEST] ejército del Jugador: {} sombras ({} fuera)", d.shadows.size(), Shadows.outCount(player));
            if (d.shadows.size() < 4) throw new IllegalStateException("el Jugador perdió sombras");
            state = 1;
            ElSistema.LOGGER.info("[SELFTEST] OK servidor integrado ({} sombras de prueba vivas)", alive);
            return;
        }
        for (Mob m : SPAWNED) {
            m.discard();
        }
        for (Mob s : SHADOWS) {
            s.discard();
        }
        state = 1;
        ElSistema.LOGGER.info("[SELFTEST] OK ({} sombras vivas tras 220 ticks)", alive);
        server.halt(false);
    }
}
