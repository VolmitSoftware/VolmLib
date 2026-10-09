package art.arcane.volmit.packaging;

import org.gradle.api.GradleException;

import javax.lang.model.SourceVersion;
import java.util.List;

final class NativeImplementationPackages {
    static final List<String> DEFAULTS = List.of("art.arcane.volmlib.nativelib");

    private NativeImplementationPackages() {
    }

    static List<String> validate(List<String> packages) {
        for (String name : packages) {
            if (name == null || !SourceVersion.isName(name) || name.split("\\.").length < 3) {
                throw new GradleException("Native implementation packages must be explicit qualified package names with at least three segments: " + name);
            }
        }
        return List.copyOf(packages);
    }

    static boolean contains(String packageName, List<String> implementations) {
        for (String implementation : implementations) {
            if (packageName.equals(implementation) || packageName.startsWith(implementation + ".")) {
                return true;
            }
        }
        return false;
    }
}
