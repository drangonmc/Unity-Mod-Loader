package qa.forgeconfig;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.bus.BusGroup;
public final class ForgeLootProbe extends LootModifier {
    private static final MapCodec<ForgeLootProbe> CODEC = RecordCodecBuilder.mapCodec(
        i -> LootModifier.<ForgeLootProbe>codecStart(i).apply(i, ForgeLootProbe::new));
    private ForgeLootProbe(LootItemCondition[] conditions) { super(conditions); }
    public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    protected ObjectArrayList<ItemStack> doApply(LootTable table, ObjectArrayList<ItemStack> loot,
                                                 LootContext c) {
        loot.add(new ItemStack(Items.ECHO_SHARD));
        return loot;
    }
    static void install(BusGroup bus) {
        var types =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "qa_forge_config");
        types.register("reward", () -> CODEC);
        types.register(bus);
    }
}
