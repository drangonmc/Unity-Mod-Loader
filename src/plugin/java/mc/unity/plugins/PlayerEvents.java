package mc.unity.plugins;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;

final class PlayerEvents implements AutoCloseable {
    private final Host host;
    private final Map<UUID,Location> lastMove=new HashMap<>();
    private final Map<UUID,CompletableFuture<Void>> chats=new HashMap<>();
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean closed;
    PlayerEvents(Host host){this.host=host;}
    private Set<Player> online(){
        var result=new LinkedHashSet<Player>();
        for(var p:host.game.getPlayerList().getPlayers())result.add(host.views.player(p));
        return result;
    }
    String command(ServerPlayer raw,String input){
        if(PlayerCommandPreprocessEvent.getHandlerList().getRegisteredListeners().length==0)return input;
        var event=new PlayerCommandPreprocessEvent(host.views.player(raw),"/"+input,online());
        host.fire(event);
        var value=event.getMessage();
        return event.isCancelled() || value==null || !value.startsWith("/")?null:value.substring(1);
    }
    void forget(UUID id){lastMove.remove(id);}
    boolean move(ServerPlayer raw,double x,double y,double z,float yaw,float pitch,double ox,double oy,double oz){
        if(PlayerMoveEvent.getHandlerList().getRegisteredListeners().length==0)return true;
        var world=host.views.world(raw.level());
        var before=new Location(world,ox,oy,oz,raw.getYRot(),raw.getXRot());
        var from=lastMove.getOrDefault(raw.getUUID(),before);
        var to=new Location(world,x,y,z,yaw,pitch);
        if(from.getWorld()==world && from.distanceSquared(to)<=1d/256 &&
                Math.abs(from.getYaw()-yaw)+Math.abs(from.getPitch()-pitch)<=10)return true;
        raw.absSnapTo(ox,oy,oz,before.getYaw(),before.getPitch());
        var player=host.views.player(raw);
        var event=new PlayerMoveEvent(player,from.clone(),to.clone());
        host.fire(event);
        if(!before.equals(player.getLocation()))return false;
        if(event.isCancelled() || event.getTo()==null){
            raw.connection.teleport(from.getX(),from.getY(),from.getZ(),from.getYaw(),from.getPitch());
            lastMove.put(raw.getUUID(),from);return false;
        }
        if(!to.equals(event.getTo())){if(!player.teleport(event.getTo()))raw.connection.teleport(ox,oy,oz,before.getYaw(),before.getPitch());return false;}
        lastMove.put(raw.getUUID(),to);return true;
    }
    TeleportTransition teleport(ServerPlayer raw,TeleportTransition t,String reason){
        lastMove.remove(raw.getUUID());
        if(PlayerTeleportEvent.getHandlerList().getRegisteredListeners().length==0)return t;
        var absolute=PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(raw),PositionMoveRotation.of(t),t.relatives());
        var pos=absolute.position();var p=host.views.player(raw);
        var to=new Location(host.views.world(t.newLevel()),pos.x,pos.y,pos.z,absolute.yRot(),absolute.xRot());
        var event=new PlayerTeleportEvent(p,p.getLocation(),to.clone(),PlayerTeleportEvent.TeleportCause.valueOf(reason));
        host.fire(event);var dest=event.getTo();
        if(event.isCancelled() || dest==null)return null;
        if(to.equals(dest))return t;
        dest.checkFinite();
        var level=((NativeWorldView)Objects.requireNonNull(dest.getWorld())).unityLevel();
        return new TeleportTransition(level,new net.minecraft.world.phys.Vec3(dest.getX(),dest.getY(),dest.getZ()),absolute.deltaMovement(),
            dest.getYaw(),dest.getPitch(),t.missingRespawnBlock(),t.asPassenger(),Set.of(),t.postTeleportTransition());
    }
    private Component adventure(net.minecraft.network.chat.Component c){
        var ops=host.game.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserializeFromTree(
            net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(ops,c).getOrThrow());
    }
    boolean chat(ServerPlayer raw,PlayerChatMessage wire,ChatType.Bound bound){
        if(AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners().length==0 &&
                AsyncChatEvent.getHandlerList().getRegisteredListeners().length==0)return false;
        var sender=host.views.player(raw);var recipients=online();var console=host.views.sender(host.game.createCommandSourceStack());
        var original=adventure(wire.decoratedContent());
        var id=raw.getUUID();
        synchronized(chats){
            if(closed)return true;
            var prev=chats.getOrDefault(id,CompletableFuture.completedFuture(null));
            var next=prev.handleAsync((v,f)-> closed?null:prepare(sender,wire,original,recipients,console),workers)
                .thenCompose(delivery->delivery==null?CompletableFuture.completedFuture(null):host.game.submit(()->{
                    if(!closed && host.game.getPlayerList().getPlayer(id)==raw)delivery.run(raw,bound);
                }));
            chats.put(id,next);next.whenComplete((ok,failure)->{
                synchronized(chats){chats.remove(id,next);}
                if(failure!=null && !closed)Host.LOG.log(Level.WARNING,"Player chat delivery",failure);
            });
        }
        return true;
    }
    interface Delivery {void run(ServerPlayer raw,ChatType.Bound bound);}
    private Delivery prepare(Player sender,PlayerChatMessage wire,Component original,Set<Player> players,org.bukkit.command.CommandSender console){
        boolean cancelled=false;var initial=wire.signedContent();Component message=original;
        ChatRenderer normal=ChatRenderer.defaultRenderer(),renderer=normal;
        if(AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners().length!=0){
            var event=new AsyncPlayerChatEvent(true,sender,initial,new LinkedHashSet<>(players));host.fire(event);
            players=new LinkedHashSet<>(event.getRecipients());cancelled=event.isCancelled();
            if(!initial.equals(event.getMessage()))message=LegacyComponentSerializer.legacySection().deserialize(event.getMessage());
            var format=event.getFormat();
            if(!"<%1$s> %2$s".equals(format))renderer=ChatRenderer.viewerUnaware((p,name,text)->
                LegacyComponentSerializer.legacySection().deserialize(String.format(format,LegacyComponentSerializer.legacySection().serialize(name),LegacyComponentSerializer.legacySection().serialize(text))));
        }
        var viewers=new LinkedHashSet<Audience>(players);viewers.add(console);
        var signed=new SignedMessage(){
            public Identity identity(){return Identity.identity(wire.sender());}
            public java.time.Instant timestamp(){return wire.timeStamp();}
            public long salt(){return wire.salt();}
            public Signature signature(){return wire.signature()==null?null:SignedMessage.signature(wire.signature().bytes().clone());}
            public Component unsignedContent(){return wire.unsignedContent()==null?null:original;}
            public String message(){return wire.signedContent();}
        };
        var event=new AsyncChatEvent(true,sender,viewers,renderer,message,original,signed);event.setCancelled(cancelled);host.fire(event);
        if(event.isCancelled())return null;
        var targets=List.copyOf(event.viewers());var result=event.message();var render=event.renderer();
        return (raw,bound)->{
            var msg=result.equals(original)?wire:wire.withUnsignedContent(host.nativeReason(result));
            var outgoing=OutgoingChatMessage.create(msg);
            var list=host.game.getPlayerList();
            if(render==normal && targets.contains(console) && targets.size()==list.getPlayerCount()+1 &&
                    list.getPlayers().stream().allMatch(p->targets.contains(host.views.player(p)))) {
                list.broadcastChatMessage(msg,raw,bound);return;
            }
            for(var target:targets){
                if(target instanceof Player p){
                    var peer=list.getPlayer(p.getUniqueId());if(peer==null || host.views.player(peer)!=p)continue;
                    boolean filtered=raw.shouldFilterMessageTo(peer);
                    if(render==normal)outgoing.sendToPlayer(peer,filtered,bound);
                    else if(!filtered || wire.filterMask().isEmpty())peer.sendSystemMessage(host.nativeReason(render.render(sender,sender.displayName(),result,target)));
                }else if(target==console)host.game.sendSystemMessage(host.nativeReason(render.render(sender,sender.displayName(),result,target)));
                else target.sendMessage(render.render(sender,sender.displayName(),result,target));
            }
        };
    }
    public void close(){synchronized(chats){closed=true;chats.clear();}workers.shutdownNow();lastMove.clear();}
}
