package com.elsistema;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElSistema implements ModInitializer {
    public static final String MOD_ID = "elsistema";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(Net.SyncPayload.TYPE, Net.SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Net.NotifyPayload.TYPE, Net.NotifyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Net.ActionPayload.TYPE, Net.ActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Net.ActionPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        SystemLogic.action(context.player(), payload.action(), payload.a(), payload.b())));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SystemLogic.onJoin(handler.player, server));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Store.save(server, handler.player.getUUID());
            Store.remove(handler.player.getUUID());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Store.saveAll(server);
            Store.clear();
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SystemLogic.onRespawn(newPlayer));

        ServerTickEvents.END_SERVER_TICK.register(SystemLogic::tick);

        // EXP al matar criaturas (cuerpo a cuerpo, flechas o habilidades)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer sp && entity != sp) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
                SystemLogic.addXp(sp, Xp.forMob(id, entity instanceof Enemy));
            }
        });

        // Bonus de daño para las flechas de elfos y arqueros
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
                                                sp.sendSystemMessage(Component.literal(
                                                        "[Sistema] Primero tienes que despertar (pulsa V)."));
                                                return 0;
                                            }
                                            SystemLogic.addXp(sp, IntegerArgumentType.getInteger(ctx, "cantidad"));
                                            return 1;
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

        LOGGER.info("El Sistema cargado");
    }
}
