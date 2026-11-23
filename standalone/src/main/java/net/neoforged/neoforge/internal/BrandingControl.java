package net.neoforged.neoforge.internal;
import java.util.*;
import java.util.function.BiConsumer;
public final class BrandingControl {
    private static List<String> brandings=List.of("Minecraft 26.2","Unity "+mc.unity.bootstrap.Main.VERSION);
    private static List<String> overCopyrightBrandings=List.of();
    public static String getClientBranding(){return "unity";}
    public static String getServerBranding(){return "unity";}
    public static void forEachLine(boolean includeMC, boolean reverse, BiConsumer<Integer,String> consumer) {
        var lines=brandings==null?List.<String>of():brandings;
        if(!includeMC)lines=lines.stream().filter(s->!s.startsWith("Minecraft ")).toList();
        if(reverse)lines=lines.reversed();
        for(int i=0;i<lines.size();i++)consumer.accept(i,lines.get(i));
    }
    public static void forEachAboveCopyrightLine(BiConsumer<Integer,String> consumer){
        if(overCopyrightBrandings!=null)for(int i=0;i<overCopyrightBrandings.size();i++)consumer.accept(i,overCopyrightBrandings.get(i));
    }
}
