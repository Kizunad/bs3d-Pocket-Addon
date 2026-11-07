package dev.kizuna.bs3d.pocket.client.mixin;

import dev.kizuna.bs3d.vanilla.client.StationHost;
import dev.kizuna.bs3d.vanilla.client.StationLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 紧凑口袋页的状态列表保持原版绘制与悬停，只将起点限制在屏幕内。 */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
    @Unique private static final int BS3D_POCKET_FULL_EFFECT_ROWS = 5;

    @Redirect(method = "extractEffects", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;topPos:I"))
    private int bs3dPocket$effectTop(AbstractContainerScreen<?> screen) {
        // 原版状态列表跟随菜单 topPos；紧凑菜单贴底，必须为整列图标预留高度。
        var host = (StationHost) screen;
        int menuTop = host.stationTop();
        var panel = host.stationPanel();
        if (!(screen instanceof InventoryScreen) || panel == null || !panel.enabled()) {
            return menuTop;
        }
        int count = Minecraft.getInstance().player.getActiveEffects().size();
        int intervals = Math.max(0, count - 1);
        int fullSpacing = EffectsInInventory.SPRITE_SQUARE_SIZE + 1;
        int spacing;
        if (count > BS3D_POCKET_FULL_EFFECT_ROWS) {
            spacing = fullSpacing * (BS3D_POCKET_FULL_EFFECT_ROWS - 1) / intervals;
        } else {
            spacing = fullSpacing;
        }
        int listHeight = EffectsInInventory.SPRITE_SQUARE_SIZE + intervals * spacing;
        int margin = StationLayout.MARGIN_PIXELS;
        return Math.max(margin, Math.min(menuTop, screen.height - margin - listHeight));
    }
}
