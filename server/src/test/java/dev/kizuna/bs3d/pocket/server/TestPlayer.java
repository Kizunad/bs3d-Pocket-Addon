package dev.kizuna.bs3d.pocket.server;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import sun.misc.Unsafe;
import java.util.ArrayList;
import java.util.List;

/** 不创建世界的测试玩家：跳过实体构造，只接上真实库存、玩家菜单，并记录掉落与提示。 */
final class TestPlayer extends ServerPlayerEntity {
    final List<ItemStack> dropped = new ArrayList<>();
    final List<Text> overlays = new ArrayList<>();

    private TestPlayer() {
        super(null, null, null);
        // 实例只由 create 分配，真实构造需要服务器与世界。
        throw new AssertionError();
    }

    static TestPlayer create() throws ReflectiveOperationException {
        // 库存规则只依赖库存、能力与菜单，三者使用原版构造并写回玩家字段。
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true /* flag */);
        var unsafe = (Unsafe) unsafeField.get(null);
        var player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
        var inventory = new PlayerInventory(player);
        var handler = new PlayerScreenHandler(inventory, true /* onServer */, player);
        assign(PlayerEntity.class, player, "inventory", inventory);
        assign(PlayerEntity.class, player, "abilities", new PlayerAbilities());
        assign(PlayerEntity.class, player, "playerScreenHandler", handler);
        assign(TestPlayer.class, player, "dropped", new ArrayList<ItemStack>());
        assign(TestPlayer.class, player, "overlays", new ArrayList<Text>());
        player.currentScreenHandler = handler;
        return player;
    }

    private static void assign(Class<?> owner, TestPlayer player, String name, Object value)
            throws ReflectiveOperationException {
        // 分配实例不会执行字段初始化，final 字段只能在这里补齐。
        var field = owner.getDeclaredField(name);
        field.setAccessible(true /* flag */);
        field.set(player, value);
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return getAbilities().creativeMode;
    }

    @Override
    public ItemEntity dropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
        // 掉落实体需要世界；测试只观察掉出的物品与数量。
        dropped.add(stack.copy());
        return null;
    }

    @Override
    public void sendMessage(Text message, boolean overlay) {
        overlays.add(message);
    }
}
