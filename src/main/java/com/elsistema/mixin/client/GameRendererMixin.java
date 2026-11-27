package com.elsistema.mixin.client;

import com.elsistema.client.Tonos;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.8.0: el shader de los tonos del Sistema se dibuja justo después del mundo (con la mano y los contornos de los
 * monstruos) y antes de la interfaz, en el mismo sitio que los efectos de Minecraft (como al ver por los ojos de un
 * creeper en espectador).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
                    shift = At.Shift.AFTER))
    private void elsistema$tonos(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        Tonos.onMixinFrame(deltaTracker.getGameTimeDeltaTicks());
    }
}
