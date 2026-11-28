package mc.unity.bootstrap;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.util.*;
import java.util.jar.JarFile;

/** Compare the shipped adapter's binary links with the candidate's classes without initializing plugin code. */
public final class JarContract {
    private final JarFile jar;
    private final Map<String,ClassModel> classes = new HashMap<>();
    private JarContract(JarFile jar) {this.jar=jar;}

    public static void check(JarFile jar, Collection<byte[]> adapter, String... namespaces) throws IOException {
        var check = new JarContract(jar);
        var clients = adapter.stream().map(b -> ClassFile.of().parse(b)).toList();
        clients.forEach(m -> check.classes.put(m.thisClass().asInternalName(), m));
        for (var client : clients) {
            for (var cp : client.constantPool()) {
                if (cp instanceof ClassEntry cls && matches(cls.asInternalName(),namespaces)) check.required(cls.asInternalName());
                if (!(cp instanceof MemberRefEntry ref) || !matches(ref.owner().asInternalName(),namespaces)) continue;
                var owner=ref.owner().asInternalName();
                if (!check.member(check.required(owner),ref.name().stringValue(),ref.type().stringValue(),
                                  cp instanceof FieldRefEntry,new HashSet<>()))
                    throw new IOException(jar.getName()+" lacks "+owner+"."+ref.name().stringValue()+ref.type().stringValue());
            }
            if (client.flags().has(java.lang.reflect.AccessFlag.ABSTRACT)) continue;
            var faces = new LinkedHashMap<String,ClassModel>();
            for (var parent = client; parent != null;) {
                for (var face : parent.interfaces()) check.interfaces(face.asInternalName(), faces);
                parent = parent.superclass().isPresent() ? check.model(parent.superclass().get().asInternalName()) : null;
            }
            var obligations=new LinkedHashMap<String,ClassModel>();
            for(var name:faces.keySet()) if(matches(name,namespaces)) check.interfaces(name,obligations);
            for (var face : obligations.values()) {
                for (var method : face.methods()) {
                    if (!method.flags().has(java.lang.reflect.AccessFlag.ABSTRACT)) continue;
                    String name=method.methodName().stringValue(), desc=method.methodType().stringValue();
                    if (!check.implemented(client,faces,name,desc))
                        throw new IOException("Adapter "+client.thisClass().asInternalName()+" lacks "+face.thisClass().asInternalName()+"."+name+desc);
                }
            }
        }
    }
    private void interfaces(String name,Map<String,ClassModel> result) throws IOException {
        if (result.containsKey(name)) return;
        var face=model(name);
        if (face==null) return;
        result.put(name,face);
        for(var parent:face.interfaces()) interfaces(parent.asInternalName(),result);
    }
    private boolean implemented(ClassModel concrete,Map<String,ClassModel> faces,String name,String desc) throws IOException {
        for (var cls=concrete;cls!=null;cls=cls.superclass().isPresent()?model(cls.superclass().get().asInternalName()):null) {
            for (var m:cls.methods()) if (m.methodName().equalsString(name) && m.methodType().equalsString(desc))
                return m.flags().has(java.lang.reflect.AccessFlag.PUBLIC) && !m.flags().has(java.lang.reflect.AccessFlag.ABSTRACT)
                        && !m.flags().has(java.lang.reflect.AccessFlag.STATIC);
        }
        var declarations=new HashMap<String,Boolean>();
        for (var face:faces.values()) for (var m:face.methods())
            if (m.methodName().equalsString(name) && m.methodType().equalsString(desc) &&
                    !m.flags().has(java.lang.reflect.AccessFlag.PRIVATE) && !m.flags().has(java.lang.reflect.AccessFlag.STATIC))
                declarations.put(face.thisClass().asInternalName(), !m.flags().has(java.lang.reflect.AccessFlag.ABSTRACT));
        var maximal=new HashMap<>(declarations);
        for (var key:declarations.keySet()) {
            var supers=new HashMap<String,ClassModel>();
            for(var parent:faces.get(key).interfaces()) interfaces(parent.asInternalName(),supers);
            supers.keySet().forEach(maximal::remove);
        }
        return maximal.values().stream().filter(Boolean::booleanValue).count()==1;
    }
    private static boolean matches(String name,String[] prefixes) {
        for(var prefix:prefixes) if(name.startsWith(prefix)) return true;
        return false;
    }
    private ClassModel required(String name) throws IOException {
        if(jar.getJarEntry(name+".class")==null)throw new IOException(jar.getName()+" lacks "+name);
        var m=model(name);
        if(m==null) throw new IOException(jar.getName()+" lacks "+name);
        return m;
    }
    private ClassModel model(String name) throws IOException {
        if(classes.containsKey(name))return classes.get(name);
        var entry=jar.getJarEntry(name+".class");
        ClassModel m;
        try(var in=entry==null ? JarContract.class.getClassLoader().getResourceAsStream(name+".class") : jar.getInputStream(entry)) {
            m=in==null?null:ClassFile.of().parse(in.readAllBytes());
        }
        classes.put(name,m);return m;
    }
    private boolean member(ClassModel m,String name,String desc,boolean field,Set<String> visited) throws IOException {
        if(m==null || !visited.add(m.thisClass().asInternalName()))return false;
        if(field ? m.fields().stream().anyMatch(f->f.fieldName().equalsString(name) && f.fieldType().equalsString(desc)) :
                   m.methods().stream().anyMatch(f->f.methodName().equalsString(name) && f.methodType().equalsString(desc)))return true;
        if(name.equals("<init>"))return false;
        for(var i:m.interfaces())if(member(model(i.asInternalName()),name,desc,field,visited))return true;
        return m.superclass().isPresent() && member(model(m.superclass().get().asInternalName()),name,desc,field,visited);
    }
}
