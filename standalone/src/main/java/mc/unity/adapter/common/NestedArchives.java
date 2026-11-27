package mc.unity.adapter.common;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
/**
 * Resolve an active mod graph before exposing its classes. Inner filenames are opaque; extraction is by
 * verified content digest.
 */
public final class NestedArchives implements AutoCloseable {
    private record
        Node(Path file, String id, String version, boolean mod, List<Edge> deps, List<Requirement> requires) {
    }
    private record Requirement(String id, String range, boolean fabric, boolean exclude) {}
    private record Edge(String key, String range, String version, Node offered) {}
    private record Selection(Node node, String version) {}
    private final Map<String, Node> scanned = new HashMap<>();
    private final Map<String, List<Selection>> choices = new TreeMap<>();
    private final List<Node> roots = new ArrayList<>();
    private final Map<String, Selection> topCoordinates = new HashMap<>();
    private final Map<String, Node> topIds = new HashMap<>();
    private final Set<Path> owned = new LinkedHashSet<>(), libraries = new HashSet<>();
    private final Map<Path, String> stamps = new HashMap<>();
    private final List<mc.unity.adapter.Entry> metadata = new ArrayList<>();
    private final Map<Path, Map<String, String>> provided = new HashMap<>();
    private int searchSteps;
    /** A selected umbrella may supply Fabric API classes only from its own declared module archives. */
    public Set<Path> fabricProviders() throws IOException {
        var umbrella = roots.stream().filter(n -> "fabric-api".equals(n.id)).findFirst();
        if (umbrella.isEmpty()) return Set.of();
        var bundle=umbrella.get();
        mc.unity.adapter.fabric.FabricVersions.check(bundle.version,">=0.152.0");
        var selectedById=new HashMap<String,Path>();
        metadata.forEach(e->selectedById.put(e.id(),e.file()));
        var providers=new LinkedHashSet<Path>();
        var ids=new HashSet<String>();
        try (var jar=new JarFile(bundle.file.toFile(),true)) {
            var json=json(jar,"fabric.mod.json");
            var depends=json.getAsJsonObject("depends");
            if (depends==null || !depends.has("minecraft") || !depends.has("fabricloader") || !json.has("jars"))
                throw new IOException("Incomplete Fabric API umbrella metadata");
            var declarations=new HashSet<String>();
            for (var member : json.getAsJsonArray("jars")) {
                String name=member.getAsJsonObject().get("file").getAsString();
                if (!name.startsWith("META-INF/jars/") || !name.endsWith(".jar") || !declarations.add(name))
                    throw new IOException("Invalid or duplicate Fabric API module: "+name);
            }
        }
        for (var edge:bundle.deps) {
            Node child=edge.offered;
            if (child.id==null || !child.id.matches("fabric-[a-z0-9-]+") || !ids.add(child.id))
                throw new IOException("Invalid Fabric API module identity: "+child.id);
            try (var jar=new JarFile(child.file.toFile(),true)) {
                var meta=json(jar,"fabric.mod.json");
                if (meta==null || !meta.has("custom") || !meta.getAsJsonObject("custom").has("fabric-api:module-lifecycle"))
                    throw new IOException("Missing Fabric API module declaration: "+child.id);
            }
            Path actual=selectedById.get(child.id);
            if (actual==null || !actual.equals(child.file) && !hash(actual).equals(hash(child.file)))
                throw new IOException("Conflicting provider for bundled Fabric module "+child.id);
            providers.add(actual);
        }
        if (!ids.containsAll(Set.of("fabric-api-base","fabric-lifecycle-events-v1","fabric-networking-api-v1")))
            throw new IOException("Incomplete Fabric API module graph");
        return Set.copyOf(providers);
    }
    public boolean officialFabricApi() throws IOException { return !fabricProviders().isEmpty(); }
    public boolean puzzles(Path file) {
        var version=provided.getOrDefault(file,Map.of()).get("puzzleslib");
        return version!=null && version.matches("26\\.2\\.[0-9]+(?:[-+].*)?");
    }
    public int searchSteps() {
        return searchSteps;
    }
    private final Map<Path, mc.unity.adapter.Entry.Kind> kinds = new HashMap<>();
    private final Map<Path, String> origins = new HashMap<>();
    public List<mc.unity.adapter.Entry> metadata() {
        return List.copyOf(metadata);
    }
    public Map<Path, String> origins() {
        return Map.copyOf(origins);
    }
    private final Map<Path, Path> containers = new LinkedHashMap<>();
    public Map<Path, Path> containers() {
        return Map.copyOf(containers);
    }
    private final List<Path> selected = new ArrayList<>();
    private Path tmp;
    private long remaining = 512L << 20;
    private final boolean client;
    private NestedArchives(boolean client) {
        this.client = client;
    }
    public static NestedArchives resolve(Path mods, boolean client) throws Exception {
        var result = new NestedArchives(client);
        try (var stream = Files.list(Files.createDirectories(mods))) {
            for (var f :
                 stream.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                     .sorted()
                     .toList()) {
                var node = result.scan(f, null, 0);
                if (node == null)
                    continue;
                result.roots.add(node);
                result.indexCoordinates(node);
                for (var id : result.provided.getOrDefault(node.file, Map.of()).keySet())
                    if (result.topIds.putIfAbsent(id, node) != null)
                        throw new IOException("Duplicate top-level mod " + id);
            }
            result.choices.values().forEach(list -> list.sort((a, b) -> compare(b.version, a.version)));
            var solution = result.solve(new TreeMap<>());
            if (solution == null)
                throw new IOException("Incompatible nested artifact requirements: " +
                                      result.choices.keySet());
            var all = new LinkedHashSet<Node>(result.roots);
            all.addAll(solution.values().stream().map(Selection::node).toList());
            for (var n : all) {
                result.selected.add(n.file);
                for (var edge : n.deps) {
                    var member = solution.get(edge.key).node;
                    if (!result.roots.contains(member))
                        result.containers.putIfAbsent(member.file, n.file);
                }
                if (n.mod)
                    result.provided.get(n.file).forEach(
                        (id, version)
                            -> result.metadata.add(new mc.unity.adapter.Entry(
                                id, version, List.of(), result.kinds.get(n.file), n.file, List.of())));
                if (!n.mod && !result.roots.contains(n))
                    result.libraries.add(n.file);
                var fingerprint = new TreeMap<String, String>();
                result.trace(n, solution, fingerprint, new HashSet<>());
                if (!fingerprint.isEmpty())
                    result.stamps.put(n.file, fingerprint.toString());
            }
            return result;
        } catch (Exception | Error ex) {
            try {
                result.close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }
    private void indexCoordinates(Node node) throws IOException {
        if (!node.mod)
            return;
        try (var jar = new JarFile(node.file.toFile(), true)) {
            var descriptors = jar.stream()
                                  .filter(e
                                          -> e.getName().startsWith("META-INF/maven/") &&
                                                 e.getName().endsWith("/pom.properties"))
                                  .toList();
            if (descriptors.size() != 1)
                return;
            for (var member : descriptors) {
                var parts = member.getName().split("/");
                if (parts.length != 5 || !parts[0].equals("META-INF") || !parts[1].equals("maven") ||
                    !parts[4].equals("pom.properties"))
                    continue;
                var metadata = new Properties();
                try (var input = jar.getInputStream(member)) {
                    metadata.load(input);
                }
                if (!parts[2].equals(metadata.getProperty("groupId")) ||
                    !parts[3].equals(metadata.getProperty("artifactId")))
                    continue;
                // Shaded JARs can include Maven metadata for many unrelated libraries. Only the
                // archive's own unique coordinate may override a pure embedded library.

                var version = metadata.getProperty("version");
                if (version == null || version.isBlank())
                    continue;
                var key = "maven:" + parts[2] + ":" + parts[3];
                if (topCoordinates.putIfAbsent(key, new Selection(node, version)) != null)
                    throw new IOException("Duplicate top-level Maven coordinate " + key);
            }
        }
    }
    private Map<String, Selection> solve(TreeMap<String, Selection> selected) throws IOException {
        if (++searchSteps > 100000)
            throw new IOException("Nested version graph too complex");
        var reached = new HashSet<Path>();
        var queue = new ArrayDeque<>(roots);
        var requested = new TreeMap<String, List<Edge>>();
        var constraints = new HashMap<String, List<Requirement>>();
        while (!queue.isEmpty()) {
            var node = queue.removeFirst();
            if (!reached.add(node.file))
                continue;
            node.requires.forEach(
                req -> constraints.computeIfAbsent(req.id, k -> new ArrayList<>()).add(req));
            for (var edge : node.deps) {
                requested.computeIfAbsent(edge.key, k -> new ArrayList<>()).add(edge);
                var choice = selected.get(edge.key);
                if (choice != null)
                    queue.add(choice.node);
            }
        }
        String nextKey = null;
        List<Selection> best = null;
        for (var req : requested.entrySet()) {
            var key = req.getKey();
            var edges = req.getValue();
            var root = override(key, edges);
            var chosen = selected.get(key);
            if (chosen != null) {
                if (root != null && !root.equals(chosen) || !allowed(chosen, edges, constraints))
                    return null;
                continue;
            }
            var options = new ArrayList<Selection>();
            for (var option : root == null ? choices.get(key) : List.of(root))
                if (allowed(option, edges, constraints))
                    options.add(option);
            if (options.isEmpty())
                return null;
            if (best == null || options.size() < best.size()) {
                best = options;
                nextKey = key;
            }
        }
        if (best != null) {
            for (var option : best) {
                selected.put(nextKey, option);
                try {
                    var solution = solve(selected);
                    if (solution != null)
                        return solution;
                } finally {
                    selected.remove(nextKey);
                }
            }
            return null;
        }
        for (var req : requested.entrySet()) {
            var chosen = selected.get(req.getKey());
            var edges = req.getValue();
            if (!roots.contains(chosen.node) &&
                edges.stream().noneMatch(e -> e.offered.file.equals(chosen.node.file)))
                return null;
            if (edges.stream()
                    .filter(e -> compare(e.version, chosen.version) == 0)
                    .map(e -> e.offered.file)
                    .distinct()
                    .limit(2)
                    .count() > 1)
                throw new IOException("Different bytes for " + req.getKey() + " " + chosen.version);
        }
        return new TreeMap<>(selected);
    }
    private boolean allowed(Selection option, List<Edge> edges, Map<String, List<Requirement>> requirements) {
        for (var edge : edges)
            if (!accepts(option.version, edge.range))
                return false;
        for (var member : provided.getOrDefault(option.node.file, Map.of()).entrySet()) {
            for (var req : requirements.getOrDefault(member.getKey(), List.of())) {
                try {
                    if (req.fabric)
                        mc.unity.adapter.fabric.FabricVersions.check(member.getValue(), req.range);
                    else
                        if (FmlDescriptor.inRange(member.getValue(), req.range) == req.exclude) return false;
                } catch (IOException excluded) {
                    return false;
                }
            }
        }
        return true;
    }
    private Selection override(String key, List<Edge> edges) throws IOException {
        var coordinate = topCoordinates.get(key);
        var result = coordinate;
        for (var edge : edges) {
            // A plain Maven library may have no platform ID.
            for (var id : provided.getOrDefault(edge.offered.file, Map.of()).keySet()) {
                var root = topIds.get(id);
                if (root == null)
                    continue;
                if (result != null && result.node != root)
                    throw new IOException("Ambiguous top-level providers for " + key);
                if (result == null)
                    result = new Selection(root, provided.get(root.file).get(id));
            }
        }
        return result;
    }
    private static int compare(String a, String b) {
        if (a.equals(b))
            return 0;
        try {
            return FmlDescriptor.compare(a, b);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unsupported nested versions " + a + ", " + b, e);
        }
    }
    private static boolean accepts(String version, String range) {
        if (range == null || range.isBlank() || range.equals("*"))
            return true;
        try {
            FmlDescriptor.checkRange(version, range);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    private void trace(Node n, Map<String, Selection> selected, Map<String, String> result, Set<Path> path)
        throws IOException {
        if (!path.add(n.file))
            throw new IOException("Nested dependency cycle " + n.id);
        for (var edge : n.deps) {
            var s = selected.get(edge.key);
            if (path.contains(s.node.file))
                throw new IOException("Nested dependency cycle at " + edge.key);
            if (result.putIfAbsent(edge.key, s.version + "@" + hash(s.node.file)) == null)
                trace(s.node, selected, result, path);
        }
        path.remove(n.file);
    }
    private static String hash(Path file) throws IOException {
        try {
            return mc.unity.bootstrap.VanillaBundle.hash(file);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
    private Node scan(Path f, String sha, int depth) throws Exception {
        if (depth > 16)
            throw new IOException("Nested archive depth exceeds 16");
        if (sha != null && scanned.containsKey(sha))
            return scanned.get(sha);
        try (var jar = new JarFile(f.toFile(), true, JarFile.OPEN_READ, Runtime.version())) {
            if (MixinDeclarations.read(jar, client) == null)
                return null;
            JsonObject fabric = json(jar, "fabric.mod.json");
            boolean isMod = fabric != null || jar.getEntry("META-INF/mods.toml") != null ||
                            jar.getEntry("META-INF/neoforge.mods.toml") != null;
            var edges = new ArrayList<Edge>();
            String id = fabric == null ? null : fabric.get("id").getAsString(),
                   version = fabric == null ? null : fabric.get("version").getAsString();
            var requires = new ArrayList<Requirement>();
            if (fabric == null && isMod) {
                boolean neo = jar.getEntry("META-INF/neoforge.mods.toml") != null;
                var metas = FmlDescriptor.mods(jar, client ? "CLIENT" : "SERVER", neo);
                id = metas.getFirst().id();
                version = metas.getFirst().version();
                var ids = new LinkedHashMap<String, String>();
                metas.forEach(m -> ids.put(m.id(), m.version()));
                provided.put(f, Collections.unmodifiableMap(ids));
                for (var meta : metas)
                    for (var dep : meta.dependencies())
                        if (!dep.kind().equals("discouraged") && (dep.negative() || dep.range() != null && !dep.range().isBlank()))
                            requires.add(new Requirement(dep.id(), dep.range(), false, dep.negative()));
            } else if (fabric != null) {
                provided.put(f, Map.of(id, version));
            }
            if (fabric != null && fabric.has("jars"))
                for (var child : fabric.getAsJsonArray("jars")) {
                    var inner = extract(jar, child.getAsJsonObject().get("file").getAsString(), depth);
                    if (inner == null)
                        continue;
                    if (inner.id == null || !inner.mod)
                        throw new IOException("Fabric nested JAR requires fabric.mod.json: " + f);
                    add(edges, "mod:" + inner.id, "*", inner.version, inner);
                }
            var jij = json(jar, "META-INF/jarjar/metadata.json");
            if (jij != null && !(fabric != null && PlatformChoice.read(jar).multiple()))
                for (var value : jij.getAsJsonArray("jars")) {
                    var child = value.getAsJsonObject();
                    var identifier = child.getAsJsonObject("identifier");
                    var v = child.getAsJsonObject("version");
                    if (child.has("isObfuscated") && child.get("isObfuscated").getAsBoolean())
                        throw new IOException("Obfuscated nested archive requires remapping");
                    var inner = extract(jar, child.get("path").getAsString(), depth);
                    if (inner != null)
                        add(edges,
                            "maven:" + identifier.get("group").getAsString() + ":" +
                                identifier.get("artifact").getAsString(),
                            v.get("range").getAsString(), v.get("artifactVersion").getAsString(), inner);
                }
            if (fabric != null && fabric.has("depends"))
                for (var dep : fabric.getAsJsonObject("depends").entrySet()) {
                    var alternatives = new ArrayList<String>();
                    if (dep.getValue().isJsonArray())
                        for (var v : dep.getValue().getAsJsonArray())
                            alternatives.add(v.getAsString());
                    else
                        alternatives.add(dep.getValue().getAsString());
                    requires.add(new Requirement(dep.getKey(), String.join(" || ", alternatives), true, false));
                }
            if (isMod)
                kinds.put(f, fabric != null ? mc.unity.adapter.Entry.Kind.FABRIC
                             : jar.getEntry("META-INF/neoforge.mods.toml") != null
                                 ? mc.unity.adapter.Entry.Kind.NEOFORGE
                                 : mc.unity.adapter.Entry.Kind.FORGE);
            var n = new Node(f, id, version, isMod, List.copyOf(edges), List.copyOf(requires));
            if (sha != null)
                scanned.put(sha, n);
            return n;
        }
    }
    private void add(List<Edge> edges, String key, String range, String version, Node n) {
        var e = new Edge(key, range, version, n);
        edges.add(e);
        var list = choices.computeIfAbsent(key, k -> new ArrayList<>());
        var choice = new Selection(n, version);
        if (!list.contains(choice))
            list.add(choice);
    }
    public static JsonObject json(JarFile jar, String name) throws IOException {
        var member = jar.getJarEntry(name);
        if (member == null)
            return null;
        try (var reader =
                 new InputStreamReader(jar.getInputStream(member), java.nio.charset.StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private Node extract(JarFile jar, String path, int depth) throws Exception {
        if (path.startsWith("/") || path.contains("\\") || path.contains(":") ||
            Arrays.stream(path.split("/", -1))
                .anyMatch(s -> s.isEmpty() || s.equals("..") || s.equals(".")) ||
            !path.endsWith(".jar"))
            throw new IOException("Invalid nested path: " + path);
        var entry = jar.getJarEntry(path);
        if (entry == null || entry.isDirectory())
            throw new IOException("Missing nested JAR " + path + " in " + jar.getName());
        if (tmp == null)
            tmp = Files.createTempDirectory("unity-nested-");
        Path part = Files.createTempFile(tmp, "extract-", ".part");
        owned.add(part);
        var digest = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (var in = jar.getInputStream(entry); var out = Files.newOutputStream(part)) {
            byte[] buf = new byte[65536];
            for (int count; (count = in.read(buf)) != -1;) {
                size += count;
                remaining -= count;
                if (size > (128L << 20) || remaining < 0)
                    throw new IOException("Nested archive size limit");
                digest.update(buf, 0, count);
                out.write(buf, 0, count);
            }
        }
        String hex = HexFormat.of().formatHex(digest.digest());
        var dest = tmp.resolve(hex + ".jar");
        if (Files.exists(dest))
            Files.delete(part);
        else {
            Files.move(part, dest);
            owned.add(dest);
        }
        owned.remove(part);
        var parent = Path.of(jar.getName());
        origins.putIfAbsent(dest, origins.getOrDefault(parent, parent.getFileName().toString()) + "!" + path);
        return scan(dest, hex, depth + 1);
    }
    public List<Path> files() {
        return List.copyOf(selected);
    }
    public Set<Path> plainLibraries() {
        return Set.copyOf(libraries);
    }
    public Map<Path, String> fingerprints() {
        return Map.copyOf(stamps);
    }
    public void close() throws IOException {
        IOException failure = null;
        for (var file : List.copyOf(owned))
            try {
                Files.deleteIfExists(file);
                owned.remove(file);
            } catch (IOException e) {
                if (failure == null)
                    failure = e;
                else
                    failure.addSuppressed(e);
            }
        if (failure != null)
            throw failure;
        if (tmp != null) {
            Files.deleteIfExists(tmp);
            tmp = null;
        }
    }
}
