package com.elsistema.client;

import com.elsistema.Net;
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

    @Override
    public void onInitializeClient() {
        STATUS = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.elsistema.status", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.elsistema"));
        SKILL = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.elsistema.skill", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.elsistema"));

        ClientPlayNetworking.registerGlobalReceiver(Net.SyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> CData.set(payload.data())));

        ClientPlayNetworking.registerGlobalReceiver(Net.NotifyPayload.TYPE, (payload, context) ->
                context.client().execute(() -> Hud.onNotify(payload.kind(), payload.a(), payload.b())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CData.reset());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (STATUS.consumeClick()) {
                if (client.screen == null && client.player != null) {
                    if (CData.awakened()) {
                        client.setScreen(new StatusScreen());
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
            CData.tick();
        });

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> Hud.render(graphics));
    }
}
