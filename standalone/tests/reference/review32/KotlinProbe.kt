package qa.unity32.kotlin
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint

object Early : PreLaunchEntrypoint {
    var called=false
    override fun onPreLaunch() { check(!called); called=true; println("U32 Kotlin preLaunch") }
}
object Main : ModInitializer {
    override fun onInitialize() { check(Early.called); println("U32 Kotlin object main") }
}
