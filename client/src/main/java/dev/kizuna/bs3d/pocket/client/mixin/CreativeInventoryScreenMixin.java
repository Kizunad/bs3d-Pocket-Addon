package dev.kizuna.bs3d.pocket.client.mixin;

import dev.kizuna.bs3d.pocket.client.PocketCreative;
import dev.kizuna.bs3d.pocket.client.TextureSlices;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 创造模式「物品栏」页签与 E 页同一做法：三维场景启用时只留口袋行、快捷栏与删除格，
 * 否则保留原版上半页并裁掉两行主背包。搜索与物品类别页签保持原版。
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin
        extends EffectRenderingInventoryScreen<CreativeModeInventoryScreen.ItemPickerMenu> implements StationHost {
    @Shadow private static CreativeModeTab selectedTab;
    @Unique private int bs3dPocket$fullHeight;

    protected CreativeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu handler,
                                           Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bs3dPocket$create(Player player, FeatureFlagSet features, boolean operatorTab,
                                  CallbackInfo callback) {
        // 记录原版窗口高度，离开物品栏页签时恢复。
        bs3dPocket$fullHeight = imageHeight;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void bs3dPocket$init(CallbackInfo callback) {
        // 窗口原点始终按原版高度计算，切换页签与布局只移动底边，上排页签与搜索框不跳动。
        imageHeight = bs3dPocket$fullHeight;
    }

    @Inject(method = "selectTab", at = @At("RETURN"))
    private void bs3dPocket$tab(CreativeModeTab group, CallbackInfo callback) {
        // 原版刚建好新页签的槽对象，先排一次，首帧之前的鼠标事件同样按当前布局命中。
        bs3dPocket$arrange();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bs3dPocket$render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        // 每帧从原版坐标重新排版，窗口缩放与库存同步都会切换紧凑与退回布局。
        bs3dPocket$arrange();
    }

    @Unique
    private boolean bs3dPocket$compact() {
        // 场景只在物品栏页签启用；窗口过小或库存尚未同步时整页退回原版上半页。
        var panel = stationPanel();
        if (panel == null) {
            return false;
        }
        return panel.enabled();
    }

    @Unique
    private TextureSlices bs3dPocket$slices() {
        // 场景启用时裁成口袋行、快捷栏与删除格，否则保留原版上半页。
        if (bs3dPocket$compact()) {
            return PocketCreative.COMPACT;
        }
        return PocketCreative.FALLBACK;
    }

    @Unique
    private void bs3dPocket$arrange() {
        // 底排页签与点击边界读取同一高度；其他页签恢复原版高度，槽位由原版重新建立。
        if (selectedTab.getType() != CreativeModeTab.Type.INVENTORY) {
            imageHeight = bs3dPocket$fullHeight;
            return;
        }
        var slices = bs3dPocket$slices();
        imageHeight = slices.height();
        for (Slot slot : menu.slots) {
            slices.arrange(slot);
        }
    }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V",
            ordinal = 0))
    private void bs3dPocket$background(GuiGraphics context, ResourceLocation texture, int left, int top,
                                      int u, int v, int regionWidth, int regionHeight) {
        // 只裁接物品栏页签背景，其他页签与滚动条保持原纹理。
        if (selectedTab.getType() != CreativeModeTab.Type.INVENTORY) {
            context.blit(texture, left, top, u, v, regionWidth, regionHeight);
            return;
        }
        bs3dPocket$slices().draw(context, texture, left, top);
    }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"))
    private void bs3dPocket$preview(GuiGraphics context, int x, int y, int size, float mouseX, float mouseY,
                                   LivingEntity entity) {
        // 紧凑布局由左侧三维人物代替原版二维预览。
        if (bs3dPocket$compact()) {
            return;
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(context, x, y, size, mouseX, mouseY, entity);
    }

    @Override
    public boolean stationSlotVisible(Slot slot) {
        // 物品栏页签的页面槽换成玩家菜单的真实槽再判断；删除格与其他页签的槽没有对应槽，保持原版。
        Slot menuSlot = stationMenuSlot(slot);
        if (menuSlot == null) {
            return true;
        }
        if (bs3dPocket$compact()) {
            return PocketCreative.compactVisible(menuSlot);
        }
        return PocketCreative.fallbackVisible(menuSlot);
    }
}
