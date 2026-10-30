package dev.kizuna.bs3d.pocket.mixin;

import dev.kizuna.bs3d.pocket.server.PocketClicks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$insert(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        // BS3D 使用原版槽位能力判定，隐藏格必须在同一入口拒绝。
        if (PocketClicks.blocked((Slot) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "canTakeItems", at = @At("HEAD"), cancellable = true)
    private void bs3dPocket$take(PlayerEntity player, CallbackInfoReturnable<Boolean> callback) {
        // 外部直接调用 Slot 也不能把旧槽作为正常可交互来源。
        if (PocketClicks.blocked((Slot) (Object) this)) {
            callback.setReturnValue(false);
        }
    }
}
