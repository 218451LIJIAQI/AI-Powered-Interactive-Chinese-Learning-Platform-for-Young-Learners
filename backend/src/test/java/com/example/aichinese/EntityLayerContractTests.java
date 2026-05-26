package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.TeacherStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Source and runtime contract checks for the entity layer.
 *
 * <p>These tests deliberately guard conventions that are easy to break in a
 * large entity package and painful to discover only after Hibernate has booted
 * in production: every concrete entity must inherit the shared audit/equality
 * contract, enum fields must never fall back to ordinal persistence, and Lombok
 * must not generate association-touching equality or toString implementations.</p>
 */
class EntityLayerContractTests {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path ENTITY_SOURCE_ROOT =
            MAIN_JAVA.resolve(Path.of("com", "example", "aichinese", "entity"));
    private static final Path ENUM_SOURCE_ROOT = ENTITY_SOURCE_ROOT.resolve("enums");
    private static final Pattern DANGEROUS_LOMBOK_ANNOTATION =
            Pattern.compile("(?m)^\\s*@(lombok\\.)?(Data|EqualsAndHashCode|ToString)\\b");
    private static final Pattern GETTER_ANNOTATION = Pattern.compile("(?m)^\\s*@(lombok\\.)?Getter\\b");
    private static final Pattern SETTER_ANNOTATION = Pattern.compile("(?m)^\\s*@(lombok\\.)?Setter\\b");
    private static final Pattern NO_ARGS_CONSTRUCTOR_ANNOTATION =
            Pattern.compile("(?m)^\\s*@(lombok\\.)?NoArgsConstructor\\b");

    @Test
    void everyConcreteEntityExtendsBaseEntityAndUsesJpaEntityAnnotation() throws Exception {
        for (Class<?> entityClass : concreteEntityClasses()) {
            assertThat(entityClass.getAnnotation(Entity.class))
                    .as(entityClass.getName() + " must be a concrete JPA entity")
                    .isNotNull();
            assertThat(entityClass)
                    .as(entityClass.getName() + " must inherit BaseEntity audit/equality behavior")
                    .isAssignableTo(BaseEntity.class);
        }
    }

    @Test
    void entitySourcesUseSafeLombokShapeOnly() throws IOException {
        List<String> violations = concreteEntitySources()
                .flatMap(source -> sourceContractViolations(source).stream())
                .toList();

        assertThat(violations)
                .as("Entity sources must keep Lombok from generating equality/toString over lazy associations")
                .isEmpty();
    }

    @Test
    void persistedEnumFieldsAlwaysUseStringMapping() throws Exception {
        for (Class<?> entityClass : concreteEntityClasses()) {
            for (Field field : entityClass.getDeclaredFields()) {
                if (!field.getType().isEnum()) {
                    continue;
                }

                Enumerated enumerated = field.getAnnotation(Enumerated.class);
                assertThat(enumerated)
                        .as(entityClass.getName() + "." + field.getName()
                                + " must explicitly use @Enumerated(EnumType.STRING)")
                        .isNotNull();
                assertThat(enumerated.value())
                        .as(entityClass.getName() + "." + field.getName()
                                + " must not persist enum ordinals")
                        .isEqualTo(EnumType.STRING);
            }
        }
    }

    @Test
    void domainEnumsExposeConsistentLookupFactories() throws Exception {
        for (Class<?> enumClass : enumClasses()) {
            Method tryFrom = enumClass.getMethod("tryFrom", String.class);
            assertThat(Modifier.isStatic(tryFrom.getModifiers()))
                    .as(enumClass.getName() + ".tryFrom must be static")
                    .isTrue();
            assertThat(tryFrom.getReturnType())
                    .as(enumClass.getName() + ".tryFrom must return Optional")
                    .isEqualTo(Optional.class);

            Method fromValue = enumClass.getMethod("fromValue", String.class);
            assertThat(Modifier.isStatic(fromValue.getModifiers()))
                    .as(enumClass.getName() + ".fromValue must be static")
                    .isTrue();
            assertThat(fromValue.getReturnType())
                    .as(enumClass.getName() + ".fromValue must return its enum type")
                    .isEqualTo(enumClass);

            Object firstConstant = enumClass.getEnumConstants()[0];
            String lowercaseName = ((Enum<?>) firstConstant).name().toLowerCase(Locale.ROOT);

            assertThat(invokeLookup(tryFrom, null))
                    .as(enumClass.getName() + ".tryFrom(null)")
                    .isEqualTo(Optional.empty());
            assertThat(invokeLookup(tryFrom, "  "))
                    .as(enumClass.getName() + ".tryFrom(blank)")
                    .isEqualTo(Optional.empty());
            assertThat(invokeLookup(tryFrom, "  " + lowercaseName + "  "))
                    .as(enumClass.getName() + ".tryFrom must be case- and whitespace-insensitive")
                    .isEqualTo(Optional.of(firstConstant));
            assertThat(invokeLookup(fromValue, "  " + lowercaseName + "  "))
                    .as(enumClass.getName() + ".fromValue must share tryFrom normalization")
                    .isSameAs(firstConstant);
            assertThatCode(() -> invokeLookup(fromValue, "not_a_" + lowercaseName))
                    .as(enumClass.getName() + ".fromValue must reject unknown values")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void teacherVerificationReviewAcceptsOnlyFinalDecisions() {
        TeacherVerificationReview review = new TeacherVerificationReview();

        review.setDecisionStatus(TeacherStatus.APPROVED);
        assertThat(review.getDecisionStatus()).isEqualTo(TeacherStatus.APPROVED);

        review.setDecisionStatus(TeacherStatus.REJECTED);
        assertThat(review.getDecisionStatus()).isEqualTo(TeacherStatus.REJECTED);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> review.setDecisionStatus(TeacherStatus.PENDING))
                .withMessage("Teacher verification review decision status must be APPROVED or REJECTED.");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> review.setDecisionStatus(null))
                .withMessage("Teacher verification review decision status is required.");
    }

    private static List<Class<?>> concreteEntityClasses() throws Exception {
        try (Stream<Path> sources = concreteEntitySources()) {
            List<Class<?>> classes = new ArrayList<>();
            sources
                    .map(EntityLayerContractTests::sourceToClassName)
                    .map(EntityLayerContractTests::loadClass)
                    .forEach(classes::add);
            classes.sort(Comparator.comparing(Class::getName));
            return List.copyOf(classes);
        }
    }

    private static Stream<Path> concreteEntitySources() throws IOException {
        return Files.list(ENTITY_SOURCE_ROOT)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .filter(path -> !"package-info.java".equals(path.getFileName().toString()))
                .sorted();
    }

    private static List<Class<?>> enumClasses() throws Exception {
        try (Stream<Path> sources = Files.list(ENUM_SOURCE_ROOT)) {
            List<Class<?>> classes = new ArrayList<>();
            sources
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !"EnumLookups.java".equals(path.getFileName().toString()))
                    .map(EntityLayerContractTests::sourceToClassName)
                    .map(EntityLayerContractTests::loadClass)
                    .filter(Class::isEnum)
                    .forEach(classes::add);
            classes.sort(Comparator.comparing(Class::getName));
            return List.copyOf(classes);
        }
    }

    private static List<String> sourceContractViolations(Path source) {
        String content;
        try {
            content = Files.readString(source);
        } catch (IOException ex) {
            throw new AssertionError("Could not read entity source " + source, ex);
        }

        String displayPath = source.toString();
        return Stream.of(
                        DANGEROUS_LOMBOK_ANNOTATION.matcher(content).find()
                                ? displayPath + " uses @Data/@EqualsAndHashCode/@ToString"
                                : null,
                        !GETTER_ANNOTATION.matcher(content).find()
                                ? displayPath + " is missing @Getter"
                                : null,
                        !SETTER_ANNOTATION.matcher(content).find()
                                ? displayPath + " is missing @Setter"
                                : null,
                        !NO_ARGS_CONSTRUCTOR_ANNOTATION.matcher(content).find()
                                ? displayPath + " is missing @NoArgsConstructor"
                                : null)
                .filter(message -> message != null)
                .toList();
    }

    private static String sourceToClassName(Path source) {
        Path relativeSource = MAIN_JAVA.relativize(source);
        String sourceName = relativeSource.toString().replace('\\', '.').replace('/', '.');
        return sourceName.substring(0, sourceName.length() - ".java".length());
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Could not load " + className, ex);
        }
    }

    private static Object invokeLookup(Method method, String value) {
        try {
            return method.invoke(null, value);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError("Lookup method threw checked exception", cause);
        } catch (IllegalAccessException ex) {
            throw new AssertionError("Lookup method is not accessible: " + method, ex);
        }
    }
}
