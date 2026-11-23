package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.SupportRequestNumber;
import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Source and runtime checks of the entity layer's conventions: every entity except the singleton support-number counter
 * (which keeps its assigned key) extends BaseEntity's id, timestamps and equality; enum fields are persisted by name,
 * never by ordinal. Mapped fields have bean accessors, and object identity and diagnostics never traverse associations.
 */
class EntityLayerContractTests {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path ENTITY_SOURCE_ROOT =
            MAIN_JAVA.resolve(Path.of("com", "example", "aichinese", "entity"));
    private static final Path ENUM_SOURCE_ROOT = ENTITY_SOURCE_ROOT.resolve("enums");

    @Test
    void concreteEntitiesUseJpaAnnotationAndTheirExpectedBaseContract() throws Exception {
        List<Class<?>> entities = sourceClasses(ENTITY_SOURCE_ROOT);
        for (Class<?> entityClass : entities) {
            assertThat(entityClass.getAnnotation(Entity.class))
                    .as(entityClass.getName() + " must be a concrete JPA entity")
                    .isNotNull();
        }
        // The baseline defines one counter row with assigned id=1, without BaseEntity's generated id/timestamps.
        assertThat(entities.stream().filter(entityClass -> !BaseEntity.class.isAssignableFrom(entityClass)))
                .as("Only the singleton support-number counter is outside the BaseEntity contract")
                .containsExactly(SupportRequestNumber.class);
    }

    @Test
    void entitiesInheritIdentityAndExposeTheirMappedProperties() throws Exception {
        for (Class<?> entity : sourceClasses(ENTITY_SOURCE_ROOT)) {
            Class<?> identityOwner = entity == SupportRequestNumber.class ? Object.class : BaseEntity.class;
            assertThat(List.of(entity.getMethod("equals", Object.class), entity.getMethod("hashCode"),
                            entity.getMethod("toString")))
                    .as("%s identity and diagnostics", entity.getSimpleName())
                    .allMatch(method -> method.getDeclaringClass() == identityOwner);

            for (Class<?> type = entity; type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    // This constructor requires both a getter and a setter, including inherited ones.
                    PropertyDescriptor property = new PropertyDescriptor(field.getName(), entity);
                    assertThat(property.getPropertyType()).as("%s.%s", entity.getSimpleName(), field.getName())
                            .isEqualTo(field.getType());
                }
            }
        }
    }

    @Test
    void persistedEnumFieldsAlwaysUseStringMapping() throws Exception {
        Set<Class<?>> checked = new HashSet<>();
        for (Class<?> entityClass : sourceClasses(ENTITY_SOURCE_ROOT)) {
            // The mapped superclasses declare enum fields too (PublishableContent.contentStatus).
            for (Class<?> type = entityClass; type != Object.class && checked.add(type); type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    if (field.getType().isEnum()) {
                        assertEnumFieldUsesStringMapping(type, field);
                    }
                }
            }
        }
    }

    /** Enums only declare the lookups the code uses; every {@code tryFrom} they declare must behave the same way. */
    @Test
    void enumTryFromLookupsIgnoreCaseAndSurroundingWhitespace() throws Exception {
        for (Class<?> enumClass : sourceClasses(ENUM_SOURCE_ROOT)) {
            if (!enumClass.isEnum()) continue;
            Method tryFrom;
            try {
                tryFrom = enumClass.getMethod("tryFrom", String.class);
            } catch (NoSuchMethodException notDeclared) {
                continue;
            }
            Object firstConstant = enumClass.getEnumConstants()[0];
            String lowercaseName = ((Enum<?>) firstConstant).name().toLowerCase(Locale.ROOT);

            assertThat(Modifier.isStatic(tryFrom.getModifiers()))
                    .as(enumClass.getName() + ".tryFrom must be static")
                    .isTrue();
            assertThat(tryFrom.getReturnType())
                    .as(enumClass.getName() + ".tryFrom must return Optional")
                    .isEqualTo(Optional.class);
            assertThat(tryFrom.invoke(null, (Object) null))
                    .as(enumClass.getName() + ".tryFrom(null)")
                    .isEqualTo(Optional.empty());
            assertThat(tryFrom.invoke(null, "  "))
                    .as(enumClass.getName() + ".tryFrom(blank)")
                    .isEqualTo(Optional.empty());
            assertThat(tryFrom.invoke(null, "UNKNOWN_VALUE"))
                    .as(enumClass.getName() + ".tryFrom(unknown)")
                    .isEqualTo(Optional.empty());
            assertThat(tryFrom.invoke(null, "  " + lowercaseName + "  "))
                    .as(enumClass.getName() + ".tryFrom must be case- and whitespace-insensitive")
                    .isEqualTo(Optional.of(firstConstant));
        }
    }

    private static List<Path> javaSources(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<Class<?>> sourceClasses(Path directory) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        for (Path source : javaSources(directory)) {
            String relative = MAIN_JAVA.relativize(source).toString().replace('\\', '.').replace('/', '.');
            classes.add(Class.forName(relative.substring(0, relative.length() - ".java".length())));
        }
        return classes;
    }

    private static void assertEnumFieldUsesStringMapping(Class<?> entityClass, Field field) {
        String fieldPath = entityClass.getName() + "." + field.getName();
        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        assertThat(enumerated)
                .as(fieldPath + " must explicitly use @Enumerated(EnumType.STRING)")
                .isNotNull();
        assertThat(enumerated.value())
                .as(fieldPath + " must not persist enum ordinals")
                .isEqualTo(EnumType.STRING);
    }
}
