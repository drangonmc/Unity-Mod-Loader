# Unity 0.2.9 Beta · Rebuild 1 · Minecraft Java 26.2

本次维护构建从已验收的 `v0.2.8-beta`（`e689f40`）重新开发，使用 Java 25 和 Unity 独立内核。新标签为 `v0.2.9-beta-rebuild.1`。先前的 0.2.9、0.3.0 构建已经作废。

## 安装

解压完整 Standalone ZIP 到新目录。专服使用 `Start-Unity.cmd`，按 Minecraft 的提示处理 EULA。客户端先安装原版 26.2，再运行 `Install-Unity-Client.cmd`，选择 Unity 配置。原始模组放入游戏目录的 `mods/`；Bukkit 插件只放入 `standalone-instance/plugins/`。需要对 WorldEdit 的方块写入执行 WorldGuard 区域规则时，将随包的 `versions/0.2.9-Beta/UnityEditRegions.jar` 也复制到插件目录。获准复制容器数据的编辑者需要 `worldedit.setnbt`。

## 原包与传输

主要机器样本是 Iron Tanks 3.3.1 的储罐和 Logistics Drones 1.1.0 的充电站。第三方传输设备为 Tiny Pipes 7.0.1 的整块管道。三者均使用作者发布的 NeoForge JAR。运行时调用 NeoForge 26.2.0.93 的物品、流体与能量传输实现，Unity 提供原版容器、组件、注册、菜单与网络适配。随包保留所选上游类的源文件与许可证。样本散列和作者下载地址见 `standalone/tests/reference/review29/originals.json`。

| 实际设备 | 初始值 | 传输完成后与重启回读 |
| --- | --- | --- |
| 两个 Iron Tanks 储罐及 Tiny Pipes 流体管 | 在供给侧先模拟插入并回滚，再提交 7,250 mB 水 | 源罐 0，目标 7,250 mB。客户端读取到水和正确数量。 |
| Copper Hopper、Tiny Pipes 物品管与箱子 | 铜漏斗中有 12 个带 `minecraft:custom_data` 的绿宝石 | 漏斗保留 1 个过滤样品，箱子收到 11 个。两名玩家各自打开箱子核对数量和组件。 |
| 两座 Logistics Drones 充电站及 Tiny Pipes 能量管 | 源站 12,345 FE，目标 0；目标站有 7 张带序列号 29012 的纸 | 源站 0，目标 12,345 FE。两名玩家打开充电站的原始界面，读取到菜单同步的电量、纸的数量和序列号。 |

测试见证模组只设置初值和作者管道的连接面。转运由游戏 tick 执行。两名客户端在混装服同时在线，服务端 82 个固定样本 JAR，每个客户端 80 个，另外使用官方 Fabric API 与验收见证。WorldEdit 7.4.6 原包执行 `//copy`、`//paste`、`//undo` 和 `//redo`。撤销后相应目标位置为空气；重做及关服重启后，源与副本仍有同样的水、能量和物品。充电站朝北，三个管道的西面抽取、东面输出配置也得到核对。测试安装了 WorldGuard 7.0.19。

## Bukkit/Paper 插件

| 原包 | 已验证的用途 |
| --- | --- |
| LuckPerms Bukkit 5.5.71 | 给两名非 OP 玩家分别授予和拒绝同一个节点。同时验证运行期添加、删除 Bukkit 权限附件。重启后用相同 UUID 复查。 |
| Vault 1.7.3 | 查询实际权限提供者 LuckPerms，通过真实经济提供者 EssentialsX Economy 写入并读取余额。 |
| EssentialsX 2.22.0 | 玩家 A 余额 291，玩家 B 余额 292。客户端执行 `/sethome` 和 `/home`，用服务端坐标核对传送结果，重启后再回家。 |

同一份测试插件在 Paper 26.2 build 121 与 Unity 上分别运行。它通过 `plugin.yml` 的 `libraries:` 加载 Commons Text，实际使用了 Commons Lang 传递依赖。玩家 PDC 与背包物品 PDC 分别存储独立值，原始 NBT 字节可读回独立容器，重启后保留。补充路径包括生命值、饥饿值、飞行、播放声音和九格自定义库存；两个客户端均打开库存核对了物品。EssentialsX 2.22.0 在这两个 26.2 宿主均提示版本不在其官方支持范围，本页记录 Unity 的实测子集。

## 审查修复及范围

- ABI 预检对一个方法里的全部缺失指令联合检查。两边各调用一个缺失接口的分支在初始化前被拒绝，验证帧里的缺失类型进入可汇总的预检错误。JVM 链接时还可能校验参数和父类型之间的赋值关系；静态条件分析不能证明所有可选集成都能装载。
- Forge 破坏事件保留前置适配器写入的 XP，只有 Forge 返回显式覆写才修改它。Bukkit 交互事件保留原版潜行语义，并记录监听器在 ALLOW、DEFAULT 和 DENY 间实际作出的修改。
- 适配器契约递归检查父接口，并接受由基类继承的公开实例实现。注册目标必须属于根表，Lambda 锚点比较完整 JVM 类名。实体元数据写入与过期清理按 UUID 原子执行，打包器默认版本已改为 0.2.9。
- 本版验收针对上述储罐、充电站、管道、插件和命令。原始 Tiny Pipes 还附有面向可选模组的配方；本次组合不安装 Tiny Redstone 和 Refined Storage，日志保留相关配方解析告警。无人机自动航线、所有 NeoForge 游戏事件、Kotlin 入口和跨平台 FakePlayer 仍需分别验收。本版没有以样本结果推算整个生态的兼容率。

完整运行回执和发布校验保存在 `evidence/0.2.9-Beta/`。
