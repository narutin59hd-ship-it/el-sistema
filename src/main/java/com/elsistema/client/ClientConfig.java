package com.elsistema.client;

import com.elsistema.ElSistema;
import com.elsistema.Fx;
import com.elsistema.Net;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ajustes del cliente (pestaña AJUSTES de la ventana del Sistema). Se guardan en config/elsistema.json. */
public final class ClientConfig {
    private ClientConfig() {}

    private static boolean particles = true;
    private static boolean performance = false;
    /** 1.8.0: shader de tonos 0 = no, 1 = suave, 2 = normal, 3 = intenso. */
    private static int shader = 2;
    /** 1.8.0: tonos de luz (antorchas cálidas, noches azules). */
    private static boolean lightTones = true;
    /** 1.8.0: ya se puso el pack del Sistema la primera vez (después manda lo que elija el jugador). */
    private static boolean packInit = false;
    /** Hay que contarle los ajustes al servidor (al entrar en un mundo o al cambiarlos). */
    private static boolean dirty = true;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("elsistema.json");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("particulas")) particles = o.get("particulas").getAsBoolean();
                if (o.has("rendimiento")) performance = o.get("rendimiento").getAsBoolean();
                if (o.has("shader")) shader = Math.max(0, Math.min(3, o.get("shader").getAsInt()));
                if (o.has("tonos_luz")) lightTones = o.get("tonos_luz").getAsBoolean();
                if (o.has("pack_iniciado")) packInit = o.get("pack_iniciado").getAsBoolean();
            }
        } catch (Exception ex) {
            ElSistema.LOGGER.warn("No se pudo leer config/elsistema.json: {}", ex.toString());
        }
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("particulas", particles);
        o.addProperty("rendimiento", performance);
        o.addProperty("shader", shader);
        o.addProperty("tonos_luz", lightTones);
        o.addProperty("pack_iniciado", packInit);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            ElSistema.LOGGER.warn("No se pudo guardar config/elsistema.json: {}", ex.toString());
        }
    }

    /** Partículas del mod (habilidades, sombras, jefes...). */
    public static boolean particles() {
        return particles;
    }

    /** Modo rendimiento: menos partículas, sin adornos que se repiten y sin brillo en los monstruos. */
    public static boolean performance() {
        return performance;
    }

    public static void setParticles(boolean on) {
        particles = on;
        save();
        dirty = true;
    }

    public static void setPerformance(boolean on) {
        performance = on;
        save();
        dirty = true;
    }

    /** Shader de tonos: 0 = no, 1 = suave, 2 = normal, 3 = intenso. */
    public static int shader() {
        return shader;
    }

    public static void setShader(int level) {
        shader = Math.max(0, Math.min(3, level));
        save();
    }

    /** Tonos de luz del pack: antorchas cálidas, noches azules y sombras profundas. */
    public static boolean lightTones() {
        return lightTones;
    }

    public static void setLightTones(boolean on) {
        lightTones = on;
        save();
    }

    public static boolean packInit() {
        return packInit;
    }

    public static void setPackInit() {
        packInit = true;
        save();
    }

    public static int bits() {
        return (particles ? 0 : Fx.NO_PARTICLES) | (performance ? Fx.PERFORMANCE : 0);
    }

    /** Al entrar en un mundo: hay que volver a mandar los ajustes. */
    public static void markDirty() {
        dirty = true;
    }

    /** Cada tick del cliente: manda los ajustes al servidor cuando hace falta. */
    public static void tick(boolean inWorld) {
        if (!dirty || !inWorld) return;
        if (!ClientPlayNetworking.canSend(Net.ActionPayload.TYPE)) return;
        ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_SETTINGS, bits(), 0));
        dirty = false;
    }
}
