package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.backpack3d.protocol.pb.VolumeProtocol.Container;
import dev.kizuna.bs3d.pocket.server.PocketMigration;
import dev.kizuna.bs3d.vanilla.client.VanillaClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** 真实客户端验收：E 页四条链路、箱子两行、创造物品栏三维人物穿脱与本地光标穿戴、旧存档迁移提示、死亡掉落。 */
public final class PocketSmokeClient implements ClientModInitializer {
    private static final String WORLD = "pocket-smoke";
    private static final String MARKER = "pocket-ok.txt";
    private static final long TIMEOUT_MILLIS = 480_000;
    private static final long WORLD_SEED = 1L;
    private static final long INITIAL_REVISION = 1;

    private enum Stage {
        CONNECT,
        INITIAL,
        E_PAGE,
        MENUS,
        LEGACY,
        RELOG,
        RELOGGED,
        DEATH,
        FINISHED
    }

    private Stage stage = Stage.CONNECT;
    private Stage logged;
    private long started;
    private boolean integrated;
    private int overlays;
    private EPageProbe page;
    private final MenuProbe menus = new MenuProbe();
    private final LegacyProbe legacy = new LegacyProbe();

    @Override
    public void onInitializeClient() {
        integrated = System.getProperty("pocket.smoke.mode", "dedicated").equals("integrated");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientReceiveMessageEvents.GAME.register(this::message);
    }

    private void message(Component message, boolean overlay) {
        // 只统计动作栏上的迁移提示，聊天与命令反馈不计入。
        if (overlay && overflow(message)) {
            overlays++;
        }
    }

    private static boolean overflow(Component message) {
        if (message.getContents() instanceof TranslatableContents content) {
            return content.getKey().equals(PocketMigration.OVERFLOW_KEY);
        }
        return false;
    }

    private void tick(Minecraft client) {
        // 每阶段等真实状态后再推进，失败日志保留具体阶段。
        watch();
        if (client.screen instanceof AccessibilityOnboardingScreen) {
            client.setScreen(new TitleScreen());
        }
        if (stage == Stage.CONNECT || stage == Stage.RELOG) {
            connect(client);
            return;
        }
        var addon = VanillaClient.instance();
        if (client.player == null || addon == null || !addon.volume().ready()) {
            return;
        }
        client.getToasts().clear();
        advance(client);
    }

    private void watch() {
        long now = System.currentTimeMillis();
        if (started == 0) {
            started = now;
        }
        if (logged != stage) {
            logged = stage;
            System.out.println("POCKET_SMOKE_STAGE " + stage);
        }
        if (now - started > TIMEOUT_MILLIS) {
            // 附带附属最后的拒绝原因，被拒的拖放不必再从截图反推。
            var addon = VanillaClient.instance();
            String detail = addon == null ? "" : addon.detail();
            throw new IllegalStateException("验收超时：" + stage + "；附属状态：" + detail);
        }
    }

    private void advance(Minecraft client) {
        switch (stage) {
            case INITIAL -> initial(client);
            case E_PAGE -> {
                if (page.tick(client)) {
                    stage = Stage.MENUS;
                }
            }
            case MENUS -> {
                if (menus.tick(client)) {
                    stage = Stage.LEGACY;
                }
            }
            case LEGACY -> relog(client);
            case RELOGGED -> {
                if (legacy.migrated(client, overlays)) {
                    stage = Stage.DEATH;
                }
            }
            case DEATH -> {
                if (legacy.death(client)) {
                    finish(client);
                }
            }
            default -> {
                // 连接阶段由 connect 处理，完成后不再推进。
            }
        }
    }

    private void connect(Minecraft client) {
        // 首次进入与重登都等标题页空闲，重登进入同一独服或同一单机存档。
        if (client.player != null || client.getOverlay() != null) {
            return;
        }
        if (!(client.screen instanceof TitleScreen)) {
            return;
        }
        boolean first = stage == Stage.CONNECT;
        stage = first ? Stage.INITIAL : Stage.RELOGGED;
        if (!integrated) {
            String address = System.getProperty("pocket.smoke.address", "127.0.0.1:25587");
            var info = new ServerData("Pocket smoke", address, false /* local */);
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address), info, false /* quickPlay */);
        } else if (first) {
            createWorld(client);
        } else {
            client.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
        }
    }

    private static void createWorld(Minecraft client) {
        // 超平坦和平世界，夹具在进入时搭建固定平台。
        var info = new LevelSettings(WORLD, GameType.SURVIVAL, false /* hardcore */, Difficulty.PEACEFUL,
                true /* allowCommands */, new GameRules(), WorldDataConfiguration.DEFAULT);
        var options = new WorldOptions(WORLD_SEED, false /* generateStructures */, false /* bonusChest */);
        client.createWorldOpenFlows().createFreshLevel(WORLD, info, options,
                registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT)
                        .createWorldDimensions());
    }

    private void initial(Minecraft client) {
        // 夹具首次进入写入初始库存；背包快照与原版库存都同步后才开始页面验收。
        var volume = VanillaClient.instance().volume();
        if (volume.state().getRevision() != INITIAL_REVISION) {
            return;
        }
        var ingots = client.player.getInventory().getItem(PocketFixture.INGOT_SLOT);
        if (!EPageProbe.holds(ingots, Items.IRON_INGOT, PocketFixture.INGOT_COUNT)) {
            return;
        }
        SmokeInput.require(integrated == client.isLocalServer(), "运行模式错误");
        String bag = volume.state().getContainersList().stream()
                .filter(Container::getWorn)
                .findFirst()
                .orElseThrow()
                .getId();
        page = new EPageProbe(bag);
        stage = Stage.E_PAGE;
    }

    private void relog(Minecraft client) {
        // 夹具在断线保存前写入旧槽位，重登读取的就是旧版 36 格存档。
        if (!legacy.arm(client)) {
            return;
        }
        overlays = 0;
        stage = Stage.RELOG;
        disconnect(client);
    }

    private void finish(Minecraft client) {
        // 标记文件只在全部断言通过后写入，随后断线让独服保存并停止。
        String mode = integrated ? "integrated" : "dedicated";
        String result = mode + ": E page model/crafting/two rows, interior->hotbar, hotbar->hand,"
                + " armor->pocket, crafting->pocket, chest two rows, shift-click, creative tab model/two rows,"
                + " creative pocket->model->pocket, creative cursor->model, forged creative slot rejection,"
                + " legacy relocation/drop/notice, death drops passed";
        try {
            Files.writeString(client.gameDirectory.toPath().resolve(MARKER), result);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        stage = Stage.FINISHED;
        disconnect(client);
        client.stop();
    }

    private static void disconnect(Minecraft client) {
        if (client.level != null) {
            client.level.disconnect();
        }
        client.clearLevel(new TitleScreen());
    }
}
