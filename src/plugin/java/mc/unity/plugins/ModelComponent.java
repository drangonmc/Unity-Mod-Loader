package mc.unity.plugins;
import java.util.*;
import org.bukkit.Color;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import net.minecraft.world.item.component.CustomModelData;
final class ModelComponent implements CustomModelDataComponent {
    private List<Float> floats;private List<Boolean> flags;private List<String> strings;private List<Color> colors;
    ModelComponent(CustomModelData value){floats=value.floats();flags=value.flags();strings=value.strings();colors=value.colors().stream().map(i->Color.fromRGB(i&0xffffff)).toList();}
    public List<Float> getFloats(){return List.copyOf(floats);}public void setFloats(List<Float> v){floats=List.copyOf(v);}
    public List<Boolean> getFlags(){return List.copyOf(flags);}public void setFlags(List<Boolean> v){flags=List.copyOf(v);}
    public List<String> getStrings(){return List.copyOf(strings);}public void setStrings(List<String> v){strings=List.copyOf(v);}
    public List<Color> getColors(){return List.copyOf(colors);}public void setColors(List<Color> v){colors=List.copyOf(v);}
    static CustomModelData nativeData(CustomModelDataComponent c){return new CustomModelData(List.copyOf(c.getFloats()),List.copyOf(c.getFlags()),List.copyOf(c.getStrings()),c.getColors().stream().map(Color::asRGB).toList());}
    public Map<String,Object> serialize(){return Map.of("floats",floats,"flags",flags,"strings",strings,"colors",colors);}
}
