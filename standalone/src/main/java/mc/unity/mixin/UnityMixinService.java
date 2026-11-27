package mc.unity.mixin;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.BiFunction;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.*;
import org.spongepowered.asm.service.*;

public final class UnityMixinService
    extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider, IClassTracker, ITransformerProvider {
    private static UnityMixinService service;
    private Map<String, byte[]> sideConfigs = Map.of();

    // A common config may list a renderer or data-generator mixin. Omit it only if every target is
    // unavailable in this runtime and belongs to the corresponding side-specific namespace.
    private void prepareSideConfigs(List<String> names, boolean client) throws IOException, ClassNotFoundException {
        var overrides = new HashMap<String, byte[]>();
        for (String name : names) {
            try (var in = getResourceAsStream(name)) {
                if (in == null) throw new FileNotFoundException(name);
                var config = com.google.gson.JsonParser.parseReader(new InputStreamReader(in,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var common = config.getAsJsonArray("mixins");
                if (common == null) continue;
                boolean changed = false;
                for (var iterator = common.iterator(); iterator.hasNext();) {
                    String prefix = config.has("package") ? config.get("package").getAsString() : "";
                    var mixinName = (prefix.isEmpty() ? "" : prefix + ".") + iterator.next().getAsString();
                    var node = getClassNode(mixinName, false, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                    if (node.invisibleAnnotations == null) continue;
                    for (var ann : node.invisibleAnnotations) {
                        if (ann.values == null || !ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                        var targets = new ArrayList<String>();
                        for (int i = 0; i < ann.values.size(); i += 2) {
                            var key = ann.values.get(i);
                            if (key.equals("value") || key.equals("targets"))
                                for (var t : (List<?>) ann.values.get(i+1))
                                    targets.add(t instanceof Type type ? type.getInternalName() : t.toString().replace('.', '/'));
                        }
                        if (!targets.isEmpty() && targets.stream().allMatch(t ->
                            ((!client && (t.startsWith("net/minecraft/client/") || t.startsWith("com/mojang/blaze3d/"))) || t.startsWith("net/neoforged/neoforge/data/"))
                            && getClass().getClassLoader().getResource(t + ".class") == null)) {
                            iterator.remove();
                            changed = true;
                            System.out.println("[Unity] Gameplay runtime excludes unavailable side targets " + targets);
                        }
                    }
                }
                if (changed) overrides.put(name, config.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        sideConfigs = Map.copyOf(overrides);
    }
    public UnityMixinService() {
        service = this;
    }
    public String getName() {
        return "Unity";
    }
    public boolean isValid() {
        return true;
    }
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.DEFAULT;
    }
    public IClassProvider getClassProvider() {
        return this;
    }
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }
    private final ClassLoader domain=getClass().getClassLoader();
    private final Set<String> excluded=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.lang.invoke.MethodHandle before=operation("beforeMixins",byte[].class,String.class,byte[].class);
    private final java.lang.invoke.MethodHandle loaded=operation("alreadyDefined",boolean.class,String.class);
    private final java.lang.invoke.MethodHandle invalid=operation("invalidMixin",void.class,String.class);
    private java.lang.invoke.MethodHandle operation(String name,Class<?> result,Class<?>... params) {
        try { return java.lang.invoke.MethodHandles.publicLookup().findVirtual(domain.getClass(),name,
                  java.lang.invoke.MethodType.methodType(result,params)).bindTo(domain); }
        catch(ReflectiveOperationException e){throw new IllegalStateException("Unity Mixin service: "+name,e);}
    }
    private final ILegacyClassTransformer unity=new ILegacyClassTransformer() {
        public String getName(){return "mc.unity.bootstrap.GameLoader";}
        public boolean isDelegationExcluded(){return false;}
        public byte[] transformClassBytes(String name,String mapped,byte[] bytes){
            try{return (byte[])before.invokeExact(mapped,bytes);}
            catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        }
    };
    public IClassTracker getClassTracker(){return this;}
    public ITransformerProvider getTransformerProvider(){return this;}
    public void registerInvalidClass(String name){
        try{invalid.invokeExact(name);}catch(Throwable e){throw new IllegalStateException(e);}
    }
    public boolean isClassLoaded(String name){
        try{return (boolean)loaded.invokeExact(name.replace('/','.'));}catch(Throwable e){throw new IllegalStateException(e);}
    }
    public String getClassRestrictions(String name){return "";}
    public Collection<ITransformer> getTransformers(){return List.of(unity);}
    public Collection<ITransformer> getDelegatedTransformers(){return excluded.contains(unity.getName())?List.of():getTransformers();}
    public void addTransformerExclusion(String name){excluded.add(name);}
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }
    public IAdviceProvider getAdviceProvider() {
        return null;
    }
    public Collection<String> getPlatformAgents() {
        return List.of();
    }
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual("Unity");
    }
    public URL[] getClassPath() {
        return ((URLClassLoader) getClass().getClassLoader()).getURLs();
    }
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return findClass(name, false);
    }
    public Class<?> findClass(String name, boolean init) throws ClassNotFoundException {
        return Class.forName(name, init, getClass().getClassLoader());
    }
    public Class<?> findAgentClass(String name, boolean init) throws ClassNotFoundException {
        return findClass(name, init);
    }
    public InputStream getResourceAsStream(String name) {
        var override = sideConfigs.get(name);
        return override != null ? new ByteArrayInputStream(override) : getClass().getClassLoader().getResourceAsStream(name);
    }
    public ClassNode getClassNode(String name) throws IOException, ClassNotFoundException {
        return getClassNode(name, true, 0);
    }
    public ClassNode getClassNode(String name, boolean transform) throws IOException, ClassNotFoundException {
        return getClassNode(name, transform, 0);
    }
    public ClassNode getClassNode(String name, boolean transform, int flags)
        throws IOException, ClassNotFoundException {
        var resource = domain.getResource(name.replace('.', '/') + ".class");
        try (var input = resource==null?null:resource.openStream()) {
            if (input == null)
                throw new ClassNotFoundException(name);
            var node = new ClassNode();
            byte[] bytes=input.readAllBytes();
            if(transform && !excluded.contains(unity.getName())) bytes=unity.transformClassBytes(name,name,bytes);
            new ClassReader(bytes).accept(node, flags);
            return node;
        }
    }
    public static void finish() {
        if (org.spongepowered.asm.service.MixinService.getGlobalPropertyService()
                instanceof PropertiesService props)
            props.clear();
        service = null;
    }
    public static BiFunction<String, byte[], byte[]> start(List<String> configs, boolean client) throws Exception {
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(
            client ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
        service.prepareSideConfigs(configs, client);
        for (String c : configs) Mixins.addConfiguration(c);
        var transformer = service.getInternal(IMixinTransformerFactory.class).createTransformer();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        return (name, bytes) -> bytes == null ? transformer.generateClass(MixinEnvironment.getCurrentEnvironment(), name)
                                              : transformer.transformClassBytes(name, name, bytes);
    }
}
