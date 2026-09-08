package art.arcane.volmit.packaging;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
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
    private static final String SHRINK_PROPERTY = "volmitShrink";
    private static final String SHRINK_VARIABLE = "VOLMIT_SHRINK";

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
        TaskProvider<VerifyLoggingPolicy> loggingPolicy = project.getTasks().register(
                "verifyLoggingPolicy", VerifyLoggingPolicy.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Fails when main sources bypass the plugin logger; exemptions only shrink.");
                    task.getReport().set(project.getLayout().getBuildDirectory().file("reports/logging-policy.txt"));
                });
        project.getTasks().matching(task -> task.getName().equals("check"))
                .configureEach(task -> task.dependsOn(verify, loggingPolicy));
        PackagingMode mode = PackagingMode.resolve(project.getProviders());
        project.afterEvaluate(ignored -> {
            for (PackagingArtifact artifact : extension.getArtifacts()) {
                configureArtifact(project, artifact, mode, verify);
            }
            configureLoggingPolicy(project, extension.getLoggingPolicy(), loggingPolicy);
        });
    }

    private void configureLoggingPolicy(Project project, LoggingPolicySpec policy,
                                        TaskProvider<VerifyLoggingPolicy> task) {
        List<File> directories = new ArrayList<>();
        for (File directory : resolveSourceDirectories(project, policy)) {
            if (directory.isDirectory()) {
                directories.add(directory);
            }
        }
        List<String> roots = new ArrayList<>();
        for (File directory : directories) {
            roots.add(directory.getAbsolutePath());
        }
        task.configure(verification -> {
            for (File directory : directories) {
                verification.getSources().from(project.fileTree(directory, tree -> tree.include("**/*.java")));
            }
            verification.getSourceRoots().set(roots);
            verification.getForbiddenPatterns().set(policy.getForbiddenPatterns());
            verification.getAllowlist().setFrom(project.files(policy.getAllowlistFile()));
            verification.getAllowlistLocation().set(policy.getAllowlistFile().getAbsolutePath());
        });
    }

    private List<File> resolveSourceDirectories(Project project, LoggingPolicySpec policy) {
        if (!policy.getSourceDirectories().isEmpty()) {
            return policy.getSourceDirectories();
        }
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return List.of();
        }
        SourceSet main = java.getSourceSets().findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (main == null) {
            return List.of();
        }
        return new ArrayList<>(main.getJava().getSrcDirs());
    }

    private void configureArtifact(Project project, PackagingArtifact policy, PackagingMode mode,
                                   TaskProvider<Task> verify) {
        if (policy.getMaximumBytes() <= 0) {
            throw new GradleException("A positive jar budget is required for " + policy.getName());
        }
        if (!policy.isModded() && policy.getMaximumBytes() > PackagingArtifact.SPIGOT_CAP_BYTES) {
            throw new GradleException("Jar budget for " + policy.getName() + " is " + policy.getMaximumBytes()
                    + " bytes, above the " + PackagingArtifact.SPIGOT_CAP_BYTES
                    + " byte Spigot cap; only modded profiles may exceed it");
        }
        String skipReason = shrinkSkipReason(project, policy, mode);
        TaskProvider<AbstractArchiveTask> producer = project.getTasks().named(policy.getTaskName(), AbstractArchiveTask.class);
        File reports = project.getLayout().getBuildDirectory().dir("reports/packaging").get().getAsFile();
        File report = new File(reports, policy.getName() + ".json");
        File libraryCache = new File(project.getGradle().getGradleUserHomeDir(), "caches/volmit-packaging");
        File libraryWork = project.getLayout().getBuildDirectory().dir("tmp/packaging/shrink-libraries").get().getAsFile();
        List<Object> librarySources = new ArrayList<>();
        Configuration compileClasspath = project.getConfigurations().findByName("compileClasspath");
        if (compileClasspath != null) {
            librarySources.add(compileClasspath);
        }
        librarySources.add(policy.getShrinkLibraries());
        FileCollection libraries = project.files(librarySources);
        producer.configure(task -> {
            task.getOutputs().file(report);
            task.getInputs().property("packaging.mode", mode.label());
            task.getInputs().property("packaging.maximumBytes", policy.getMaximumBytes());
            task.getInputs().property("packaging.modded", policy.isModded());
            task.getInputs().property("packaging.stripDirectories", policy.isStripDirectories());
            task.getInputs().property("packaging.stripLocalVariables", policy.isStripLocalVariables());
            task.getInputs().property("packaging.releaseCompression", policy.isReleaseCompression());
            task.getInputs().property("packaging.prunePrefixes", policy.getPrunePrefixes());
            task.getInputs().property("packaging.keepPrefixes", policy.getKeepPrefixes());
            task.getInputs().property("packaging.requiredEntries", policy.getRequiredEntries());
            task.getInputs().property("packaging.forbiddenPrefixes", policy.getForbiddenPrefixes());
            task.getInputs().property("packaging.shrink", skipReason == null ? "on" : skipReason);
            task.getInputs().property("packaging.shrinkKeep", policy.getShrinkKeep());
            task.getInputs().property("packaging.shrinkDontwarn", policy.getShrinkDontwarn());
            task.getInputs().property("packaging.shrinkRelocations", policy.getShrinkRelocations());
            if (skipReason == null) {
                task.getInputs().files(policy.getShrinkRules()).withPathSensitivity(PathSensitivity.NONE);
                task.getInputs().files(libraries).withPathSensitivity(PathSensitivity.NONE);
                task.dependsOn(libraries);
            }
            task.doLast(ignored -> optimize(task, policy, mode, report, skipReason, libraries, libraryCache, libraryWork));
        });
        TaskProvider<Task> check = project.getTasks().register("verify" + capitalize(policy.getName()) + "Packaging", task -> {
            task.setGroup("verification");
            task.dependsOn(producer);
            task.getInputs().file(producer.flatMap(AbstractArchiveTask::getArchiveFile));
            task.doLast(verification -> validate(producer.get().getArchiveFile().get().getAsFile(), policy, mode,
                    verification.getLogger()));
        });
        verify.configure(task -> task.dependsOn(check));
        producer.configure(task -> task.finalizedBy(check));
    }

    private String shrinkSkipReason(Project project, PackagingArtifact policy, PackagingMode mode) {
        if (policy.isModded()) {
            return "modded profile";
        }
        if (mode.development()) {
            return mode.source();
        }
        ProviderFactory providers = project.getProviders();
        if ("false".equalsIgnoreCase(providers.gradleProperty(SHRINK_PROPERTY).getOrElse("true"))) {
            return SHRINK_PROPERTY + "=false";
        }
        if ("false".equalsIgnoreCase(providers.environmentVariable(SHRINK_VARIABLE).getOrElse("true"))) {
            return SHRINK_VARIABLE + "=false";
        }
        if (!policy.isShrink()) {
            return "shrink=false";
        }
        return null;
    }

    private void optimize(AbstractArchiveTask task, PackagingArtifact policy, PackagingMode mode, File report,
                          String skipReason, FileCollection libraries, File libraryCache, File libraryWork) {
        File artifact = task.getArchiveFile().get().getAsFile();
        long before = artifact.length();
        try {
            JarShrinker.ShrinkResult shrink = skipReason == null
                    ? shrink(artifact, policy, report.getParentFile(), libraries, libraryCache, libraryWork)
                    : JarShrinker.ShrinkResult.skipped(skipReason, before);
            List<String> roots = new ArrayList<>(policy.getKeepPrefixes());
            for (String required : policy.getRequiredEntries()) {
                if (required.endsWith(".class")) {
                    roots.add(required.substring(0, required.length() - ".class".length()));
                }
            }
            Set<String> removed = JarReachability.unusedClasses(artifact, policy.getPrunePrefixes(), roots);
            JarCompactor.compact(artifact, new JarCompactor.CompactionOptions(removed, policy.isStripDirectories(),
                    policy.isStripLocalVariables(), policy.isReleaseCompression()));
            JarArtifactAudit.write(artifact, policy, mode, report, before, removed, shrink);
            task.getLogger().lifecycle("{}: {} -> {} bytes; shrink {} ({} classes, {} tolerated warnings, {} advice classes restored); {} unused dependency classes removed",
                    artifact.getName(), before, artifact.length(), shrink.applied() ? "applied" : shrink.reason(),
                    shrink.removedClasses(), shrink.toleratedWarnings().size(), shrink.restoredClasses().size(),
                    removed.size());
        } catch (IOException exception) {
            throw new GradleException("Cannot thin " + artifact, exception);
        }
    }

    private JarShrinker.ShrinkResult shrink(File artifact, PackagingArtifact policy, File reports,
                                            FileCollection libraries, File libraryCache, File libraryWork)
            throws IOException {
        List<File> resolved = new ArrayList<>();
        Set<String> bundled = JarShrinker.classEntries(artifact);
        for (File library : libraries.getFiles()) {
            if (library.exists() && !JarShrinker.overlaps(library, bundled)) {
                resolved.add(library);
            }
        }
        List<File> libraryFiles = new ArrayList<>();
        libraryFiles.add(JdkClassLibrary.export(libraryCache));
        libraryFiles.addAll(LibraryRelocator.relocate(resolved, policy.getShrinkRelocations(), libraryWork));
        File output = new File(artifact.getParentFile(), artifact.getName() + ".shrink.jar");
        List<String> dontwarn = new ArrayList<>(ShrinkRules.SHARED_DONTWARN);
        dontwarn.addAll(policy.getShrinkDontwarn());
        return JarShrinker.shrink(new ShrinkRules.ShrinkRequest(artifact, output, policy, libraryFiles, reports,
                policy.getName()), dontwarn);
    }

    private void validate(File artifact, PackagingArtifact policy, PackagingMode mode, Logger logger) {
        try {
            List<String> warnings = JarArtifactAudit.verify(artifact, policy, mode);
            if (mode.development()) {
                logger.lifecycle("{}: DEVELOPMENT packaging via {}. The ProGuard shrink is skipped and the {} byte size budget is advisory. Not a release jar.",
                        artifact.getName(), mode.source(), policy.getEffectiveMaximumBytes());
            }
            for (String warning : warnings) {
                logger.warn("{}: {}", artifact.getName(), warning);
            }
        } catch (IOException exception) {
            throw new GradleException("Invalid plugin artifact " + artifact, exception);
        }
    }

    private String capitalize(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
