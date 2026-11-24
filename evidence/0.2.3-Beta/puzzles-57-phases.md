# Puzzles Lib 26.2.5 的 57 项原始预检缺口

公共／专服 26 项，客户端 27 项，数据生成 4 项。每项按首个报错的作者引用类归类；公共端包括专服和图形客户端。两项客户端配置网络引用通过客户端代理的构造代码和接收连接类型确认。

JVM 栏表示实测进程定义了引用该符号的作者类。类中可含未执行的方法，具体行为见最后一栏。原包散列、每项完整类名和两次运行的来源依据见同名 JSON 及 beta-mixed-gameplay.json。

| 序号 | 引用阶段 | 原始类型或成员 | 作者类 | 已定义该类的 JVM | 验证范围 |
|---:|---|---|---|---|---|
| 1 | 数据生成 | `net/neoforged/neoforge/common/data/SoundDefinitionsProvider` | `AbstractSoundProvider` | 未观察到 | 生成声音资源 |
| 2 | 数据生成 | `net/neoforged/neoforge/data/event/GatherDataEvent$DataProviderFromOutput` | `DataProviderHelper$1` | 未观察到 | 构造器可注册工厂；其产物只在数据生成时使用 |
| 3 | 数据生成 | `net/neoforged/neoforge/common/data/DatapackBuiltinEntriesProvider` | `DataProviderHelper` | server、A、B | 生成内置动态注册表数据 |
| 4 | 数据生成 | `net/neoforged/neoforge/data/event/GatherDataEvent.getResourceManager(Lnet/minecraft/server/packs/PackType;)Lnet/minecraft/server/packs/resources/ResourceManager;` | `NeoForgeDataProviderContext` | server、A、B | 生成器所用的资源管理器 |
| 5 | 公共／专服 | `net/neoforged/neoforge/capabilities/ICapabilityProvider` | `NeoForgeCapabilityHelper` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 6 | 公共／专服 | `net/neoforged/neoforge/attachment/IAttachmentHolder` | `NeoForgeAttachmentTypeAdapter` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 7 | 公共／专服 | `net/neoforged/neoforge/registries/NeoForgeRegistries$Keys` | `NeoForgeDataAttachmentBuilder` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 8 | 公共／专服 | `net/neoforged/neoforge/attachment/AttachmentType$Builder` | `NeoForgeEntityDataAttachmentBuilder` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 9 | 公共／专服 | `net/neoforged/neoforge/common/world/ClimateSettingsBuilder` | `ClimateSettingsContextNeoForge` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 10 | 公共／专服 | `net/neoforged/neoforge/common/world/BiomeGenerationSettingsBuilder` | `GenerationSettingsContextNeoForge` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 11 | 公共／专服 | `net/neoforged/neoforge/common/world/MobSpawnSettingsBuilder` | `MobSpawnSettingsContextNeoForge` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 12 | 公共／专服 | `net/neoforged/neoforge/common/world/BiomeSpecialEffectsBuilder` | `SpecialEffectsContextNeoForge` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 13 | 客户端 | `net/neoforged/neoforge/client/event/AddClientReloadListenersEvent` | `PuzzlesLibNeoForgeClient` | A、B | 客户端入口中的开发模式分支 |
| 14 | 客户端 | `net/neoforged/neoforge/client/gui/ConfigurationScreen$ConfigurationListScreen` | `NeoForgeConfigurationScreen$CustomConfigurationListScreen` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 15 | 客户端 | `net/neoforged/neoforge/client/gui/ConfigurationScreen$ConfigurationSectionScreen` | `NeoForgeConfigurationScreen$CustomConfigurationSectionScreen` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 16 | 客户端 | `net/neoforged/neoforge/client/gui/ConfigurationScreen` | `NeoForgeConfigurationScreen` | A、B | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 17 | 客户端 | `net/neoforged/neoforge/client/ClientHooks` | `NeoForgeClientProxy` | A、B | Unity 客户端服务提供器继承作者代理；按能力接入 |
| 18 | 客户端 | `net/neoforged/neoforge/client/event/RegisterColorHandlersEvent$BlockTintSources` | `BlockColorsContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 19 | 客户端 | `net/neoforged/neoforge/client/event/EntityRenderersEvent$RegisterRenderers` | `BlockEntityRenderersContextNeoForgeImpl` | 未观察到 | 0.1.4 提供客户端渲染工厂注册；世界探针覆盖新实体工厂 |
| 20 | 客户端 | `net/neoforged/neoforge/client/event/ModelEvent$ModifyBakingResult` | `BlockStateResolverContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 21 | 客户端 | `net/neoforged/neoforge/client/event/RegisterBlockModelsEvent` | `BuiltInBlockModelsContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 22 | 客户端 | `net/neoforged/neoforge/client/event/RegisterClientTooltipComponentFactoriesEvent` | `ClientTooltipComponentsContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 23 | 客户端 | `net/neoforged/neoforge/client/event/RegisterEntitySpectatorShadersEvent` | `EntitySpectatorShadersContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 24 | 客户端 | `net/neoforged/neoforge/client/event/RegisterGuiLayersEvent` | `GuiLayersContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 25 | 客户端 | `net/neoforged/neoforge/client/event/RegisterItemModelsEvent` | `ItemModelsContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 26 | 客户端 | `net/neoforged/neoforge/client/event/RegisterKeyMappingsEvent` | `KeyMappingsContextNeoForgeImpl` | 未观察到 | 已提供真实键位注册事件及 Options 注入点；本样本未声明新键位 |
| 27 | 客户端 | `net/neoforged/neoforge/client/event/EntityRenderersEvent$RegisterLayerDefinitions` | `LayerDefinitionsContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 28 | 客户端 | `net/neoforged/neoforge/client/event/RegisterMenuScreensEvent` | `MenuScreensContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 29 | 客户端 | `net/neoforged/neoforge/client/event/RegisterParticleProvidersEvent` | `ParticleProvidersContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 30 | 客户端 | `net/neoforged/neoforge/client/event/RegisterPictureInPictureRenderersEvent` | `PictureInPictureRenderersContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 31 | 客户端 | `net/neoforged/neoforge/client/event/RegisterRenderPipelinesEvent` | `RenderPipelinesContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 32 | 客户端 | `net/neoforged/neoforge/event/AddPackFindersEvent` | `ResourcePackSourcesContextNeoForgeImpl` | 未观察到 | 当前引用来自客户端资源包上下文；AddPackFindersEvent 类型也可用于服务端 |
| 33 | 客户端 | `net/neoforged/neoforge/client/event/EntityRenderersEvent$CreateSkullModels` | `SkullRenderersContextNeoForgeImpl` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 34 | 客户端 | `net/neoforged/neoforge/client/event/ScreenEvent$Opening` | `NeoForgeClientEventInvokers` | 未观察到 | 客户端事件由 Unity 提供器按已支持的回调接入 |
| 35 | 客户端 | `net/neoforged/neoforge/client/settings/IKeyConflictContext` | `NeoForgeKeyMappingHelper` | 未观察到 | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 36 | 客户端 | `net/neoforged/neoforge/client/model/quad/BakedNormals` | `NeoForgeMutableBakedQuad` | A、B | 客户端专用配置、渲染或事件适配；本次未单独验证该接口 |
| 37 | 公共／专服 | `net/neoforged/neoforge/server/ServerLifecycleHooks` | `NeoForgeCommonProxy` | server、A、B | 公共代理可被两端加载；查询当前服务端不是本次玩法所需调用 |
| 38 | 公共／专服 | `net/neoforged/neoforge/data/loading/DatagenModLoader` | `NeoForgeEnvironment` | server、A、B | 已提供游戏模式判定；正常启动时跳过作者的数据生成工作 |
| 39 | 公共／专服 | `net/neoforged/neoforge/event/AddServerReloadListenersEvent` | `NeoForgeModConstructor` | 未观察到 | Unity 构造 SPI 调用作者的 onConstructMod；额外重载钩子未作为通用实现 |
| 40 | 公共／专服 | `net/neoforged/neoforgespi/language/IModInfo.getVersion()Lorg/apache/maven/artifact/versioning/ArtifactVersion;` | `NeoForgeModContainer` | 未观察到 | 查询模组版本的通用元数据接口，未作为本次通过项 |
| 41 | 公共／专服 | `net/neoforged/neoforge/network/registration/PayloadRegistrar.optional()Lnet/neoforged/neoforge/network/registration/PayloadRegistrar;` | `NeoForgeModContext` | server、A、B | 已实现 optional 注册、双向处理器及按连接握手；两名玩家与专服都有匹配及未匹配通道的对照 |
| 42 | 公共／专服 | `net/neoforged/neoforge/common/world/BiomeModifier` | `BiomeModificationsContextNeoForgeImpl$1` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 43 | 公共／专服 | `net/neoforged/neoforge/registries/DataPackRegistryEvent$NewRegistry` | `DataPackRegistriesContextNeoForgeImpl` | 未观察到 | 0.1.4 提供基础数据注册表声明，测试含 NeoForge 自定义注册表 |
| 44 | 公共／专服 | `net/neoforged/neoforge/event/entity/EntityAttributeCreationEvent` | `EntityAttributesContextNeoForgeImpl` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 45 | 公共／专服 | `net/neoforged/neoforge/registries/NewRegistryEvent` | `GameRegistriesContextNeoForgeImpl` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 46 | 公共／专服 | `net/neoforged/neoforge/common/ItemAbility` | `GameplayContentContextNeoForgeImpl$BlockConversionBuilder` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 47 | 公共／专服 | `net/neoforged/neoforge/common/ItemAbilities` | `GameplayContentContextNeoForgeImpl` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 48 | 公共／专服 | `net/neoforged/neoforge/event/ModifyDefaultComponentsEvent` | `ItemComponentsContextNeoForgeImpl` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 49 | 客户端 | `net/neoforged/neoforge/network/registration/PayloadRegistrar.configurationToClient(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;Lnet/neoforged/neoforge/network/handling/IPayloadHandler;)Lnet/neoforged/neoforge/network/registration/PayloadRegistrar;` | `PayloadTypesContextNeoForgeImpl$ClientImpl` | 未观察到 | 由 NeoForgeClientProxy 创建的客户端注册器，属于配置阶段 S2C；与 play 握手分开 |
| 50 | 公共／专服 | `net/neoforged/neoforge/event/entity/RegisterSpawnPlacementsEvent` | `SpawnPlacementsContextNeoForgeImpl` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 51 | 公共／专服 | `net/neoforged/neoforge/registries/ModifyRegistriesEvent` | `NeoForgeEventInvokers` | 未观察到 | 作者原生 NeoForge 事件工厂的注册表修改通知；Unity 走选定的服务提供器 |
| 52 | 公共／专服 | `net/neoforged/neoforge/network/IContainerFactory` | `MenuTypeWithData` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 53 | 公共／专服 | `net/neoforged/neoforge/registries/RegistryBuilder` | `NeoForgeRegistryFactory` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 54 | 公共／专服 | `net/neoforged/neoforge/registries/DeferredHolder.create(Lnet/minecraft/resources/ResourceKey;)Lnet/neoforged/neoforge/registries/DeferredHolder;` | `NeoForgeRegistryManager` | server、A、B | 通用扩展，超出 Block Runner 本次实测调用面 |
| 55 | 公共／专服 | `net/neoforged/neoforge/common/crafting/IntersectionIngredient` | `NeoForgeCombinedIngredients` | 未观察到 | 通用扩展，超出 Block Runner 本次实测调用面 |
| 56 | 客户端 | `net/neoforged/neoforge/network/handling/IPayloadContext.listener()Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;` | `MessageContextNeoForgeImpl$ClientboundConfiguration` | 未观察到 | 泛型父类绑定 ClientConfigurationPacketListenerImpl，接收者是客户端 |
| 57 | 客户端 | `net/neoforged/neoforge/internal/BrandingControl` | `BrandingControlAccessor` | 未观察到 | 提供可供 Mixin 访问的品牌字段；未对开发模式品牌编辑作玩法验收 |
