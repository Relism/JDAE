package dev.relism.jdae.plugin;

import dev.relism.jdae.core.bytecode.ClassScanner;
import dev.relism.jdae.core.bytecode.ExpanderCandidate;
import dev.relism.jdae.core.expansion.ExpansionEngine;
import dev.relism.jdae.core.expansion.ExpanderRegistry;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mojo(
        name = "expand-annotations",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        threadSafe = true
)
public class JDAEExpandMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String classesDirectory;

    @Parameter(property = "jdae.removeOriginal", defaultValue = "true")
    private boolean removeOriginal;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        Path classesDir = Paths.get(classesDirectory);
        if (!Files.exists(classesDir)) {
            getLog().info("Classes directory does not exist: " + classesDir);
            return;
        }

        URLClassLoader projectClassLoader = createProjectClassLoader();

        ClassScanner scanner = new ClassScanner();

        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(projectClassLoader);

        try {
            ExpansionEngine engine = new ExpansionEngine(new ExpanderRegistry(projectClassLoader));

            int expanded = 0;
            int skipped = 0;

            try (Stream<Path> paths = Files.walk(classesDir)) {
                List<Path> classFiles = paths
                        .filter(p -> p.toString().endsWith(".class"))
                        .toList();

                for (Path p : classFiles) {
                    byte[] original = Files.readAllBytes(p);
                    List<ExpanderCandidate> candidates = scanner.scan(original);
                    if (candidates.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    byte[] modified = engine.expand(original, candidates, removeOriginal);
                    if (modified != original && java.util.Arrays.compare(modified, original) != 0) {
                        Files.write(p, modified);
                        expanded++;
                    } else {
                        skipped++;
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed during JDAE expansion", e);
            }

            getLog().info("JDAE: expanded " + expanded + " class files, skipped " + skipped);

        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
            try {
                projectClassLoader.close();
            } catch (IOException e) {
                getLog().warn("Failed to close project classloader", e);
            }
        }
    }

    private URLClassLoader createProjectClassLoader() throws MojoExecutionException {
        List<URL> urls = new ArrayList<>();

        try {
            urls.add(Paths.get(classesDirectory).toUri().toURL());
            // Include both compile and runtime classpath elements to ensure all application types
            // referenced from annotation members (e.g., Class<?> values) are resolvable in dev mode.
            List<String> compileCp = project.getCompileClasspathElements();
            for (String element : compileCp) {
                urls.add(Paths.get(element).toUri().toURL());
            }
            List<String> runtimeCp = project.getRuntimeClasspathElements();
            for (String element : runtimeCp) {
                URL url = Paths.get(element).toUri().toURL();
                if (!urls.contains(url)) {
                    urls.add(url);
                }
            }

            getLog().debug("JDAE ClassLoader URLs (" + urls.size() + " entries):");
            if (getLog().isDebugEnabled()) {
                urls.forEach(url -> getLog().debug("  - " + url));
            }

        } catch (DependencyResolutionRequiredException | IOException e) {
            throw new MojoExecutionException("Failed to resolve project dependencies for JDAE", e);
        }

        return new URLClassLoader(
                urls.toArray(new URL[0]),
                getClass().getClassLoader()
        );
    }
}