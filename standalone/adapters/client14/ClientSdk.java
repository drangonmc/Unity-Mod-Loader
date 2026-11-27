import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.nio.file.*;
import java.util.jar.JarFile;

/** Present the same public screen factory to javac as Unity's runtime access transform. */
class ClientSdk {
    public static void main(String[] args) throws Exception {
        String outer = "net/minecraft/client/gui/screens/MenuScreens";
        String factory = outer + "$ScreenConstructor";
        var cf = ClassFile.of();
        try (var jar = new JarFile(args[0])) {
            for (var name : java.util.List.of(outer, factory)) {
                var target = Path.of(args[1], name + ".class");
                Files.createDirectories(target.getParent());
                var model = cf.parse(jar.getInputStream(jar.getJarEntry(name + ".class")).readAllBytes());
                Files.write(target, cf.transformClass(model, (b,e) -> {
                    if (e instanceof InnerClassesAttribute inners)
                        b.with(InnerClassesAttribute.of(inners.classes().stream().map(i ->
                            i.innerClass().asInternalName().equals(factory)
                             ? InnerClassInfo.of(i.innerClass(),i.outerClass(),i.innerName(),i.flagsMask() | ClassFile.ACC_PUBLIC) : i).toList()));
                    else if (e instanceof AccessFlags flags && name.equals(factory)) b.withFlags(flags.flagsMask() | ClassFile.ACC_PUBLIC);
                    else b.with(e);
                }));
            }
        }
    }
}
