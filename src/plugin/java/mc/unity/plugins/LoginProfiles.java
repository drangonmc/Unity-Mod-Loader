package mc.unity.plugins;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.GameProfile;
import java.util.*;

final class LoginProfiles {
    static PlayerProfile view(GameProfile profile){
        var fields=new LinkedHashSet<ProfileProperty>();
        profile.properties().values().forEach(p->fields.add(new ProfileProperty(p.name(),p.value(),p.signature())));
        return view(profile.id(),profile.name(),fields);
    }
    static com.mojang.authlib.GameProfile nativeProfile(org.bukkit.profile.PlayerProfile profile){
        var id=profile.getUniqueId();var name=profile.getName();
        var values=com.google.common.collect.ImmutableMultimap.<String,com.mojang.authlib.properties.Property>builder();
        Set<ProfileProperty> properties=new LinkedHashSet<>();
        if(profile instanceof PlayerProfile paper)properties.addAll(paper.getProperties());else ProfileTextures.set(properties,profile.getTextures());
        for(var p:properties)values.put(p.getName(),new com.mojang.authlib.properties.Property(p.getName(),p.getValue(),p.getSignature()));
        return new GameProfile(id==null?net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(Objects.requireNonNull(name)):id,name==null?"":name,new com.mojang.authlib.properties.PropertyMap(values.build()));
    }
    static PlayerProfile view(UUID id,String name,Set<ProfileProperty> properties){
        var uuid=new java.util.concurrent.atomic.AtomicReference<>(id);
        var username=new java.util.concurrent.atomic.AtomicReference<>(name);
        return Views.of(PlayerProfile.class,"profile "+name,(m,a)->switch(m){
            case "getUniqueId","getId"->uuid.get();
            case "setId"->uuid.getAndSet((UUID)a[0]);
            case "getName"->username.get();
            case "setName"->username.getAndSet((String)a[0]);
            case "isComplete"->uuid.get()!=null && username.get()!=null;
            case "getTextures"->ProfileTextures.read(properties);
            case "setTextures"->{ProfileTextures.set(properties,(org.bukkit.profile.PlayerTextures)a[0]);yield null;}
            case "getProperties"->properties;
            case "hasProperty"->properties.stream().anyMatch(p->p.getName().equals(a[0]));
            case "setProperty"->{var p=(ProfileProperty)a[0];properties.removeIf(x->x.getName().equals(p.getName()));properties.add(p);yield null;}
            case "clearProperties"->{properties.clear();yield null;}
            case "clone"->view(uuid.get(),username.get(),new LinkedHashSet<>(properties));
            default->throw Views.unsupported("PlayerProfile",m);
        });
    }
}
