package qa.bridge;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
public final class Bridge implements net.fabricmc.api.ModInitializer {
    public void onInitialize() {
        if (!qa.plain.Plain.order.contains("N") || !qa.plain.Plain.order.contains("L")
            || !qa.plain.Plain.value().equals("transformed"))
            throw new IllegalStateException("Dependency order or shared mod target");
        if (!qa.plain.Plain.VendorWitness.class.isAssignableFrom(
                com.mojang.brigadier.context.StringRange.class))
            throw new IllegalStateException("Vendor Mixin missing");
        qa.plain.Plain.order += "F";
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.parse("aa_bridge:only_tab"),
            CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 23)
                .title(net.minecraft.network.chat.Component.literal("Test"))
                .icon(() -> new ItemStack(Items.STONE))
                .build());
    }
    public static boolean ready() {
        return qa.plain.Plain.order.endsWith("F");
    }
    public static class Server implements net.fabricmc.api.DedicatedServerModInitializer {
        public void onInitializeServer() {
            if (!ready())
                throw new IllegalStateException("main initializer order");
            System.out.println("CHECK FABRIC SERVER ENTRY");
        }
    }
    public static class Client implements net.fabricmc.api.ClientModInitializer {
        public void onInitializeClient() {
            if (!ready())
                throw new IllegalStateException("main initializer order");
            System.out.println("CHECK FABRIC CLIENT ENTRY");
        }
    }
}
