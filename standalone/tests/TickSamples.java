import com.sun.tools.attach.VirtualMachine;
import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.Arrays;
import javax.management.*;
import javax.management.remote.*;

/** Read Mojang's 100-tick ring through its own MBean. No classes are injected into the server. */
class TickSamples {
    public static void main(String[] args) throws Exception {
        var vm=VirtualMachine.attach(args[0]);
        String address;
        try { address=vm.startLocalManagementAgent(); } finally { vm.detach(); }
        try (var connector=JMXConnectorFactory.connect(new JMXServiceURL(address));
             var out=Files.newBufferedWriter(Path.of(args[1]))) {
            var connection=connector.getMBeanServerConnection();
            var bean=new ObjectName("net.minecraft.server:type=Server");
            var memory=ManagementFactory.newPlatformMXBeanProxy(connection,ManagementFactory.MEMORY_MXBEAN_NAME,MemoryMXBean.class);
            long[] previous=(long[])connection.getAttribute(bean,"tickTimes");
            int windows=Integer.parseInt(args[2]);
            out.write("window,slot,tickNanoseconds,heapBytes\n");
            for (int window=0;window<windows;window++) {
                long deadline=System.nanoTime()+60_000_000_000L;
                long[] next;
                // A changed value at every index proves that each tick in this sample is new.
                do {
                    Thread.sleep(1000);
                    next=(long[])connection.getAttribute(bean,"tickTimes");
                    if (System.nanoTime()>deadline) throw new IOException("Tick ring did not advance");
                } while (!fresh(previous,next));
                long heap=memory.getHeapMemoryUsage().getUsed();
                for(int i=0;i<next.length;i++) out.write(window+","+i+","+next[i]+","+heap+"\n");
                out.flush();
                previous=next;
            }
        }
    }
    private static boolean fresh(long[] previous,long[] next) {
        if (previous.length!=100 || next.length!=100) throw new IllegalStateException("Unexpected tick ring");
        for(int i=0;i<next.length;i++) if(next[i]<=0 || next[i]==previous[i]) return false;
        return true;
    }
}
