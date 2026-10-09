package art.arcane.volmit.packaging;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;

public class PluginPackagingExtension {
    private final NamedDomainObjectContainer<PackagingArtifact> artifacts;
    private final LoggingPolicySpec loggingPolicy;
    private final ListProperty<String> nativeImplementationPackages;

    public PluginPackagingExtension(Project project) {
        artifacts = project.getObjects().domainObjectContainer(PackagingArtifact.class);
        loggingPolicy = new LoggingPolicySpec(project.file("logging-policy-allowlist.txt"));
        nativeImplementationPackages = project.getObjects().listProperty(String.class);
        nativeImplementationPackages.set(NativeImplementationPackages.DEFAULTS);
    }

    public NamedDomainObjectContainer<PackagingArtifact> getArtifacts() {
        return artifacts;
    }

    public void artifacts(Action<? super NamedDomainObjectContainer<PackagingArtifact>> action) {
        action.execute(artifacts);
    }

    public LoggingPolicySpec getLoggingPolicy() {
        return loggingPolicy;
    }

    public ListProperty<String> getNativeImplementationPackages() {
        return nativeImplementationPackages;
    }

    public void loggingPolicy(Action<? super LoggingPolicySpec> action) {
        action.execute(loggingPolicy);
    }
}
