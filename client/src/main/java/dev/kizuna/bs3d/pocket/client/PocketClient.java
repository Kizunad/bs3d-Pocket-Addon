package dev.kizuna.bs3d.pocket.client;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import dev.kizuna.bs3d.vanilla.client.PlayerInventoryRows;
import net.fabricmc.api.ClientModInitializer;

/** 槽位权限由服务端 Mixin 决定，客户端只登记展示行数。 */
public final class PocketClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 已适配工作站的玩家区与 E 页共用同一可见行数。
        PlayerInventoryRows.setVisibleRows(PocketSlots.VISIBLE_MAIN_ROWS);
    }
}
