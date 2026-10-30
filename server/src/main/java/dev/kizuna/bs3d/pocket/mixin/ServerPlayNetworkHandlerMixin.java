package dev.kizuna.bs3d.pocket.mixin;

import dev.kizuna.bs3d.pocket.server.PocketClicks;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onCreativeInventoryAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread"
                    + "(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;"
                    + "Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void bs3dPocket$creative(CreativeInventoryActionC2SPacket packet, CallbackInfo callback) {
        // 创造模式直接写槽，不经过 onSlotClick；拒绝后整页重发，撤销客户端已显示的预测。
        if (PocketClicks.blocked(player.playerScreenHandler, packet.getSlot())) {
            player.playerScreenHandler.syncState();
            callback.cancel();
        }
    }
}
