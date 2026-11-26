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

    // Acciones (cliente -> servidor)
    public static final int ACT_SKILL = 1;
    public static final int ACT_STAT = 2;
    public static final int ACT_CHOOSE = 3;
    public static final int ACT_TREE = 4;
    public static final int ACT_BUY = 5;
    public static final int ACT_SELL = 6;

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
