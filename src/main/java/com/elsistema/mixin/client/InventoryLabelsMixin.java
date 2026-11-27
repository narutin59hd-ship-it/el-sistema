package com.elsistema.mixin.client;

import com.elsistema.client.Tonos;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 1.8.0: con el pack del Sistema el inventario es oscuro, así que el título «Fabricación» va en azul claro. */
@Mixin(InventoryScreen.class)
public abstract class InventoryLabelsMixin {
    @ModifyArg(method = "renderLabels", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
            index = 4)
    private int elsistema$colorTitulo(int color) {
        return Tonos.labelColor(this, color);
    }
}
