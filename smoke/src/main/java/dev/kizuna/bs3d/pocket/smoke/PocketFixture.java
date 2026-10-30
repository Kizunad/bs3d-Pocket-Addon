package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.backpack3d.item.ContainerItems;
import dev.kizuna.backpack3d.protocol.StackCodec;
import dev.kizuna.backpack3d.protocol.Volumes;
import dev.kizuna.backpack3d.protocol.pb.VolumeProtocol.Item;
import dev.kizuna.backpack3d.protocol.pb.VolumeProtocol.Mount;
import dev.kizuna.backpack3d.protocol.pb.VolumeProtocol.WornContainer;
import dev.kizuna.backpack3d.server.PlayerAccount;
import dev.kizuna.bs3d.pocket.api.PocketSlots;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/** 隔离世界的口袋验收夹具：固定初始库存、真实箱子，以及在离线存档里写入旧槽位物品。 */
public final class PocketFixture implements ModInitializer {
    public static final int LEGACY_STACKS = PocketSlots.VANILLA_MAIN_SLOTS - PocketSlots.AVAILABLE_SLOTS;
    public static final int LEGACY_ROOM = 3;
    public static final int FULL_STACK = 64;
    public static final int LOG_COUNT = 4;
    public static final int INGOT_COUNT = 2;
    public static final int STONE_COUNT = 5;
    public static final int INGOT_SLOT = 1;
    public static final int HEAD_SLOT = PlayerInventory.MAIN_SIZE + EquipmentSlot.HEAD.getEntitySlotId();
    private static final int OPERATOR_LEVEL = 2;
    // 随机抛出的掉落物离脚边至多约七格；平台接住它们，免得落下高空时漂出地面统计范围。
    private static final int PLATFORM_RADIUS = 8;
    private static final int PLATFORM_Y = 64;
    private static final int DISTANCE_BLOCKS = 2;
    private static final double BLOCK_CENTER = 0.5;
    private static boolean legacyArmed;

    @Override
    public void onInitialize() {
        // 只在隔离测试世界登记命令；生产模组不包含这些入口。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> seed(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leave(handler.player, server));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var command = CommandManager.literal("pocket-fixture")
                    .requires(source -> source.hasPermissionLevel(OPERATOR_LEVEL));
            command.then(CommandManager.literal("chest")
                    .executes(context -> chest(context.getSource().getPlayerOrThrow())));
            command.then(CommandManager.literal("legacy")
                    .executes(context -> legacy(context.getSource().getPlayerOrThrow())));
            dispatcher.register(command);
        });
    }

    private static void seed(ServerPlayerEntity player) {
        // 固定平台与模式；只有首次进入写入铁锭、头盔和装着原木的草包。
        var server = player.getServer();
        server.getPlayerManager().addToOperators(player.getGameProfile());
        var world = player.getServerWorld();
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                world.setBlockState(new BlockPos(x, PLATFORM_Y, z), Blocks.STONE.getDefaultState());
            }
        }
        player.teleport(world, BLOCK_CENTER, PLATFORM_Y + 1, BLOCK_CENTER, 0, 0);
        player.changeGameMode(GameMode.SURVIVAL);
        var account = new PlayerAccount(player);
        if (account.data().getRevision() > 0) {
            return;
        }
        var slots = account.inventory();
        for (int slot = 0; slot < slots.size(); slot++) {
            slots.set(slot, ItemStack.EMPTY);
        }
        slots.set(INGOT_SLOT, new ItemStack(Items.IRON_INGOT, INGOT_COUNT));
        slots.set(HEAD_SLOT, new ItemStack(Items.IRON_HELMET));
        var data = account.data().toBuilder()
                .setRevision(1)
                .clearWorn()
                .addWorn(pouch())
                .build();
        try {
            account.commit(data, slots);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static WornContainer pouch() {
        // 草包内放一组原木，供内景拖到快捷栏和 2×2 合成使用。
        var pouch = ContainerItems.create(ContainerItems.POUCH);
        var logs = Item.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setContainer(ContainerItems.identity(pouch))
                .setSize(Volumes.UNIT)
                .setBaseSize(Volumes.UNIT)
                .setStackNbt(StackCodec.encode(new ItemStack(Items.OAK_LOG, LOG_COUNT)))
                .build();
        ContainerItems.setContents(pouch, List.of(logs));
        return WornContainer.newBuilder()
                .setMount(Mount.WAIST_RIGHT_FRONT)
                .setStackNbt(StackCodec.encode(pouch))
                .build();
    }

    private static int chest(ServerPlayerEntity player) {
        // 真实箱子方块实体，第一格放石头供 Shift 搬运检查。
        var pos = player.getBlockPos().east(DISTANCE_BLOCKS);
        var world = player.getServerWorld();
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        var state = Blocks.CHEST.getDefaultState();
        world.setBlockState(pos, state);
        if (world.getBlockEntity(pos) instanceof Inventory inventory) {
            inventory.setStack(0, new ItemStack(Items.STONE, STONE_COUNT));
            inventory.markDirty();
        }
        player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
        return 1;
    }

    private static int legacy(ServerPlayerEntity player) {
        // 口袋只留三个空格，下线保存前再写旧槽位，迫使登录整理同时经过口袋、随身容器与脚边。
        var inventory = player.getInventory();
        int room = 0;
        for (int slot = 0; slot < PocketSlots.AVAILABLE_SLOTS; slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                continue;
            }
            if (room < LEGACY_ROOM) {
                room++;
            } else {
                inventory.setStack(slot, new ItemStack(Items.DIRT, FULL_STACK));
            }
        }
        inventory.markDirty();
        player.playerScreenHandler.sendContentUpdates();
        legacyArmed = true;
        return 1;
    }

    private static void leave(ServerPlayerEntity player, MinecraftServer server) {
        // 断线事件早于原版保存；写入旧槽后不经过任何服务器 tick，存档即为旧版 36 格布局。
        if (legacyArmed) {
            legacyArmed = false;
            var inventory = player.getInventory();
            for (int slot = PocketSlots.AVAILABLE_SLOTS; slot < PocketSlots.VANILLA_MAIN_SLOTS; slot++) {
                inventory.setStack(slot, new ItemStack(Items.COBBLESTONE, FULL_STACK));
            }
            return;
        }
        if (server.isDedicated()) {
            server.execute(() -> server.stop(false /* waitForShutdown */));
        }
    }
}
