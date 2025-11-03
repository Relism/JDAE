package dev.relism.jdae.plugin;

import dev.relism.jdae.core.bytecode.ClassScanner;
import dev.relism.jdae.core.bytecode.ExpanderCandidate;
import dev.relism.jdae.core.expansion.ExpansionEngine;
import dev.relism.jdae.core.expansion.ExpanderRegistry;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Mojo(name = "expand-annotations", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class JDAEExpandMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String classesDirectory;

    @Parameter(property = "jdae.removeOriginal", defaultValue = "true")
    private boolean removeOriginal;

    @Override
    public void execute() throws MojoExecutionException {
        Path classesDir = Paths.get(classesDirectory);
        if (!Files.exists(classesDir)) {
            getLog().info("Classes directory does not exist: " + classesDir);
            return;
        }

        ClassScanner scanner = new ClassScanner();
        // Build a project-aware classloader that includes the compiled classes directory,
        // with the plugin's classloader as parent so shared API types remain compatible.
        ClassLoader pluginCl = getClass().getClassLoader();
        java.net.URL projectOutputUrl;
        try {
            projectOutputUrl = classesDir.toUri().toURL();
        } catch (java.net.MalformedURLException e) {
            throw new MojoExecutionException("Invalid classes directory URL: " + classesDir, e);
        }
        java.net.URLClassLoader projectCl = new java.net.URLClassLoader(new java.net.URL[]{projectOutputUrl}, pluginCl);
        // Set as context CL to assist any nested lookups that rely on it
        Thread current = Thread.currentThread();
        ClassLoader prevCl = current.getContextClassLoader();
        current.setContextClassLoader(projectCl);
        ExpansionEngine engine = new ExpansionEngine(new ExpanderRegistry(projectCl));

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
                Files.write(p, modified);
                expanded++;
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed during JDAE expansion", e);
        } finally {
            // Restore previous context classloader
            Thread.currentThread().setContextClassLoader(prevCl);
        }

        getLog().info("JDAE: expanded " + expanded + " class files, skipped " + skipped);
    }
}