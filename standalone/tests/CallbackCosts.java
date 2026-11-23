import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import com.google.gson.GsonBuilder;
import mc.unity.core.Events;
import mc.unity.adapter.forge.CommandBus;
import java.security.MessageDigest;

/** CPU-only dispatcher comparison. Mutable callback results are checked after every timed batch. */
class CallbackCosts {
    private record Batch(int iteration,long elapsedNs,int calls,long checksum){}
    private static final class Counter implements Consumer<Counter> {
        final int salt;long v;
        Counter(int salt){this.salt=salt;}
        public void accept(Counter event){event.v=Long.rotateLeft(event.v,3)^salt;}
    }
    private static volatile long sink;
    private static Batch time(int round,int count,Consumer<Counter> dispatch) {
        var event=new Counter(0);event.v=round+1;
        long start=System.nanoTime();
        for(int i=0;i<count;i++)dispatch.accept(event);
        long elapsed=System.nanoTime()-start;
        sink=event.v;return new Batch(round,elapsed,count,event.v);
    }
    public static void main(String[] args)throws Exception{
        var groups=new TreeMap<String,Object>();
        for(int fanout:List.of(0,1,8,32)){
            Counter[] listeners=new Counter[fanout];
            var signal=new Events.Signal<Counter>();
            var bus=new CommandBus<Counter>();
            for(int i=0;i<fanout;i++){listeners[i]=new Counter(i+123);signal.listen(listeners[i]);bus.addListener(listeners[i]);}
            Map<String,Consumer<Counter>> methods=new LinkedHashMap<>();
            methods.put("direct",e->{for(var listener:listeners)listener.accept(e);});
            methods.put("core",signal::post);methods.put("forge",bus::dispatch);
            var rows=new TreeMap<String,List<Batch>>();
            var order=new ArrayList<>(methods.keySet());
            for(int round=-10;round<20;round++){
                Collections.rotate(order,1);
                Long check=null;
                for(String key:order){
                    var run=time(round,300000,methods.get(key));
                    if(check!=null && check!=run.checksum)throw new AssertionError("Mismatched callback result");
                    check=run.checksum;
                    if(round>=0)rows.computeIfAbsent(key,k->new ArrayList<>()).add(run);
                }
            }
            groups.put(Integer.toString(fanout),rows);
        }
        var jar=Path.of(Events.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        var report=Map.of("loader",sha,"java",System.getProperty("java.runtime.version"),"batches",groups,
            "units","elapsedNs per batch; divide by calls for nanoseconds per dispatch");
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
