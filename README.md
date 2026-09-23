# JDAE — Java Dynamic Annotation Expansion

One small annotation on your code, the annotations it stands for in the class file. JDAE runs
after compilation, replaces each annotation you mark with whatever its expander builds, and checks
the result against the annotation types it writes — so what reaches the JVM is what you would have
written by hand, without writing it.

It earns its place where annotations are verbose, repetitive and conditional: OpenAPI
documentation, persistence mapping, validation groups, anything where the same block is copied
across handlers with two words changed.

> Early days: the API still moves between minor versions, and there is no Gradle plugin yet.
> Issues and pull requests welcome.

## Install

JitPack builds it from the tag. Both the API and the plugin come from there:

```xml
<properties>
    <jdae.version>v1.2.0</jdae.version>
</properties>

<repositories>
    <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>
<pluginRepositories>
    <pluginRepository><id>jitpack.io</id><url>https://jitpack.io</url></pluginRepository>
</pluginRepositories>

<dependencies>
    <!-- Expanders compile against this; nothing needs it at runtime. -->
    <dependency>
        <groupId>com.github.Relism.JDAE</groupId>
        <artifactId>jdae-api</artifactId>
        <version>${jdae.version}</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build><plugins>
    <plugin>
        <groupId>com.github.Relism.JDAE</groupId>
        <artifactId>jdae-maven-plugin</artifactId>
        <version>${jdae.version}</version>
        <executions><execution><goals><goal>expand-annotations</goal></goals></execution></executions>
    </plugin>
</plugins></build>
```

The goal binds to `process-classes`, so tests and packaging already see the expanded classes.
`-Djdae.skip` turns it off. The version is the tag, `v` included: JitPack does not strip it.

## Writing one

An annotation names its expander, and the expander builds what it stands for:

```java
@Expander(ListResponseExpander.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ListResponse {
    Class<?> value();
    String description() default "";
}
```

```java
final class ListResponseExpander implements JDAEExpander<ListResponse> {

    @Override
    public void expand(ExpansionContext ctx, ListResponse annotation) {
        if (ctx.getTargetKind() != TargetKind.CLASS) ctx.fail("only handlers carry @ListResponse");

        String what = annotation.value().getSimpleName();
        ctx.addAnnotation(APIResponse.class, a -> a
                .member("responseCode", "200")
                .member("description", annotation.description().isBlank() ? "A list of " + what : annotation.description())
                .nested("content", Content.class, c -> c
                        .member("schema", annotation.value())
                        .member("array", true)));
    }
}
```

```java
@GET("/users")
@ListResponse(User.class)
public final class ListUsers extends RequestHandler { ... }
```

The expander runs once per annotated target. It needs a no-argument constructor and nothing else;
package-private is fine. A `ServiceLoader` entry for `JDAEExpander` registers one too, matched by
its type argument, for annotations that would rather not name their expander.

## What the context gives you

| | |
|---|---|
| `addAnnotation(type, …)` | Writes it, replacing any annotation of that type the target carried |
| `addOrModifyAnnotation(type, …)` | Starts from what the target carries: members set here win, arrays are added to |
| `annotation(Type.class)` | What the target carries right now, or null — for expanding conditionally |
| `getTargetKind()`, `getClassInfo()`, `getMethodInfo()`, `getFieldInfo()` | Where this annotation sits |
| `fail(message)` | Refuses, naming the target in the build error |

Member values are what the annotation type declares: strings, primitives, `Class`, enum constants,
nested annotations (`nested`), arrays (`nestedArray`, or any array or collection). A single value
given for an array member means an array of one, exactly as it would in source.

Two annotations of one `@Repeatable` type are written into their container for you. Two of a type
that is not repeatable is an error, not a class file the JVM will reject later.

## What it guarantees

- **Every annotation typechecks before it is written.** Unknown member, wrong type, unknown enum
  constant, a member with no default left unset — each fails the build naming the target and the
  member, instead of surfacing as an `AnnotationFormatError` the first time something reads it.
- **Expanding twice changes nothing.** An expanded annotation is removed from the class file, and
  an annotation that is written replaces the one it replaces — so a build without a `clean` never
  stacks a second copy. Keep the original with `@Expander(keepOriginal = true)` when something
  reads it at runtime; it stays idempotent.
- **Nothing else in the class is touched.** Code, frames and the constant pool are copied through:
  the only difference between the class before and after is the annotations.
- **Failures are loud.** An expander that cannot be loaded, constructed or run stops the build.
  There is no silent "expanded nothing".
- Annotations land where their own `@Retention` says: runtime-visible unless they are `CLASS`.

## Modules

| | |
|---|---|
| `jdae-api` | What an expander is written against: `@Expander`, `JDAEExpander`, `ExpansionContext`, `AnnotationBuilder` |
| `jdae-core` | The expansion: scanning, validation, the ASM rewrite |
| `jdae-maven-plugin` | The `expand-annotations` goal |

Build it with `mvn install`; `mvn test` compiles real fixtures, expands them and reads the
annotations back through a fresh classloader.
