package art.arcane.volmit.packaging;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;

public class PluginPackagingExtension {
    private final NamedDomainObjectContainer<PackagingArtifact> artifacts;

    public PluginPackagingExtension(Project project) {
        artifacts = project.getObjects().domainObjectContainer(PackagingArtifact.class);
    }

    public NamedDomainObjectContainer<PackagingArtifact> getArtifacts() {
        return artifacts;
    }

    public void artifacts(Action<? super NamedDomainObjectContainer<PackagingArtifact>> action) {
        action.execute(artifacts);
    }
}
