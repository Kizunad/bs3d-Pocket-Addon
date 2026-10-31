package dev.kizuna.bs3d.pocket.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 26.2 的页面高度改为 final；E 页与创造页按可见口袋行改写高度，需要去掉 final 后写入。 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
    @Mutable
    @Accessor("imageHeight")
    void bs3dPocket$setImageHeight(int heightPixels);
}
