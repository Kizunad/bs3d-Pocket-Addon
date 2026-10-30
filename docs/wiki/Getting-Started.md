# 安装与构建

需要 Minecraft 1.20.1、Java 17、Fabric Loader 0.16.10、Fabric API 0.92.3+1.20.1，以及同一批源码构建的 Framework、BS3D 与 Vanilla-Addon。四个项目均使用 `0.1.0-SNAPSHOT`，仅凭文件名无法区分源码版本。

本附属依赖的接口：BS3D 的 `InventoryAccess.tryStoreCarried`，Vanilla-Addon 的 `PlayerInventoryRows`、`StationHost.stationLayout`、`StationHost.stationSlotVisible`、`StationHost.stationMenuSlot`、`SlotPosition` 与创造物品栏页签的场景接入。配套源码状态记录在[验证记录](Validation.md)。依次构建 Framework、BS3D、Vanilla-Addon，最后在本仓库执行：

```bash
./gradlew -Dorg.gradle.java.home=/path/to/jdk17 \
  -PframeworkDir=/path/to/Kizuna-Inventory-UI-Framework \
  -Pbs3dDir=/path/to/backpack-and-storage-3D \
  -PvanillaDir=/path/to/bs3d-Vanilla-Addon test build
python3 scripts/wiki.py check
```

默认依赖目录为相邻的 `../Kizuna-Inventory-UI-Framework`、`../backpack-and-storage-3D` 和 `../bs3d-Vanilla-Addon`。Gradle Wrapper 已提交，不需要全局安装 Gradle；首次构建需要网络下载依赖。

产物：

| 产物 | 用途 |
| --- | --- |
| `client/build/libs/bs3d-pocket-addon-client-0.1.0-SNAPSHOT.jar` | 客户端、单机；与 Framework、BS3D、Vanilla-Addon 的客户端 JAR 及 owo-lib 一起安装 |
| `server/build/libs/bs3d-pocket-addon-server-0.1.0-SNAPSHOT.jar` | 专用服务器；与 Framework、BS3D、Vanilla-Addon 的服务端 JAR 一起安装，无客户端渲染依赖 |

同一个实例只安装对应端的一份附属 JAR。客户端 JAR 已包含单机逻辑服务器，不再叠放 server JAR。服务器和客户端应同时安装或同时卸载：槽位规则由服务器执行，两行显示由客户端执行，只装一端会出现显示与权限不一致。

## 安装与卸载

装上即生效，不需要命令或配置。已有存档第一次登录时，18–35 号槽里的物品会被整理到口袋、随身容器或脚边，见[操作说明](Interaction.md)。

卸载后 18–35 号槽恢复可用。附属不改存档结构，也不留下额外数据；卸载前已经掉在地上或存进随身容器的物品不会自动搬回主背包。
