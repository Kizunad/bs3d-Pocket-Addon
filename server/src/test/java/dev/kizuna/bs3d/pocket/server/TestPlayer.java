package dev.kizuna.bs3d.pocket.server;

import dev.kizuna.backpack3d.server.PlayerStorage;
import dev.kizuna.backpack3d.service.TransferService;
import sun.misc.Unsafe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerEquipment;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/** 不创建世界的测试玩家：跳过实体构造，只接上真实库存、玩家菜单，并记录掉落与提示。 */
final class TestPlayer extends ServerPlayer {
    final List<ItemStack> dropped = new ArrayList<>();
    final List<Component> overlays = new ArrayList<>();

    private TestPlayer() {
        super(null, null, null, null);
        // 实例只由 create 分配，真实构造需要服务器与世界。
        throw new AssertionError();
    }

    static TestPlayer create() throws ReflectiveOperationException {
        // 库存规则只依赖库存、装备、能力与菜单，均使用原版构造并写回玩家字段；26.2 的盔甲与副手存放在实体装备中。
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true /* flag */);
        var unsafe = (Unsafe) unsafeField.get(null);
        var player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
        // allocateInstance 同样跳过 BS3D Mixin 字段初始化，装备校验仍需要合法的空库存。
        ((PlayerStorage) (Object) player).bs3d$setInventory(TransferService.empty());
        var equipment = new PlayerEquipment(player);
        var inventory = new Inventory(player, equipment);
        var handler = new InventoryMenu(inventory, true /* onServer */, player);
        assign(LivingEntity.class, player, "equipment", equipment);
        assign(Player.class, player, "inventory", inventory);
        assign(Player.class, player, "abilities", new Abilities());
        assign(Player.class, player, "inventoryMenu", handler);
        assign(TestPlayer.class, player, "dropped", new ArrayList<ItemStack>());
        assign(TestPlayer.class, player, "overlays", new ArrayList<Component>());
        player.containerMenu = handler;
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
        return getAbilities().instabuild;
    }

    @Override
    public ItemEntity drop(ItemStack stack, boolean randomly, boolean thrownFromHand) {
        // 掉落实体需要世界；测试只观察掉出的物品与数量。
        dropped.add(stack.copy());
        return null;
    }

    @Override
    public void sendOverlayMessage(Component message) {
        overlays.add(message);
    }
}
