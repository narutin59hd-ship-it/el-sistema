package com.elsistema.client;

import com.elsistema.ElSistema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 1.8.0: el pack de texturas del Sistema y los tonos de luz.
 * <ul>
 * <li>El pack va dentro del mod (resourcepacks/sistema, lo genera tools/pack_sistema.py) y Fabric API lo enseña en
 * Opciones &gt; Paquetes de recursos. Trae la interfaz, el inventario, los cofres, el sol, la luna y los shaders.</li>
 * <li>Tonos de luz: después de que Minecraft calcule el mapa de luz (16x16: luz de bloque x luz del cielo), lo
 * teñimos: antorchas más cálidas, noches azules y sombras profundas. Lo usa todo (bloques, mobs, Sodium...).</li>
 * <li>Shader de tonos: un post-procesado (shaders/post/tonos_*.json del pack) con resplandor, tonos del Sistema y
 * viñeta, que se dibuja antes de la interfaz (mixin GameRendererMixin; si no está, al final del mundo).</li>
 * </ul>
 * Si el pack está quitado no se hace nada de esto (no hay sistema_pack.json). Con un shader de Iris activo, tampoco.
 */
public final class Tonos {
    private Tonos() {}

    public static final ResourceLocation PACK = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "sistema");
    private static final ResourceLocation MARKER = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "sistema_pack.json");
    private static final ResourceLocation INVENTORY = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    public static final String[] LEVEL_NAMES = {"NO", "SUAVE", "NORMAL", "INTENSO"};
    private static final String[] LEVEL_FILES = {"", "suave", "normal", "intenso"};
    /** Color por defecto de los títulos de los inventarios (gris oscuro). */
    private static final int VANILLA_LABEL = 0x404040;

    // ---------------------------------------------------------------- estado tras cargar los recursos
    private static boolean packActive = false;
    private static boolean labelsInventory = false;
    private static boolean labelsChest = false;
    private static int labelColor = 0xA8E6FF;
    private static final float[] WARM = {1.12F, 0.96F, 0.74F};
    private static final float[] NIGHT = {0.80F, 0.90F, 1.22F};
    private static final float[] SHADE = {0.78F, 0.84F, 1.30F};
    private static float strength = 1.0F;

    // ---------------------------------------------------------------- shader
    private static PostChain chain = null;
    private static String chainKey = "";
    private static boolean chainFailed = false;
    private static int chainW = -1;
    private static int chainH = -1;
    private static int mixinFrames = 0;
    private static int endFrames = 0;

    // ---------------------------------------------------------------- mapa de luz
    private static final int[] LAST = new int[256];
    private static boolean lastValid = false;

    // ---------------------------------------------------------------- Iris
    private static boolean irisLoaded = false;
    private static boolean irisActive = false;
    private static int irisTimer = 0;
    private static Method irisGet = null;
    private static Method irisInUse = null;

    // ---------------------------------------------------------------- contadores (prueba automática)
    public static int reloads = 0;
    public static int postFrames = 0;
    public static int tintUploads = 0;
    public static int labelHits = 0;
    public static String lastError = null;

    public static void register() {
        FabricLoader.getInstance().getModContainer(ElSistema.MOD_ID).ifPresent(mod -> {
            boolean ok = ResourceManagerHelper.registerBuiltinResourcePack(PACK, mod,
                    Component.literal("El Sistema: interfaz, luz y shaders"), ResourcePackActivationType.DEFAULT_ENABLED);
            if (!ok) ElSistema.LOGGER.warn("[tonos] no se pudo registrar el pack de texturas del Sistema");
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "tonos");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                onReload(manager);
            }
        });
        WorldRenderEvents.START.register(context -> tintLightmap());
        // Por si el mixin del GameRenderer no se aplica (otro mod lo cambia): el shader va al final del mundo
        WorldRenderEvents.END.register(context -> {
            if (mixinFrames == 0 && ++endFrames > 5) renderPost(1.0F);
        });
        irisLoaded = FabricLoader.getInstance().isModLoaded("iris");
    }

    // ================================================================ recursos

    private static void onReload(ResourceManager rm) {
        reloads++;
        closeChain();
        chainFailed = false;
        lastValid = false;
        labelsInventory = false;
        labelsChest = false;
        Optional<Resource> marker = rm.getResource(MARKER);
        packActive = marker.isPresent();
        if (packActive) {
            String source = marker.get().sourcePackId();
            labelsInventory = rm.getResource(INVENTORY).map(r -> source.equals(r.sourcePackId())).orElse(false);
            labelsChest = rm.getResource(CHEST).map(r -> source.equals(r.sourcePackId())).orElse(false);
            try (Reader reader = marker.get().openAsReader()) {
                JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                if (o.has("etiquetas")) labelColor = Integer.parseInt(o.get("etiquetas").getAsString(), 16) & 0xFFFFFF;
                if (o.has("luz")) {
                    JsonObject luz = o.getAsJsonObject("luz");
                    readColor(luz, "calida", WARM);
                    readColor(luz, "noche", NIGHT);
                    readColor(luz, "sombra", SHADE);
                    if (luz.has("fuerza")) strength = Mth.clamp(luz.get("fuerza").getAsFloat(), 0.0F, 2.0F);
                }
            } catch (Exception e) {
                ElSistema.LOGGER.warn("[tonos] no se pudo leer {}: {}", MARKER, e.toString());
            }
        }
        ElSistema.LOGGER.info("[tonos] pack del Sistema {} (inventario {}, cofres {})", packActive ? "activo" : "quitado",
                labelsInventory, labelsChest);
    }

    private static void readColor(JsonObject o, String key, float[] out) {
        if (!o.has(key)) return;
        JsonArray a = o.getAsJsonArray(key);
        for (int i = 0; i < 3 && i < a.size(); i++) {
            JsonElement e = a.get(i);
            out[i] = Mth.clamp(e.getAsFloat(), 0.3F, 2.0F);
        }
    }

    public static boolean packActive() {
        return packActive;
    }

    /** Id del pack integrado en la lista de paquetes de recursos ("elsistema:sistema"). */
    public static String packId(PackRepository repo) {
        String want = PACK.toString();
        if (repo.getAvailableIds().contains(want)) return want;
        for (String id : repo.getAvailableIds()) {
            if (id.contains(ElSistema.MOD_ID) && id.endsWith("sistema") && !id.equals(ElSistema.MOD_ID)) return id;
        }
        return null;
    }

    /** ¿Está el pack puesto en Opciones &gt; Paquetes de recursos? */
    public static boolean packSelected() {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        String id = packId(repo);
        return id != null && repo.getSelectedIds().contains(id);
    }

    /** Pone o quita el pack (recarga los recursos, como en el menú de paquetes). */
    public static boolean setPack(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        String id = packId(repo);
        if (id == null) {
            ElSistema.LOGGER.warn("[tonos] no encuentro el pack del Sistema entre {}", repo.getAvailableIds());
            return false;
        }
        boolean changed = on ? repo.addPack(id) : repo.removePack(id);
        if (changed) {
            ElSistema.LOGGER.info("[tonos] pack del Sistema {}", on ? "puesto" : "quitado");
            mc.options.updateResourcePacks(repo);
        }
        return changed;
    }

    /** Cada tick del cliente. La primera vez que se usa la 1.8.0 pone el pack aunque ya hubiera una lista guardada. */
    public static void tick(Minecraft mc) {
        if (irisLoaded && ++irisTimer >= 40) {
            irisTimer = 0;
            irisActive = irisShaders();
        }
        if (!ClientConfig.packInit() && mc.getOverlay() == null) {
            ClientConfig.setPackInit();
            try {
                PackRepository repo = mc.getResourcePackRepository();
                String id = packId(repo);
                if (id != null && !repo.getSelectedIds().contains(id)) {
                    ElSistema.LOGGER.info("[tonos] pongo el pack del Sistema por primera vez");
                    setPack(true);
                }
            } catch (Exception e) {
                ElSistema.LOGGER.warn("[tonos] no se pudo poner el pack: {}", e.toString());
            }
        }
    }

    private static boolean irisShaders() {
        try {
            if (irisGet == null) {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisGet = c.getMethod("getInstance");
                irisInUse = c.getMethod("isShaderPackInUse");
            }
            return Boolean.TRUE.equals(irisInUse.invoke(irisGet.invoke(null)));
        } catch (Throwable t) {
            irisLoaded = false;
            return false;
        }
    }

    // ================================================================ títulos de los inventarios

    /** Los títulos de los inventarios y cofres del pack van en azul claro (el fondo es oscuro). */
    public static int labelColor(Object screen, int color) {
        if ((color & 0xFFFFFF) != VANILLA_LABEL) return color;
        if ((screen instanceof InventoryScreen && labelsInventory) || (screen instanceof ContainerScreen && labelsChest)) {
            labelHits++;
            return labelColor;
        }
        return color;
    }

    // ================================================================ tonos de luz (mapa de luz)

    private static void tintLightmap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !packActive || !ClientConfig.lightTones() || irisActive) {
            lastValid = false;
            return;
        }
        try {
            LightTexture lt = mc.gameRenderer.lightTexture();
            NativeImage img = lt.lightPixels;
            if (lastValid && unchanged(img)) return;
            float day = Mth.clamp((mc.level.getSkyDarken(1.0F) - 0.2F) / 0.8F, 0.0F, 1.0F);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int c = img.getPixelRGBA(x, y);
                    // la casilla de luz máxima es la de la interfaz y los objetos que brillan: no se toca
                    int t = x == 15 && y == 15 ? c : tint(c, x / 15.0F, y / 15.0F, day);
                    if (t != c) img.setPixelRGBA(x, y, t);
                    LAST[y * 16 + x] = t;
                }
            }
            lt.lightTexture.upload();
            lastValid = true;
            tintUploads++;
        } catch (Throwable t) {
            lastValid = false;
            if (lastError == null) {
                lastError = t.toString();
                ElSistema.LOGGER.warn("[tonos] no se pudo teñir el mapa de luz", t);
            }
        }
    }

    private static boolean unchanged(NativeImage img) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (img.getPixelRGBA(x, y) != LAST[y * 16 + x]) return false;
            }
        }
        return true;
    }

    /** Tiñe un píxel del mapa de luz (formato ABGR). bl = luz de bloque 0..1, sl = luz del cielo 0..1. */
    static int tint(int abgr, float bl, float sl, float day) {
        float r = (abgr & 0xFF) / 255.0F;
        float g = ((abgr >> 8) & 0xFF) / 255.0F;
        float b = ((abgr >> 16) & 0xFF) / 255.0F;
        float lum = 0.2126F * r + 0.7152F * g + 0.0722F * b;
        float s = strength;
        float daylight = sl * day;
        float wWarm = bl * bl * (1.0F - daylight) * (1.0F - smooth(0.86F, 1.0F, lum)) * s;
        float wNight = sl * (1.0F - day) * (1.0F - bl) * s;
        float wShade = (1.0F - smooth(0.03F, 0.32F, lum)) * (1.0F - bl * 0.85F) * s;
        float mr = mul(WARM[0], wWarm) * mul(NIGHT[0], wNight) * mul(SHADE[0], wShade);
        float mg = mul(WARM[1], wWarm) * mul(NIGHT[1], wNight) * mul(SHADE[1], wShade);
        float mb = mul(WARM[2], wWarm) * mul(NIGHT[2], wNight) * mul(SHADE[2], wShade);
        float nr = r * mr;
        float ng = g * mg;
        float nb = b * mb;
        float nl = 0.2126F * nr + 0.7152F * ng + 0.0722F * nb;
        if (nl > 1.0E-4F) {
            float k = Mth.clamp(lum / nl, 0.85F, 1.2F);
            nr *= k;
            ng *= k;
            nb *= k;
        }
        return (abgr & 0xFF000000) | (to255(nb) << 16) | (to255(ng) << 8) | to255(nr);
    }

    private static float mul(float target, float w) {
        return 1.0F + (target - 1.0F) * Mth.clamp(w, 0.0F, 1.0F);
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static int to255(float v) {
        return Mth.clamp(Math.round(v * 255.0F), 0, 255);
    }

    // ================================================================ shader de tonos (post-procesado)

    /** Lo llama GameRendererMixin justo después de dibujar el mundo y antes de la interfaz. */
    public static void onMixinFrame(float delta) {
        mixinFrames++;
        renderPost(delta);
    }

    private static void renderPost(float delta) {
        Minecraft mc = Minecraft.getInstance();
        int level = ClientConfig.shader();
        if (level <= 0 || level >= LEVEL_FILES.length || !packActive || irisActive || mc.level == null) return;
        String key = "tonos_" + LEVEL_FILES[level] + (ClientConfig.performance() ? "_ligero" : "");
        if (chain != null && !chainKey.equals(key)) closeChain();
        if (chain == null) {
            if (chainFailed) return;
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "shaders/post/" + key + ".json");
            if (mc.getResourceManager().getResource(loc).isEmpty()) {
                chainFailed = true;
                return;
            }
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), loc);
                chainKey = key;
                chainW = -1;
                chainH = -1;
                ElSistema.LOGGER.info("[tonos] shader {} cargado", key);
            } catch (Exception e) {
                chainFailed = true;
                lastError = e.toString();
                ElSistema.LOGGER.warn("[tonos] no se pudo cargar el shader {}: {}", loc, e.toString());
                return;
            }
        }
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (main.width != chainW || main.height != chainH) {
                chain.resize(main.width, main.height);
                chainW = main.width;
                chainH = main.height;
            }
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(delta);
            postFrames++;
        } catch (Exception e) {
            chainFailed = true;
            lastError = e.toString();
            ElSistema.LOGGER.warn("[tonos] el shader {} falló y se apaga: {}", key, e.toString());
            closeChain();
        }
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
    }

    private static void closeChain() {
        if (chain != null) {
            try {
                chain.close();
            } catch (Exception ignored) {
                // nada
            }
            chain = null;
            chainKey = "";
        }
    }

    /** Para la prueba automática y los ajustes. */
    public static String status() {
        return "pack " + (packActive ? "activo" : "quitado") + " (seleccionado " + packSelected() + ")"
                + ", shader " + LEVEL_NAMES[Mth.clamp(ClientConfig.shader(), 0, 3)] + " [" + chainKey + "] "
                + postFrames + " fotogramas (mixin " + mixinFrames + ")"
                + ", luz " + (ClientConfig.lightTones() ? "sí" : "no") + " (" + tintUploads + " veces)"
                + ", títulos " + labelHits + (irisActive ? ", Iris activo" : "")
                + (lastError != null ? ", error: " + lastError : "");
    }
}
