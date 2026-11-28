import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Verify the second physical placer on exactly the same saved quarry coordinates. */
class SavedSecond {
    public static void main(String[] args)throws Exception{
        var world=Path.of(args[0]);var core=SavedMachines.tile(world,64,81,24);
        var owner=core.getCompoundOrEmpty("unity:attachments").getCompoundOrEmpty("unity:machine_owner");
        if(!"VolumeB".equals(owner.getString("name").orElseThrow()) ||
            !"0b731fd8-6b40-3e28-b59c-a5d6dcaf3d3d".equals(owner.getString("id").orElseThrow()))throw new AssertionError(owner);
        if(core.getCompoundOrEmpty("Energy").getIntOr("energy",-1)!=0)throw new AssertionError(core);
        var items=SavedMachines.itemsNonEmpty(core.getCompoundOrEmpty("Inventory").getListOrEmpty("stacks"));
        if(!items.equals(Map.of("minecraft:gold_block",1,"minecraft:emerald_block",1,"minecraft:copper_block",1)))throw new AssertionError(items);
        for(int x=66;x<=69;x++){
            var state=SavedWorld.block(world,x,80,25);
            if(!state.equals(x==68?"minecraft:lapis_block":"minecraft:air"))throw new AssertionError(state);
        }
        var result=new JsonObject();result.addProperty("uuid",owner.getString("id").orElseThrow());
        result.add("drops",new Gson().toJsonTree(items));result.addProperty("energy",0);
        Files.writeString(Path.of(args[1]),result.toString());
    }
}
