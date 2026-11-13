package dev.kizuna.bs3d.pocket.server;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 每名玩家只说一次的说明：随身格子变少了。 */
public final class PocketHints {
    /** 说过以后记在玩家身上的原版实体标签；{@code /tag <玩家> remove <标签>} 可以让它再说一遍。 */
    public static final String SLOTS_TAG = "meridian.hint.pocket.slots";
    public static final String SLOTS_KEY = "bs3d_pocket.hint.slots";

    private PocketHints() {
        // 只有静态入口。
    }

    static void slots(ServerPlayer player) {
        // 第一次进服时说：随身只有快捷栏和一排，共多少格。标签存进玩家档，下次登录不再说。
        if (!player.addTag(SLOTS_TAG)) {
            return;
        }
        player.sendSystemMessage(Component.translatable(SLOTS_KEY, PocketSlots.AVAILABLE_SLOTS));
    }
}
