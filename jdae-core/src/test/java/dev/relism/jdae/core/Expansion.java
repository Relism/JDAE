package dev.relism.jdae.core;

import dev.relism.jdae.api.annotations.Expander;
import dev.relism.jdae.core.expansion.ExpanderRegistry;
import dev.relism.jdae.core.expansion.ExpansionEngine;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles Java source, expands the classes, and hands them back through a fresh classloader.
 *
 * <p>Nothing about an annotation rewrite is observable except by reading the annotations off a
 * loaded class, so every test here goes through a real compiler and a real classloader.
 */
final class Expansion implements AutoCloseable {

    private final Path classes;
    private final List<String> sources = new ArrayList<>();
    private URLClassLoader loader;

    Expansion(Path classes) {
        this.classes = classes;
    }

    /** Adds one compilation unit; the first type it declares gives it its name. */
    Expansion source(String binaryName, String source) {
        sources.add(binaryName + "\u0000" + source);
        return this;
    }

    /** Compiles everything added so far and expands it in place. Returns the classes to read. */
    ClassLoader expand() throws Exception {
        compile();
        rewrite();
        return reload();
    }

    /** Expands twice over the same output, as a build without a clean does. */
    ClassLoader expandTwice() throws Exception {
        compile();
        rewrite();
        rewrite();
        return reload();
    }

    /** The bytes of one class as they stand on disk. */
    byte[] bytes(String binaryName) throws IOException {
        return Files.readAllBytes(fileOf(binaryName));
    }

    private void compile() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Files.createDirectories(classes);

        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            files.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classes.toFile()));
            List<JavaFileObject> units = sources.stream().map(Expansion::unitOf).toList();
            boolean compiled = compiler.getTask(null, files, diagnostics,
                    List.of("-classpath", apiClasspath()), null, units).call();
            if (!compiled) throw new IllegalStateException("the fixture does not compile: " + diagnostics.getDiagnostics());
        }
    }

    private void rewrite() throws Exception {
        try (URLClassLoader classpath = classLoader()) {
            ExpansionEngine engine = new ExpansionEngine(new ExpanderRegistry(classpath), classpath);
            try (var tree = Files.walk(classes)) {
                for (Path file : tree.filter(p -> p.toString().endsWith(".class")).toList()) {
                    byte[] original = Files.readAllBytes(file);
                    byte[] expanded = engine.expand(original);
                    if (expanded != original) Files.write(file, expanded);
                }
            }
        }
    }

    private ClassLoader reload() throws IOException {
        loader = classLoader();
        return loader;
    }

    private URLClassLoader classLoader() throws IOException {
        return new URLClassLoader(new URL[]{classes.toUri().toURL()}, Expansion.class.getClassLoader());
    }

    private Path fileOf(String binaryName) {
        return classes.resolve(binaryName.replace('.', '/') + ".class");
    }

    /** Where jdae-api sits, so a fixture can carry {@code @Expander} without knowing the build layout. */
    private static String apiClasspath() {
        return Path.of(URI.create(Expander.class.getProtectionDomain().getCodeSource().getLocation().toString())).toString();
    }

    private static JavaFileObject unitOf(String named) {
        String binaryName = named.substring(0, named.indexOf('\u0000'));
        String source = named.substring(named.indexOf('\u0000') + 1);
        return new SimpleJavaFileObject(URI.create("string:///" + binaryName.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
    }

    @Override public void close() throws IOException {
        if (loader != null) loader.close();
    }
}
