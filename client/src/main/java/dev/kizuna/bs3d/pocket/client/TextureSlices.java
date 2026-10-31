package dev.kizuna.bs3d.pocket.client;

import dev.kizuna.bs3d.vanilla.client.SlotPosition;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

/** 原版页面纹理按行切片拼接；槽位坐标与背景使用同一组切片平移。 */
public record TextureSlices(int width, List<Slice> slices) {
    /** 原版容器页面纹理（玩家页与创造页签背景）均为 256×256。 */
    public static final int TEXTURE_SIZE_PIXELS = 256;
    private static final Map<Slot, Position> VANILLA = new WeakHashMap<>();

    public int height() {
        return slices.stream().mapToInt(Slice::height).sum();
    }

    public int offset(int y) {
        // 被裁掉行内的槽位保持原位，由可见性规则隐藏且不参与命中。
        return slices.stream()
                .filter(slice -> slice.contains(y))
                .mapToInt(Slice::shift)
                .findFirst()
                .orElse(0);
    }

    public void arrange(Slot slot) {
        Position vanilla = vanilla(slot);
        move(slot, vanilla.x(), vanilla.y() + offset(vanilla.y()));
    }

    public void draw(GuiGraphicsExtractor context, Identifier texture, int left, int top) {
        // 每段按原版纹理一比一绘制，槽位边框与槽位坐标使用同一组切片。
        for (Slice slice : slices) {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, left, top + slice.targetY(), 0, slice.sourceY(),
                    width, slice.height(), TEXTURE_SIZE_PIXELS, TEXTURE_SIZE_PIXELS);
        }
    }

    public static Position vanilla(Slot slot) {
        // 槽对象首次被排版前的坐标即原版坐标，之后每次都从它重新排版。
        return VANILLA.computeIfAbsent(slot, value -> new Position(value.x, value.y));
    }

    public static void move(Slot slot, int x, int y) {
        // 只改坐标，不替换带绑定诅咒与装备类型检查的原版槽对象。
        var position = (SlotPosition) slot;
        position.bs3dVanilla$setX(x);
        position.bs3dVanilla$setY(y);
    }

    public record Position(int x, int y) {}

    public record Slice(int sourceY, int height, int targetY) {
        boolean contains(int y) {
            return y >= sourceY && y < sourceY + height;
        }

        int shift() {
            return targetY - sourceY;
        }
    }
}
