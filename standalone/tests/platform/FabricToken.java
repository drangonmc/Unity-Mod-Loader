package sample;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.item.Item;
public final class FabricToken implements ModInitializer {
    public void onInitialize() {
        var key = ResourceKey.create(Registries.ITEM, Identifier.parse("unity_fabric_sample:token"));
        Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, context, mode) -> dispatcher.register(Commands.literal("unityfabric").executes(c -> {
                c.getSource().sendSuccess(
                    () -> Component.literal("FABRIC_TOKEN " + BuiltInRegistries.ITEM.getValue(key)), false);
                return 1;
            })));
    }
}
