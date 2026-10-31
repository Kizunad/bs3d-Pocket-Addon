package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.backpack3d.client.InteriorRenderer;
import dev.kizuna.backpack3d.client.InteriorRenderer.Rect;
import dev.kizuna.backpack3d.client.WornPreview;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import dev.kizuna.bs3d.vanilla.client.StationPanel;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.Slot;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** 验收只经由真实 Screen 输入与按键绑定；反射仅用于读取场景投影找命中点。 */
final class SmokeInput {
    private static final int SLOT_CENTER_PIXELS = 8;

    private SmokeInput() {
        // 静态工具，不保存任何页面或库存状态。
    }

    static void pressKey(KeyMapping binding) {
        // 与键盘回调相同地累计按下次数，客户端 tick 按原版流程打开页面。
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(binding));
    }

    static void closeWithKey(AbstractContainerScreen<?> screen, KeyMapping binding) {
        // 页面内再次按物品栏键，走原版 keyPressed 关闭路径。
        screen.keyPressed(new KeyEvent(KeyMappingHelper.getBoundKeyOf(binding).getValue(), 0, 0));
    }

    static int[] slotPoint(AbstractContainerScreen<?> screen, Slot slot) {
        // 原版槽位坐标相对于菜单原点。
        var host = (StationHost) screen;
        return new int[] {host.stationLeft() + slot.x + SLOT_CENTER_PIXELS,
                host.stationTop() + slot.y + SLOT_CENTER_PIXELS};
    }

    static void click(Screen screen, int[] point) {
        // 完整按下和松开，不留下上一轮的拖动捕获。
        var event = leftButton(point);
        screen.mouseClicked(event, false /* doubleClick */);
        screen.mouseReleased(event);
    }

    static void drag(AbstractContainerScreen<?> screen, int[] source, int[] target) {
        // 按下、移动、松开三步全部经过页面入口，由附属决定来源与落点。
        screen.mouseClicked(leftButton(source), false /* doubleClick */);
        screen.mouseDragged(leftButton(target), target[0] - source[0], target[1] - source[1]);
        screen.mouseReleased(leftButton(target));
    }

    private static MouseButtonEvent leftButton(int[] point) {
        return new MouseButtonEvent(point[0], point[1], new MouseButtonInfo(0 /* 左键 */, 0 /* 无修饰键 */));
    }

    static int[] modelPoint(AbstractContainerScreen<?> screen, String bag) {
        // 只在人物场景里找容器模型的实际表面像素。
        WornPreview player = read(panel(screen), "player", WornPreview.class);
        Rect area = ((StationHost) screen).stationLayout().player();
        for (int y = area.y(); y < area.bottom(); y++) {
            for (int x = area.x(); x < area.right(); x++) {
                if (player.hit(bag, x, y)) {
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    static int[] bodyPoint(AbstractContainerScreen<?> screen, int inventorySlot) {
        // 身体部位命中要求该像素没有被身上容器遮挡。
        WornPreview player = read(panel(screen), "player", WornPreview.class);
        float[] box = player.slotBounds(inventorySlot);
        if (box == null) {
            return null;
        }
        for (int y = (int) box[1]; y <= box[3]; y++) {
            for (int x = (int) box[0]; x <= box[2]; x++) {
                if (player.slotAt(x, y) == inventorySlot && player.pick(x, y).isEmpty()) {
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    static int[] itemPoint(AbstractContainerScreen<?> screen, String bag) {
        // 内景尚未完成开盖渲染时返回 null，由调用方继续等待。
        Map<?, ?> interiors = read(panel(screen), "interiors", Map.class);
        var interior = (InteriorRenderer) interiors.get(bag);
        if (interior == null) {
            return null;
        }
        Rect area = ((StationHost) screen).stationLayout().interior();
        for (int y = area.y(); y < area.bottom(); y++) {
            for (int x = area.x(); x < area.right(); x++) {
                if (!interior.itemAt(x, y).isEmpty()) {
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    static StationPanel panel(AbstractContainerScreen<?> screen) {
        return ((StationHost) screen).stationPanel();
    }

    static <T> T read(Object object, String name, Class<T> type) {
        // 夹具读取场景投影与原版页面尺寸，生产 API 不暴露测试专用入口。
        try {
            return type.cast(field(object.getClass(), name).get(object));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static int invokeInt(Object object, String name, Class<?> parameter, Object argument) {
        // 原版页签坐标是私有方法，夹具按原版计算结果定位真实点击点。
        try {
            var method = object.getClass().getDeclaredMethod(name, parameter);
            method.setAccessible(true /* flag */);
            return (int) method.invoke(object, argument);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        // 原版页面字段可能声明在 AbstractContainerScreen 等父类。
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true /* flag */);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException(name);
    }

    static AtomicBoolean screenshot(Minecraft client, String name) {
        // 复制最近一帧的帧缓冲，文件在 IO 线程写完后回调；何时调用由 Evidence 按帧号决定。
        var saved = new AtomicBoolean();
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1,
                message -> saved.set(true /* saved */));
        return saved;
    }

    static void require(boolean condition, String reason) {
        if (!condition) {
            throw new IllegalStateException(reason);
        }
    }
}
