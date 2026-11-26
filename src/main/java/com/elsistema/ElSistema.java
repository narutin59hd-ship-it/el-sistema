package com.elsistema;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElSistema implements ModInitializer {
    public static final String MOD_ID = "elsistema";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.register();
        ModEntities.register();

        PayloadTypeRegistry.playS2C().register(Net.SyncPayload.TYPE, Net.SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Net.NotifyPayload.TYPE, Net.NotifyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Net.ShadowsPayload.TYPE, Net.ShadowsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Net.ActionPayload.TYPE, Net.ActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Net.ActionPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        SystemLogic.action(context.player(), payload.action(), payload.a(), payload.b())));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SystemLogic.onJoin(handler.player, server));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Dungeons.onDisconnect(handler.player);
            Shadows.storeAll(handler.player, true);
            Shadows.forget(handler.player);
            Store.save(server, handler.player.getUUID());
            Store.remove(handler.player.getUUID());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Dungeons.shutdown();
            Shadows.shutdown(server);
            Store.saveAll(server);
            Store.clear();
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SystemLogic.onRespawn(newPlayer));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SystemLogic.tick(server);
            Dungeons.tick(server);
            Shadows.tick(server);
        });

        // EXP, oro y botín al matar criaturas (cuerpo a cuerpo, flechas, habilidades o tus sombras)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer sp && entity != sp) {
                Ranks.onKill(sp, entity, 1.0);
            } else if (Shadows.isShadow(source.getEntity())) {
                ServerPlayer owner = Shadows.ownerPlayer(source.getEntity());
                if (owner != null) Ranks.onKill(owner, entity, 0.6);
            }
            Shadows.onDeath(entity, source);
        });

        // Las sombras no mueren: vuelven a la sombra de su dueño para recuperarse
        ServerLivingEntityEvents.ALLOW_DEATH.register(Shadows::allowDeath);

        // El cliente necesita saber qué criaturas son sombras para pintarlas oscuras
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (Shadows.isShadow(entity)) {
                SystemLogic.notify(player, Net.N_SHADOW_MARK, entity.getId(), 1);
            }
        });

        // Esquivar, devolver daño, robo de vida, fuego y veneno de los árboles
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Combat::allowDamage);

        // Dentro de una mazmorra no se pueden romper bloques (la sala no se puede escapar ni saquear)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer sp) || Store.get(sp).inDungeon != 1);

        // Flechas más fuertes y monstruos con rango
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> SystemLogic.onEntityLoad(entity));

        // Comandos de prueba (necesitan trucos u operador)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("sistema")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("exp")
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 10000000))
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            if (Store.get(sp).awakened != 1) {
                                                sp.sendSystemMessage(Component.literal("[Sistema] Primero tienes que despertar (pulsa V)."));
                                                return 0;
                                            }
                                            SystemLogic.addXp(sp, IntegerArgumentType.getInteger(ctx, "cantidad"));
                                            return 1;
                                        })))
                        .then(Commands.literal("oro")
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 10000000))
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            Data d = Store.get(sp);
                                            d.coins += IntegerArgumentType.getInteger(ctx, "cantidad");
                                            SystemLogic.sync(sp, d);
                                            return 1;
                                        })))
                        .then(Commands.literal("puntos")
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            Data d = Store.get(sp);
                                            d.skillPoints += IntegerArgumentType.getInteger(ctx, "cantidad");
                                            SystemLogic.sync(sp, d);
                                            return 1;
                                        })))
                        .then(Commands.literal("puerta")
                                .then(Commands.argument("rango", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            int rank = Defs.rankIndex(StringArgumentType.getString(ctx, "rango"));
                                            if (rank < 0) {
                                                sp.sendSystemMessage(Component.literal("[Sistema] Rango no válido. Usa E, D, C, B, A o S."));
                                                return 0;
                                            }
                                            return Dungeons.openGate(sp, rank, -1, true) ? 1 : 0;
                                        })))
                        .then(Commands.literal("cambiar")
                                .executes(ctx -> {
                                    SystemLogic.rechoose(ctx.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("reiniciar")
                                .executes(ctx -> {
                                    SystemLogic.reset(ctx.getSource().getPlayerOrException());
                                    return 1;
                                }))));

        String selfTest = System.getenv("ELSISTEMA_SELFTEST");
        if (selfTest != null && !selfTest.isBlank()) {
            SelfTest.register(selfTest);
        }

        LOGGER.info("El Sistema cargado");
    }
}
