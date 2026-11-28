import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.nbt.*;

/** Read the closed world using vanilla NBT. These checks run without Unity or either region plugin. */
public final class SavedMachines {
    static CompoundTag tile(Path world,int x,int y,int z)throws IOException {
        for(var tag:SavedWorld.chunk(world,x,z).getListOrEmpty("block_entities")){
            var t=(CompoundTag)tag;
            if(t.getIntOr("x",-1)==x && t.getIntOr("y",-1)==y && t.getIntOr("z",-1)==z)return t;
        }
        throw new IOException("Missing saved block entity at "+x+","+y+","+z);
    }
    static Map<String,Integer> items(ListTag list){
        var result=new TreeMap<String,Integer>();
        for(var tag:list){var item=(CompoundTag)tag;result.merge(item.getString("id").orElseThrow(),item.getInt("count").orElseThrow(),Integer::sum);}
        return result;
    }
    static Map<String,Integer> itemsNonEmpty(ListTag stacks){
        var entries=new ListTag();for(var t:stacks)if(((CompoundTag)t).contains("id"))entries.add(t);
        return items(entries);
    }
    public static void main(String[] args)throws Exception{
        Path world=Path.of(args[0]);boolean unity=args[2].startsWith("unity");var result=new JsonObject();
        if(!args[2].equals("unity-machines"))for(int x:new int[]{65,66,67,68,70}){
            var contents=items(tile(world,x,80,15).getListOrEmpty("Items"));
            if(!contents.equals(Map.of("minecraft:amethyst_shard",9)))throw new AssertionError(contents);
        }
        if(unity){
            for(int i=0;i<5;i++){
                String expected=new String[]{"air","gold_block","emerald_block","lapis_block","air"}[i];
                String actual=SavedWorld.block(world,65+i,80,25);
                if(!actual.equals("minecraft:"+expected))throw new AssertionError(actual);
            }
            var core=tile(world,64,81,24);
            var owner=core.getCompoundOrEmpty("unity:attachments").getCompoundOrEmpty("unity:machine_owner");
            if(!owner.toString().contains("VolumeA") || core.getCompoundOrEmpty("Energy").getIntOr("energy",-1)!=0)throw new AssertionError(core);
            if(!itemsNonEmpty(core.getCompoundOrEmpty("Inventory").getListOrEmpty("stacks")).equals(Map.of("minecraft:iron_block",1,"minecraft:copper_block",1)))throw new AssertionError(core);
            result.addProperty("quarryOwner",owner.toString());result.addProperty("quarry",core.toString());
            int[] zs={32,39,35,40,36};
            for(int i=0;i<zs.length;i++){
                var held=items(tile(world,65+i,80,zs[i]).getListOrEmpty("Items"));
                var expect=i==1?Map.of("minecraft:diamond",9):i==4?Map.of("minecraft:emerald",12):Map.of();
                if(!held.equals(expect))throw new AssertionError(held);
                result.addProperty("chest"+(65+i),held.toString());
            }
            var droneOwners=new TreeSet<String>();
            var dir=world.resolve("dimensions/minecraft/overworld/entities");
            for(int cx=3;cx<=5;cx++)for(int cz=0;cz<=3;cz++){
                CompoundTag root;
                try{root=SavedWorld.region(dir,cx*16,cz*16);}
                catch(FileNotFoundException ex){continue;}
                catch(IOException ex){if(ex.getMessage().startsWith("Missing chunk at"))continue;throw ex;}
                for(var tag:root.getListOrEmpty("Entities")){
                    var ent=(CompoundTag)tag;if(!ent.getStringOr("id","").equals("logisticsdrones:stone_drone"))continue;
                    var actor=ent.getCompoundOrEmpty("unity:attachments").getCompoundOrEmpty("unity:machine_owner");
                    var name=actor.getString("name").orElseThrow();if(!droneOwners.add(name))throw new AssertionError(ent);
                    var uuid=java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    int[] nativeId=ent.getIntArray("OwnerUUID").orElseThrow();
                    var actual=net.minecraft.core.UUIDUtil.uuidFromIntArray(nativeId);
                    if(!uuid.equals(actual) || !actor.getString("id").orElse("").equals(uuid.toString()) || !ent.getListOrEmpty("Items").isEmpty())throw new AssertionError(ent);
                    result.addProperty("drone"+name,ent.toString());
                }
            }
            if(!droneOwners.equals(Set.of("VolumeA","VolumeB")))throw new AssertionError(droneOwners);
            Path uid=world.resolve("uid.dat");try(var in=new DataInputStream(Files.newInputStream(uid))){
                result.addProperty("worldUUID",new UUID(in.readLong(),in.readLong()).toString());if(in.read()!=-1)throw new IOException("Trailing world ID data");
            }
        }
        Files.writeString(Path.of(args[1]),result.toString());
    }
}
