package dev.kizuna.bs3d.pocket.smoke;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;

/** 截图读取本步骤状态之后绘制完成的一帧；存盘之前不发下一次输入，画面只含当前步骤的结果。 */
final class Evidence {
    // 26.2 每轮先 tick 再绘制，tick 内帧缓冲仍是上一帧；按世界绘制结束计帧，界面在同一帧随后提取。
    // 绘制与 tick 同在客户端主线程，计数无需同步。
    private static long frames;
    private long stateFrame = -1;
    private AtomicBoolean saved;

    static void countFrames() {
        LevelRenderEvents.END_MAIN.register(context -> frames++);
    }

    boolean captured(Minecraft client, String name) {
        // 首次调用记下帧号；出现新帧后，帧缓冲已含本步骤状态，再排队截图；存盘返回 true 并复位。
        if (saved == null) {
            if (stateFrame < 0) {
                stateFrame = frames;
                return false;
            }
            if (frames == stateFrame) {
                return false;
            }
            saved = SmokeInput.screenshot(client, name);
        }
        if (!saved.get()) {
            return false;
        }
        stateFrame = -1;
        saved = null;
        return true;
    }
}
