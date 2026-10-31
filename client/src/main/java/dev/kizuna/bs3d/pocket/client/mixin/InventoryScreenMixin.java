package dev.kizuna.bs3d.pocket.client.mixin;

import dev.kizuna.bs3d.pocket.client.PocketLayout;
import dev.kizuna.bs3d.pocket.client.PocketPage;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import dev.kizuna.bs3d.vanilla.client.StationLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原地改写原版 E 页：场景就绪时用紧凑布局，否则保留原版页面并裁掉两行主背包。 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu>
        implements StationHost {
    @Unique private static final int BS3D_POCKET_BUTTON_X_PIXELS = 104;
    @Unique private static final int BS3D_POCKET_BUTTON_Y_PIXELS = 61;
    @Shadow @Final private RecipeBookComponent recipeBookComponent;
    @Unique private AbstractWidget bs3dPocket$recipeButton;

    protected InventoryScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener bs3dPocket$recipeButton(GuiEventListener button) {
        // 配方书按钮是 E 页唯一的原版按钮，保留引用以便两种布局各自摆放。
        bs3dPocket$recipeButton = (AbstractWidget) button;
        return button;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void bs3dPocket$init(CallbackInfo callback) {
        // 首帧之前到达的鼠标事件同样按当前布局命中。
        bs3dPocket$arrange();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bs3dPocket$render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        // 每帧从原版坐标重新排版，窗口缩放、配方书开合与库存同步都会切换布局。
        bs3dPocket$arrange();
    }

    @Unique
    private boolean bs3dPocket$compact() {
        // 面板停用（配方书展开、窗口过小或库存尚未同步）时整页退回原版布局。
        var panel = stationPanel();
        if (panel == null) {
            return false;
        }
        return panel.enabled();
    }

    @Unique
    private void bs3dPocket$arrange() {
        if (bs3dPocket$compact()) {
            bs3dPocket$arrangeCompact();
        } else {
            bs3dPocket$arrangeFallback();
        }
    }

    @Unique
    private void bs3dPocket$arrangeCompact() {
        // 原版背景原点移到底部菜单，槽位坐标、悬停与点击边界随之一致。
        var layout = PocketLayout.calculate(width, height);
        var menu = layout.menu();
        var button = layout.recipeButton();
        leftPos = menu.x();
        topPos = menu.y();
        imageHeight = menu.height();
        PocketPage.arrangeCompact(this.menu, layout);
        bs3dPocket$recipeButton.setPosition(button.x(), button.y());
    }

    @Unique
    private void bs3dPocket$arrangeFallback() {
        // 按缩短后的高度居中，配方书按钮保持原版相对位置。
        imageHeight = PocketPage.FALLBACK_HEIGHT_PIXELS;
        leftPos = recipeBookComponent.updateScreenPosition(width, imageWidth);
        topPos = (height - imageHeight) / 2;
        PocketPage.arrangeFallback(menu);
        bs3dPocket$recipeButton.setPosition(leftPos + BS3D_POCKET_BUTTON_X_PIXELS, topPos + BS3D_POCKET_BUTTON_Y_PIXELS);
    }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$compactBackground(GuiGraphics context, float delta, int mouseX, int mouseY,
                                             CallbackInfo callback) {
        // 紧凑页不画二维人物、盔甲与副手，这些由左侧三维模型承担。
        if (!bs3dPocket$compact()) {
            return;
        }
        var layout = PocketLayout.calculate(width, height);
        PocketPage.drawCompact(context, layout, title, stationPanel().interiorOpen());
        callback.cancel();
    }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void bs3dPocket$fallbackBackground(GuiGraphics context, ResourceLocation texture, int left, int top,
                                              int u, int v, int regionWidth, int regionHeight) {
        // 退回布局只裁掉被移除的两行，二维人物仍由原版在原位置绘制。
        PocketPage.drawFallback(context, texture, left, top);
    }

    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$foreground(GuiGraphics context, int mouseX, int mouseY, CallbackInfo callback) {
        // 合成标题已随右侧合成面板绘制，底部菜单不再重复。
        if (bs3dPocket$compact()) {
            callback.cancel();
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$outside(double mouseX, double mouseY, int left, int top, int button,
                                   CallbackInfoReturnable<Boolean> callback) {
        // 合成面板位于原版背景之外，点在其中不能被原版解析为丢出光标物品。
        if (!bs3dPocket$compact()) {
            return;
        }
        boolean craftingShown = !stationPanel().interiorOpen();
        var layout = PocketLayout.calculate(width, height);
        callback.setReturnValue(!layout.inside(mouseX, mouseY, craftingShown));
    }

    @Override
    public StationLayout stationLayout() {
        // 场景仍由 Vanilla-Addon 绘制与交互；退回布局时面板整体停用，不再读取该区域。
        return PocketLayout.calculate(width, height).scenes();
    }

    @Override
    public boolean stationSlotVisible(Slot slot) {
        // 退回布局保留原版装备槽，口袋行数由 Vanilla-Addon 的统一行数规则裁剪。
        if (!bs3dPocket$compact()) {
            return true;
        }
        return PocketPage.compactVisible(slot, stationPanel().interiorOpen());
    }
}
