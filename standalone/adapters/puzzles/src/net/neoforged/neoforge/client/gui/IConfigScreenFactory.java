package net.neoforged.neoforge.client.gui;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
@FunctionalInterface
public interface IConfigScreenFactory extends IExtensionPoint {
    Screen createScreen(ModContainer mod,Screen parent);
    static Optional<IConfigScreenFactory> getForMod(IModInfo info) {
        return ModList.get().getModContainerById(info.getModId()).flatMap(c->c.getCustomExtension(IConfigScreenFactory.class));
    }
}
