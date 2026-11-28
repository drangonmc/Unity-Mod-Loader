"""Extract the pinned upstream transfer API, with its license and corresponding Java sources."""
import hashlib,sys,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
PREFIX="net/neoforged/neoforge/"
# Only the transfer subsystem is part of this artifact. Unity bootstraps Minecraft and owns capabilities.
PACKAGES=("transfer/resource/","transfer/transaction/","transfer/energy/","transfer/item/","transfer/access/")
CLASSES=("transfer/TransferPreconditions","transfer/ResourceHandler","transfer/ResourceHandlerUtil",
         "transfer/StacksResourceHandler","transfer/CombinedResourceHandler","transfer/RangedResourceHandler",
         "transfer/fluid/FluidResource","transfer/fluid/FluidStacksResourceHandler",
         "fluids/FluidStack","fluids/FluidInstance","fluids/FluidStackTemplate","fluids/capability/IFluidHandler","registries/RegisterEvent",
         "event/entity/EntityEvent","event/entity/living/LivingEvent","event/entity/player/PlayerEvent",
         "event/entity/player/PlayerInteractEvent","client/model/quad/BakedColors","client/event/RegisterPictureInPictureRenderersEvent","client/gui/PictureInPictureRendererRegistration",
         "common/extensions/ContainerExtension","common/MutableDataComponentHolder","world/inventory/StackCopySlot","transfer/DelegatingResourceHandler","transfer/IndexModifier","transfer/ItemAccessResourceHandler","fluids/capability/FluidResourceHandlerAdapter",
         "common/extensions/IMenuTypeExtension","network/IContainerFactory","client/event/RegisterItemModelsEvent","client/event/RegisterSpecialModelRendererEvent",
         "client/event/ExtractBlockOutlineRenderStateEvent","client/event/SubmitCustomGeometryEvent","client/IRenderableSection","client/CustomBlockOutlineRenderer","event/entity/player/FluidTooltipEvent",
         "event/level/BlockEvent","event/level/block/BreakBlockEvent","common/util/BlockSnapshot",
         "event/entity/player/AttackEntityEvent","event/tick/LevelTickEvent","event/level/LevelEvent","event/level/ChunkEvent","event/entity/EntityJoinLevelEvent",
         "common/damagesource/DamageContainer","common/damagesource/IReductionFunction",
         "event/entity/living/LivingShieldBlockEvent","event/entity/living/LivingIncomingDamageEvent","event/entity/living/LivingDamageEvent",
         "event/entity/living/LivingDeathEvent","event/entity/living/LivingDropsEvent")
def build(binary,source,out):
    expect=(Path(__file__).parent/"inputs.sha256").read_text().splitlines()
    for f,h in zip((binary,source),expect,strict=True):
        if hashlib.sha256(f.read_bytes()).hexdigest()!=h:raise ValueError(f"Wrong upstream SDK: {f.name}")
    with zipfile.ZipFile(binary) as upstream,zipfile.ZipFile(source) as src,zipfile.ZipFile(out,"w") as result:
        root=src.namelist()[0].split("/")[0]+"/"
        paths=set()
        def put(n,b):result.writestr(zipfile.ZipInfo(n,(2026,1,1,0,0,0)),b,compress_type=zipfile.ZIP_DEFLATED)
        for n in sorted(upstream.namelist()):
            if not n.startswith(PREFIX) or not n.endswith(".class"): continue
            base=n[len(PREFIX):-6].split("$")[0]
            if not (base.startswith(PACKAGES) or base in CLASSES):continue
            put(n,upstream.read(n))
            paths.add(PREFIX+base+".java")
        for n in sorted(paths):
            f,=(f for f in src.namelist() if f.endswith("/java/"+n))
            put("META-INF/unity-sources/"+n,src.read(f))
        put("META-INF/LICENSE-NeoForge.txt",src.read(root+"LICENSE.txt"))
        put("META-INF/README-LICENSE-NeoForge.md",src.read(root+"README-LICENSE.md"))
if __name__=="__main__":build(*(Path(f) for f in sys.argv[1:]))
