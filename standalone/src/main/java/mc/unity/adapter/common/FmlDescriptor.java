package mc.unity.adapter.common;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;

/** Decode TOML once per open archive; apply javafml constraints only to fields consumed by Unity. */
public final class FmlDescriptor {
    private static final Map<JarFile, UnmodifiableConfig> documents = new WeakHashMap<>();
    public record Dependency(String id, String range, String kind, String ordering) {
        public Dependency(String id,String range,boolean required,String ordering) {
            this(id,range,required ? "required" : "optional",ordering);
        }
        public boolean required() { return kind.equals("required"); }
        public boolean negative() { return kind.equals("incompatible") || kind.equals("discouraged"); }
        public boolean accepts(String version) throws IOException {
            boolean match = inRange(version, range);
            return kind.equals("incompatible") ? !match : kind.equals("discouraged") || match;
        }
        public void validate(String owner,String version) throws IOException {
            if (!accepts(version)) throw new IOException(owner+" "+kind+" dependency "+id+" "+range+"; installed "+version);
            if (kind.equals("discouraged") && inRange(version,range))
                System.getLogger("mc.unity").log(System.Logger.Level.WARNING,owner+" discourages "+id+" "+version);
        }
    }
    public record
        Meta(String id, String version, List<Dependency> dependencies, String title, String description) {}
    private static UnmodifiableConfig parse(Reader in) throws IOException {
        try {
            return new TomlParser().parse(in);
        } catch (com.electronwill.nightconfig.core.io.ParsingException e) {
            throw new IOException("Invalid FML TOML: " + e.getMessage(), e);
        }
    }
    static UnmodifiableConfig document(JarFile jar, boolean neo) throws IOException {
        // The same JAR may contain both descriptors. Cache only unambiguous platform documents.
        boolean cacheable =
            jar.getJarEntry(neo ? "META-INF/mods.toml" : "META-INF/neoforge.mods.toml") == null;
        if (cacheable)
            synchronized (documents) {
                var parsed = documents.get(jar);
                if (parsed != null)
                    return parsed;
            }
        String name = neo ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml";
        var entry = jar.getJarEntry(name);
        if (entry == null)
            throw new FileNotFoundException(jar.getName() + "!/" + name);
        try (var in = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            var parsed = parse(in);
            if (cacheable)
                synchronized (documents) {
                    documents.put(jar, parsed);
                }
            return parsed;
        }
    }
    public static String kotlinRange(JarFile jar,boolean neo)throws IOException{
        if(!neo)return null;
        var doc=document(jar,true);
        return "kotlinforforge".equals(string(doc,"modLoader","javafml"))?string(doc,"loaderVersion","[0,)"):null;
    }
    public static boolean clientSideOnly(JarFile jar) throws IOException {
        Object value=document(jar,false).getRaw("clientSideOnly");
        if(value==null)return false;
        if(value instanceof Boolean flag)return flag;
        throw new IOException("Expected a boolean clientSideOnly in "+jar.getName());
    }
    static List<UnmodifiableConfig> tables(UnmodifiableConfig doc, String key) throws IOException {
        Object raw = doc.getRaw(List.of(key));
        if (raw == null)
            return List.of();
        if (!(raw instanceof List<?> list))
            throw new IOException("Expected array of tables: " + key);
        var result = new ArrayList<UnmodifiableConfig>();
        for (var element : list) {
            if (!(element instanceof UnmodifiableConfig table))
                throw new IOException("Expected a table in " + key);
            result.add(table);
        }
        return result;
    }
    static String string(UnmodifiableConfig doc, String key, String fallback) throws IOException {
        Object value = doc.getRaw(List.of(key));
        if (value == null)
            return fallback;
        if (value instanceof String s)
            return s;
        throw new IOException("Expected TOML string for " + key);
    }
    static List<String> transformFiles(JarFile jar, String section, String property) throws IOException {
        var files = new ArrayList<String>();
        for (var row : tables(document(jar, true), section)) {
            for (var key : row.valueMap().keySet()) {
                if (key.equals(property))
                    continue;
                if (key.equals("requiredMods") && row.getRaw(List.of(key)) instanceof List<?> list &&
                    list.isEmpty())
                    continue;
                throw new IOException("Unsupported " + section + " property " + key + " in " + jar.getName());
            }
            var file = string(row, property, null);
            if (file == null || file.isBlank())
                throw new IOException("Missing " + section + "." + property);
            files.add(file);
        }
        return List.copyOf(files);
    }
    public static Meta read(BufferedReader in, String side) throws IOException {
        return read(in, side, false);
    }
    public static Meta read(BufferedReader in, String side, boolean neo) throws IOException {
        var mods = mods(parse(in), side, neo, null);
        if (mods.size() != 1)
            throw new IOException("Expected exactly one mod for this metadata reader");
        return mods.getFirst();
    }
    public static List<Meta> mods(JarFile jar, String side, boolean neo) throws IOException {
        return mods(document(jar, neo), side, neo, implementationVersion(jar));
    }
    static String implementationVersion(JarFile jar) throws IOException {
        var manifest = jar.getManifest();
        return manifest == null ? null : manifest.getMainAttributes().getValue("Implementation-Version");
    }
    static String version(UnmodifiableConfig table, String implementation) throws IOException {
        String value = string(table, "version", "");
        return value.equals("${file.jarVersion}") && implementation != null ? implementation : value;
    }
    private static List<Meta> mods(UnmodifiableConfig doc, String side, boolean neo, String jarVersion)
        throws IOException {
        var language=string(doc,"modLoader",neo?"javafml":"");
        boolean kotlin=neo && language.equals("kotlinforforge");
        if(!kotlin && !language.equals("javafml"))throw new IOException("Unsupported FML language "+language);
        if(!kotlin)checkRange(neo?"11.0.16":"65.1.5",string(doc,"loaderVersion",null));
        Object raw = doc.getRaw("dependencies");
        if (raw != null && !(raw instanceof UnmodifiableConfig))
            throw new IOException("Expected dependencies table");
        var dependencies = (UnmodifiableConfig)raw;
        var ids = new LinkedHashSet<String>();
        var result = new ArrayList<Meta>();
        for (var table : tables(doc, "mods")) {
            String id = string(table, "modId", ""), version = version(table, jarVersion);
            if (!id.matches("[a-z][a-z0-9_]{1,63}") || !ids.add(id))
                throw new IOException("Invalid or duplicate mod ID " + id);
            var deps = new ArrayList<Dependency>();
            if(kotlin && !id.equals("kotlinforforge"))deps.add(new Dependency("kotlinforforge",string(doc,"loaderVersion",null),true,"AFTER"));
            if (dependencies != null)
                for (var d : tables(dependencies, id)) {
                    String targetSide = string(d, "side", "BOTH");
                    if (!Set.of("CLIENT", "SERVER", "BOTH").contains(targetSide))
                        throw new IOException("Unknown dependency side " + targetSide);
                    if (!targetSide.equals("BOTH") && !targetSide.equals(side))
                        continue;
                    String dep = string(d, "modId", ""),
                           kind = string(d, "type", "required").toLowerCase(Locale.ROOT);
                    if (!dep.matches("[a-z][a-z0-9_-]{1,63}"))
                        throw new IOException("Invalid dependency ID " + dep + " in " + id);
                    if (!(neo ? Set.of("required","optional","incompatible","discouraged") : Set.of("required","optional")).contains(kind))
                        throw new IOException("Unsupported dependency type in " + id);
                    Object mandatory = d.getRaw("mandatory");
                    if (mandatory != null && !(mandatory instanceof Boolean))
                        throw new IOException("Expected TOML boolean for mandatory in " + id);
                    String range = string(d, "versionRange", null), order = string(d, "ordering", "NONE");
                    var dependency = new Dependency(dep,range,kind.equals("required") && Boolean.FALSE.equals(mandatory) ? "optional" : kind,order);
                    if (neo && dep.equals("neoforge")) dependency.validate(id,"26.2.0.93");
                    else if (!neo && dep.equals("forge")) dependency.validate(id,"65.1.5");
                    else if (dep.equals("minecraft")) dependency.validate(id,"26.2");
                    else deps.add(dependency);
                }
            result.add(new Meta(id, version, List.copyOf(deps), string(table, "displayName", id),
                                string(table, "description", "")));
        }
        if (ids.isEmpty())
            throw new IOException("FML descriptor has no [[mods]] entries");
        if (dependencies != null && !ids.containsAll(dependencies.valueMap().keySet()))
            throw new IOException("Unknown dependency owner in " + dependencies.valueMap().keySet());
        return List.copyOf(result);
    }
    private static final Pattern VERSION = Pattern.compile(
        "([0-9]+(?:\\.[0-9]+)*)(?:-([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*))?(?:\\+[A-Za-z0-9-]+(?:"
        + "\\.[A-Za-z0-9-]+)*)?");
    public static int compare(String left, String right) throws IOException {
        var a = VERSION.matcher(left);
        var b = VERSION.matcher(right);
        if (!a.matches() || !b.matches())
            throw new IOException("Unsupported version: " + left + " or " + right);
        var l = a.group(1).split("\\.");
        var r = b.group(1).split("\\.");
        for (int i = 0; i < Math.max(l.length, r.length); i++) {
            int c = new java.math.BigInteger(i < l.length ? l[i] : "0")
                        .compareTo(new java.math.BigInteger(i < r.length ? r[i] : "0"));
            if (c != 0)
                return c;
        }
        if (a.group(2) == null || b.group(2) == null)
            return Boolean.compare(a.group(2) == null, b.group(2) == null);
        l = a.group(2).split("\\.");
        r = b.group(2).split("\\.");
        for (int i = 0; i < Math.min(l.length, r.length); i++) {
            boolean ln = l[i].matches("[0-9]+"), rn = r[i].matches("[0-9]+");
            int c = ln && rn ? new java.math.BigInteger(l[i]).compareTo(new java.math.BigInteger(r[i]))
                    : ln     ? -1
                    : rn     ? 1
                             : l[i].compareTo(r[i]);
            if (c != 0)
                return c;
        }
        return Integer.compare(l.length, r.length);
    }
    public static void checkRange(String v, String range) throws IOException {
        if (!inRange(v, range)) throw new IOException("Version " + v + " does not satisfy " + range);
    }
    public static boolean inRange(String v, String range) throws IOException {
        if (range == null || range.isBlank() || range.equals("*")) return true;
        String text = range.strip();
        boolean match = false;
        for (int start = 0; start < text.length();) {
            char left = text.charAt(start);
            if (left != '(' && left != '[') throw new IOException("Invalid version range " + range);
            int end = start + 1;
            while (end < text.length() && text.charAt(end) != ')' && text.charAt(end) != ']') end++;
            if (end == text.length()) throw new IOException("Incomplete version range " + range);
            char right = text.charAt(end);
            String[] bounds = text.substring(start+1,end).split(",",-1);
            if (bounds.length == 3 && bounds[2].isBlank() && !bounds[1].isBlank() && right == ')') {
                bounds = new String[]{bounds[0],bounds[1]}; right = ']';
            }
            if (bounds.length == 1 && left == '[' && right == ']') match |= compare(v,bounds[0].strip()) == 0;
            else if (bounds.length == 2) {
                String low=bounds[0].strip(), high=bounds[1].strip();
                boolean above=low.isEmpty() || compare(v,low) >= (left=='[' ? 0 : 1);
                boolean below=high.isEmpty() || compare(v,high) <= (right==']' ? 0 : -1);
                match |= above && below;
            } else throw new IOException("Invalid version range " + range);
            start = end + 1;
            if (start == text.length()) break;
            if (text.charAt(start++) != ',') throw new IOException("Expected union in "+range);
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
            if (start == text.length()) throw new IOException("Missing interval in "+range);
        }
        return match;
    }
}
