package com.elsistema.client;

import com.elsistema.Net;
import com.elsistema.client.render.ModRenderers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ElSistemaClient implements ClientModInitializer {
    public static KeyMapping STATUS;
    public static KeyMapping SKILL;
    public static KeyMapping ARISE;
    public static KeyMapping SHADOWS;

    private static int pruneTimer = 0;

    @Override
    public void onInitializeClient() {
        STATUS = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.elsistema.status", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.elsistema"));
        SKILL = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.elsistema.skill", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.elsistema"));
        ARISE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.elsistema.arise", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.elsistema"));
        SHADOWS = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.elsistema.shadows", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.elsistema"));

        ModRenderers.register();

        ClientPlayNetworking.registerGlobalReceiver(Net.SyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> CData.set(payload.data())));

        ClientPlayNetworking.registerGlobalReceiver(Net.NotifyPayload.TYPE, (payload, context) ->
                context.client().execute(() -> Hud.onNotify(payload.kind(), payload.a(), payload.b())));

        ClientPlayNetworking.registerGlobalReceiver(Net.ShadowsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientShadows.set(payload.nums(), payload.strs())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            CData.reset();
            ClientShadows.reset();
            Hud.reset();
        }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (STATUS.consumeClick()) {
                if (client.screen == null && client.player != null) {
                    if (CData.awakened()) {
                        client.setScreen(new SystemScreen());
                    } else {
                        client.setScreen(new AwakeningScreen());
                    }
                }
            }
            while (SKILL.consumeClick()) {
                if (client.screen == null && client.player != null) {
                    ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_SKILL, 0, 0));
                }
            }
            while (ARISE.consumeClick()) {
                if (client.screen == null && client.player != null) {
                    ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_ARISE, 0, 0));
                }
            }
            while (SHADOWS.consumeClick()) {
                if (client.screen == null && client.player != null) {
                    ClientPlayNetworking.send(new Net.ActionPayload(Net.ACT_SHADOWS_TOGGLE, 0, 0));
                }
            }
            CData.tick();
            if (++pruneTimer >= 200) {
                pruneTimer = 0;
                if (client.level != null) ClientShadows.prune(client.level);
            }
        });

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> Hud.render(graphics));

        if ("client".equalsIgnoreCase(System.getenv("ELSISTEMA_SELFTEST"))) {
            ClientSelfTest.register();
        }
    }
}
