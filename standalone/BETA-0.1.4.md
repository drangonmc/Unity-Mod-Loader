# Unity 0.1.4 Beta · Minecraft Java 26.2

安装模组后可从主菜单进入「单人游戏」，用原版创建世界、选择存档和保存退出。客户端与内置服务器共享同一套模组入口和静态注册表，每个世界独立持有数据包注册表、服务端配置和存档。打开第二个世界时会重新触发服务端生命周期。Unity 原生模组的 `environment=both` 可同时接收客户端和逻辑服务器回调，物理端声明仍控制模组的发现。启动器配置与专服的使用步骤见 [README](../README.md)。

## 单人世界实测

一个隐藏的 26.2 图形客户端依次新建 A、新建 B、再从磁盘打开 A。测试通过原版 WorldOpenFlows、保存退出流程及实验性世界的备份确认界面操作。每次进入、关闭均有独立的服务端事件，主世界、下界和末地分别收到加载与卸载回调。原生扩展初始化一次，在三个逻辑服务器中各自收到启动、游戏刻和停止事件。

| 进入顺序 | 数据包值 | 背包绿宝石 | 物品自定义组件 |
|---|---:|---:|---:|
| 新建 A | 73 | 7 | 17 |
| 新建 B | 109 | 3 | 29 |
| 重开 A | 73 | 7 | 17 |

A 中放置的振金块、测试方块实体和召唤的自定义实体均被读回，实体 UUID 与保存前相同。该组使用上版客户端适用的 19 个作者模组、其依赖，以及分别按 Forge、Fabric、NeoForge 原始发行接口编译的测试模组和 Unity 原生生命周期探针。[单人三次进出与原始观测](../evidence/0.1.4-Beta/integrated.json) · [单人实机截图](../evidence/0.1.4-Beta/integrated-world.png)

## 注册表与数据包

静态内置注册表现覆盖方块、物品、方块实体、菜单、创造标签页，以及声音事件、实体类型、数据组件和配方序列化器。新增四类参与启动注册、冻结和登录摘要。测试实际注册自定义声音、可同步和保存的实体、带持久化与网络编解码器的组件，以及带自定义数值的配方。服务端用一根木棍匹配该配方，组装出的绿宝石带有组件值 31。静默客户端记录了声音事件及音效资源的回调。

Forge 可以使用 `ForgeRegistries.SOUND_EVENTS`、`ENTITY_TYPES`、`RECIPE_SERIALIZERS` 与 `Registries.DATA_COMPONENT_TYPE`。NeoForge 也可使用 `DeferredRegister.createEntities` 和 `createDataComponents` 的类型化工厂。Fabric 可直接向原版内置注册表注册。

每个世界的数据注册表通过磁盘 codec 加载；声明了网络 codec 的注册表进入原版配置协议。支持 Fabric `DynamicRegistries.register/registerSynced` 和 Forge／NeoForge `DataPackRegistryEvent.NewRegistry` 的基础重载。数据文件放在 `data/<条目命名空间>/<注册表命名空间>/<注册表名>/<条目名>.json`。由原版世界加载器处理注册项、标签及资源包选择。新增注册表描述需要在冻结前声明，数据值随世界加载；单人退出后可以加载另一套值。[Fabric 动态注册表示例](https://docs.fabricmc.net/develop/registries/dynamic-registry)

另起的独立专服在世界数据包中提供了客户端本地没有的条目，客户端读到 127。仅服务端的注册项值 9 留在服务端。独立进程还核对了四类静态数字 ID、自定义实体 UUID、配方和背包组件 53。[专服／客户端网络验收](../evidence/0.1.4-Beta/registry-wire.json)

## 客户端渲染与轻量 Fabric API

- 支持通过 Fabric 的 `BlockEntityRendererRegistry.register`，以及 Forge／NeoForge 的 `EntityRenderersEvent.RegisterRenderers` 注册实体或方块实体渲染工厂。测试方块实体使用自己的渲染器真实提交钟模型；资源重载后新的工厂实例再次收到渲染调用。
- 26.2 的烘焙四边形自带材质层。玻璃模型读取作者的 `force_translucent` 材质并进入 TRANSLUCENT；测试格栅原本也进入 TRANSLUCENT，通过 `mc.unity.api.client.BlockRenderLayers.register` 改为 CUTOUT。按模型缓存转换后的面，并保留朝向、UV、粒子材质、光照和颜色信息。
- 提供 `ModelLoadingPlugin.Context.modifyBlockModelAfterBake()`。插件可以读取实际方块状态、未烘焙模型和 baker，返回替换模型。每轮烘焙使用独立回调上下文；测试在资源重载前后均核对了作者返回的模型类型和对应烘焙轮次。
- 轻量事件覆盖服务端启动／关闭四阶段、维度加载／卸载、服务端与客户端的 tick 前后及每维度 tick、客户端启动／关闭，以及 `UseBlockCallback` 和 `UseItemCallback`。注册使用 Fabric 的数组事件及阶段排序。测试玩家持木棍右击格栅，客户端与逻辑服务端各收到一次回调。原有命令、创造栏、物品提示和实体事件继续参加回归。

编写轻量 Fabric 模组时，可以直接订阅 `ServerLifecycleEvents.SERVER_STARTED`、`ServerLevelEvents.LOAD`、`ServerTickEvents.END_LEVEL_TICK` 或 `UseBlockCallback.EVENT`。这些回调在单人与多人游戏中使用同一套逻辑端事件。客户端入口可以用下面的形式选择材质层。

```java
BlockRenderLayers.register(grate, ChunkSectionLayer.CUTOUT);
BlockEntityRendererRegistry.register(standType, StandRenderer::new);
```

第一行使用 Unity 的客户端扩展，第二行使用 Fabric 26.2 的公开注册接口。工作示例见 [客户端验收模组](tests/worlds/Visuals.java)、[数据注册与使用回调](tests/worlds/FabricMod.java)。

本版验收聚焦上述接口。模型加载插件的其他修改阶段、任意自定义渲染管线，以及所有中型模组的兼容性仍需分别实测。

## 复验

Java 25、已安装的原版 26.2 测试启动器目录与官方服务端 JAR 是运行输入。发行包附带 QA 模组的完整源码和编译样本，普通样本安装不加入这些探针。以下命令从已解压的项目根目录运行。

```powershell
python tools/unity.py samples --cache-only
python standalone/tests/integrated.py --client-home cache/0.1.4-Beta/client-home --samples cache/0.1.4-Beta/samples --fixture standalone/tests/worlds/fixtures
python standalone/tests/registry_network.py --client-home cache/0.1.4-Beta/client-home --bundle cache/0.1.4-Beta/mojang-server-26.2.jar --eula-from instance/eula.txt --fixture standalone/tests/worlds/fixtures
```

`standalone/tests/worlds/build.py` 使用锁定的 Forge、NeoForge 和 Fabric 参考件重建四份验收 JAR。`standalone/adapters/client14/build.py` 可单独从源码重建客户端 API 组件。游戏窗口在 QA 模式保持隐藏，普通玩家启动游戏仍显示窗口。
