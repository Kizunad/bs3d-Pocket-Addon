package dev.kizuna.bs3d.pocket.mixin;

import dev.kizuna.bs3d.pocket.server.PocketClicks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$click(int slot, int button, SlotActionType action, PlayerEntity player,
                                 CallbackInfo callback) {
        // 拒绝时光标和旧槽都不变；原版点击包处理随后按真实库存纠正客户端预测。
        if (PocketClicks.blocked((ScreenHandler) (Object) this, slot)) {
            callback.cancel();
        }
    }

    @Redirect(method = "insertItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack bs3dPocket$merge(Slot slot) {
        // 原版合并阶段不调用 canInsert，必须先让不可用格退出合并搜索。
        if (PocketClicks.blocked(slot)) {
            return ItemStack.EMPTY;
        }
        return slot.getStack();
    }

    @Redirect(method = "insertItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/slot/Slot;canInsert(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean bs3dPocket$empty(Slot slot, ItemStack stack) {
        // 空格阶段也检查实际槽号，覆盖覆写 canInsert 的第三方槽位类型。
        return !PocketClicks.blocked(slot) && slot.canInsert(stack);
    }
}
