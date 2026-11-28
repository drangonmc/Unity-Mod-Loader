package mc.unity.bootstrap;
import java.io.*;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarFile;
import static java.lang.constant.ConstantDescs.*;

/** KFF's FML language service is a host component. The game library and the Kotlin context remain upstream. */
public final class KffLanguage {
    private static final String PKG="thedarkcolour/kotlinforforge/neoforge/";
    private static final String SERVICE="META-INF/services/net.neoforged.neoforgespi.language.IModLanguageLoader";
    public static final String CONTAINER=PKG+"KotlinModContainer";
    private static final String CONTEXT=PKG+"KotlinModLoadingContext";
    private static final String SUFFIX="$thedarkcolour_kotlinforforge_lang";
    public static boolean provider(JarFile jar)throws IOException{
        var service=jar.getJarEntry(SERVICE);if(service==null)return false;
        try(var in=jar.getInputStream(service)){
            var lines=new String(in.readNBytes(65536),StandardCharsets.UTF_8).lines().map(s->s.split("#",2)[0].trim()).filter(s->!s.isEmpty()).toList();
            if(!lines.equals(List.of("thedarkcolour.kotlinforforge.neoforge.KotlinLanguageLoader")))return false;
        }
        var version=jar.getManifest().getMainAttributes().getValue("Implementation-Version");
        if(version==null || !version.startsWith("6."))throw new IOException("Unsupported KFF language service version "+version);
        // Check the binary contract of the caller retained from the original JAR.
        var cf=ClassFile.of();var path=jar.getJarEntry(CONTEXT+".class");
        if(path==null || jar.getJarEntry(CONTAINER+".class")==null)throw new IOException("Incomplete KFF language service");
        try(var in=jar.getInputStream(path)){
            var methods=cf.parse(in.readAllBytes()).methods();
            if(methods.stream().noneMatch(m->m.methodName().equalsString("<init>") && m.methodType().equalsString("(L"+CONTAINER+";)V")) ||
               methods.stream().noneMatch(m->m.methodName().equalsString("getKEventBus") && m.methodType().equalsString("()Lnet/neoforged/bus/api/IEventBus;")))
                throw new IOException("Incompatible KotlinModLoadingContext");
        }
        return true;
    }
    public static boolean hostInternal(String path){
        if(path.equals(SERVICE))return true;
        return path.startsWith(CONTAINER+"$") && path.endsWith(".class") || List.of("KotlinLanguageLoader","AutoKotlinEventBusSubscriber").stream()
            .anyMatch(n->path.equals(PKG+n+".class") || path.startsWith(PKG+n+"$") && path.endsWith(".class"));
    }
    public static byte[] container(){
        var type=ClassDesc.ofInternalName(CONTAINER);var parent=ClassDesc.of("net.neoforged.fml.ModContainer");
        var context=ClassDesc.ofInternalName(CONTEXT);var bus=ClassDesc.of("net.neoforged.bus.api.IEventBus");
        var ctor=MethodTypeDesc.of(CD_void,CD_String,bus,CD_String,CD_String,CD_String);
        return ClassFile.of().build(type,b->{
            b.withFlags(ClassFile.ACC_PUBLIC|ClassFile.ACC_FINAL|ClassFile.ACC_SUPER).withSuperclass(parent);
            b.withField("context",context,ClassFile.ACC_PRIVATE|ClassFile.ACC_FINAL);
            b.withMethodBody("<init>",ctor,ClassFile.ACC_PUBLIC,c->c.aload(0).aload(1).aload(2).aload(3).aload(4).aload(5).invokespecial(parent,"<init>",ctor)
                .aload(0).new_(context).dup().aload(0).invokespecial(context,"<init>",MethodTypeDesc.of(CD_void,type)).putfield(type,"context",context).return_());
            b.withMethodBody("getContext"+SUFFIX,MethodTypeDesc.of(context),ClassFile.ACC_PUBLIC,c->c.aload(0).getfield(type,"context",context).areturn());
            b.withMethodBody("getEventBus"+SUFFIX,MethodTypeDesc.of(bus),ClassFile.ACC_PUBLIC,c->c.aload(0).invokevirtual(parent,"getEventBus",MethodTypeDesc.of(bus)).areturn());
        });
    }
}
