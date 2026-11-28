import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.google.gson.*;
import net.minecraft.nbt.*;

class SavedPlayers {
    public static void main(String[] args)throws Exception {
        var result=new JsonObject();
        for(String name:new String[]{"VolumeA","VolumeB"}){
            var id=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            var n=NbtIo.readCompressed(Path.of(args[0],"players","data",id+".dat"),NbtAccounter.defaultQuota());
            var bag=new JsonObject();
            for(var t:n.getListOrEmpty("Inventory")){
                var item=(CompoundTag)t;
                bag.addProperty(""+item.getByteOr("Slot",(byte)-1),item.getStringOr("id","")+"*"+item.getIntOr("count",0));
            }
            var pos=new JsonArray();
            for(var t:n.getListOrEmpty("Pos"))pos.add(t.asDouble().orElseThrow());
            var player=new JsonObject();player.add("position",pos);player.add("bag",bag);
            result.add(name,player);
        }
        Files.writeString(Path.of(args[1]),result.toString());
    }
}
