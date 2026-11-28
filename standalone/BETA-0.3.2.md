# Unity 0.3.2 Beta

Unity 使用独立内核运行 Minecraft Java 26.2，需要 Java 25。[GitHub 发行页](https://github.com/drangonmc/Unity-Mod-Loader/releases/tag/0.3.2-Beta)

## 安装

解压完整 Standalone ZIP。专服运行 `Start-Unity.cmd`。客户端先安装原版 26.2，再通过 `Install-Unity-Client.cmd` 创建 Unity 配置。模组原包放在游戏目录的 `mods/`，Bukkit 插件放在 `standalone-instance/plugins/`。WorldEdit 区域写入需要 WorldGuard 保护时，另将 `versions/0.3.2-Beta/UnityEditRegions.jar` 复制到插件目录。

## 死亡、重生和箱子

Paper 26.2 build 121 和 Unity 使用同一个测试监听器以及 12 个完全相同的作者插件 JAR。新增的四个是 playerKeepInventory、DeathCoords、LSpawn 和 NeatSort。既有的 WorldGuard、WorldEdit、LuckPerms、EssentialsX、Vault、CommandBlocker、EasyChatFilter 和 MuteChat 均在场。Unity 专服加载 87 个作者顶层模组文件，每个隐藏窗口的真实客户端加载 85 个。语言和权限见证程序单独计数。[插件版本与原包 SHA-512](standalone/tests/reference/review32/originals.json)

| 用例 | 两服的实测结果 |
| --- | --- |
| 死亡与保包 | VolumeA 通过原插件命令启用保包，9 个金锭留在背包中。VolumeB 的 7 个绿宝石进入死亡掉落列表，监听器将它们替换为地上的 3 个钻石。没有额外掉出的金锭或绿宝石。 |
| 重生位置 | DeathCoords 给两个客户端发出真实死亡位置。LSpawn 指定重生点 (62.5, 80, 20.5)。重生首步被取消时，人物停在重生点。 |
| 开箱和关箱 | NeatSort 在关箱时将两个槽位中的 6 和 7 个铁锭合并为 13。VolumeA 保持界面打开时，VolumeB 的开箱在 HIGH 被取消，忽略已取消事件的 HIGHEST 跳过，MONITOR 收到取消。B 没有打开界面，A 和服务端仍读到 13。授权后两人同时打开箱子，B 取走 7 个，双方界面与服务端均剩 6 个。 |
| 重启 | 两个客户端重新进服，打开同一箱子核对。停服后另外用原版 NBT 读取器检查了两份玩家文件和真实区块中的箱子。 |

测试通过原插件配置关闭了 Keep But Penalty 在死亡瞬间的强制保包，以便比较 Bukkit 监听器的决定。区域、背包、语言依赖和出生点均在独立的本地测试实例设置。[Unity 实机回执](evidence/0.3.2-Beta/unity/result.json) · [Paper 对照](evidence/0.3.2-Beta/paper/result.json) · [箱子及附件 NBT](evidence/0.3.2-Beta/unity/world.json)

## 世界操作与审查修复

- 验证了 `CreatureSpawnEvent` 对插件生成生物的取消与放行，以及实体爆炸的方块列表修改和方块爆炸取消。爆炸的方块阶段发生在实体受伤计算之后。
- 实测 `Block.setType/setBlockData`、`World.spawnEntity/dropItem`、`ItemMeta` 的附魔与 Adventure Lore 读写，以及客户端实际请求的 Brigadier 标签补全。
- 修正了重生后的移动基准、跨维度旧位置、首次旋转的旧朝向与角度环绕。Paper 26.2 build 121 对 `PlayerMoveEvent.setTo` 的目标变更仍会发起 PLUGIN 传送，Unity 保留这一语义。控制台具有稳定的聊天观众标识，插件可将它从观众集合移除。
- 区分末影珍珠、地狱门、末地门、折跃门、旁观者及消耗品传送。该 Paper 版本的 `CHORUS_FRUIT` 是 `CONSUMABLE_EFFECT` 的别名。验收时临时开放 NoDim 的三条规则，并向角色提供 Portal Lock 要求的荧石和末地水晶。
- 盾牌事件按实际扇区与绕盾条件给出原始结果。未持盾的监听器可以主动格挡。完全由吸收之心承担的 3 点伤害能观察到 Post，生命伤害为 0。配置支持带常量类体的枚举，并拒绝不能表示为有限 Float 的值。

## NeoForge 附件、Kotlin 与机器身份

- NeoForge 实体及区块附件支持按接收者过滤、默认创建、更新、显式同步、移除、首次跟踪与登录回放。两名客户端均读到玩家公开值 3201 和区块值 3202，只有拥有者读到私有值 3211。移除后客户端值消失，重启后公开值从服务端 NBT 重新同步。
- 验收使用未修改的 Fabric Language Kotlin 1.13.14（Kotlin 2.4.20）和 KotlinForForge 6.5.0 的 26.2 NeoForge 发行包。真正编译的 Kotlin 单例依次运行 Fabric `preLaunch`、`main`，另一个 Kotlin `@Mod` 通过 KFF 原包的 `MOD_CONTEXT` 和 `MOD_BUS` 收到生命周期事件。Unity 承担 KFF 的宿主容器职责，游戏侧上下文和扩展库来自作者原包。共享库根据模块标识、发布方、版本约束和内容校验选择。要求 Fabric Loader 0.19.5 的较新 Fabric Kotlin 包超出本版声明的 0.19.3 基线。[语言验收清单](evidence/0.3.2-Beta/language.json)
- 放置方块实体后，Unity 保存放置者身份。测试模组通过 `mc.unity.api.MachineActors.forMachine` 为实际放置的 Copper Hopper 取得操作者，并让 NeoForge 与 Fabric 的 FakePlayer 走原版破坏路径。在同一 WorldGuard 区域内，所有者可以破坏目标，另一身份被拒。重启后重复了相同检查。没有归属记录时接口返回空值，直接写世界的自动化模组需要主动接入。Residence 和原无人机 AI 的完整操作链尚未独立验收。

复验入口是 `standalone/tests/reference/review32/run.py --help`。测试目录由程序新建，`--original_matrix` 提供哈希已知的模组和插件原件。Unity 分支另需 NeoForge SDK、两份语言原包与 Kotlin 编译器。游戏厂商的服务端、客户端和玩家存档由本机测试实例提供。

兼容范围以上述行为及原包版本为准。双客户端通过回环网络、使用离线测试账号。Unity 连续四次从发出 `stop` 到 JVM 自行退出均在 180 秒内，具体耗时、退出码及超过采样阈值时的线程记录见[停服回执](evidence/0.3.2-Beta/shutdowns.json)。本次在旧版回归配置中复现了 180 秒超时。两次线程栈均显示 WorldGuard 的非守护 UUID 迁移计时器在迁移结束后空等。宿主现在跟踪该 WorldGuard 加载域实际创建的迁移计时器，插件关闭后逐个释放。之前 0.3.1 发布时那一轮没有现场栈。[停服修复证据](evidence/0.3.2-Beta/exit-regression.json)Tiny Pipes 的部分可选配方在这个组合中仍有已知解析告警。[事件表](evidence/0.3.2-Beta/game-event-coverage.tsv) · [上版回归](evidence/0.3.2-Beta/prior-regression.json) · [第三方组件声明](NOTICE.md)
