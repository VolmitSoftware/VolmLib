package art.arcane.volmit.packaging;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;

public class PluginPackagingExtension {
    private final NamedDomainObjectContainer<PackagingArtifact> artifacts;
    private final LoggingPolicySpec loggingPolicy;

    public PluginPackagingExtension(Project project) {
        artifacts = project.getObjects().domainObjectContainer(PackagingArtifact.class);
        loggingPolicy = new LoggingPolicySpec(project.file("logging-policy-allowlist.txt"));
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

    public void loggingPolicy(Action<? super LoggingPolicySpec> action) {
        action.execute(loggingPolicy);
    }
}
