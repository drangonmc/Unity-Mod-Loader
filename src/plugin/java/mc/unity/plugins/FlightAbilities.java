package mc.unity.plugins;

import java.lang.invoke.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/** Attribute Bukkit's flight grant to its own source when PlayerAbilityLib is installed. */
final class FlightAbilities {
    private record Pal(Object source,Object allow,Object flying,MethodHandle grant,MethodHandle revoke){}
    private final Pal pal;
    FlightAbilities(){
        var loader=FlightAbilities.class.getClassLoader();
        Pal found=null;
        try{
            var api=Class.forName("io.github.ladysnake.pal.Pal",false,loader);
            var source=Class.forName("io.github.ladysnake.pal.AbilitySource",false,loader);
            var ability=Class.forName("io.github.ladysnake.pal.PlayerAbility",false,loader);
            var vanilla=Class.forName("io.github.ladysnake.pal.VanillaAbilities",false,loader);
            var look=MethodHandles.publicLookup();
            var token=api.getMethod("getAbilitySource",Identifier.class).invoke(null,Identifier.fromNamespaceAndPath("unity","plugin_flight"));
            var sig=MethodType.methodType(void.class,Player.class,ability,source);
            found=new Pal(token,vanilla.getField("ALLOW_FLYING").get(null),vanilla.getField("FLYING").get(null),
                look.findStatic(api,"grantAbility",sig),look.findStatic(api,"revokeAbility",sig));
        }catch(ClassNotFoundException absent){/* Vanilla abilities cover this session. */}
        catch(ReflectiveOperationException ex){throw new IllegalStateException("PlayerAbilityLib flight API",ex);}
        pal=found;
    }
    void set(ServerPlayer p,boolean allow,boolean flag) {
        var abilities=p.getAbilities();
        if(!allow && flag && !abilities.mayfly)throw new IllegalArgumentException("Flight is not enabled");
        if(pal!=null) {
            try{
                (flag?pal.grant:pal.revoke).invoke(p,allow?pal.allow:pal.flying,pal.source);
                if(allow && !flag && !abilities.mayfly)pal.revoke.invoke(p,pal.flying,pal.source);
            }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException("PlayerAbilityLib",e);}
        }else{
            if(allow){abilities.mayfly=flag;if(!flag)abilities.flying=false;}
            else abilities.flying=flag;
            p.onUpdateAbilities();
        }
    }
}
