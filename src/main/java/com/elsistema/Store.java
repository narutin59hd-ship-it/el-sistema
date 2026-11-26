package com.elsistema;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Guarda los datos de cada Jugador en la carpeta del mundo (el_sistema/UUID.json). */
public final class Store {
    private Store() {}

    private static final Gson GSON = new Gson();
    private static final Map<UUID, Data> DATA = new HashMap<>();

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("el_sistema");
    }

    public static Data load(MinecraftServer server, UUID id) {
        Data d = null;
        try {
            Path f = dir(server).resolve(id + ".json");
            if (Files.exists(f)) {
                d = GSON.fromJson(Files.readString(f), Data.class);
            }
        } catch (Exception e) {
            ElSistema.LOGGER.error("No se pudieron leer los datos de {}", id, e);
        }
        if (d == null) {
            d = new Data();
        }
        d.fix();
        DATA.put(id, d);
        return d;
    }

    public static Data get(ServerPlayer p) {
        Data d = DATA.get(p.getUUID());
        if (d == null) {
            d = load(p.getServer(), p.getUUID());
        }
        return d;
    }

    public static void save(MinecraftServer server, UUID id) {
        Data d = DATA.get(id);
        if (d == null) return;
        try {
            Path folder = dir(server);
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(id + ".json"), GSON.toJson(d));
        } catch (Exception e) {
            ElSistema.LOGGER.error("No se pudieron guardar los datos de {}", id, e);
        }
    }

    public static void saveAll(MinecraftServer server) {
        for (UUID id : new ArrayList<>(DATA.keySet())) {
            save(server, id);
        }
    }

    public static void remove(UUID id) {
        DATA.remove(id);
    }

    public static void clear() {
        DATA.clear();
    }
}
