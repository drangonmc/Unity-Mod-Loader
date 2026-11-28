import com.google.gson.JsonObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.*;

/** Offline check of the closed 26.2 test world using the vanilla NBT reader. */
class SavedWorld {
    static CompoundTag chunk(Path world,int x,int z) throws IOException {
        var dir=world.resolve("dimensions/minecraft/overworld/region");
        try (var f=new RandomAccessFile(dir.resolve("r."+Math.floorDiv(x,512)+"."+Math.floorDiv(z,512)+".mca").toFile(),"r")) {
            int slot=Math.floorMod(x>>4,32)+32*Math.floorMod(z>>4,32);
            f.seek(4L*slot);
            long sector=f.readInt()>>>8;
            if(sector<2)throw new IOException("Missing chunk at "+x+","+z);
            f.seek(4096*sector);
            int size=f.readInt();
            if(size<=1 || size>4*1024*1024 || f.readUnsignedByte()!=2)throw new IOException("Unexpected test region compression");
            byte[] compressed=new byte[size-1]; f.readFully(compressed);
            try(var in=new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed)))) {
                return NbtIo.read(in);
            }
        }
    }
    static String block(Path world,int x,int y,int z) throws IOException {
        for(var tag:chunk(world,x,z).getListOrEmpty("sections")) {
            var section=(CompoundTag)tag;
            if(section.getByteOr("Y",(byte)-99)!=y>>4) continue;
            var states=section.getCompoundOrEmpty("block_states");var palette=states.getListOrEmpty("palette");
            int index=0;
            if(palette.size()>1) {
                int bits=Math.max(4,32-Integer.numberOfLeadingZeros(palette.size()-1));
                int per=64/bits,local=(y&15)*256+(z&15)*16+(x&15);
                var data=states.getLongArray("data").orElseThrow();
                index=(int)(data[local/per] >>> (local%per*bits)) & ((1<<bits)-1);
            }
            return ((CompoundTag)palette.get(index)).getStringOr("Name","");
        }
        throw new IOException("Missing section");
    }
    public static void main(String[] args)throws Exception {
        var world=Path.of(args[0]);var result=new JsonObject();var players=new JsonObject();
        for(var name:new String[]{"VolumeA","VolumeB"}) {
            var id=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            var nbt=NbtIo.readCompressed(world.resolve("players/data/"+id+".dat"),NbtAccounter.defaultQuota());
            var bag=new JsonObject();
            for(var tag:nbt.getListOrEmpty("Inventory")) {
                var item=(CompoundTag)tag;
                bag.addProperty(Integer.toString(item.getByteOr("Slot",(byte)-99)),
                                item.getStringOr("id","")+"*"+item.getIntOr("count",0));
            }
            var player=new JsonObject();player.addProperty("health",nbt.getFloatOr("Health",-1));player.add("bag",bag);players.add(name,player);
        }
        var blocks=new JsonObject();
        for(int x:new int[]{46,47,49,50,52}) blocks.addProperty(Integer.toString(x),block(world,x,80,20));
        result.add("players",players);result.add("blocks",blocks);
        Files.writeString(Path.of(args[1]),result.toString(),StandardCharsets.UTF_8);
    }
}
