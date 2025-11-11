# Java Dynamic Annotation Expansion
[![Relism Repository](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fmaven.relism.dev%2Freleases%2Fdev%2Frelism%2Fjdae-api%2Fmaven-metadata.xml&query=%2Fmetadata%2Fversioning%2Fversions%2Fversion%5Blast()%5D&style=flat-square&label=Latest%20Release&link=https%3A%2F%2Fmaven.relism.dev%2F%23%2Freleases%2Fdev%2Frelism%2Fjdae-api
)](https://maven.relism.dev/#/releases/dev/relism/jdae-api)
[![License: MIT](https://img.shields.io/github/license/Relism/JDAE)](LICENSE)
### A metaprogramming Java library for post-compile dynamic annotation expansion.

----------

## About Annotation Expansion
**Annotation expansion** is the process of programmatically transforming and replacing lightweight _"expander"_ annotations
with more complex or multiple annotations during compilation.

This approach is especially valuable for reducing repetitive boilerplate and for handling intricate
configurations that depend on multiple contextual conditions, all without sacrificing type safety or
compile-time validation.

## ⚠️ Note
This library is still in **early development**. The API may change significantly in future releases.
I'm by no means an expert in Java annotation processing: feedback, issues, and pull requests are very welcome!

## Usage

### Installation
To use JDAE in your project, you first need to add the api dependency to your project.
For maven, add the following to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.relism</groupId>
    <artifactId>jdae-api</artifactId>
    <version>VERSION</version>
</dependency>
```
For gradle, add the following to your `build.gradle`:

```groovy
implementation 'dev.relism:jdae-api:VERSION'
```

Next, you need to add the maven plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>dev.relism</groupId>
    <artifactId>jdae-maven-plugin</artifactId>
    <version>${jdae.version}</version>
    <extensions>true</extensions>
    <executions>
        <execution>
            <goals>
                <goal>expand-annotations</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <removeOriginal>true</removeOriginal>
    </configuration>
</plugin>
```

For gradle, add the following to your `build.gradle`:

```groovy
plugins {
    id 'dev.relism.jdae' version 'VERSION'
}
```

## Example
For a simple yet meaningful example, consider the following scenario:
You are developing a Jakarta EE application (e.g. using Spring Boot, Micronaut, or Quarkus) that generates OpenAPI documentation through SmallRye OpenAPI or Springdoc.

Suppose you want to document a REST endpoint that returns a paginated list of users.
Normally, the corresponding annotation setup would look like this:

```java
@GET
@ApiResponse(
        responseCode = "200",
        description = "My description",
        content = {
                @Content(
                        schema = @Schema(
                                implementation = User.class,
                                type = SchemaType.ARRAY,
                                name = "User"
                        )
                )
        },
        headers = {
                @Header(
                        name = "X-Page",
                        description = "Current page index (0-based)",
                        schema = @Schema(type = SchemaType.INTEGER)
                ),
                @Header(
                        name = "X-Page-Size",
                        description = "Number of 'User' per page",
                        schema = @Schema(type = SchemaType.INTEGER)
                ),
                @Header(
                        name = "X-Total-Count",
                        description = "Total number of 'User' available",
                        schema = @Schema(type = SchemaType.INTEGER)
                )
        }
)
public List<User> getUsers() {
    // ...
}
```

This setup is quite verbose and repetitive, especially if you have multiple endpoints that return paginated lists of different entities.
To simplify this, you can define a custom expander annotation `@ArrayResponse` that automatically expands into the necessary OpenAPI annotations.

```java
@Expander(value = ArrayResponseExpander.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ArrayResponse {
    String responseCode() default "200";
    String description() default "";
    Class<?> implementation();
    String overrideName() default "";
    boolean paginated() default false;
}
```

And the corresponding expander:

```java
public class ArrayResponseExpander implements JDAEExpander<ArrayResponse> {

    @Override
    public void expand(ExpansionContext ctx, ArrayResponse ann) {
        if (ctx.getTargetKind() != TargetKind.METHOD) {
            throw new IllegalStateException("@ArrayResponse can only be applied to methods");
        }

        String implementationName = ann.overrideName().isBlank() ?
                ann.implementation().getSimpleName() : ann.overrideName();

        String description = ann.description().isBlank()
                ? "Returns an array of '" + implementationName + "'."
                : ann.description();

        List<AnnotationBuilder> headers = new ArrayList<>();

        if (ann.paginated()) {
            headers.add(AnnotationBuilder.of(Header.class)
                    .member("name", "X-Page")
                    .member("description", "Current page index (0-based)")
                    .nested("schema", Schema.class, schema ->
                            schema.member("type", SchemaType.INTEGER))
            );

            headers.add(AnnotationBuilder.of(Header.class)
                    .member("name", "X-Page-Size")
                    .member("description", "Number of '" + implementationName + "' per page")
                    .nested("schema", Schema.class, schema ->
                            schema.member("type", SchemaType.INTEGER))
            );

            headers.add(AnnotationBuilder.of(Header.class)
                    .member("name", "X-Total-Count")
                    .member("description", "Total number of '" + implementationName + "' available")
                    .nested("schema", Schema.class, schema ->
                            schema.member("type", SchemaType.INTEGER))
            );
        }

        ctx.addOrModifyAnnotation(APIResponse.class, api -> {
            api.member("responseCode", ann.responseCode());
            api.member("description", description);

            api.nestedArray("content", AnnotationBuilder.of(Content.class)
                    .nested("schema", Schema.class, schema -> {
                        schema.member("implementation", ann.implementation());
                        schema.member("type", SchemaType.ARRAY);
                        schema.member("named", implementationName);
                    }));

            if (!headers.isEmpty()) {
                api.nestedArray("headers", headers.toArray(AnnotationBuilder[]::new));
            }
        });
    }
}
```

Now, you can annotate your REST endpoint simply as:

```java
@GET
@ArrayResponse(
        implementation = User.class,
        paginated = true
)
public List<User> getUsers() {
    // ...
}
```

During compilation, this is automatically expanded into the full equivalent `@APIResponse`
annotation shown earlier, including pagination headers if `paginated = true`.

## API Documentation
Full API documentation is in progress and will be available soon.
In the meantime, refer to the source code and examples, since usage is straightforward and type-safe.