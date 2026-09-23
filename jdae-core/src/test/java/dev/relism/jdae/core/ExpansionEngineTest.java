package dev.relism.jdae.core;

import dev.relism.jdae.api.ExpansionException;
import dev.relism.jdae.core.expansion.ExpanderRegistry;
import dev.relism.jdae.core.expansion.ExpansionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.annotation.Annotation;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an expansion does to a real class, read back through a real classloader. */
class ExpansionEngineTest {

    @TempDir Path classes;

    private static final String MARKER = """
            package fixture;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
            public @interface Marker {
                String name();
                String[] tags() default {};
                Class<?> type() default Object.class;
                Level level() default Level.LOW;
                Inner inner() default @Inner("");
            }
            """;

    private static final String LEVEL = """
            package fixture;
            public enum Level { LOW, HIGH }
            """;

    private static final String INNER = """
            package fixture;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Inner { String value(); }
            """;

    /** An expander annotation plus its expander, both built from one body of expansion code. */
    private static String expander(String name, String body) {
        return """
                package fixture;
                import dev.relism.jdae.api.*;
                import dev.relism.jdae.api.annotations.Expander;
                import java.lang.annotation.*;

                @Expander(%1$sExpander.class)
                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
                public @interface %1$s { String value() default ""; }

                class %1$sExpander implements JDAEExpander<%1$s> {
                    @Override public void expand(ExpansionContext ctx, %1$s annotation) {
                        %2$s
                    }
                }
                """.formatted(name, body);
    }

    private Expansion fixture(String expanderName, String body, String subject) {
        return new Expansion(classes)
                .source("fixture.Marker", MARKER)
                .source("fixture.Level", LEVEL)
                .source("fixture.Inner", INNER)
                .source("fixture." + expanderName, expander(expanderName, body))
                .source("fixture.Subject", subject);
    }

    @Test
    void writesTheAnnotationAndDropsTheOneItExpanded() throws Exception {
        try (Expansion expansion = fixture("Doc",
                "ctx.addAnnotation(Marker.class, a -> a.member(\"name\", annotation.value()));",
                "package fixture; @Doc(\"hello\") public class Subject {}")) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");

            assertEquals("hello", member(annotationNamed(subject, "fixture.Marker"), "name"));
            assertNull(annotationNamed(subject, "fixture.Doc"));
        }
    }

    @Test
    void expandingTwiceLeavesOneAnnotation() throws Exception {
        try (Expansion expansion = fixture("Doc",
                "ctx.addAnnotation(Marker.class, a -> a.member(\"name\", annotation.value()));",
                "package fixture; @Doc(\"hello\") public class Subject {}")) {

            Class<?> subject = expansion.expandTwice().loadClass("fixture.Subject");

            assertEquals(1, subject.getAnnotations().length);
            assertEquals("hello", member(annotationNamed(subject, "fixture.Marker"), "name"));
        }
    }

    @Test
    void anAnnotationKeptIsNotExpandedTwiceOverItself() throws Exception {
        String kept = expander("Kept", "ctx.addAnnotation(Marker.class, a -> a.member(\"name\", \"once\"));")
                .replace("@Expander(KeptExpander.class)", "@Expander(value = KeptExpander.class, keepOriginal = true)");

        try (Expansion expansion = new Expansion(classes)
                .source("fixture.Marker", MARKER).source("fixture.Level", LEVEL).source("fixture.Inner", INNER)
                .source("fixture.Kept", kept)
                .source("fixture.Subject", "package fixture; @Kept public class Subject {}")) {

            Class<?> subject = expansion.expandTwice().loadClass("fixture.Subject");

            assertEquals(2, subject.getAnnotations().length, "the kept annotation and the one it expands into");
            assertEquals("once", member(annotationNamed(subject, "fixture.Marker"), "name"));
        }
    }

    @Test
    void addingReplacesWhatTheTargetCarried() throws Exception {
        try (Expansion expansion = fixture("Doc",
                "ctx.addAnnotation(Marker.class, a -> a.member(\"name\", annotation.value()));",
                "package fixture; @Marker(name = \"old\", tags = \"kept\") @Doc(\"new\") public class Subject {}")) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");
            Annotation marker = annotationNamed(subject, "fixture.Marker");

            assertEquals("new", member(marker, "name"));
            assertArrayEquals(new String[0], (String[]) member(marker, "tags"), "a replacement keeps nothing");
        }
    }

    @Test
    void modifyingStartsFromWhatWasWrittenByHand() throws Exception {
        try (Expansion expansion = fixture("Doc", """
                ctx.addOrModifyAnnotation(Marker.class, a -> a
                        .member("tags", new String[]{"added"})
                        .member("level", Level.HIGH));
                """,
                "package fixture; @Marker(name = \"kept\", tags = \"first\") @Doc public class Subject {}")) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");
            Annotation marker = annotationNamed(subject, "fixture.Marker");

            assertEquals("kept", member(marker, "name"));
            assertArrayEquals(new String[]{"first", "added"}, (String[]) member(marker, "tags"));
            assertEquals("HIGH", member(marker, "level").toString());
        }
    }

    @Test
    void aLoneValueModifiedIntoAnArrayIsAddedToIt() throws Exception {
        try (Expansion expansion = fixture("Doc",
                "ctx.addOrModifyAnnotation(Marker.class, a -> a.member(\"tags\", \"added\"));",
                "package fixture; @Marker(name = \"kept\", tags = \"first\") @Doc public class Subject {}")) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");

            assertArrayEquals(new String[]{"first", "added"}, (String[]) member(annotationNamed(subject, "fixture.Marker"), "tags"));
        }
    }

    @Test
    void whatAnExpanderReadsIsWhatTheTargetCarries() throws Exception {
        try (Expansion expansion = fixture("Doc", """
                Marker existing = ctx.annotation(Marker.class);
                ctx.addAnnotation(Marker.class, a -> a.member("name", existing == null ? "none" : existing.name() + "!"));
                """,
                "package fixture; @Marker(name = \"read\") @Doc public class Subject {}")) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");

            assertEquals("read!", member(annotationNamed(subject, "fixture.Marker"), "name"));
        }
    }

    @Test
    void twoOfARepeatableTypeAreWrittenIntoTheirContainer() throws Exception {
        String response = """
                package fixture;
                import java.lang.annotation.*;
                @Repeatable(Responses.class) @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
                public @interface Response { String code(); }
                """;
        String responses = """
                package fixture;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
                public @interface Responses { Response[] value(); }
                """;

        try (Expansion expansion = new Expansion(classes)
                .source("fixture.Response", response)
                .source("fixture.Responses", responses)
                .source("fixture.Doc", expander("Doc", """
                        ctx.addAnnotation(Response.class, a -> a.member("code", "200"));
                        ctx.addAnnotation(Response.class, a -> a.member("code", "404"));
                        """))
                .source("fixture.Subject", "package fixture; @Doc public class Subject {}")) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");
            @SuppressWarnings("unchecked")
            Annotation[] found = subject.getAnnotationsByType(
                    (Class<Annotation>) subject.getClassLoader().loadClass("fixture.Response"));

            assertEquals(2, found.length);
            assertEquals("200", member(found[0], "code"));
            assertEquals("404", member(found[1], "code"));
        }
    }

    @Test
    void oneValueGivenForAnArrayMemberBecomesAnArrayOfOne() throws Exception {
        try (Expansion expansion = fixture("Doc",
                "ctx.addAnnotation(Marker.class, a -> a.member(\"name\", \"n\").member(\"tags\", \"alone\"));",
                "package fixture; @Doc public class Subject {}")) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");

            assertArrayEquals(new String[]{"alone"}, (String[]) member(annotationNamed(subject, "fixture.Marker"), "tags"));
        }
    }

    @Test
    void classesEnumsAndNestedAnnotationsSurviveTheRoundTrip() throws Exception {
        try (Expansion expansion = fixture("Doc", """
                ctx.addAnnotation(Marker.class, a -> a
                        .member("name", "full")
                        .member("type", String.class)
                        .member("level", Level.HIGH)
                        .nested("inner", Inner.class, i -> i.member("value", "nested")));
                """,
                "package fixture; @Doc public class Subject {}")) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");
            Annotation marker = annotationNamed(subject, "fixture.Marker");

            assertEquals(String.class, member(marker, "type"));
            assertEquals("HIGH", member(marker, "level").toString());
            assertEquals("nested", member((Annotation) member(marker, "inner"), "value"));
        }
    }

    @Test
    void methodsAndFieldsAreExpandedToo() throws Exception {
        try (Expansion expansion = fixture("Doc",
                "ctx.addAnnotation(Marker.class, a -> a.member(\"name\", ctx.getTargetKind().name()));", """
                package fixture;
                public class Subject {
                    @Doc public String field;
                    @Doc public void method() {}
                }
                """)) {

            Class<?> subject = expansion.expand().loadClass("fixture.Subject");

            assertEquals("FIELD", member(annotationNamed(subject.getField("field"), "fixture.Marker"), "name"));
            assertEquals("METHOD", member(annotationNamed(subject.getMethod("method"), "fixture.Marker"), "name"));
        }
    }

    @Test
    void aClassWithNothingToExpandIsNotRewritten() throws Exception {
        byte[] original = getClass().getResourceAsStream("/dev/relism/jdae/core/Expansion.class").readAllBytes();
        ClassLoader loader = getClass().getClassLoader();

        byte[] result = new ExpansionEngine(new ExpanderRegistry(loader), loader).expand(original);

        assertSame(original, result, "an untouched class must come back as the very same bytes");
    }

    // ── What has to fail the build ────────────────────────────────────────────

    @Test
    void anUnknownMemberIsRefused() {
        assertFails("ctx.addAnnotation(Marker.class, a -> a.member(\"name\", \"n\").member(\"nope\", 1));",
                "has no member nope");
    }

    @Test
    void aMemberOfTheWrongTypeIsRefused() {
        assertFails("ctx.addAnnotation(Marker.class, a -> a.member(\"name\", 42));",
                "expects java.lang.String but got java.lang.Integer");
    }

    @Test
    void aMemberWithNoDefaultMustBeSet() {
        assertFails("ctx.addAnnotation(Marker.class, a -> a.member(\"tags\", new String[]{\"t\"}));",
                "name has no default and was not set");
    }

    @Test
    void anEnumConstantThatDoesNotExistIsRefused() {
        assertFails("ctx.addAnnotation(Marker.class, a -> a.member(\"name\", \"n\").member(\"level\", \"HIGHEST\"));",
                "expects fixture.Level but got java.lang.String");
    }

    @Test
    void anExpanderThatRefusesNamesTheTarget() {
        assertFails("ctx.fail(\"not on this one\");", "@Doc on fixture.Subject: not on this one");
    }

    @Test
    void anExpanderThatThrowsNamesTheTarget() {
        assertFails("throw new IllegalStateException(\"boom\");", "@Doc on fixture.Subject: DocExpander threw IllegalStateException");
    }

    @Test
    void twoOfANonRepeatableTypeAreRefused() {
        assertFails("""
                ctx.addAnnotation(Marker.class, a -> a.member("name", "one"));
                ctx.addAnnotation(Marker.class, a -> a.member("name", "two"));
                """, "is not @Repeatable");
    }

    private void assertFails(String body, String message) {
        try (Expansion expansion = fixture("Doc", body, "package fixture; @Doc public class Subject {}")) {
            ExpansionException refused = assertThrows(ExpansionException.class, expansion::expand);
            assertTrue(refused.getMessage().contains(message),
                    "expected a message naming <" + message + ">, got <" + refused.getMessage() + ">");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Annotation annotationNamed(java.lang.reflect.AnnotatedElement element, String className) {
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType().getName().equals(className)) return annotation;
        }
        return null;
    }

    private static Object member(Annotation annotation, String name) throws Exception {
        return annotation.annotationType().getMethod(name).invoke(annotation);
    }
}
