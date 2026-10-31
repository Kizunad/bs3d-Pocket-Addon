package dev.kizuna.bs3d.pocket.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.kizuna.bs3d.pocket.client.PocketLayout;
import dev.kizuna.bs3d.pocket.client.PocketPage;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import dev.kizuna.bs3d.vanilla.client.StationLayout;
import dev.kizuna.bs3d.vanilla.client.mixin.RecipeBookScreenAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原地改写原版 E 页：场景就绪时用紧凑布局，否则保留原版页面并裁掉两行主背包。 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu>
        implements StationHost {
    @Unique private static final int BS3D_POCKET_BUTTON_X_PIXELS = 104;
    @Unique private static final int BS3D_POCKET_BUTTON_Y_PIXELS = 61;
    @Unique private ImageButton bs3dPocket$recipeButton;

    protected InventoryScreenMixin(
            InventoryMenu menu, RecipeBookComponent<?> recipeBook, Inventory inventory, Component title) {
        super(menu, recipeBook, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void bs3dPocket$init(CallbackInfo callback) {
        // 创造模式分支直接换成创造页、不经过这里；只有真正初始化的 E 页才排版。
        // 26.2 的配方书按钮由父类私有方法加入，没有可截取的调用点；此时 Fabric 的初始化事件尚未触发，
        // 页面上只有这一个图片按钮，保留引用以便两种布局各自摆放。
        var buttons = children().stream()
                .filter(ImageButton.class::isInstance)
                .map(ImageButton.class::cast)
                .toList();
        if (buttons.size() != 1) {
            throw new IllegalStateException("E 页应只有配方书一个图片按钮，实际 " + buttons.size() + " 个");
        }
        bs3dPocket$recipeButton = buttons.getFirst();
        // 首帧之前到达的鼠标事件同样按当前布局命中。
        bs3dPocket$arrange();
    }

    @Inject(method = "extractBackground", at = @At("HEAD"))
    private void bs3dPocket$frame(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta,
                                  CallbackInfo callback) {
        // 背景是每帧最先提取的部分；从原版坐标重新排版，窗口缩放、配方书开合与库存同步都会切换布局，
        // 背景、槽位与悬停随后都按本帧布局提取。
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
        ((ContainerScreenAccess) (Object) this).bs3dPocket$setImageHeight(menu.height());
        PocketPage.arrangeCompact(this.menu, layout);
        bs3dPocket$recipeButton.setPosition(button.x(), button.y());
    }

    @Unique
    private void bs3dPocket$arrangeFallback() {
        // 按缩短后的高度居中，配方书按钮保持原版相对位置。
        ((ContainerScreenAccess) (Object) this).bs3dPocket$setImageHeight(PocketPage.FALLBACK_HEIGHT_PIXELS);
        var recipeBook = ((RecipeBookScreenAccess) (Object) this).bs3dVanilla$recipeBook();
        leftPos = recipeBook.updateScreenPosition(width, imageWidth);
        topPos = (height - imageHeight) / 2;
        PocketPage.arrangeFallback(menu);
        bs3dPocket$recipeButton.setPosition(leftPos + BS3D_POCKET_BUTTON_X_PIXELS, topPos + BS3D_POCKET_BUTTON_Y_PIXELS);
    }

    @Redirect(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
    private void bs3dPocket$background(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier texture,
                                       int left, int top, float u, float v, int regionWidth, int regionHeight,
                                       int textureWidth, int textureHeight) {
        // 原版暗幕照常；紧凑页拼接底部菜单与合成面板，退回布局只裁掉被移除的两行。
        if (bs3dPocket$compact()) {
            var layout = PocketLayout.calculate(width, height);
            PocketPage.drawCompact(context, layout, title, stationPanel().interiorOpen());
        } else {
            PocketPage.drawFallback(context, texture, left, top);
        }
    }

    @Redirect(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"))
    private void bs3dPocket$entity(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int size,
                                   float offsetY, float mouseX, float mouseY, LivingEntity entity) {
        // 紧凑页不画二维人物，由左侧三维模型承担；退回布局的二维人物仍在原位置。
        if (!bs3dPocket$compact()) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    context, x0, y0, x1, y1, size, offsetY, mouseX, mouseY, entity);
        }
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$foreground(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo callback) {
        // 合成标题已随右侧合成面板绘制，底部菜单不再重复。
        if (bs3dPocket$compact()) {
            callback.cancel();
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        // 26.2 的 E 页沿用配方书父类的判断；紧凑页的合成面板位于原版背景之外，点在其中不能被解析为丢出光标物品。
        if (!bs3dPocket$compact()) {
            return super.hasClickedOutside(mouseX, mouseY, left, top);
        }
        boolean craftingShown = !stationPanel().interiorOpen();
        var layout = PocketLayout.calculate(width, height);
        return !layout.inside(mouseX, mouseY, craftingShown);
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
