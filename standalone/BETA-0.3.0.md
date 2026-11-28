# Unity 0.3.0 Beta

Minecraft Java 26.2，Java 25。Unity 使用独立内核启动游戏。本版的 Git 标签是 [`0.3.0-Beta`](https://github.com/drangonmc/Unity-Mod-Loader/releases/tag/0.3.0-Beta)。此前标签 `v0.3.0-beta` 对应的旧发行包已撤回。

## 安装

解压完整 Standalone ZIP 到独立目录。专服运行 `Start-Unity.cmd`，按 Minecraft 的提示处理 EULA。客户端先通过官方启动器安装 Minecraft 26.2，再运行 `Install-Unity-Client.cmd` 并选择 Unity 配置。作者原始模组放入游戏目录的 `mods/`，Bukkit 插件放入 `standalone-instance/plugins/`。使用 WorldEdit 和 WorldGuard 的区域编辑桥时，将随包的 `versions/0.3.0-Beta/UnityEditRegions.jar` 复制到插件目录。

## 已验证的 NeoForge 事件与玩法

| 作者原包 | 触发条件 | 本版观察 |
| --- | --- | --- |
| Vines Cutter 1.0.2-26.2 | 生存玩家剪断贴墙藤蔓柱顶部。下方各段仍有独立支撑 | 三段藤蔓全部移除，两台客户端与专服一致。 |
| No Berry Damage 2.1.3+mc26.2 | 对玩家施加甜浆果丛伤害 | NeoForge 收到已取消的伤害事件，两台客户端和服务端读取的生命值均保持 15。普通伤害的正对照仍可扣血。 |
| Villager Bonus Drops 1.0.0+neoforge.26.2 | 盔甲匠死亡 | 两名玩家都能看到掉落的高炉，重启后再次核对。 |

模组版本、下载地址和 SHA-512 见 [作者原包清单](standalone/tests/reference/review30/originals.json)。专服核对了 85 个作者模组文件，每个客户端核对了 83 个。两个客户端均使用真实游戏渲染器、独立进程和隐藏窗口，从回环网络连接同一专服。

- **破坏与放置。** 26.2 SDK 的破坏事件为 `BreakBlockEvent`。在 HIGHEST 取消后，常规监听器跳过事件，选择接收已取消事件的 LOWEST 和 Bukkit MONITOR 都收到取消标记。允许破坏时三个 NeoForge 优先级按序执行。取消放置后仍有 6 块砖，随后允许放置一次剩 5 块。两台客户端对照专服检查方块。
- **交互与伤害。** 取消右键后拉杆维持关闭，取消近战后目标生命值不变。Bukkit 取消普通伤害后目标保持 15 点。另一次伤害由 NeoForge Pre 从 4 改为 2，Post 报告 2，两台客户端与服务端均读到 13 点。
- **死亡与掉落。** 监听器恢复 4 点生命并取消死亡。另一组用例取消整批掉落，包括同服 Fabric 模组在死亡回调中额外生成的皮革。允许替换掉落时仅留下 5 个绿宝石。重启后检查存活实体、掉落、双方背包、生命值和世界方块。停服后另外用原版 NBT 读取器核对两个玩家文件和区块调色板。
- **上版回归。** 保留 Iron Tanks、Tiny Pipes、Logistics Drones、Copper Hopper、WorldEdit、WorldGuard、LuckPerms、Vault 和 EssentialsX。实测传输了 7,250 mB 水、12 个带组件物品中的 11 个和 12,345 FE。WorldEdit 执行复制、撤销、重做后重新打开两个充电站。Paper 26.2 build 121 与 Unity 跑同一个 Bukkit 插件，对照权限、余额、背包物品 PDC、嵌套列表二进制和重启后的玩家状态。

## 运行时修正

扩展菜单通道经过同一 JVM 两次关闭、注册的契约测试。插件权限查询复用玩家的权限对象，登录阶段断线会清理尚未入世界的玩家视图。插件类加载域拥有 Sisu、JSON.simple、Commons Codec、JSR-330 和 Commons Logging 的随包版本。停服时单独处理插件私有清理错误。踢出与登录拒绝消息保留富文本。默认物品、流体资源对象的热路径使用 volatile 读取，首次创建时加锁。

PDC 默认写出与本次 Paper 构建一致的原始 NBT，同时接受 GZIP 输入，非空嵌套列表按元素类型检查。NeoForge 配置界面支持 Vines Cutter 使用的双参数工厂，可以打开本地 TOML。复杂配置编辑器和任意版本的前置库仍需按模组验证。

兼容清单覆盖以上组合和路径。原始 Tiny Pipes 中依赖可选模组及公共标签的部分配方在本组合中仍有解析告警。Kotlin、跨平台 FakePlayer、其他 Bukkit 运营事件以及完整 NeoForge SDK 覆盖率均未作为本次验收结果。

[可复验的联机记录](evidence/0.3.0-Beta/unity/result.json) · [Paper 对照](evidence/0.3.0-Beta/paper/result.json) · [上游组件与许可证](NOTICE.md)
