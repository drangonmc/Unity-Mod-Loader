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
    private static PlayerProfile view(UUID id,String name,Set<ProfileProperty> properties){
        var uuid=new java.util.concurrent.atomic.AtomicReference<>(id);
        var username=new java.util.concurrent.atomic.AtomicReference<>(name);
        return Views.of(PlayerProfile.class,"profile "+name,(m,a)->switch(m){
            case "getUniqueId","getId"->uuid.get();
            case "setId"->uuid.getAndSet((UUID)a[0]);
            case "getName"->username.get();
            case "setName"->username.getAndSet((String)a[0]);
            case "isComplete"->uuid.get()!=null && username.get()!=null;
            case "getProperties"->properties;
            case "hasProperty"->properties.stream().anyMatch(p->p.getName().equals(a[0]));
            case "setProperty"->{var p=(ProfileProperty)a[0];properties.removeIf(x->x.getName().equals(p.getName()));properties.add(p);yield null;}
            case "clearProperties"->{properties.clear();yield null;}
            case "clone"->view(uuid.get(),username.get(),new LinkedHashSet<>(properties));
            default->throw Views.unsupported("PlayerProfile",m);
        });
    }
}
