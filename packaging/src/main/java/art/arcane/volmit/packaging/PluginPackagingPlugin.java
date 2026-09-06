package art.arcane.volmit.packaging;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PluginPackagingPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        PluginPackagingExtension extension = project.getExtensions().create(
                "pluginPackaging", PluginPackagingExtension.class, project);
        project.allprojects(child -> child.getTasks().withType(JavaCompile.class).configureEach(task -> {
            if (!task.getName().toLowerCase(Locale.ROOT).contains("test")) {
                task.getOptions().getDebugOptions().setDebugLevel("source,lines");
                if (!task.getOptions().getCompilerArgs().contains("-parameters")) {
                    task.getOptions().getCompilerArgs().add("-parameters");
                }
            }
        }));
        TaskProvider<Task> verify = project.getTasks().register("verifyPluginJars", task -> {
            task.setGroup("verification");
            task.setDescription("Verifies compact plugin artifacts and size budgets.");
        });
        project.getTasks().matching(task -> task.getName().equals("check"))
                .configureEach(task -> task.dependsOn(verify));
        project.afterEvaluate(ignored -> {
            for (PackagingArtifact artifact : extension.getArtifacts()) {
                configureArtifact(project, artifact, verify);
            }
        });
    }

    private void configureArtifact(Project project, PackagingArtifact policy, TaskProvider<Task> verify) {
        if (policy.getMaximumBytes() <= 0) {
            throw new GradleException("A positive jar budget is required for " + policy.getName());
        }
        TaskProvider<AbstractArchiveTask> producer = project.getTasks().named(policy.getTaskName(), AbstractArchiveTask.class);
        File report = project.getLayout().getBuildDirectory().file(
                "reports/packaging/" + policy.getName() + ".json").get().getAsFile();
        producer.configure(task -> {
            task.getOutputs().file(report);
            task.getInputs().property("packaging.maximumBytes", policy.getMaximumBytes());
            task.getInputs().property("packaging.stripDirectories", policy.isStripDirectories());
            task.getInputs().property("packaging.stripLocalVariables", policy.isStripLocalVariables());
            task.getInputs().property("packaging.releaseCompression", policy.isReleaseCompression());
            task.getInputs().property("packaging.prunePrefixes", policy.getPrunePrefixes());
            task.getInputs().property("packaging.keepPrefixes", policy.getKeepPrefixes());
            task.getInputs().property("packaging.requiredEntries", policy.getRequiredEntries());
            task.getInputs().property("packaging.forbiddenPrefixes", policy.getForbiddenPrefixes());
            task.doLast(ignored -> optimize(task, policy, report));
        });
        TaskProvider<Task> check = project.getTasks().register("verify" + capitalize(policy.getName()) + "Packaging", task -> {
            task.setGroup("verification");
            task.dependsOn(producer);
            task.getInputs().file(producer.flatMap(AbstractArchiveTask::getArchiveFile));
            task.doLast(ignored -> validate(producer.get().getArchiveFile().get().getAsFile(), policy));
        });
        verify.configure(task -> task.dependsOn(check));
        producer.configure(task -> task.finalizedBy(check));
    }

    private void optimize(AbstractArchiveTask task, PackagingArtifact policy, File report) {
        File artifact = task.getArchiveFile().get().getAsFile();
        long before = artifact.length();
        try {
            List<String> roots = new ArrayList<>(policy.getKeepPrefixes());
            for (String required : policy.getRequiredEntries()) {
                if (required.endsWith(".class")) {
                    roots.add(required.substring(0, required.length() - ".class".length()));
                }
            }
            Set<String> removed = JarReachability.unusedClasses(artifact, policy.getPrunePrefixes(), roots);
            JarCompactor.compact(artifact, new JarCompactor.CompactionOptions(removed, policy.isStripDirectories(),
                    policy.isStripLocalVariables(), policy.isReleaseCompression()));
            JarArtifactAudit.write(artifact, policy, report, before, removed);
            task.getLogger().lifecycle("{}: {} -> {} bytes; {} unused dependency classes removed",
                    artifact.getName(), before, artifact.length(), removed.size());
        } catch (IOException exception) {
            throw new GradleException("Cannot thin " + artifact, exception);
        }
    }

    private void validate(File artifact, PackagingArtifact policy) {
        try {
            JarArtifactAudit.verify(artifact, policy);
        } catch (IOException exception) {
            throw new GradleException("Invalid plugin artifact " + artifact, exception);
        }
    }

    private String capitalize(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
