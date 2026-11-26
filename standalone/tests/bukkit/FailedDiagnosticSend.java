package qa.auditwire;

import java.util.*;
import mc.unity.api.UnityMod;
import mc.unity.core.LoginSync;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

public class FailedDiagnosticSend implements UnityMod {
    public void serverStarted(MinecraftServer server){
        var ids=new ArrayList<Integer>();
        var messages=new ArrayList<String>();
        var transport=new Connection(PacketFlow.SERVERBOUND){
            public void send(Packet<?> packet){
                int id=((ClientboundCustomQueryPacket)packet).transactionId();ids.add(id);
                if(id==LoginSync.TX_DIFF)throw new IllegalStateException("local transport already closed");
            }
        };
        var login=new ServerLoginPacketListenerImpl(server,transport,false){
            public void disconnect(Component msg){messages.add(msg.getString());}
        };
        LoginSync.Resume noResume=()->{throw new AssertionError("Failed login resumed");};
        LoginSync.ready(transport,noResume,server);
        LoginSync.answer(login,transport,new ServerboundCustomQueryAnswerPacket(LoginSync.TX,new LoginSync.Reply("f".repeat(64),Set.of())));
        LoginSync.answer(login,transport,new ServerboundCustomQueryAnswerPacket(LoginSync.TX_DIFF,null));
        if(messages.size()!=1 || !messages.getFirst().contains("发送失败"))throw new AssertionError(messages);
        LoginSync.ready(transport,noResume,server);
        if(!ids.equals(List.of(LoginSync.TX,LoginSync.TX_DIFF,LoginSync.TX)))throw new AssertionError(ids);
        LoginSync.forget(transport);
        System.out.println("QA24 FAILED SEND CLEANED EXACTLY ONCE");
    }
}
