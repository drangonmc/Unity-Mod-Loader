import java.lang.classfile.*;
import java.lang.constant.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;
import java.io.*;
import static java.lang.constant.ConstantDescs.*;

/** Generate forwarding methods from the pinned Bukkit interface, retaining covariant bridge descriptors. */
class FacadeBuild {
    static final ClassFile CF=ClassFile.of();
    static ClassModel model(ClassLoader files,ClassDesc type) throws IOException {
        String path=type.descriptorString().substring(1,type.descriptorString().length()-1)+".class";
        try(var in=files.getResourceAsStream(path)) {if(in==null)throw new FileNotFoundException(path);return CF.parse(in.readAllBytes());}
    }
    static void methods(ClassLoader files,ClassDesc iface,Map<String,MethodModel> out,Set<ClassDesc> seen)throws IOException{
        if(!seen.add(iface))return;
        var m=model(files,iface);
        for(var method:m.methods())
            if(method.flags().has(java.lang.reflect.AccessFlag.PUBLIC) && !method.flags().has(java.lang.reflect.AccessFlag.STATIC))
                out.putIfAbsent(method.methodName().stringValue()+method.methodType().stringValue(),method);
        for(var parent:m.interfaces())methods(files,parent.asSymbol(),out,seen);
    }
    static void build(Path classes,ClassLoader files,String target,String parent,String iface,String game)throws IOException {
        var owner=ClassDesc.of(target);var base=ClassDesc.of(parent);var api=ClassDesc.of(iface);
        var ct=MethodTypeDesc.of(CD_void,api,ClassDesc.of(game));
        var all=new TreeMap<String,MethodModel>();
        methods(files,api,all,new HashSet<>());
        for(var m:model(files,base).methods())if(!m.flags().has(java.lang.reflect.AccessFlag.ABSTRACT))
            all.remove(m.methodName().stringValue()+m.methodType().stringValue());
        byte[] bytes=CF.build(owner,b->{
            b.withFlags(ClassFile.ACC_PUBLIC|ClassFile.ACC_FINAL|ClassFile.ACC_SUPER).withSuperclass(base).withInterfaceSymbols(api);
            b.withMethodBody("<init>",ct,ClassFile.ACC_PUBLIC,c->c.aload(0).aload(1).aload(2).invokespecial(base,"<init>",ct).return_());
            for(var m:all.values()){
                var name=m.methodName().stringValue();var desc=m.methodTypeSymbol();
                b.withMethodBody(name,desc,ClassFile.ACC_PUBLIC,c->{
                    c.aload(0).getfield(base,"view",api);
                    int slot=1;
                    for(var arg:desc.parameterList()){var kind=TypeKind.from(arg);c.loadLocal(kind,slot);slot+=kind.slotSize();}
                    c.invokeinterface(api,name,desc).return_(TypeKind.from(desc.returnType()));
                });
            }
        });
        Path path=classes.resolve(target.replace('.','/')+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);
    }
    public static void main(String[] args)throws Exception{
        Path classes=Path.of(args[0]);var urls=new ArrayList<URL>();urls.add(classes.toUri().toURL());
        for(var s:Files.readAllLines(Path.of(args[1])))urls.add(Path.of(s).toUri().toURL());
        try(var files=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())){
            build(classes,files,"org.bukkit.craftbukkit.CraftServer","mc.unity.plugins.ServerFacade","org.bukkit.Server","net.minecraft.server.MinecraftServer");
            build(classes,files,"org.bukkit.craftbukkit.entity.CraftPlayer","org.bukkit.craftbukkit.entity.CraftHumanEntity","org.bukkit.entity.Player","net.minecraft.server.level.ServerPlayer");
        }
    }
}
