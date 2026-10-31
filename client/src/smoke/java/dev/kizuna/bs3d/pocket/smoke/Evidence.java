package dev.kizuna.bs3d.pocket.smoke;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

/** 截图在本帧绘制后才读取帧缓冲；读完之前不发下一次输入，画面只含当前步骤的结果。 */
final class Evidence {
    private AtomicBoolean saved;

    boolean captured(Minecraft client, String name) {
        // 首次调用排队截图，之后每 tick 查询；读完返回 true 并复位，供下一张使用。
        if (saved == null) {
            saved = SmokeInput.screenshot(client, name);
        }
        if (!saved.get()) {
            return false;
        }
        saved = null;
        return true;
    }
}
