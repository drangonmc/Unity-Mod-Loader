package mc.unity.plugins;
import org.bukkit.*;
import org.bukkit.material.MaterialData;
import net.minecraft.util.datafix.fixes.*;

final class LegacyValues {
    static UnsafeValues create(){return Views.of(UnsafeValues.class,"unsafe values",(method,args)->{
        if (method.equals("getDataVersion"))return net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
        if (method.equals("fromLegacy")) {
            Material m=args[0] instanceof MaterialData d ? d.getItemType() : (Material)args[0];
            byte data=args[0] instanceof MaterialData d ? d.getData() : args.length>1 && args[1] instanceof Byte b ? b : 0;
            Material mapped=m;
            if(m.isLegacy()){
                String id=m.getId()<256 ? BlockStateData.getTag((m.getId()<<4)|(data&15)).get("Name").asString("minecraft:air")
                    : ItemIdFix.getItem(m.getId());
                if(m.getId()>=256 && id!=null){var modern=ItemStackTheFlatteningFix.updateItem(id,data&255);if(modern!=null)id=modern;}
                mapped=id==null?null:Material.matchMaterial(id);
                if(mapped==null)mapped=Material.AIR;
            }
            return args.length==2 && args[1] instanceof Byte ? mapped.createBlockData() : mapped;
        }
        throw Views.unsupported("UnsafeValues",method);
    });}
}
