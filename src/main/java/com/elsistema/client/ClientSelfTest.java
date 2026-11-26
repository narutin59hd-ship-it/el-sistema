package com.elsistema.client;

import com.elsistema.ElSistema;
import com.elsistema.Net;
import com.elsistema.SelfTest;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
                steps(mc, ticks - t0);
                return;
            }
            if (mc.getOverlay() != null) return;
            if (mc.level == null || mc.player == null) {
                titleTicks++;
                if (titleTicks == 100) openWorld(mc);
                if (titleTicks >= 1400) {
                    ElSistema.LOGGER.warn("[SELFTEST-CLIENTE] no se pudo entrar al mundo: pruebo solo la ventana");
                    fakeData();
                    begin(false);
                }
                return;
            }
            worldTicks++;
            if (worldTicks == 1) {
                ElSistema.LOGGER.info("[SELFTEST-CLIENTE] mundo cargado");
                checkResources(mc);
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
                "entity/oso_hielo", "entity/hormiga", "entity/sombra"};
        for (String t : textures) {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "textures/" + t + ".png");
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
                6, 3, 0, 0, 0, 10, 2, 4, 0, 0, 4, 2, 3, 0, 0, 5, 9, 3};
        for (int v : base) {
            d.add(v);
        }
        CData.set(d);
        List<Integer> nums = new ArrayList<>();
        List<String> strs = new ArrayList<>();
        Object[][] army = {
                {1, 2, 24, 1, 80, 0, 14, "elsistema:goblin", "entity.elsistema.goblin"},
                {2, 3, 31, 0, 100, 0, 40, "elsistema:lobo_acero", "entity.elsistema.lobo_acero"},
                {3, 4, 52, 2, 0, 37, 77, "elsistema:caballero", "entity.elsistema.caballero"},
                {4, 1, 12, 0, 100, 0, 3, "minecraft:zombie", "entity.minecraft.zombie"},
                {5, 5, 60, 1, 55, 0, (1 << 24) | 120, "elsistema:coloso", "entity.elsistema.coloso"}};
        for (Object[] r : army) {
            for (int i = 0; i < 7; i++) {
                nums.add((Integer) r[i]);
            }
            strs.add((String) r[7]);
            strs.add((String) r[8]);
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

    private static void steps(Minecraft mc, int rt) {
        switch (rt) {
            case 0 -> note(Net.N_CORPSE, 10, 0);
            case 20 -> shot(mc, "mundo, monstruos, sombras y HUD");
            case 24 -> note(Net.N_ARISE_OK, 1, 2);
            case 32 -> shot(mc, "¡Levántate!");
            case 60 -> {
                int[][] all = {{Net.N_ARISE_FAIL, 2, 0}, {Net.N_ARMY_FULL, 6, 6}, {Net.N_SHADOW_BACK, 45, 1},
                        {Net.N_SHADOW_LEVEL, 1, 24}, {Net.N_NO_SHADOWS, 0, 0}, {Net.N_RANK_REQ, 3, 0}, {Net.N_MANA, 60, 0},
                        {Net.N_RETURN, 0, 0}, {Net.N_SHADOWS_OUT, 3, 0}, {Net.N_SHADOWS_IN, 2, 0}, {Net.N_BOUGHT, 16, 1},
                        {Net.N_TREE_UP, 2, 0}, {Net.N_NO_CORPSE, 0, 0}, {Net.N_ARISE_NO_MP, 40, 10}, {Net.N_GATE_OPEN, 2, 14},
                        {Net.N_RANK_UP, 3, 2}, {Net.N_SHADOW_MARK, -5, 1}};
                for (int[] n : all) {
                    note(n[0], n[1], n[2]);
                }
            }
            case 72 -> shot(mc, "avisos");
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
            case 194 -> {
                try {
                    ElSistema.LOGGER.info("[SELFTEST-CLIENTE] {} textos de la ventana revisados", SystemScreen.selfCheck());
                } catch (Throwable t) {
                    fail("textos de la ventana", t);
                }
                mc.setScreen(null);
            }
            case 198 -> {
                if (worldMode) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 214 -> {
                if (worldMode) shot(mc, "tercera persona: armadura del Monarca y guadaña");
            }
            case 250 -> finish(mc);
            default -> {
            }
        }
    }

    private static void finish(Minecraft mc) {
        if (done) return;
        done = true;
        if (failures == 0) {
            ElSistema.LOGGER.info("[SELFTEST-CLIENTE] OK ({}, {} capturas)", worldMode ? "en el mundo" : "solo la ventana", shots);
        } else {
            ElSistema.LOGGER.error("[SELFTEST-CLIENTE] FALLO: {} errores", failures);
        }
        mc.stop();
    }
}
