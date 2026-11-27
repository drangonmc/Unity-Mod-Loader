package mc.unity.adapter.common;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;
/** Resolve rules against the verified 26.2 class bytes after Unity's built-ins and before Mixin. */
public final class AccessTransforms {
    private enum Target { CLASS, FIELD, METHOD }
    private record Rule(String owner, Target target, String name, String desc, int visibility,
                        int changeFinal, boolean widen, String source) {}
    private static final int VIS = ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE;
    private static final Pattern AT = Pattern.compile("(public|protected|private|default)([+-]f)?");
    private final Map<String, Map<String, String>> interfaces = new LinkedHashMap<>();
    private final List<Rule> rules = new ArrayList<>();
    private static String read(JarFile jar, String name) throws IOException {
        if (name.startsWith("/") || name.contains("\\") || name.contains(".."))
            throw new IOException("Invalid transform resource " + name);
        var e = jar.getJarEntry(name);
        if (e == null)
            throw new IOException("Missing transform resource " + jar.getName() + "!" + name);
        try (var in = jar.getInputStream(e)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private void parse(JarFile jar, String path, boolean aw) throws IOException {
        boolean header = !aw;
        int line = 0;
        for (String raw : read(jar, path).lines().toList()) {
            line++;
            var l = raw.replace("\ufeff", "").split("#", 2)[0].trim();
            if (l.isEmpty())
                continue;
            String source = jar.getName() + "!" + path + ":" + line;
            String[] t = l.split("\\s+");
            try {
                if (!header) {
                    if (t.length != 3 ||
                        !(t[0].equals("accessWidener") && Set.of("v1", "v2").contains(t[1]) ||
                          t[0].equals("classTweaker") && t[1].equals("v1")) ||
                        !Set.of("official", "named", "intermediary").contains(t[2]))
                        throw new IllegalArgumentException("Unsupported access header");
                    header = true;
                    continue;
                }
                if (aw) {
                    String op = t[0].replaceFirst("^transitive-", "");
                    if (op.equals("inject-interface")) {
                        if (t.length != 3)
                            throw new IllegalArgumentException("Invalid interface target");
                        String value = t[2], binaryName = value.split("<", 2)[0],
                               signature = "L" + value + ";";
                        if (!binaryName.matches("[a-zA-Z0-9_$/]+"))
                            throw new IllegalArgumentException("Invalid interface " + value);
                        var parsed = java.lang.classfile.Signature.parseFrom(signature);
                        if (!(parsed instanceof java.lang.classfile.Signature.ClassTypeSig) ||
                            !parsed.signatureString().equals(signature))
                            throw new IllegalArgumentException("Invalid interface signature " + value);
                        var previous = interfaces.computeIfAbsent(t[1], k -> new LinkedHashMap<>())
                                           .putIfAbsent(binaryName, signature);
                        if (previous != null && !previous.equals(signature))
                            throw new IllegalArgumentException("Conflicting generic interface " + binaryName);
                        continue;
                    }
                    var target = Target.valueOf(t[1].toUpperCase(Locale.ROOT));
                    if (t.length != (target == Target.CLASS ? 3 : 5))
                        throw new IllegalArgumentException("Invalid AW member");
                    if (!(op.equals("accessible") || op.equals("extendable") && target != Target.FIELD ||
                          op.equals("mutable") && target == Target.FIELD))
                        throw new IllegalArgumentException("Unsupported AW operation " + op);
                    String owner = t[2];
                    rules.add(new Rule(owner, target, target == Target.CLASS ? "" : t[3],
                                       target == Target.CLASS ? "" : t[4],
                                       op.equals("accessible") || target == Target.CLASS ? ACC_PUBLIC
                                       : op.equals("extendable")                         ? ACC_PROTECTED
                                                                                         : -1,
                                       op.equals("accessible") ? 0 : -1, true, source));
                    if (target != Target.CLASS && !op.equals("mutable"))
                        rules.add(new Rule(owner, Target.CLASS, "", "", ACC_PUBLIC,
                                           op.equals("extendable") ? -1 : 0, true, source));
                } else {
                    if (t.length < 2 || t.length > 3)
                        throw new IllegalArgumentException("Invalid AT member");
                    var m = AT.matcher(t[0]);
                    if (!m.matches())
                        throw new IllegalArgumentException("Invalid AT modifier");
                    String member = t.length == 3 ? t[2] : "";
                    int sig = member.indexOf('(');
                    rules.add(new Rule(t[1].replace('.', '/'),
                                       t.length == 2 ? Target.CLASS
                                       : sig < 0     ? Target.FIELD
                                                     : Target.METHOD,
                                       sig < 0 ? member : member.substring(0, sig),
                                       sig < 0 ? "" : member.substring(sig),
                                       switch (m.group(1)) {
                                           case "public" -> ACC_PUBLIC;
                                           case "protected" -> ACC_PROTECTED;
                                           case "private" -> ACC_PRIVATE;
                                           default -> 0;
                                       },
                                       m.group(2) == null        ? 0
                                       : m.group(2).equals("-f") ? -1
                                                                 : 1,
                                       false, source));
                }
            } catch (RuntimeException e) {
                throw new IOException(source + " " + l, e);
            }
        }
        if (!header)
            throw new IOException("Empty access widener " + path);
    }
    public static Map<String, byte[]> prepare(Collection<JarFile> jars, ClassLoader domain, boolean client)
        throws IOException {
        var plan = new AccessTransforms();
        if (jars.stream().anyMatch(j -> j.getJarEntry("fabric.mod.json") != null)) {
            try (var origin = new JarFile(
                     new File(
                         AccessTransforms.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
                     true)) {
                plan.parse(origin, "META-INF/unity/content-registration.aw", true);
            } catch (java.net.URISyntaxException e) {
                throw new IOException(e);
            }
        }
        for (var jar : jars) {
            var fab = NestedArchives.json(jar, "fabric.mod.json");
            if (fab != null && fab.has("accessWidener"))
                plan.parse(jar, fab.get("accessWidener").getAsString(), true);
            var choice = PlatformChoice.read(jar);
            if (choice.multiple() && choice.kind() == mc.unity.adapter.Entry.Kind.FABRIC)
                continue;
            var at = new LinkedHashSet<String>();
            if (jar.getJarEntry("META-INF/accesstransformer.cfg") != null)
                at.add("META-INF/accesstransformer.cfg");
            if (jar.getManifest() != null &&
                (!choice.multiple() || choice.kind() == mc.unity.adapter.Entry.Kind.FORGE)) {
                var list = jar.getManifest().getMainAttributes().getValue("FMLAT");
                if (list != null && !list.isBlank())
                    for (String name : list.trim().split("\\s+"))
                        at.add("META-INF/" + name);
            }
            if (jar.getJarEntry("META-INF/neoforge.mods.toml") != null)
                at.addAll(FmlDescriptor.transformFiles(jar, "accessTransformers", "file"));
            for (String path : at)
                plan.parse(jar, path, false);
        }
        return plan.build(domain, client);
    }
    private static int rank(int v) {
        return v == ACC_PUBLIC ? 3 : v == ACC_PROTECTED ? 2 : v == 0 ? 1 : 0;
    }
    private static int change(int original, List<Rule> rules) {
        int requested = -1, fin = 0;
        for (var r : rules) {
            int v = r.visibility;
            if (v != -1 && rank(original & VIS) > rank(v))
                v = original & VIS;
            if (v != -1 && (requested < 0 || rank(v) > rank(requested)))
                requested = v;
            if (r.changeFinal != 0) {
                if (fin != 0 && fin != r.changeFinal)
                    throw new IllegalArgumentException("Conflicting final modifiers: " + r.source);
                fin = r.changeFinal;
            }
        }
        int flags = requested == -1 ? original : (original & ~VIS) | requested;
        return fin < 0 ? flags & ~ACC_FINAL : fin > 0 ? flags | ACC_FINAL : flags;
    }
    private Map<String, byte[]> build(ClassLoader domain, boolean client) throws IOException {
        var owners = new LinkedHashMap<String, List<Rule>>();
        var nested = new HashMap<String, List<Rule>>();
        for (var addition : interfaces.entrySet()) {
            owners.put(addition.getKey(), new ArrayList<>());
        }
        for (var r : rules) {
            owners.computeIfAbsent(r.owner, k -> new ArrayList<>()).add(r);
            if (r.target == Target.CLASS && r.owner.contains("$")) {
                nested.computeIfAbsent(r.owner, k -> new ArrayList<>()).add(r);
                String parent = r.owner;
                while (parent.contains("$")) {
                    parent = parent.substring(0, parent.lastIndexOf('$'));
                    owners.computeIfAbsent(parent, k -> new ArrayList<>());
                }
            }
        }
        var plan = new mc.unity.bootstrap.TransformPlan();
        var inputs = new LinkedHashMap<String, byte[]>();
        for (var e : owners.entrySet()) {
            String owner = e.getKey();
            var applies = e.getValue();
            if (!owner.matches("[A-Za-z_$][A-Za-z0-9_$]*(/[A-Za-z_$][A-Za-z0-9_$]*)*"))
                throw new IOException("Invalid JVM access owner " + owner);
            if (owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("jdk/") || owner.startsWith("sun/") || owner.startsWith("com/sun/") || owner.startsWith("mc/unity/") ||
                owner.startsWith("org/spongepowered/asm/"))
                throw new IOException("Unsupported access target " + owner);
            try (var in = domain.getResourceAsStream(owner + ".class")) {
                if (in == null) {
                    if (!client && (owner.startsWith("net/minecraft/client/") ||
                                    owner.startsWith("com/mojang/blaze3d/")))
                        continue;
                    throw new IOException("Missing access target " + owner + " " + applies);
                }
                for (var name : interfaces.getOrDefault(owner, Map.of()).keySet())
                    try (var faceBytes = domain.getResourceAsStream(name + ".class")) {
                        if (faceBytes == null ||
                            (new ClassReader(faceBytes).getAccess() & ACC_INTERFACE) == 0)
                            throw new IOException("Missing injected interface " + name);
                    }
                inputs.put(owner, in.readAllBytes());
                plan.addBytes(owner, 400, "Mod access rules", "visibility and final", (cf, input) -> {
                    var reader = new ClassReader(input);
                    if (!reader.getClassName().equals(owner))
                        throw new IOException("Wrong source for " + owner);
                    var writer = new ClassWriter(reader, 0);
                    var found = new HashSet<Rule>();
                    var virtual = new HashSet<String>();
                    reader.accept(new ClassVisitor(ASM9) {
                        public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                            if ((a & ACC_PRIVATE) != 0 && !n.startsWith("<") && (a & ACC_STATIC) == 0 &&
                                (change(a, matching(applies, Target.METHOD, n, d)) & ACC_PRIVATE) == 0)
                                virtual.add(n + d);
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    reader.accept(new ClassVisitor(ASM9, writer) {
                        boolean itf;
                        int apply(int flags, Target t, String n, String d) {
                            var m = matching(applies, t, n, d);
                            found.addAll(m);
                            return change(flags, m);
                        }
                        public void visit(int v, int a, String n, String sig, String sup, String[] faces) {
                            itf = (a & ACC_INTERFACE) != 0;
                            int adjusted = apply(a, Target.CLASS, "", "");
                            if ((adjusted & (ACC_PRIVATE | ACC_PROTECTED)) != 0)
                                adjusted =
                                    (adjusted & ~VIS) | ((adjusted & ACC_PROTECTED) != 0 ? ACC_PUBLIC : 0);
                            var merged = new LinkedHashSet<>(Arrays.asList(faces));
                            var added = new ArrayList<String>();
                            var additions = interfaces.getOrDefault(n, Map.of());
                            for (var extra : additions.keySet())
                                if (merged.add(extra))
                                    added.add(additions.get(extra));
                            if (!added.isEmpty() &&
                                (sig != null || added.stream().anyMatch(s -> s.contains("<")))) {
                                var signature = new org.objectweb.asm.signature.SignatureWriter();
                                if (sig != null)
                                    new org.objectweb.asm.signature.SignatureReader(sig).accept(signature);
                                else {
                                    new org.objectweb.asm.signature.SignatureReader("L" + sup + ";")
                                        .acceptType(signature.visitSuperclass());
                                    for (var face : faces)
                                        new org.objectweb.asm.signature.SignatureReader("L" + face + ";")
                                            .acceptType(signature.visitInterface());
                                }
                                for (var extra : added)
                                    new org.objectweb.asm.signature.SignatureReader(extra).acceptType(
                                        signature.visitInterface());
                                sig = signature.toString();
                            }
                            super.visit(v, adjusted, n, sig, sup, merged.toArray(String[] ::new));
                        }
                        public void visitInnerClass(String n, String outer, String inner, int a) {
                            super.visitInnerClass(n, outer, inner,
                                                  change(a, nested.getOrDefault(n, List.of())));
                        }
                        public void visitPermittedSubclass(String name) {
                            if (applies.stream().noneMatch(
                                    r -> r.target == Target.CLASS && r.changeFinal < 0))
                                super.visitPermittedSubclass(name);
                        }
                        public FieldVisitor visitField(int a, String n, String d, String s, Object v) {
                            return super.visitField(apply(a, Target.FIELD, n, d), n, d, s, v);
                        }
                        public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                            int adjusted = apply(a, Target.METHOD, n, d);
                            if (!itf && !n.startsWith("<") &&
                                (a & (ACC_PRIVATE | ACC_STATIC)) == ACC_PRIVATE &&
                                (adjusted & VIS) == ACC_PUBLIC &&
                                matching(applies, Target.METHOD, n, d)
                                    .stream()
                                    .anyMatch(r -> r.widen && r.changeFinal == 0) &&
                                matching(applies, Target.METHOD, n, d)
                                    .stream()
                                    .noneMatch(r -> r.changeFinal < 0 || !r.widen && r.changeFinal == 0 && r.visibility == ACC_PUBLIC))
                                adjusted |= ACC_FINAL;
                            var output = super.visitMethod(adjusted, n, d, s, ex);
                            if (virtual.isEmpty())
                                return output;
                            return new MethodVisitor(ASM9, output) {
                                public void visitMethodInsn(int opcode, String o, String name, String desc,
                                                            boolean face) {
                                    super.visitMethodInsn(opcode == INVOKESPECIAL && o.equals(owner) &&
                                                                  virtual.contains(name + desc)
                                                              ? (itf ? INVOKEINTERFACE : INVOKEVIRTUAL)
                                                              : opcode,
                                                          o, name, desc, face);
                                }
                                public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
                                                                   Object... args) {
                                    for (int j = 0; j < args.length; j++)
                                        if (args[j] instanceof Handle h && h.getTag() == H_INVOKESPECIAL &&
                                            h.getOwner().equals(owner) &&
                                            virtual.contains(h.getName() + h.getDesc()))
                                            args[j] = new Handle(itf ? H_INVOKEINTERFACE : H_INVOKEVIRTUAL,
                                                                 owner, h.getName(), h.getDesc(), itf);
                                    super.visitInvokeDynamicInsn(name, desc, bsm, args);
                                }
                            };
                        }
                    }, 0);
                    for (var r : applies)
                        if (!found.contains(r) && !r.name.equals("*"))
                            throw new IOException("Missing access member: " + r);
                    return writer.toByteArray();
                });
            }
        }
        try {
            return plan.buildFrom(inputs, java.lang.classfile.ClassFile.of());
        } catch (Exception e) {
            if (e instanceof IOException io)
                throw io;
            throw new IOException("Access transforms", e);
        }
    }
    private static List<Rule> matching(List<Rule> rules, Target t, String n, String d) {
        return rules.stream()
            .filter(
                r
                -> r.target == t && (r.name.equals(n) || r.name.equals("*")) &&
                       (r.desc.isEmpty() || r.desc.equals(d) || r.name.equals("*") && r.desc.equals("()")))
            .toList();
    }
}
