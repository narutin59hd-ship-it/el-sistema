package com.elsistema.mixin.client;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * La Ciudad de los Cazadores es una dimensión definida con datos (data/elsistema/dimension/ciudad.json). Para
 * Minecraft eso son «ajustes experimentales» y avisaría cada vez que abres o creas un mundo: aquí se quita el aviso.
 */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
    @Redirect(method = "openWorldCheckWorldStemCompatibility", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/WorldData;worldGenSettingsLifecycle()Lcom/mojang/serialization/Lifecycle;"))
    private Lifecycle elsistema$sinAvisoAlAbrir(WorldData data) {
        return Lifecycle.stable();
    }

    @ModifyVariable(method = "confirmWorldCreation", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Lifecycle elsistema$sinAvisoAlCrear(Lifecycle lifecycle) {
        return Lifecycle.stable();
    }
}
