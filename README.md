# Unity 0.2.3 Beta · Minecraft Java 26.2

Unity 是从零开发的 Minecraft 模组加载器。项目的长期目标是让 Forge、NeoForge、Fabric 模组与 Bukkit、Spigot、Paper 插件共用一个世界。当前独立 Beta 已在 Minecraft Java 26.2 上验证三种模组平台的指定样本；插件兼容仍属后续目标。

使用 Java 25，下载并解压独立发行包。Windows 专服从 `Start-Unity.cmd` 启动；客户端先安装原版 26.2，再运行 `Install-Unity-Client.cmd`，在启动器中选择 Unity 配置。完整 Fabric API 可运行 `Install-Unity-Fabric-API.cmd` 安装。[下载 V0.2.3 Beta](https://github.com/drangonmc/Unity-Mod-Loader/releases/tag/v0.2.3-beta) · [0.2.3 兼容范围与验收](standalone/BETA-0.2.3.md)

Unity 源码使用仓库的 MIT 许可证。随包第三方依赖保留各自的版权声明与许可。[项目许可证](LICENSE)

V0.2.3 接入了完整的官方 Fabric API。客户端加载 43 个模块，专服加载 36 个适用模块；安装器使用随包原始 JAR。[完整模式说明](standalone/BETA-0.2.3.md)

V0.2.2 修复了配置握手、跨世界文件监听和嵌套归档求解，保留全部 256 个 Play 可选通道。Forge 和 NeoForge 描述文件使用 NightConfig 解析，新增单平台多模组 JAR、Java 多版本 JAR 和符合条件的通用纯数据包支持。[0.2.2 兼容范围与验收](standalone/BETA-0.2.2.md)

V0.2.1 增加了 NeoForge 与 Fabric 配置阶段双向网络、按连接协商的接收器和加入世界前的配置任务。修复了损坏附件的回读、过滤储罐的有效性检测和单人连接的重复配置发送。[0.2.1 功能与验收](standalone/BETA-0.2.1.md)

V0.2.0 扩展了 Forge 与 NeoForge 配置同步、实体属性和出生规则、数据包地物与掉落，以及跨平台物品、流体、能量和存档附件。65 个作者模组同服测试使用两个独立客户端。性能清单与普通样本安装目录分开。[0.2.0 功能、测试方法与兼容边界](standalone/BETA-0.2.0.md)

## 已有功能与玩法回归

Unity 支持客户端单人集成服。安装原版 Minecraft Java 26.2 和 Unity 客户端配置后，可直接从主菜单创建、退出和重开世界。新增四类静态注册、按世界加载的数据包注册表，以及客户端方块实体渲染器、方块材质层和模型烘焙扩展。[0.1.4 功能与实机验收](standalone/BETA-0.1.4.md)


基础玩法回归在同一个专服运行 21 个作者模组，其中 Forge 11 个、Fabric 5 个、NeoForge 5 个。两个图形客户端分别装入其中的 19 个。两个专服命令模组仅需放在服务端。样本安装器还会校验并安装 Puzzles Lib 26.2.5 和所需的官方 Fabric API 模块，两个内嵌库从作者原始 JAR 加载。

两名玩家在石头上各走 8.199 格，在草径上各走 11.069 格。两端和服务器的起止坐标一致。矿锤一次挖掉九块石头，损耗 9 点耐久。重启后，速度、背包、矿墙和振金块均已复验。详见 [完整玩法和复验说明](standalone/BETA-0.1.3.md) 与 [57 项预检分类](evidence/0.2.2-Beta/puzzles-57-phases.md)。

Unity 使用 Java 25 启动原版 Minecraft。服务端和多人客户端共享同一套注册、资源包、登录握手及 play 网络实现。混装使用作者发布的原始 JAR，专服和图形客户端都在独立运行时启动。游戏 JVM 中的 Forge、Fabric、NeoForge 兼容 API 由 Unity 提供。Mixin 配置由 Unity 的 Mixin 服务和随包固定版本的 Sponge Mixin、ASM 执行。

## 延续 V0.1.1 的内容模组回归

内容回归独立装入原来的 19 个作者发布的原始 JAR，其中 Forge 11 个、Fabric 4 个、NeoForge 4 个。客户端样本目录安装 17 个，另外两个 Forge 命令模组仅需放在专服。每份 JAR 按作者发布时的 SHA-512 校验。

| 平台 | 新增内容模组 | 双端生存放置的方块 |
|---|---|---|
| Fabric | [Bedrock Crafter 1.3.5](https://modrinth.com/mod/bedrock-crafter/version/6YpE9O2t) | 基岩灯 |
| Fabric | [Extra More Blocks 1.0.1](https://modrinth.com/mod/extra-more-blocks/version/S1G3wuzi) | 蓝色染色玻璃门 |
| NeoForge | [MoreBricks 2.0.0](https://modrinth.com/mod/morebricks/version/7Gck4fiE) | 花岗岩砖 |
| NeoForge | [Macaw’s Fences and Walls 1.2.1](https://modrinth.com/mod/macaws-fences-and-walls/version/j11V8ma4) | 橡实金属栅栏 |

两名玩家在两个独立图形客户端连接同一专服。每个客户端都核对了四种物品和方块的数字 ID、烘焙模型、创造标签页归属及作者原始纹理的 SHA-256。两人分别选中 Bedrock Crafter 和 Macaw’s 的创造标签页，显示 22 项和 188 项。创造物品栏可用 Page Up / Page Down 切换标签页组，再点击标签。生存玩家每种方块持有 3 个，放置后剩下 2 个。重启专服和两个客户端后核对了四处方块状态、双方 UUID 和背包。记录与截图在 `evidence/0.2.2-Beta/new-content.json` 同目录，样本及原始贴图散列在 `standalone/creative-content.json`。

### 放置、伤害与监听器

- `LivingDamageEvent` 取消保留独立的取消标志，跳过剩余的伤害统计等逻辑。带吸收生命的玩家受到两次同等伤害，取消的一次统计保持 0，正常放行的一次统计变为 20。两次均经过更早的吸收结算，吸收值依次为 8、6、4。两端和服务端见到的最终生命值均为 16，重启后相同。
- 对同一坐标写两次后返回 PASS 或 FAIL，木桶、7 个绿宝石和手持 3 件物品均恢复。返回成功时只发一次放置事件、执行一次最终方块的 onPlace，手持变为 2。事件取消仍会同步方块、方块实体和背包。
- NeoForge 可为方法引用和 Lambda 使用局部变量、条件表达式、优先级与接收已取消事件的重载。Forge 可取消总线按具体事件缓存适用监听器，同时保留父类监听。右键方块和客户端物品使用转换按原版调用及其操作数定位，并校验锚点。
- Fabric 已覆盖的生命周期、实体、创造标签页等子模块按官方发布版本参与依赖匹配。26.2 对应的创造页接口名为 CreativeModeTabEvents。本版也接入样本实际使用的 FabricLoader 查询、NeoForge 专用 Blocks / Items 延迟注册和创造页插入顺序。版本记录在 `standalone/fabric-modules.json`。

### 访问权限、嵌套依赖与游戏网络

Unity 在类定义和 Mixin 前应用所声明的 Fabric Access Widener、classTweaker 中的访问指令，以及 Forge / NeoForge Access Transformer。支持本版本真实类名和成员描述符、已实现的访问级别和 final 调整。随包使用 Fabric 官方 26.2 transitive-access-wideners-v1 的访问规则，版权文件和散列随源码发布。Bedrock Crafter 实际使用该基线访问原版注册接口。

加载器支持 Fabric 的 jars 列表和 Forge / NeoForge 的 JarJar 元数据、递归嵌套、活动依赖版本筛选及纯库加载。在多个可用内嵌 JarJar 版本中选取满足生效范围的最高版本。等版本不同字节、范围冲突和越界路径会触发启动错误。每次运行将所选原始成员按散列写入临时目录，结束后关闭并清理。任意命名空间转换、完整 Class Tweaker DSL 和所有 JarJar 版本表达式仍待覆盖。

另外用生产解析器对两个作者发行文件分别走服务端和客户端发现流程，核对了原包内嵌成员的字节和卸载后的临时目录。Immersive Ores 内嵌 PlayerAbilityLib 1.16.0，Block Runner 内嵌 MultiLoader Data Extensions 26.2.0。该项验收止于嵌套解析，两者未计入 19 个联机模组。详情在 `evidence/0.2.2-Beta/upstream-nested.json`。[Immersive Ores 发行页](https://modrinth.com/mod/immersive-ores/version/fjZyYXGJ) · [Block Runner 发行页](https://modrinth.com/mod/block-runner/version/VNgfrtBA)。

NeoForge 的 RegisterPayloadHandlersEvent 支持本版验证的 play 阶段收发和回复。发行包另附两份用锁定上游接口编译的验收模组，验证 Fabric 嵌套模组的私有成员访问、NeoForge 经嵌套 Forge 包选取 1.3.0 前置库、监听优先级和取消，以及两个图形客户端分别与专服收发带独立随机数的消息。验收报告同时列出作者模组和自制探针的散列。本版新增可选 play 通道的按连接协商；配置阶段自定义网络仍需单独适配。

## 延续 V0.1.0 的玩法事件

MC 保持 26.2，游戏由 Unity 独立启动。独立的玩法回归使用 11 个 Forge、2 个 Fabric、2 个 NeoForge 原始 JAR。除原有八个 Forge 样本外，新增如下。发布编号、原始文件名、大小、网址和 SHA-512 均记录在 `standalone/ecosystem.json`。部分作者的文件名或发布编号包含多个 MC 版本；所选版本均由 Modrinth API 明确列出支持 26.2，并经实际双端联机检验。

| 平台 | 新增作者样本 | 验收操作 |
|---|---|---|
| Forge | [Server Tree Feller](https://modrinth.com/mod/server-tree-feller/version/SkedpK1k) | 实际放置一根原木，潜行砍掉相邻的天然原木柱，核对树冠段消失、玩家放置的原木保留 |
| Forge | [Crop Harvest](https://modrinth.com/mod/crop-harvest-mc/version/OLJJcHj1) | 生存玩家携带种子右键成熟小麦，服务端和两端均见重新种植的幼苗 |
| Forge | [PvPControl](https://modrinth.com/mod/pvpcontrol/version/TshsF9oN) | 玩家用作者的指令关闭或打开 PvP，另一名玩家从正常攻击路径发起攻击 |
| Fabric | [Back Command](https://modrinth.com/mod/back-command/version/zYg29ASn)、[CommandsSpy](https://modrinth.com/mod/commandsspy/version/aqZ611TQ) | 记录实际玩家命令，在玩家重生后用 /back 返回死亡地点 |
| NeoForge | [Always Shield](https://modrinth.com/mod/always-shield/version/2jHvV4To)、[Simple Kill Command](https://modrinth.com/mod/skc/version/huscYSca) | 检查举盾第 0 刻的阻挡状态；执行作者命令、重生，并让 Fabric 模组读取该次死亡位置 |

正常安装保留作者的默认命令和配置。Simple Kill Command 默认提供 /kill；本机独立验收实例使用 fixturedeath 别名，以明确区分作者命令和原版命令。取消策略探针由本项目针对官方 Forge API 编译，只在专门验收中装入，普通样本安装脚本只安装原始第三方文件。

- **放置和破坏**。专服捕获物品使用期间的方块状态、更新标志、方块实体 NBT 和玩家手持栈。在监听器返回后提交通知或逆序还原。支持 EntityPlaceEvent、EntityMultiPlaceEvent、BreakEvent，以及破坏经验的读取与改写。空桶/液体使用和模组直接调用 setBlock 的世界编辑不属于通用放置事务。客户端发生的瞬时预测特效可能已经播放。
- **交互和伤害**。接入左键方块、右键方块、右键物品、指定位置的实体交互、攻击实体和客户端空挥事件。LivingAttackEvent 在受击入口决定是否接受攻击；LivingHurtEvent 处理减伤前的数值；LivingDamageEvent 处理扣血前的最终数值。晚阶段取消遵循该阶段的时间点，早前已经发生的护甲、吸收或击退效果需分别判断。
- **优先级**。按 Forge EventBus 7 的字节优先级从高到低分发，同级保留订阅顺序。普通监听器返回 true 后停止后续普通监听，监视监听仍接收最终取消标志。支持直接 Consumer、Predicate、总是取消的 Consumer 和观察最终结果的监听器，以及对应的注解注册方式。
- **生命周期与总线**。专服生命周期、服务端/客户端 Tick、玩家登录和退出统一在 mc.unity.core.Events 发出，再由各平台适配器订阅。创造标签页支持按模组总线 getBus(BusGroup) 注册，保留原始 JAR 使用的静态 BUS 入口。错误总线会报告异常，自动 BOTH 注册会选择该事件所属总线。
- **Fabric 边界和类签名**。main 入口执行后在对应物理侧调用 client 或 server 入口。按本项目约定，^0.19.3 的上限为 0.20.0，^0.0.3 的上限为 0.0.4，~1 的上限为 2.0.0。这个范围策略与 Fabric Loader 0.19.3 的 SAME_TO_NEXT_MAJOR/MINOR 实现存在差异。Unity 对可转换库在首次定义类前即确定运行时来源归属，使用签名原件读到底验签；修改后的字节不归属于原作者签名。回归测试使用 JDK 生成并实际签名的库，在开启转换前后分别加载同包的两个类，并验证第二个类的字节码转换结果。

`standalone/tests/gameplay.py` 从两个图形进程发起交互，用客户端所见、服务端指令、玩家 NBT 和优先级日志分别核对结果。专用探针及源码见 `standalone/tests/gameplay/`，发布证据见 `evidence/0.2.2-Beta/ecology.json`。复验时使用 `--rules standalone/tests/gameplay/unity_cancel_fixture.jar`，其余输入为 `--client-home`、`--bundle`、`--samples` 和 `--eula-from`，与下文回归驱动一致。

## 类加载、注册和网络

三个平台的模组共享游戏类加载器，按活动侧筛选并在同一依赖图里排序。类重名、保留命名空间、缺失前置库、循环依赖及不支持的平台字节码引用都会在调用模组入口前报告。Forge 的 ModList 可以查询其他两类已发现的模组。Fabric 支持默认适配器的对象入口、数组版本备选，以及空格连接的范围约束、波浪号、脱字符和尾部通配符。Fabric 在五种已接入注册表中的直接注册均参与内容摘要。

Forge 清单、Fabric 元数据和 NeoForge 的 mixins 表都可声明 Mixin 配置。独立加载器按侧收集配置，缓存每个活动模组的原始 JAR 句柄，并从原始文件验证并读入字节。实测覆盖普通 Forge 前置库中的类、原版 Brigadier 库和客户端 Blaze3D。Java 平台及加载器自身的基础设施不属于模组可替换的类域。AT、AW 和嵌套包使用上文的转换与发现流程。JNI 和跨版本模组另需适配。

| 新增 Forge 能力 | 接口或验证行为 |
|---|---|
| 专服生命周期 | ServerAboutToStartEvent、ServerStartingEvent、ServerStartedEvent、ServerStoppingEvent、ServerStoppedEvent |
| 游戏刻和玩家 | 专服与客户端 Tick 的 Pre/Post，PlayerLoggedInEvent、PlayerLoggedOutEvent |
| 事件订阅 | 已支持事件的直接 BUS 注册、带优先级的监听、BusGroup 注解注册、按端筛选的 @Mod.EventBusSubscriber 静态订阅 |
| 服务端发包 | PLAYER 指定单人，ALL 广播给所有在线玩家，TRACKING_CHUNK 直接使用原版 ChunkMap 的追踪玩家集合 |
| 网络与客户端生命周期 | play 注册表在冻结点发布不可变快照，登录应答只消费一次。客户端 setup 位于第一次资源重载前，会话关闭后重置 setup 和 Mixin 属性 |

公共标签来自固定的 NeoForge 26.2.0.93 发行版，共 513 份，包含 265 份物品标签、127 份方块标签及其他注册表的标签。原始路径和每份文件的 SHA-256 见 standalone/common-tags.json。运行 python tools/common_tags.py 可逐项核验；提供 --archive 原版JAR --restore 可从已锁定的上游档案恢复。模组兼容测试与吞吐量基准分别计量，本版未测多人压测下的 tick 分位数。

## 启动

1. 安装 64 位 Java 25 并完整解压 ZIP。运行 `Install-Unity-Samples.cmd` 可安装 21 个作者模组和所需依赖。该可选脚本需要 Python 3.11+，按锁定的 SHA-512 校验每个文件。19 个作者模组和 8 个外部依赖装入服务端和客户端，另外两个 Forge 命令模组装入服务端；仅声明专服入口的代码在客户端不会实例化。
2. 运行 `Start-Unity.cmd`。原版 26.2 服务端按 SHA-256 校验；也可预先放在 `cache/0.2.2-Beta/mojang-server-26.2.jar`。阅读并同意 [Minecraft EULA](https://www.minecraft.net/eula) 后，在自己的 `standalone-instance/eula.txt` 中设置 `eula=true`。默认监听 `127.0.0.1:25966`，启用账号验证。控制台输入 `unity` 查看模组，输入 `stop` 存档退出。
3. 从 Minecraft 启动器安装原版 Java 26.2。关闭启动器，运行 `Install-Unity-Client.cmd`，需要时传入启动器根目录。重新打开启动器并新建安装，选择 `unity-standalone-0.2.2-Beta-26.2`，游戏目录选 `standalone-client`，进入多人游戏。实际图形验收平台为 Windows x64。
4. 独立服沿用 `standalone-instance/world/` 和玩家资料。第三方 JAR 放在双方游戏目录的 `mods/`，Unity 原生模组沿用 `unity-mods/`。开始使用新加的方块前，先确保参与玩家已安装与服务端匹配的内容模组。

## 保留的八个 Forge 样本

| 模组 | 已验证行为 |
|---|---|
| [Storage Delight](https://modrinth.com/mod/storage-delight/version/TrGOmL0g) | 两名生存玩家同时打开同一橡木抽屉。逐步核对双方屏幕、游标栈、玩家背包和服务器方块实体，退出双方并停服后重新打开。 |
| [Inventory Storages](https://modrinth.com/mod/inventory-storages/version/vjpAR64q) | 玩家放置槽位存储器并打开原作者界面；待坐标同步后，用界面编辑槽位号至 17，接收并执行作者的服务端自定义消息，重启读取槽位号。 |
| [Laser Blade Tools](https://modrinth.com/mod/laserblade-tools/version/wwfZjLAZ) | 玩家放置着色器，在作者界面点击颜色按钮，核对作者服务端处理器及菜单颜色数据。 |
| More Berry Uses、Crate Delight、Stacked Blocks | 生存玩家吃甜浆果派、放置/破坏/再次放置苹果箱和堆叠圆石；核对重启后的方块和物品。 |
| Repair Commands、Random Items Command | 玩家身份修复受损工具，向玩家实际背包发放随机物品。 |

原有八个 Forge 文件的发行地址、版本、大小和 SHA-512 分别记录在 `standalone/forge-containers.json`、`standalone/forge-content.json`、`standalone/forge-samples.json`。抽屉测试用右键取半堆：17 个胡萝卜箱取走 9 个，9 块果酱饼干取走 5 块。原作者的设置操作使用各自的 C2S 包；独立测试探针还会在打开的菜单中发送带随机数的 C2S 查询，服务端从该玩家实际菜单读取槽位，并通过自定义 S2C 回复。常规 GUI 槽位和数据更新沿用原版协议。

## 内核与适配器

- `mc.unity.core` 管理注册和冻结、方块状态编号、资源包挂载、创造标签页排序、内部事件、按方向注册的游戏消息以及登录摘要。摘要覆盖内容模组 ID 和原始文件散列、Unity 协议实现、注册表数字 ID、方块状态和通道版本。服务端校验客户端的本地摘要，成功后在服务端线程显式继续原版登录步骤。退出及初始化失败会释放会话状态。
- `adapter.forge` 实现本版使用的 Forge API，转接方块、物品、方块实体、菜单及事件。支持已覆盖的 play 通道、编解码器、发送者连接和线程调度。客户端设置回调用于注册原作者屏幕工厂。公共标签作为内置包随游戏数据一起加载。
- `adapter.fabric` 处理所覆盖的 `fabric.mod.json` 与 `ModInitializer`，注册阶段可直接调用原版 `Registry.register`。适配了 v2 命令注册回调。`adapter.neoforge` 支持本版样本使用的 `@Mod`、模组事件总线、`DeferredRegister`、推断或显式事件类型的命令与创造标签页回调。两个三平台验收样本分别用上游 Fabric 和 NeoForge API 编译；它们由本项目编写，源码及官方编译依赖锁位于 `standalone/tests/platform/`。
- Forge ABI 预检扫描结构化类/成员引用、注解及泛型签名。版本范围支持预发行后缀与构建元数据。客户端/服务端使用同一份固定原版 26.2 输入；Forge 所需的原版访问扩展由独立转换步骤接入。

兼容范围由本版列出的 API 和样本限定。其他 Fabric API、更多平台事件与构造器、其他嵌套依赖和转换配置组合、跨版本世界迁移均待单独验收。Mixin 已验证八个样本中实际声明的配置；其他注入目标和第三方 Mixin 插件需要另行测试。网络测试使用专门的本机回环玩家，发行配置启用在线验证。

## 源码重建与复验

```text
java standalone/Build.java . <官方26.2服务端JAR> <原版26.2客户端根目录>
python tests/check.py
python standalone/tests/platform_samples.py <编译用官方API缓存> <三平台样本输出目录>
python standalone/tests/dual.py --bundle <官方服务端JAR> --client-home <测试客户端根目录> --samples <八个原始Forge样本目录> --eula-from <已同意EULA的本机测试实例/eula.txt> --platform-samples <三平台样本输出目录>
```

`standalone/lib/SHA256SUMS` 固定通用字节码库。源码包携带这些原始库，核心构建可离线完成。三平台测试样本另外按锁取得上游 API，仅在编译器进程使用。单客户端物品/方块和缺模组拒绝场景在 `standalone/tests/content.py`，空载、原生扩展及重启回归在 `standalone/tests/acceptance.py`。测试不会向发行 ZIP 写入 Minecraft、用户账号、第三方玩法样本、已接受的 EULA 或世界。

`evidence/0.2.2-Beta/dual.json` 记录双方进程和身份、每步物品数、菜单、包往返、运行类来源和日志散列；同目录含两张由游戏生成的抽屉画面截图。窗口探针的 frames 字段是读到正 FPS 的游戏刻数。此套验收用于功能正确性，不提供并发服性能基准。

要重放三平台测试，发行包提供 `standalone/tests/platform/unity_fabric_sample.jar` 和 `unity_neo_sample.jar`。将两者同时放入独立服与独立客户端的 `mods/`，重启后可用 `unityfabric`、`unityneo` 两条命令检查各自的物品注册。两份 JAR 均由测试源码从官方接口编译，已与实机验收用文件核对。

## 跨平台依赖、事件与分发器复验

standalone/tests/interop/ 下有四个自行编写的测试模组和对应源码。构建驱动 standalone/tests/build_interop.py 只把固定的上游平台 API、Mixin 库、测试前置库和原版游戏放入编译器类路径。zz_plainlib 是普通 Forge 库，zz_neo_before 声明 BEFORE，aa_bridge 使用 Fabric 对象入口并仅向创造标签页注册表注册，aa_events 使用 Forge 注解事件并检测另两个平台的模组。它们与平台样本、八个作者原始 JAR 一起参与双客户端测试，普通安装无需加入这些探针。

运行双端驱动时追加 --interop-samples standalone/tests/interop，并将 --platform-samples 指向 standalone/tests/platform。报告 dual.json 的 distributors 记录各条消息的实际应答者，完整服务端和客户端日志记录启动、登录、游戏刻检查、资源初始化、退出和关服。离开区块追踪范围的玩家仍然会收到全服广播。验收用后续全服消息确认两条连接都处理完已有消息，再核对接收者集合。

资源读取微基准使用同一个 Laser Blade Tools 原始 JAR，在上版和本版之间交替启动四个 JVM。每个进程预热 2 批，再记录 7 批，每批连续读取 500 次并校验原始字节。各版本的实测中位耗时见 JSON 报告。测试期间同机也在执行游戏回归，CPU 与磁盘负载未隔离。结果位于 `evidence/0.2.2-Beta/resource-reads.json`，复验脚本为 `standalone/tests/archive_reads.py`，需要另行提供对照版本加载器。本机有一次在两个验收专服并行刷盘时触发了原版 60 秒看门狗；主线程阻塞于 ChunkMap.saveAllChunks，区域写入线程停在文件写入。容器存档验收随后单独运行，仍启用原版看门狗。


### 重放玩法与内容验收

```text
python standalone/tests/gameplay.py --bundle <官方26.2服务端JAR> --client-home <测试用原版客户端根目录> --samples <15个作者JAR目录> --eula-from <已接受EULA的本机实例/eula.txt> --rules standalone/tests/gameplay/unity_cancel_fixture.jar
```

新增双端内容测试可直接使用发行包附带的探针。使用独立测试启动器目录、原版服务端、包含 19 个作者原始 JAR 的目录，以及本机已接受的 EULA 文件。

```text
python standalone/tests/creative_content.py --client-home <测试启动器目录> --bundle <原版服务端JAR> --samples <作者JAR目录> --eula-from <本机测试实例/eula.txt> --nested standalone/tests/nested
```

要重新编译两份嵌套验收 JAR，准备 `standalone/tests/platform/references.json` 和 `standalone/tests/interop/references.json` 中固定的依赖，以对应键名加 .jar 命名，再运行以下命令。

```text
python standalone/tests/build_nested.py <已下载的上游编译依赖目录> <输出目录>
```
