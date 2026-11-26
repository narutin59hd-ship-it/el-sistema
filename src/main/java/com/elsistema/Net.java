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

    // Acciones (cliente -> servidor)
    public static final int ACT_SKILL = 1;
    public static final int ACT_STAT = 2;
    public static final int ACT_CHOOSE = 3;

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

    /** Cliente -> servidor: usar habilidad, subir estadística o elegir raza y clase. */
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
