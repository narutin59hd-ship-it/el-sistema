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
            if (ticks == startAt + 220) {
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
            default -> {
            }
        }
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
