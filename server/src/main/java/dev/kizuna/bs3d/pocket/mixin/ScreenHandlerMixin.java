package dev.kizuna.bs3d.pocket.mixin;

import dev.kizuna.bs3d.pocket.server.PocketClicks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$click(int slot, int button, ContainerInput action, Player player,
                                 CallbackInfo callback) {
        // 拒绝时光标和旧槽都不变；原版点击包处理随后按真实库存纠正客户端预测。
        if (PocketClicks.blocked((AbstractContainerMenu) (Object) this, slot)) {
            callback.cancel();
        }
    }

    @Redirect(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bs3dPocket$merge(Slot slot) {
        // 原版合并阶段不调用 canInsert，必须先让不可用格退出合并搜索。
        if (PocketClicks.blocked(slot)) {
            return ItemStack.EMPTY;
        }
        return slot.getItem();
    }

    @Redirect(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean bs3dPocket$empty(Slot slot, ItemStack stack) {
        // 空格阶段也检查实际槽号，覆盖覆写 canInsert 的第三方槽位类型。
        return !PocketClicks.blocked(slot) && slot.mayPlace(stack);
    }
}
