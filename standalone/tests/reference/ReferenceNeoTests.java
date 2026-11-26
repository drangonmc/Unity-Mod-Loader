package qa.reference;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.testframework.conf.FrameworkConfiguration;
@Mod("reference_neo_tests")
public final class ReferenceNeoTests {
    public ReferenceNeoTests(IEventBus bus, ModContainer container) {
        FrameworkConfiguration.builder(Identifier.parse("reference_neo_tests:suite"))
            .enableTests("testCustomHelper","testLavaBucketToEmptyBucket")
            .build().create().init(bus,container);
    }
}
