# 仓库协作约定

- 公共 MIT 附属，只管理原版口袋容量与 E 页排版；库存、容器树和拖放事务复用 BS3D。
- 对玩家可选安装；本附属硬依赖 BS3D 与 Vanilla-Addon，两端安装一致，不写缺失依赖分支。
- 简体中文交流、文档与方法内部意图注释；公开标识符使用英文。
- Java 代码形状遵守 `~/.codex/AGENTS.md`；原版配方、装备和绑定诅咒规则不复制。
- 客户端只提交原版点击或既有 Protobuf 意图，不提交库存 NBT。
- 测试夹具只放 smoke 源集，不进入生产 JAR；文档源为 `docs/wiki/`。
- 保留已有修改，不提交、不推送；交付执行真实 smoke、wiki 检查及 diff --check。真实 smoke：入口还没登记进装配清单，kz 暂时跑不了。

## 构建和测试

构建、测试一律走工作区的 `kz`（`~/Code/Kizuna-remote-session/scripts/kz`），不直接运行 `./gradlew`。细则以 `~/Code/Kizuna-remote-session/handoff/codex-worker-rules.md` 为准，动手前先读。

- 单测、构建和服务端 GameTest：`kz test ~/Code/<本仓的 worktree>`
- 要带上别的仓的 worktree 一起测：每个加一次 `--with ~/Code/<那个仓的 worktree>`
- 客户端测试：`kz client ~/Code/<本仓的 worktree>` 列出有哪些项，`kz client ~/Code/<本仓的 worktree> <项>` 跑其中一项
- 要自己写 Gradle 参数时：`kz gradle ~/Code/<本仓的 worktree> -- <参数>`
- 不提交、不推送；在自己的 worktree（`~/Code/wt-*`）里改，不改主目录。
