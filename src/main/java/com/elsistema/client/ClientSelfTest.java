package com.elsistema.client;

import com.elsistema.ElSistema;
import com.elsistema.Fx;
import com.elsistema.Net;
import com.elsistema.SelfTest;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Prueba automática del cliente (ELSISTEMA_SELFTEST=client): entra en el mundo de prueba, comprueba texturas,
 * modelos y traducciones, dibuja los monstruos, las sombras, los avisos y todas las pestañas de la ventana del
 * Sistema, hace capturas de pantalla y cierra el juego.
 */
public final class ClientSelfTest {
    private ClientSelfTest() {}

    private static final String WORLD = "selftest";
    private static int ticks = 0;
    private static int titleTicks = 0;
    private static int worldTicks = 0;
    private static int t0 = -1;
    private static boolean worldMode = false;
    private static boolean done = false;
    private static int failures = 0;
    private static int shots = 0;
    /** Espera (sin avanzar los pasos) hasta que se cumpla algo, por ejemplo que acabe de recargar los recursos. */
    private static BooleanSupplier hold = null;
    private static int holdLeft = 0;
    private static String holdWhat = "";

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
        ElSistema.LOGGER.info("[SELFTEST-CLIENTE] activado");
    }

    private static void fail(String what, Throwable t) {
        failures++;
        ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO en {}", what, t);
    }

    private static void tick(Minecraft mc) {
        if (done) return;
        ticks++;
        try {
            if (t0 >= 0) {
                if (hold != null) {
                    boolean ok = hold.getAsBoolean();
                    if (!ok && --holdLeft > 0) {
                        t0++;
                        return;
                    }
                    if (!ok) {
                        failures++;
                        ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: tiempo agotado esperando: {}", holdWhat);
                    } else {
                        ElSistema.LOGGER.info("[SELFTEST-CLIENTE] hecho: {}", holdWhat);
                    }
                    hold = null;
                }
                steps(mc, ticks - t0);
                return;
            }
            if (mc.getOverlay() != null) return;
            if (mc.level == null || mc.player == null) {
                titleTicks++;
                if (titleTicks == 100) openWorld(mc);
                if (titleTicks == 700 && mc.screen != null) {
                    ElSistema.LOGGER.warn("[SELFTEST-CLIENTE] pantalla al abrir el mundo: {}", mc.screen.getClass().getName());
                    shot(mc, "pantalla al abrir el mundo");
                }
                if (titleTicks >= 1400) {
                    failures++;
                    ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: no se pudo entrar al mundo: pruebo solo la ventana");
                    fakeData();
                    begin(false);
                }
                return;
            }
            worldTicks++;
            if (worldTicks == 1) {
                ElSistema.LOGGER.info("[SELFTEST-CLIENTE] mundo cargado");
                checkResources(mc);
                ElSistema.LOGGER.info("[SELFTEST-CLIENTE] paquetes: {} (puestos {})", mc.getResourcePackRepository().getAvailableIds(),
                        mc.getResourcePackRepository().getSelectedIds());
                ElSistema.LOGGER.info("[SELFTEST-CLIENTE] tonos: {}", Tonos.status());
            }
            mc.player.setYRot(0.0F);
            mc.player.setXRot(12.0F);
            if (SelfTest.state != 0 || worldTicks > 1500) {
                if (SelfTest.state != 1) {
                    failures++;
                    ElSistema.LOGGER.error("[SELFTEST-CLIENTE] el servidor integrado no terminó bien (estado {})", SelfTest.state);
                }
                begin(true);
            }
        } catch (Throwable t) {
            fail("tick " + ticks, t);
            finish(mc);
        }
    }

    private static void begin(boolean inWorld) {
        worldMode = inWorld;
        t0 = ticks;
        ElSistema.LOGGER.info("[SELFTEST-CLIENTE] empiezan las pruebas visuales ({})", inWorld ? "en el mundo" : "sin mundo");
    }

    /** Abre el mundo de prueba aunque no haya funcionado --quickPlaySingleplayer (solo en desarrollo). */
    private static void openWorld(Minecraft mc) {
        try {
            Object source = Minecraft.class.getMethod("getLevelSource").invoke(mc);
            Object exists = source.getClass().getMethod("levelExists", String.class).invoke(source, WORLD);
            if (!Boolean.TRUE.equals(exists)) {
                ElSistema.LOGGER.warn("[SELFTEST-CLIENTE] no existe el mundo {}", WORLD);
                return;
            }
            Object flows = Minecraft.class.getMethod("createWorldOpenFlows").invoke(mc);
            for (Method m : flows.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("openWorld") && p.length == 2 && p[0] == String.class && p[1] == Runnable.class) {
                    m.invoke(flows, WORLD, (Runnable) () -> ElSistema.LOGGER.warn("[SELFTEST-CLIENTE] se canceló la carga del mundo"));
                    ElSistema.LOGGER.info("[SELFTEST-CLIENTE] abriendo el mundo de prueba");
                    return;
                }
            }
            ElSistema.LOGGER.warn("[SELFTEST-CLIENTE] no encontré WorldOpenFlows.openWorld");
        } catch (Throwable t) {
            ElSistema.LOGGER.warn("[SELFTEST-CLIENTE] no se pudo abrir el mundo", t);
        }
    }

    private static void checkResources(Minecraft mc) {
        ResourceManager rm = mc.getResourceManager();
        List<String> missing = new ArrayList<>();
        String[] textures = {"entity/goblin", "entity/orco", "entity/caballero", "entity/coloso", "entity/lobo_acero",
                "entity/oso_hielo", "entity/hormiga", "entity/sombra", "entity/guardian_juicio", "entity/bestia_eclipse",
                "entity/reina_carmesi", "entity/coloso_primordial"};
        for (String t : textures) {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "textures/" + t + ".png");
            if (rm.getResource(rl).isEmpty()) missing.add(rl.toString());
        }
        for (String n : com.elsistema.CityNpc.SKIN) {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "textures/entity/ciudadano/" + n + ".png");
            if (rm.getResource(rl).isEmpty()) missing.add(rl.toString());
        }
        for (String set : new String[]{"cazador", "archimago", "caballero_negro", "monarca"}) {
            for (int layer = 1; layer <= 2; layer++) {
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID,
                        "textures/models/armor/" + set + "_layer_" + layer + ".png");
                if (rm.getResource(rl).isEmpty()) missing.add(rl.toString());
            }
        }
        int items = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!ElSistema.MOD_ID.equals(id.getNamespace())) continue;
            items++;
            ResourceLocation model = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/item/" + id.getPath() + ".json");
            if (rm.getResource(model).isEmpty()) missing.add(model.toString());
            if (!id.getPath().endsWith("_huevo")) {
                ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
                if (rm.getResource(tex).isEmpty()) missing.add(tex.toString());
            }
            if (!I18n.exists(item.getDescriptionId())) missing.add("traducción " + item.getDescriptionId());
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (ElSistema.MOD_ID.equals(id.getNamespace()) && !I18n.exists(type.getDescriptionId())) {
                missing.add("traducción " + type.getDescriptionId());
            }
        }
        for (String k : new String[]{"key.elsistema.status", "key.elsistema.skill", "key.elsistema.arise", "key.elsistema.shadows"}) {
            if (!I18n.exists(k)) missing.add("traducción " + k);
        }
        if (missing.isEmpty()) {
            ElSistema.LOGGER.info("[SELFTEST-CLIENTE] recursos OK ({} objetos revisados)", items);
        } else {
            failures++;
            ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: faltan {} recursos: {}", missing.size(), missing);
        }
    }

    private static void fakeData() {
        List<Integer> d = new ArrayList<>();
        int[] base = {1, 5, 2, 45, 1200, 5000, 3, 40, 22, 30, 55, 18, 140, 260, 0, 1, 123456, 4,
                6, 3, 0, 0, 0, 10, 2, 4, 0, 0, 4, 2, 3, 0, 0, 5, 9, 3,
                0, 7, 1, 1, 1, 0, 2, 0, 1, 0, 0, 0, 37, 4, 10, 0, 0, 0, 0,
                3, 0, 1,
                // 1.7.0: esencias, 8 contratos (estado y progreso) y si está en la ciudad
                135, 3, 1, 2, 0, 0, 1, 0, 0, 3, 17, 7, 9, 0, 2, 0, 0, 0};
        for (int v : base) {
            d.add(v);
        }
        CData.set(d);
        List<Integer> nums = new ArrayList<>();
        List<String> strs = new ArrayList<>();
        // id, rango, nivel, estado, vida, caída, bajas|jefe, ataque, EXP, puntos, árbol (7)
        Object[][] army = {
                {1, 2, 24, 1, 80, 0, 14, 95, 450, 3, 2, 1, 0, 0, 1, 0, 0, "elsistema:goblin", "entity.elsistema.goblin"},
                {2, 3, 31, 0, 100, 0, 40, 160, 120, 0, 5, 4, 2, 1, 0, 1, 0, "elsistema:lobo_acero", "entity.elsistema.lobo_acero"},
                {3, 4, 52, 2, 0, 53000, 77, 412, 900, 1, 0, 0, 0, 0, 0, 0, 0, "elsistema:caballero", "entity.elsistema.caballero"},
                {4, 1, 12, 0, 100, 0, 3, 51, 0, 0, 0, 0, 0, 0, 0, 0, 0, "minecraft:zombie", "entity.minecraft.zombie"},
                {5, 5, 60, 1, 55, 0, (1 << 24) | 120, 980, 1000, 7, 20, 20, 15, 10, 10, 10, 10, "elsistema:coloso",
                        "entity.elsistema.coloso"}};
        for (Object[] r : army) {
            for (int i = 0; i < Net.SHADOW_STRIDE; i++) {
                nums.add((Integer) r[i]);
            }
            strs.add((String) r[Net.SHADOW_STRIDE]);
            strs.add((String) r[Net.SHADOW_STRIDE + 1]);
        }
        ClientShadows.set(nums, strs);
    }

    private static void shot(Minecraft mc, String what) {
        shots++;
        ElSistema.LOGGER.info("[SELFTEST-CLIENTE] captura {}: {}", shots, what);
        try {
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                    c -> ElSistema.LOGGER.info("[SELFTEST-CLIENTE] {}", c.getString()));
        } catch (Throwable t) {
            ElSistema.LOGGER.warn("[SELFTEST-CLIENTE] no se pudo hacer la captura", t);
        }
    }

    private static void view(Minecraft mc, int tab, int sub, int cat) {
        SystemScreen s;
        if (mc.screen instanceof SystemScreen open) {
            s = open;
        } else {
            s = new SystemScreen();
            mc.setScreen(s);
        }
        s.selfTestView(tab, sub, cat);
    }

    private static void note(int kind, int a, int b) {
        try {
            Hud.onNotify(kind, a, b);
        } catch (Throwable t) {
            fail("aviso " + kind, t);
        }
    }

    private static void trial(int step) {
        if (worldMode) {
            SelfTest.trialRequest = step;
            ElSistema.LOGGER.info("[SELFTEST-CLIENTE] pido el paso {} de la prueba del cambio de clase", step);
        }
    }

    private static void steps(Minecraft mc, int rt) {
        switch (rt) {
            case 0 -> note(Net.N_CORPSE, 10, 0);
            case 20 -> shot(mc, "mundo, monstruos, jefes secretos, sombras y HUD");
            case 24 -> note(Net.N_ARISE_OK, 1, 2);
            case 32 -> shot(mc, "¡Levántate!");
            case 60 -> {
                int[][] all = {{Net.N_ARISE_FAIL, 2, 0}, {Net.N_ARMY_FULL, 6, 6}, {Net.N_SHADOW_BACK, 45, 1},
                        {Net.N_SHADOW_LEVEL, 1, 24}, {Net.N_NO_SHADOWS, 0, 0}, {Net.N_RANK_REQ, 3, 0}, {Net.N_MANA, 60, 0},
                        {Net.N_RETURN, 0, 0}, {Net.N_SHADOWS_OUT, 3, 0}, {Net.N_SHADOWS_IN, 2, 0}, {Net.N_BOUGHT, 16, 1},
                        {Net.N_TREE_UP, 2, 0}, {Net.N_NO_CORPSE, 0, 0}, {Net.N_ARISE_NO_MP, 40, 10}, {Net.N_GATE_OPEN, 2, 14},
                        {Net.N_RANK_UP, 3, 2}, {Net.N_SHADOW_MARK, -5, 1}, {Net.N_QUEST_FOUND, 1, 0},
                        {Net.N_HIDDEN_REWARD, 3, 0}, {Net.N_SECRET_FLED, 1, 0}, {Net.N_TRIAL_DENIED, 1, 0},
                        {Net.N_SLOTS_UP, 2, 22}, {Net.N_TRIAL_FAIL, 0, 1}, {Net.N_TRIAL_START, 3, 48}, {Net.N_QUEST_DONE, 3, 0},
                        {Net.N_HIDDEN_REWARD, 8, 0}, {Net.N_SKILL, 2, 1}, {Net.N_CORPSE_INFO, 42, 3 + 220},
                        {Net.N_EXAM_READY, 2, 25}, {Net.N_EXAM_FAIL, 2, 1}, {Net.N_EXAM_DENIED, 0, 25},
                        {Net.N_EXAM_DENIED, 3, 0}, {Net.N_PRESTIGE_DENIED, 0, 0}, {Net.N_SHADOW_READY, 1, 0},
                        {Net.N_BOSS, 3, 1}, {Net.N_EXAM_START, 2, 27}, {Net.N_SHADOWS_LEVEL, 3, 5}, {Net.N_SHADOW_TREE, 1, 503}};
                for (int[] n : all) {
                    note(n[0], n[1], n[2]);
                }
            }
            case 72 -> shot(mc, "avisos");
            case 76 -> note(Net.N_SECRET_BOSS, 3, 20);
            case 86 -> shot(mc, "¡jefe secreto!");
            case 90 -> view(mc, 0, 0, 0);
            case 100 -> shot(mc, "pestaña ESTADO");
            case 104 -> view(mc, 1, 0, 0);
            case 114 -> shot(mc, "árbol de raza");
            case 118 -> view(mc, 1, 1, 0);
            case 128 -> shot(mc, "árbol de clase");
            case 132 -> view(mc, 1, 2, 0);
            case 142 -> shot(mc, "árbol de sombras");
            case 146 -> view(mc, 2, 0, 0);
            case 162 -> shot(mc, "pestaña SOMBRAS");
            case 166 -> view(mc, 3, 0, 1);
            case 176 -> shot(mc, "tienda: armas");
            case 180 -> view(mc, 3, 0, 2);
            case 190 -> shot(mc, "tienda: armaduras");
            case 194 -> view(mc, 4, 0, 0);
            case 204 -> shot(mc, "pestaña MISIONES");
            case 208 -> view(mc, 4, 1, 0);
            case 216 -> shot(mc, "misiones: hitos de nivel");
            case 218 -> view(mc, 5, 0, 0);
            case 221 -> shot(mc, "pestaña RANGO (examen disponible)");
            case 222 -> {
                try {
                    ElSistema.LOGGER.info("[SELFTEST-CLIENTE] {} textos de la ventana revisados", SystemScreen.selfCheck());
                } catch (Throwable t) {
                    fail("textos de la ventana", t);
                }
                mc.setScreen(null);
            }
            case 226 -> trial(1);
            case 336 -> {
                if (worldMode && mc.player != null) mc.player.setXRot(-4.0F);
            }
            case 340 -> {
                if (worldMode) shot(mc, "prueba del cambio de clase: oleada 1");
            }
            case 344 -> trial(2);
            case 384 -> {
                if (worldMode) shot(mc, "prueba: el jefe secreto (Guardián del Juicio)");
            }
            case 390 -> trial(3);
            case 420 -> {
                if (worldMode) shot(mc, "¡cambio de clase!");
            }
            case 424 -> trial(4);
            case 440 -> view(mc, 0, 0, 0);
            case 452 -> shot(mc, "pestaña ESTADO tras la mutación");
            case 456 -> view(mc, 4, 0, 0);
            case 468 -> shot(mc, "pestaña MISIONES tras la prueba");
            case 472 -> {
                mc.setScreen(null);
                if (worldMode) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 490 -> {
                if (worldMode) shot(mc, "tercera persona: armadura del Monarca y guadaña");
            }
            case 500 -> {
                if (worldMode) mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            // ---------------------------------------------------- examen de ascenso, sombra caída y prestigio
            case 640 -> trial(5);
            case 646 -> {
                if (worldMode) shot(mc, "examen de ascenso al rango D (y aviso de sombra caída)");
            }
            case 700 -> trial(6);
            case 730 -> {
                if (worldMode) shot(mc, "examen: el examinador");
            }
            case 740 -> trial(7);
            case 770 -> {
                if (worldMode) shot(mc, "¡ascenso de rango!");
            }
            case 790 -> trial(8);
            case 800 -> view(mc, 5, 0, 0);
            case 812 -> shot(mc, "pestaña RANGO: rango D y prestigio disponible");
            case 816 -> view(mc, 2, 0, 0);
            case 828 -> shot(mc, "pestaña SOMBRAS: ataque y sombra caída");
            case 832 -> mc.setScreen(null);
            case 1000 -> trial(9);
            case 1016 -> {
                if (worldMode) shot(mc, "¡prestigio!");
            }
            case 1040 -> trial(10);
            case 1044 -> view(mc, 5, 0, 0);
            case 1056 -> shot(mc, "pestaña RANGO tras el prestigio (MONARCA)");
            case 1060 -> view(mc, 0, 0, 0);
            case 1072 -> shot(mc, "pestaña ESTADO tras el prestigio");
            case 1076 -> mc.setScreen(null);
            // ---------------------------------------------------- 1.5.0: árbol propio de una sombra y galería de monstruos
            case 1080 -> view(mc, 2, 1, 0);
            case 1092 -> shot(mc, "pestaña SOMBRAS: árbol propio de una sombra");
            case 1096 -> mc.setScreen(null);
            case 1100 -> {
                // Galería sin el HUD ni la mano, para que se vean todos los monstruos
                mc.options.hideGui = true;
                trial(11);
            }
            case 1170 -> {
                if (worldMode) shot(mc, "galería: goblin, orco, caballero maldito y Guardián del Juicio");
            }
            case 1174 -> trial(12);
            case 1234 -> {
                if (worldMode) shot(mc, "galería: lobo, colosos, Bestia del Eclipse y oso de hielo");
            }
            case 1238 -> trial(13);
            case 1298 -> {
                if (worldMode) shot(mc, "galería: hormiga, Reina Carmesí y una sombra");
            }
            case 1302 -> trial(14);
            case 1350 -> {
                if (worldMode) shot(mc, "galería de noche: ojos, runas y magma que brillan");
            }
            // ---------------------------------------------------- 1.6.0: ajustes y animaciones de las sombras
            case 1352 -> {
                mc.options.hideGui = false;
                view(mc, 6, 0, 0);
            }
            case 1362 -> shot(mc, "pestaña AJUSTES");
            case 1364 -> {
                ClientConfig.setParticles(false);
                ClientConfig.setPerformance(true);
            }
            case 1374 -> {
                shot(mc, "AJUSTES: sin partículas y modo rendimiento");
                checkSettings(mc, Fx.NO_PARTICLES | Fx.PERFORMANCE);
                ClientConfig.setParticles(true);
                ClientConfig.setPerformance(false);
            }
            case 1384 -> {
                checkSettings(mc, 0);
                mc.setScreen(null);
            }
            case 1386 -> {
                mc.options.hideGui = true;
                trial(15);
            }
            case 1395 -> {
                if (worldMode) shot(mc, "animación: tres sombras saliendo del suelo");
            }
            case 1420 -> {
                mc.options.hideGui = false;
                trial(16);
            }
            case 1432 -> {
                if (worldMode) shot(mc, "¡Levántate!: el humo y las almas se juntan");
            }
            case 1444 -> {
                if (worldMode) shot(mc, "¡Levántate!: la sombra sale del suelo");
            }
            case 1478 -> {
                if (worldMode) shot(mc, "¡Levántate!: ya ha salido");
            }
            // ---------------------------------------------------- 1.7.0: la Ciudad de los Cazadores
            case 1490 -> {
                mc.options.hideGui = false;
                trial(17);
            }
            case 1540 -> {
                if (worldMode) shot(mc, "la Ciudad de los Cazadores: plaza, Cristal del Sistema y la Asociación");
            }
            case 1544 -> trial(18);
            case 1566 -> trial(19);
            case 1580 -> {
                if (mc.screen instanceof NpcScreen ns) ns.selfTestSelect(0);
            }
            case 1586 -> {
                if (worldMode) shot(mc, "herrería de Bruno: espada +3 y habilidad oculta");
            }
            case 1590 -> {
                mc.setScreen(null);
                trial(20);
            }
            case 1612 -> {
                if (worldMode) shot(mc, "Asociación: contratos de Lucía");
            }
            case 1616 -> {
                mc.setScreen(null);
                trial(21);
            }
            case 1626 -> trial(22);
            case 1642 -> {
                if (worldMode) shot(mc, "alquimia de Selene");
            }
            case 1646 -> {
                mc.setScreen(null);
                trial(23);
            }
            case 1666 -> {
                if (worldMode) shot(mc, "mercader de esencias de Ezequiel");
            }
            case 1670 -> {
                mc.setScreen(null);
                mc.options.hideGui = true;
                trial(24);
            }
            case 1700 -> {
                if (worldMode) shot(mc, "la ciudad desde el aire");
            }
            case 1704 -> trial(25);
            case 1740 -> {
                if (worldMode) shot(mc, "la ciudad de noche");
            }
            case 1744 -> {
                mc.options.hideGui = false;
                trial(26);
            }
            case 1770 -> view(mc, 4, 2, 0);
            case 1782 -> shot(mc, "MISIONES > CIUDAD: contratos y viaje");
            // ---------------------------------------------------- 1.8.0: pack de texturas, tonos de luz y shader
            case 1786 -> {
                mc.setScreen(null);
                packCheck(true, "al empezar");
                ElSistema.LOGGER.info("[SELFTEST-CLIENTE] tonos: {}", Tonos.status());
                if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
            }
            case 1796 -> shot(mc, "pack del Sistema: inventario");
            case 1798 -> openChest(mc);
            case 1808 -> shot(mc, "pack del Sistema: cofre grande");
            case 1810 -> mc.setScreen(new PauseScreen(true));
            case 1820 -> shot(mc, "pack del Sistema: menú de pausa (botones y menús)");
            case 1822 -> {
                mc.setScreen(null);
                trial(27);
            }
            case 1860 -> {
                if (worldMode) shot(mc, "tonos de luz y shader NORMAL: patio con antorchas de noche");
            }
            case 1862 -> {
                ClientConfig.setShader(0);
                ClientConfig.setLightTones(false);
            }
            case 1872 -> {
                if (worldMode) shot(mc, "el mismo patio sin shader ni tonos de luz (Minecraft normal)");
                ClientConfig.setShader(3);
                ClientConfig.setLightTones(true);
            }
            case 1882 -> {
                if (worldMode) shot(mc, "tonos de luz y shader INTENSO");
                ClientConfig.setShader(2);
                mc.options.hideGui = true;
                mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
                trial(28);
            }
            case 1910 -> {
                if (worldMode) shot(mc, "la luna del Sistema");
                trial(29);
            }
            case 1940 -> {
                if (worldMode) shot(mc, "el sol del Sistema");
                mc.options.hideGui = false;
                mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
                view(mc, 6, 0, 0);
            }
            case 1950 -> {
                shot(mc, "AJUSTES: pack, shader y tonos de luz");
                tonosCheck();
                mc.setScreen(null);
                Tonos.setPack(false);
                waitFor(() -> !Tonos.packActive() && mc.getOverlay() == null, 1500, "quitar el pack del Sistema");
            }
            case 1956 -> {
                packCheck(false, "tras quitarlo");
                if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
            }
            case 1966 -> {
                shot(mc, "sin el pack: el inventario normal de Minecraft");
                mc.setScreen(null);
                Tonos.setPack(true);
                waitFor(() -> Tonos.packActive() && mc.getOverlay() == null, 1500, "volver a poner el pack del Sistema");
            }
            case 1972 -> {
                packCheck(true, "al volver a ponerlo");
                ElSistema.LOGGER.info("[SELFTEST-CLIENTE] tonos: {}", Tonos.status());
            }
            case 1976 -> finish(mc);
            default -> {
            }
        }
    }

    private static void waitFor(BooleanSupplier cond, int maxTicks, String what) {
        hold = cond;
        holdLeft = maxTicks;
        holdWhat = what;
        ElSistema.LOGGER.info("[SELFTEST-CLIENTE] espero: {}", what);
    }

    /** Cofre grande de mentira (sólo en el cliente) con algunas cosas, para ver la textura del pack. */
    private static void openChest(Minecraft mc) {
        if (mc.player == null) return;
        ChestMenu menu = ChestMenu.sixRows(1000, mc.player.getInventory());
        ItemStack[] stuff = {new ItemStack(Items.DIAMOND, 12), new ItemStack(Items.GOLD_INGOT, 30), new ItemStack(Items.ENDER_PEARL, 8),
                new ItemStack(Items.IRON_SWORD), new ItemStack(Items.TORCH, 64), new ItemStack(Items.AMETHYST_SHARD, 5),
                new ItemStack(Items.LANTERN, 3), new ItemStack(Items.BOOK, 2)};
        for (int i = 0; i < stuff.length; i++) {
            menu.getContainer().setItem(i * 7 % 54, stuff[i]);
        }
        mc.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Cofre del Sistema")));
    }

    private static void packCheck(boolean want, String when) {
        boolean selected = Tonos.packSelected();
        boolean active = Tonos.packActive();
        if (selected != want || active != want) {
            failures++;
            ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: pack del Sistema {}: puesto {} y activo {} (debería ser {})", when,
                    selected, active, want);
        } else {
            ElSistema.LOGGER.info("[SELFTEST-CLIENTE] OK pack del Sistema {}: {}", when, want ? "puesto" : "quitado");
        }
    }

    /** Comprueba que el shader se ha dibujado, que el mapa de luz se ha teñido y que los títulos cambiaron de color. */
    private static void tonosCheck() {
        ElSistema.LOGGER.info("[SELFTEST-CLIENTE] tonos: {}", Tonos.status());
        if (Tonos.postFrames <= 0) {
            failures++;
            ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: el shader de tonos no se ha dibujado");
        }
        if (Tonos.tintUploads <= 0) {
            failures++;
            ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: no se han teñido los tonos de luz");
        }
        if (Tonos.labelHits <= 0) {
            failures++;
            ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: los títulos del inventario no cambiaron de color");
        }
        if (Tonos.postFrames > 0 && Tonos.tintUploads > 0 && Tonos.labelHits > 0) {
            ElSistema.LOGGER.info("[SELFTEST-CLIENTE] OK tonos: shader, luz y títulos");
        }
    }

    /** Comprueba que el servidor integrado ha recibido los ajustes del cliente. */
    private static void checkSettings(Minecraft mc, int want) {
        if (!worldMode || mc.player == null || mc.getSingleplayerServer() == null) return;
        try {
            net.minecraft.server.level.ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            int got = sp == null ? -1 : Fx.bits(sp);
            if (got != want) {
                failures++;
                ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: el servidor tiene los ajustes {} y deberían ser {}", got, want);
            } else {
                ElSistema.LOGGER.info("[SELFTEST-CLIENTE] OK ajustes en el servidor: {}", got);
            }
            java.nio.file.Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsistema.json");
            if (!java.nio.file.Files.exists(f)) {
                failures++;
                ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: no se guardó config/elsistema.json");
            }
        } catch (Throwable t) {
            fail("ajustes", t);
        }
    }

    private static void finish(Minecraft mc) {
        if (done) return;
        done = true;
        if (worldMode && SelfTest.state != 1) {
            failures++;
            ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: el servidor integrado terminó con estado {}", SelfTest.state);
        }
        if (failures == 0) {
            ElSistema.LOGGER.info("[SELFTEST-CLIENTE] OK ({}, {} capturas)", worldMode ? "en el mundo" : "solo la ventana", shots);
        } else {
            ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: {} errores", failures);
        }
        mc.stop();
    }
}
