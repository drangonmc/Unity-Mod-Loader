package qa.registry;
import com.mojang.serialization.Codec;
import fuzs.multiloaderdataextensions.common.api.v2.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.*;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
@Mod("qa_registry_data")
public final class NestedDataProbe {
    static final DataMapToken<Block, Integer> TYPE = DataMapRegistrar.register(
        Identifier.parse("qa_registry_data:scores"), Registries.BLOCK, Codec.INT, Codec.INT, true);
    public NestedDataProbe(ModContainer mod) {
        var builder = new ModConfigSpec.Builder();
        var config = builder.comment("Test an authored TOML value").defineInRange("limit", 7, 1, 99);
        mod.registerConfig(ModConfig.Type.COMMON, builder.build(), "qa_registry_data.toml");
        NeoForge.EVENT_BUS.addListener(
            (RegisterCommandsEvent e)
                -> e.getDispatcher().register(Commands.literal("unitydataprobe").executes(c -> {
                System.out.println("QA_DATA_MAP " + read() + " CONFIG " + config.get());
                return 1;
            })));
    }
    public static int read() {
        var holder = Blocks.STONE.builtInRegistryHolder();
        int value = DataMapLookup.getData(TYPE, holder);
        int viaRegistry = DataMapLookup.getData(BuiltInRegistries.BLOCK, TYPE, holder.key());
        int fromMap = DataMapLookup.getDataMap(BuiltInRegistries.BLOCK, TYPE).get(holder.key());
        if (value != viaRegistry || value != fromMap)
            throw new IllegalStateException("Data map views differ");
        return value;
    }
}
