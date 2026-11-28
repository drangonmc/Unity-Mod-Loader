# Unity 0.3.1 Beta

Unity 使用独立内核启动 Minecraft Java 26.2，运行环境为 Java 25。本版发布标签为 [0.3.1-Beta](https://github.com/drangonmc/Unity-Mod-Loader/releases/tag/0.3.1-Beta)。

## 安装

解压完整 Standalone ZIP。专服使用 `Start-Unity.cmd`。客户端先安装原版 26.2，然后运行 `Install-Unity-Client.cmd` 创建 Unity 启动配置。模组原包放入游戏的 `mods/` 目录，服务端插件放入 `standalone-instance/plugins/`。WorldEdit 与 WorldGuard 的编辑区域桥在 `versions/0.3.1-Beta/UnityEditRegions.jar`。

## Bukkit 与 Paper 实机兼容清单

测试专服加载了上版清单里的 85 个作者模组 JAR，每个真实客户端加载 83 个。对照服为原版 Paper 26.2 build 121。两个环境使用同一校验插件以及同一组作者插件。WorldEdit 7.4.6、WorldGuard 7.0.19、LuckPerms、Vault 和 EssentialsX 均在场。每轮有两名玩家，客户端使用隐藏窗口和正常渲染器，通过回环网络以离线测试账号登录。在线账户的消息签名与第三方自定义逐人渲染器仍需分别实测。新增插件的作者下载链接及 SHA-512 见[原包清单](standalone/tests/reference/review31/originals.json)。

| 游戏操作 | Paper 与 Unity 的共同观察 |
| --- | --- |
| 聊天 | 先触发异步的旧 Bukkit 聊天事件，再触发 Paper `AsyncChatEvent`。HIGH 取消后，忽略已取消事件的 HIGHEST 被跳过，MONITOR 能看到取消。另一名玩家收到了后续对照消息，聊天日志中没有被取消的文本。 |
| EasyChatFilter、MuteChat | 作者过滤器按测试词表将 `orangecactus` 显示为 `###`。收件人启用 MuteChat 后，发送者还能看到自己发送的消息，收件人看不到。解除后双方再次收到后续消息。 |
| 命令预处理与 CommandBlocker | 作者插件拦截的计数命令实际执行次数为 0。将另一个命令改写到允许的目标后，实际执行 1 次。原命令未经改写时保留原版签名验证结果。 |
| 移动、传送与 WorldGuard | 取消正常行走后，发起者、观察者、服务端坐标一致。解除后可以行走。控制台传送与插件传送分别带有 COMMAND、PLUGIN 原因，取消后玩家保持原位。WorldGuard 的禁止进入区域分别阻止走入和传送进入。 |
| 重启读档 | 两个客户端退出、专服存盘关闭后重新启动，再次连接原世界。VolumeA 位于 (64.5, 80, 20.5)，背包中有 9 个绿宝石。VolumeB 位于 (66.5, 80, 20.5)，背包中有 7 个面包。另用 Minecraft 的 NBT 读取器直接读取两个玩家文件。 |

## NeoForge 与运行时修正

- Unity 在实际扣血后发送 `LivingDamageEvent.Post`，其后才会发生死亡及掉落。Pre 将伤害置零、Forge 在 Hurt 或 Damage 阶段取消时均不会发 Post。这个顺序区别于 NeoForge 26.2.0.93 在 `hurtServer` 尾部发 Post 的原补丁。
- 为盾牌接入 `LivingShieldBlockEvent`，支持修改格挡量及盾牌耐久消耗。玩家死亡验收关闭了 Keep But Penalty 的 `death.keepInventory`，使玩家背包实际进入原版丢弃路径。测试以 11 个紫水晶碎片换成 3 个钻石并检查地面物品。
- 客户端分别触发世界 Tick Pre/Post、区块加载/卸载、实体加入和预测破坏事件。客户端与服务器均保留冒险模式和管理方块的硬性破坏限制。对可替换方块，放置事件使用点击坐标及变更前的方块状态。
- 校验伤害副作用局部变量的名称、类型和两处写入数量。掉落钩子校验方法描述符。伤害适配器重复绑定会报错。支持空踢出原因，并按命令源与实际权限隔离非玩家的权限附件。
- 通用 TOML 配置接受不同 List 实现、枚举名称和可精确表示的数值。配置界面支持使用自定义页面工厂的构造函数，并允许扩展该适配类。Puzzles Lib 完整的分节编辑器仍在兼容清单之外。

一次前置混装测试已记录世界保存和 Unity 关闭完成，但 Java 进程超过 180 秒退出上限。该轮没有停滞时的线程栈，原因尚待定位。最终双客户端验收要求两轮服务端均正常退出。[退出观察](evidence/0.3.1-Beta/exit-observation.json)

本版没有给整个 NeoForge 或插件生态标注兼容百分比。附件联网同步、自动化机器的跨平台权限归属、Kotlin 语言适配器和其他运营事件仍需单独验收。Tiny Pipes 部分可选集成的配方告警沿用上一版的说明。

[五项事件覆盖表](evidence/0.3.1-Beta/player-event-coverage.tsv) · [Unity 双客户端验收](evidence/0.3.1-Beta/unity/result.json) · [Paper 对照](evidence/0.3.1-Beta/paper/result.json) · [玩家文件读回](evidence/0.3.1-Beta/unity/offline.json) · [上一版玩法回归](evidence/0.3.1-Beta/previous/result.json) · [第三方许可证](NOTICE.md)
