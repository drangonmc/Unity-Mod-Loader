package mc.unity.plugins;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.profile.PlayerTextures;

final class ProfileTextures implements PlayerTextures {
    private URL skin,cape;
    private SkinModel model=SkinModel.CLASSIC;
    private long timestamp;
    private boolean signed;
    static ProfileTextures read(Set<ProfileProperty> props){
        var result=new ProfileTextures();
        for(var p:props)if(p.getName().equals("textures")){
            var json=JsonParser.parseString(new String(Base64.getDecoder().decode(p.getValue()),StandardCharsets.UTF_8)).getAsJsonObject();
            var textures=json.getAsJsonObject("textures");if(textures!=null){
                var s=textures.getAsJsonObject("SKIN");var c=textures.getAsJsonObject("CAPE");
                if(s!=null){result.skin=url(s.get("url").getAsString());var meta=s.getAsJsonObject("metadata");
                    if(meta!=null && meta.has("model") && meta.get("model").getAsString().equalsIgnoreCase("slim"))result.model=SkinModel.SLIM;}
                if(c!=null)result.cape=url(c.get("url").getAsString());
            }
            result.timestamp=json.has("timestamp")?json.get("timestamp").getAsLong():0;result.signed=p.getSignature()!=null;break;
        }
        return result;
    }
    static void set(Set<ProfileProperty> props,PlayerTextures value){
        props.removeIf(p->p.getName().equals("textures"));if(value==null || value.isEmpty())return;
        var root=new JsonObject();var textures=new JsonObject();root.add("textures",textures);
        if(value.getSkin()!=null){var s=new JsonObject();s.addProperty("url",value.getSkin().toExternalForm());textures.add("SKIN",s);
            if(value.getSkinModel()==SkinModel.SLIM){var m=new JsonObject();m.addProperty("model","slim");s.add("metadata",m);}}
        if(value.getCape()!=null){var c=new JsonObject();c.addProperty("url",value.getCape().toExternalForm());textures.add("CAPE",c);}
        props.add(new ProfileProperty("textures",Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8))));
    }
    private static URL url(String text){try{return URI.create(text).toURL();}catch(MalformedURLException e){throw new IllegalArgumentException(text,e);}}
    private static URL checked(URL u){
        if(u!=null && (!u.getHost().equalsIgnoreCase("textures.minecraft.net") || !Set.of("https","http").contains(u.getProtocol())))throw new IllegalArgumentException("Invalid Minecraft texture URL");
        return u;
    }
    public boolean isEmpty(){return skin==null && cape==null;}
    public void clear(){skin=null;cape=null;model=SkinModel.CLASSIC;changed();}
    public URL getSkin(){return skin;}
    public void setSkin(URL s){setSkin(s,SkinModel.CLASSIC);}
    public void setSkin(URL s,SkinModel m){skin=checked(s);model=m==null || s==null?SkinModel.CLASSIC:m;changed();}
    public SkinModel getSkinModel(){return model;}
    public URL getCape(){return cape;}
    public void setCape(URL c){cape=checked(c);changed();}
    private void changed(){timestamp=0;signed=false;}
    public long getTimestamp(){return timestamp;}
    public boolean isSigned(){return signed;}
}
