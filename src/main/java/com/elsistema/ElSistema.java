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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
            Fx.forget(handler.player.getUUID());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SecretBoss.reset();
            Dungeons.shutdown();
            Shadows.shutdown(server);
            Store.saveAll(server);
            Store.clear();
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SystemLogic.onRespawn(newPlayer, alive));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SystemLogic.tick(server);
            Dungeons.tick(server);
            Shadows.tick(server);
            City.tick(server);
            Forge.tick();
        });

        // EXP, oro y botín al matar criaturas (cuerpo a cuerpo, flechas, habilidades o tus sombras)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer sp && entity != sp) {
                Ranks.onKill(sp, entity, 1.0);
                Quests.onKill(sp, entity);
                SideQuests.onKill(sp, entity);
            } else if (Shadows.isShadow(source.getEntity())) {
                ServerPlayer owner = Shadows.ownerPlayer(source.getEntity());
                if (owner != null) {
                    Ranks.onKill(owner, entity, 0.6);
                    Quests.onKill(owner, entity);
                    SideQuests.onKill(owner, entity);
                }
            }
            Shadows.onDeath(entity, source);
        });

        // Las sombras no mueren: vuelven a la sombra de su dueño para recuperarse
        ServerLivingEntityEvents.ALLOW_DEATH.register(Shadows::allowDeath);
        // Armadura con «Último aliento» (herrería de la ciudad): te salva una vez cada 5 minutos
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer sp) || !Forge.saveFromDeath(sp));

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
                !(player instanceof ServerPlayer sp) || Store.get(sp).inDungeon != 1 && (!City.inCity(sp) || sp.isCreative()));

        // La ciudad no se puede romper ni llenar de bloques (salvo en creativo)
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player instanceof ServerPlayer sp && City.inCity(sp) && !sp.isCreative()) {
                net.minecraft.world.item.ItemStack held = sp.getItemInHand(hand);
                if (held.getItem() instanceof net.minecraft.world.item.BlockItem
                        || held.getItem() instanceof net.minecraft.world.item.BucketItem) {
                    return net.minecraft.world.InteractionResult.FAIL;
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // Flechas más fuertes y monstruos con rango
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> SystemLogic.onEntityLoad(entity));

        // 1.8.2: /spawn lleva a cualquiera a la plaza de la Ciudad de los Cazadores (no necesita trucos)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("spawn")
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            City.toSpawn(sp, Store.get(sp));
                            return 1;
                        })));

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
                        .then(Commands.literal("esencias")
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 1000000))
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            Data d = Store.get(sp);
                                            d.essences += IntegerArgumentType.getInteger(ctx, "cantidad");
                                            SystemLogic.sync(sp, d);
                                            return 1;
                                        })))
                        .then(Commands.literal("ciudad")
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                    Data d = Store.get(sp);
                                    if (City.inCity(sp)) City.back(sp, d);
                                    else City.travel(sp, d);
                                    return 1;
                                }))
                        .then(Commands.literal("sombras")
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            int n = Shadows.addPointsAll(sp, IntegerArgumentType.getInteger(ctx, "cantidad"));
                                            sp.sendSystemMessage(Component.literal("[Sistema] Puntos de árbol dados a " + n + " sombras."));
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
                        .then(Commands.literal("jefe")
                                .then(Commands.argument("tipo", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            SecretBoss.Kind k = SecretBoss.parse(StringArgumentType.getString(ctx, "tipo"));
                                            if (k == null) {
                                                sp.sendSystemMessage(Component.literal("[Sistema] Usa: guardian, eclipse, reina o coloso."));
                                                return 0;
                                            }
                                            if (Store.get(sp).awakened != 1) {
                                                sp.sendSystemMessage(Component.literal("[Sistema] Primero tienes que despertar (pulsa V)."));
                                                return 0;
                                            }
                                            if (SecretBoss.spawnNear(sp, k, true) == null) {
                                                sp.sendSystemMessage(Component.literal("[Sistema] No hay sitio para el jefe aquí."));
                                                return 0;
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("prueba")
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                    Data d = Store.get(sp);
                                    if (d.awakened != 1) {
                                        sp.sendSystemMessage(Component.literal("[Sistema] Primero tienes que despertar (pulsa V)."));
                                        return 0;
                                    }
                                    if (d.mutated == 1) {
                                        sp.sendSystemMessage(Component.literal("[Sistema] Tu clase ya ha mutado."));
                                        return 0;
                                    }
                                    if (d.quest[Quests.Q_CLASS] == Quests.HIDDEN) Quests.discover(sp, d, Quests.Q_CLASS);
                                    Quests.startTrial(sp);
                                    return 1;
                                }))
                        .then(Commands.literal("cambiar")
                                .executes(ctx -> {
                                    SystemLogic.rechoose(ctx.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("reiniciar")
                                .executes(ctx -> {
                                    SystemLogic.reset(ctx.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("nivel")
                                .then(Commands.argument("nivel", IntegerArgumentType.integer(1, Defs.MAX_LEVEL))
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            Data d = Store.get(sp);
                                            if (d.awakened != 1) {
                                                sp.sendSystemMessage(Component.literal("[Sistema] Primero tienes que despertar (pulsa V)."));
                                                return 0;
                                            }
                                            int lv = IntegerArgumentType.getInteger(ctx, "nivel");
                                            if (lv > d.level) d.points += (lv - d.level) * SystemLogic.pointsPerLevel(d);
                                            d.level = lv;
                                            d.xp = 0;
                                            SystemLogic.applyAll(sp, d);
                                            SystemLogic.sync(sp, d);
                                            sp.sendSystemMessage(Component.literal("[Sistema] Ahora eres nivel " + lv + "."));
                                            return 1;
                                        })))
                        .then(Commands.literal("rango")
                                .then(Commands.argument("rango", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            int rank = Defs.rankIndex(StringArgumentType.getString(ctx, "rango"));
                                            if (rank < 0) {
                                                sp.sendSystemMessage(Component.literal("[Sistema] Rango no válido. Usa E, D, C, B, A o S."));
                                                return 0;
                                            }
                                            Data d = Store.get(sp);
                                            d.hunterRank = rank;
                                            SystemLogic.applyAll(sp, d);
                                            Shadows.refresh(sp);
                                            SystemLogic.sync(sp, d);
                                            Shadows.sendList(sp);
                                            sp.sendSystemMessage(Component.literal("[Sistema] Ahora eres rango " + Defs.RANK[rank] + "."));
                                            return 1;
                                        })))
                        .then(Commands.literal("examen")
                                .executes(ctx -> {
                                    Ranking.startExam(ctx.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("prestigio")
                                .executes(ctx -> {
                                    Ranking.prestige(ctx.getSource().getPlayerOrException());
                                    return 1;
                                }))));

        String selfTest = System.getenv("ELSISTEMA_SELFTEST");
        if (selfTest != null && !selfTest.isBlank()) {
            SelfTest.register(selfTest);
        }

        LOGGER.info("El Sistema cargado");
    }
}
