package dev.relism.jdae.plugin;

import dev.relism.jdae.api.ExpansionException;
import dev.relism.jdae.core.expansion.ExpanderRegistry;
import dev.relism.jdae.core.expansion.ExpansionEngine;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Expands the annotations in this module's compiled classes.
 *
 * <p>Runs after compilation and before the tests, so everything downstream — tests, packaging,
 * anything reading the classes reflectively — sees the expanded form. Expanders are loaded from
 * the module's own compile classpath, which is what lets them name the annotations they build.
 */
@Mojo(name = "expand-annotations", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class JDAEExpandMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String classesDirectory;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Skips expansion entirely, for a build that only needs to compile. */
    @Parameter(property = "jdae.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("JDAE: skipped");
            return;
        }
        Path classes = Path.of(classesDirectory);
        if (!Files.exists(classes)) return;

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();

        try (URLClassLoader loader = classLoader()) {
            thread.setContextClassLoader(loader);
            ExpansionEngine engine = new ExpansionEngine(new ExpanderRegistry(loader), loader);
            int expanded = 0;

            try (Stream<Path> tree = Files.walk(classes)) {
                for (Path file : tree.filter(p -> p.toString().endsWith(".class")).toList()) {
                    byte[] original = Files.readAllBytes(file);
                    byte[] result = engine.expand(original);
                    if (result != original) {
                        Files.write(file, result);
                        expanded++;
                        getLog().debug("JDAE: expanded " + classes.relativize(file));
                    }
                }
            }
            getLog().info("JDAE: expanded " + expanded + (expanded == 1 ? " class" : " classes"));
        } catch (ExpansionException refused) {
            throw new MojoExecutionException(refused.getMessage(), refused);
        } catch (IOException e) {
            throw new MojoExecutionException("JDAE could not read or write " + classesDirectory, e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /** The module's own classes first, then everything it compiles against. */
    private URLClassLoader classLoader() throws MojoExecutionException {
        List<URL> urls = new ArrayList<>();
        try {
            for (String element : project.getCompileClasspathElements()) urls.add(Path.of(element).toUri().toURL());
            for (String element : project.getRuntimeClasspathElements()) {
                URL url = Path.of(element).toUri().toURL();
                if (!urls.contains(url)) urls.add(url);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("JDAE could not resolve this module's classpath", e);
        }
        return new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
    }
}
