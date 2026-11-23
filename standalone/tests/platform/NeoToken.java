package sample;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.registries.*;
@Mod("unity_neo_sample")
public final class NeoToken {
    public NeoToken(IEventBus bus) {
        var register = DeferredRegister.create(Registries.ITEM, "unity_neo_sample");
        var token = register.register(
            "token", id -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
        register.register(bus);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
            e -> e.getDispatcher().register(Commands.literal("unityneo").executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal("NEO_TOKEN " + token.get()), false);
                return 1;
            })));
        bus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey().identifier().toString().equals("minecraft:ingredients"))
                e.accept(token.get());
        });
    }
}
