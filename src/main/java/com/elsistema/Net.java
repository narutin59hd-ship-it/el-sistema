package com.elsistema;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Mensajes entre servidor y cliente. */
public final class Net {
    private Net() {}

    // Avisos (servidor -> cliente)
    public static final int N_LEVEL_UP = 1;
    public static final int N_AWAKEN_PROMPT = 2;
    public static final int N_AWAKENED = 3;
    public static final int N_XP = 4;
    public static final int N_SKILL = 5;
    public static final int N_COOLDOWN = 6;
    public static final int N_NO_MP = 7;
    public static final int N_RANK_UP = 8;
    public static final int N_NOT_AWAKENED = 9;
    public static final int N_MAX_LEVEL = 10;
    public static final int N_WELCOME = 11;
    public static final int N_COINS = 12;
    public static final int N_GATE_OPEN = 13;
    public static final int N_DUNGEON_ENTER = 14;
    public static final int N_WAVE = 15;
    public static final int N_BOSS = 16;
    public static final int N_DUNGEON_CLEAR = 17;
    public static final int N_DUNGEON_FAIL = 18;
    public static final int N_DODGE = 19;
    public static final int N_LAST_STAND = 20;
    public static final int N_RUNE = 21;
    public static final int N_BOUGHT = 22;
    public static final int N_NO_COINS = 23;
    public static final int N_SOLD = 24;
    public static final int N_GATE_FAIL = 25;
    public static final int N_TREE_UP = 26;
    public static final int N_CORPSE = 27;        // a = segundos para extraer
    public static final int N_ARISE_OK = 28;      // a = id de la sombra, b = rango
    public static final int N_ARISE_FAIL = 29;    // a = intentos que quedan
    public static final int N_ARMY_FULL = 30;     // a = sombras, b = máximo
    public static final int N_SHADOW_BACK = 31;   // a = días inhabilitada, b = id de la sombra
    public static final int N_SHADOW_MARK = 32;   // a = id de entidad, b = 1 es sombra
    public static final int N_NO_CORPSE = 33;
    public static final int N_SHADOWS_OUT = 34;   // a = cuántas
    public static final int N_SHADOWS_IN = 35;    // a = cuántas
    public static final int N_RANK_REQ = 36;      // a = rango necesario
    public static final int N_SHADOW_LEVEL = 37;  // a = id de la sombra, b = nivel
    public static final int N_MANA = 38;          // a = maná recuperado
    public static final int N_RETURN = 39;
    public static final int N_ARISE_NO_MP = 40;   // a = coste
    public static final int N_NO_SHADOWS = 41;
    public static final int N_QUEST_FOUND = 42;   // a = misión
    public static final int N_QUEST_DONE = 43;    // a = misión
    public static final int N_MUTATION = 44;      // a = clase
    public static final int N_SECRET_BOSS = 45;   // a = jefe secreto, b = distancia (0 = en la mazmorra)
    public static final int N_SECRET_FLED = 46;   // a = jefe secreto
    public static final int N_HIDDEN_REWARD = 47; // a = hito de nivel
    public static final int N_TRIAL_FAIL = 48;    // a = 1 si se acabó el tiempo
    public static final int N_TRIAL_START = 49;   // a = rango, b = nivel
    public static final int N_TRIAL_DENIED = 50;  // a = motivo
    public static final int N_SLOTS_UP = 51;      // a = sombras extra, b = máximo
    public static final int N_CORPSE_INFO = 52;   // a = % de éxito, b = intentos + 10 * maná
    public static final int N_EXAM_READY = 53;    // a = rango al que puedes ascender
    public static final int N_EXAM_START = 54;    // a = rango, b = nivel
    public static final int N_EXAM_FAIL = 55;     // a = motivo (1 tiempo, 2 caído), b = intentos
    public static final int N_EXAM_DENIED = 56;   // a = motivo, b = nivel necesario
    public static final int N_PRESTIGE = 57;      // a = prestigio, b = clase
    public static final int N_PRESTIGE_DENIED = 58; // a = motivo
    public static final int N_SHADOW_READY = 59;  // a = id de la sombra
    public static final int N_SHADOWS_LEVEL = 60; // a = cuántas sombras han subido de nivel, b = puntos sin gastar
    public static final int N_SHADOW_TREE = 61;   // a = id de la sombra, b = nudo * 100 + rango

    // Acciones (cliente -> servidor)
    public static final int ACT_SKILL = 1;
    public static final int ACT_STAT = 2;
    public static final int ACT_CHOOSE = 3;
    public static final int ACT_TREE = 4;
    public static final int ACT_BUY = 5;
    public static final int ACT_SELL = 6;
    public static final int ACT_ARISE = 7;
    public static final int ACT_SHADOWS_TOGGLE = 8;
    public static final int ACT_SHADOW = 9;        // a = id, b = 0 invocar, 1 guardar, 2 liberar
    public static final int ACT_SHADOWS_ALL = 10;  // a = 1 invocar todas, 0 guardar todas
    public static final int ACT_QUEST = 11;        // a = misión, b = 0 empezar la prueba
    public static final int ACT_RANK = 12;         // a = 0 examen de ascenso, 1 prestigio
    public static final int ACT_SHADOW_TREE = 13;  // a = id de la sombra, b = nudo de su árbol propio
    public static final int ACT_SETTINGS = 14;     // a = ajustes del cliente (bits de Fx: sin partículas, modo rendimiento)

    /** Números por sombra en la lista de la pestaña Sombras (8 + EXP + puntos + los rangos de su árbol). */
    public static final int SHADOW_STRIDE = 10 + ShadowTree.SIZE;

    /** Servidor -> cliente: todos los datos del Jugador. */
    public record SyncPayload(List<Integer> data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SyncPayload::data, SyncPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: un aviso para mostrar en pantalla. */
    public record NotifyPayload(int kind, int a, int b) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NotifyPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "notify"));

        public static final StreamCodec<RegistryFriendlyByteBuf, NotifyPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, NotifyPayload::kind,
                ByteBufCodecs.VAR_INT, NotifyPayload::a,
                ByteBufCodecs.VAR_INT, NotifyPayload::b,
                NotifyPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: lista de sombras (8 números y 2 textos por sombra). */
    public record ShadowsPayload(List<Integer> nums, List<String> strs) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ShadowsPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "shadows"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ShadowsPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ShadowsPayload::nums,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ShadowsPayload::strs,
                ShadowsPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Cliente -> servidor: acciones del Jugador. */
    public record ActionPayload(int action, int a, int b) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ElSistema.MOD_ID, "action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ActionPayload::action,
                ByteBufCodecs.VAR_INT, ActionPayload::a,
                ByteBufCodecs.VAR_INT, ActionPayload::b,
                ActionPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
