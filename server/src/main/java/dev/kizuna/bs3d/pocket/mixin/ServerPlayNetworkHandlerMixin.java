package dev.kizuna.bs3d.pocket.mixin;

import dev.kizuna.bs3d.pocket.server.PocketClicks;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void bs3dPocket$creative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo callback) {
        // 创造模式直接写槽，不经过 onSlotClick；拒绝后整页重发，撤销客户端已显示的预测。
        if (PocketClicks.blocked(player.inventoryMenu, packet.slotNum())) {
            player.inventoryMenu.sendAllDataToRemote();
            callback.cancel();
        }
    }
}
