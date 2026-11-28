package qa.unity32.kff

import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_CONTEXT

@Mod("unity32kff")
object MachineLanguage {
    init {
        check(MOD_BUS === MOD_CONTEXT.getKEventBus())
        MOD_BUS.addListener(FMLCommonSetupEvent::class.java) { _: FMLCommonSetupEvent ->
            println("U32 KFF lifecycle")
        }
        println("U32 KFF object main")
    }
}
