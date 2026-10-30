package dev.kizuna.bs3d.pocket.mixin;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Redirect(method = {"getEmptySlot", "getOccupiedSlotWithRoomForStack", "getSlotWithStack", "indexOf"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;size()I"))
    private int bs3dPocket$searchLimit(DefaultedList<ItemStack> stacks) {
        // 存档仍是 36 格，自动寻位只扫描口袋与快捷栏；选中格和副手的原版合并优先级不变。
        return PocketSlots.AVAILABLE_SLOTS;
    }

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$explicitSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        // 指定旧主背包槽的调用视为放不下，调用者保留原栈并按原版背包满处理。
        if (PocketSlots.unavailable(slot)) {
            callback.setReturnValue(false);
        }
    }
}
