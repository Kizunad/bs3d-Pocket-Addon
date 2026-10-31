package dev.kizuna.bs3d.pocket.server;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** 槽位限制由 Mixin 生效，这里只负责把旧槽里的物品归还给玩家。 */
public final class PocketServer implements ModInitializer {
    @Override
    public void onInitialize() {
        // 登录和重生立即整理；每 tick 再检查，接住其他模组直接写入旧槽的物品。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PocketMigration.clean(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> PocketMigration.clean(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                PocketMigration.clean(player);
            }
        });
    }
}
