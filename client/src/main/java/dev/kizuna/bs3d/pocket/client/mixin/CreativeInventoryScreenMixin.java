package dev.kizuna.bs3d.pocket.client.mixin;

import dev.kizuna.bs3d.pocket.client.PocketCreative;
import dev.kizuna.bs3d.pocket.client.TextureSlices;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin
        extends AbstractInventoryScreen<CreativeInventoryScreen.CreativeScreenHandler> implements StationHost {
    @Shadow private static ItemGroup selectedTab;
    @Unique private int bs3dPocket$fullHeight;

    protected CreativeInventoryScreenMixin(CreativeInventoryScreen.CreativeScreenHandler handler,
                                           PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bs3dPocket$create(PlayerEntity player, FeatureSet features, boolean operatorTab,
                                  CallbackInfo callback) {
        // 记录原版窗口高度，离开物品栏页签时恢复。
        bs3dPocket$fullHeight = backgroundHeight;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void bs3dPocket$init(CallbackInfo callback) {
        // 窗口原点始终按原版高度计算，切换页签与布局只移动底边，上排页签与搜索框不跳动。
        backgroundHeight = bs3dPocket$fullHeight;
    }

    @Inject(method = "setSelectedTab", at = @At("RETURN"))
    private void bs3dPocket$tab(ItemGroup group, CallbackInfo callback) {
        // 原版刚建好新页签的槽对象，先排一次，首帧之前的鼠标事件同样按当前布局命中。
        bs3dPocket$arrange();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bs3dPocket$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
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
        if (selectedTab.getType() != ItemGroup.Type.INVENTORY) {
            backgroundHeight = bs3dPocket$fullHeight;
            return;
        }
        var slices = bs3dPocket$slices();
        backgroundHeight = slices.height();
        for (Slot slot : handler.slots) {
            slices.arrange(slot);
        }
    }

    @Redirect(method = "drawBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
            ordinal = 0))
    private void bs3dPocket$background(DrawContext context, Identifier texture, int left, int top,
                                      int u, int v, int regionWidth, int regionHeight) {
        // 只裁接物品栏页签背景，其他页签与滚动条保持原纹理。
        if (selectedTab.getType() != ItemGroup.Type.INVENTORY) {
            context.drawTexture(texture, left, top, u, v, regionWidth, regionHeight);
            return;
        }
        bs3dPocket$slices().draw(context, texture, left, top);
    }

    @Redirect(method = "drawBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawEntity"
                    + "(Lnet/minecraft/client/gui/DrawContext;IIIFFLnet/minecraft/entity/LivingEntity;)V"))
    private void bs3dPocket$preview(DrawContext context, int x, int y, int size, float mouseX, float mouseY,
                                   LivingEntity entity) {
        // 紧凑布局由左侧三维人物代替原版二维预览。
        if (bs3dPocket$compact()) {
            return;
        }
        InventoryScreen.drawEntity(context, x, y, size, mouseX, mouseY, entity);
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
