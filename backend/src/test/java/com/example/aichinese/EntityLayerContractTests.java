package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.SupportRequestNumber;
import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.EnumLookups;
import com.example.aichinese.entity.enums.PracticeType;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MapKeyEnumerated;

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
            checkEnumFields(entityClass, checked);
        }
    }

    private static void checkEnumFields(Class<?> rootType, Set<Class<?>> checked) {
        // The mapped superclasses and @Embeddable types declare enum fields too (PublishableContent.contentStatus).
        for (Class<?> type = rootType; type != null && type != Object.class && checked.add(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if (field.getType().isEnum()) {
                    assertEnumFieldUsesStringMapping(type, field);
                } else if (field.isAnnotationPresent(ElementCollection.class)) {
                    checkElementCollectionEnumMapping(type, field);
                } else if (field.isAnnotationPresent(Embedded.class) || field.getType().isAnnotationPresent(Embeddable.class)) {
                    checkEnumFields(field.getType(), checked);
                }
            }
        }
    }

    private static void checkElementCollectionEnumMapping(Class<?> owner, Field field) {
        if (!(field.getGenericType() instanceof ParameterizedType parameterized)) {
            return;
        }
        Type[] args = parameterized.getActualTypeArguments();
        if (Collection.class.isAssignableFrom(field.getType()) && args.length == 1
                && args[0] instanceof Class<?> elementClass && elementClass.isEnum()) {
            assertEnumFieldUsesStringMapping(owner, field);
        } else if (Map.class.isAssignableFrom(field.getType()) && args.length == 2) {
            if (args[0] instanceof Class<?> keyClass && keyClass.isEnum()) {
                String fieldPath = owner.getName() + "." + field.getName();
                MapKeyEnumerated keyEnumerated = field.getAnnotation(MapKeyEnumerated.class);
                assertThat(keyEnumerated).as(fieldPath + " map key must use @MapKeyEnumerated(EnumType.STRING)").isNotNull();
                assertThat(keyEnumerated.value()).as(fieldPath + " map key must not persist ordinals").isEqualTo(EnumType.STRING);
            }
            if (args[1] instanceof Class<?> valueClass && valueClass.isEnum()) {
                assertEnumFieldUsesStringMapping(owner, field);
            }
        }
    }

    /** {@link EnumLookups#tryFrom} works uniformly across every enum, and {@link PracticeType#tryFrom} also accepts hyphens. */
    @Test
    void enumTryFromLookupsIgnoreCaseAndSurroundingWhitespace() throws Exception {
        int verifiedEnums = 0;
        for (Class<?> enumClass : sourceClasses(ENUM_SOURCE_ROOT)) {
            if (!enumClass.isEnum()) continue;
            verifiedEnums++;
            assertEnumLookupChecked(enumClass);
        }
        assertThat(verifiedEnums).as("at least one enum exists").isPositive();

        assertThat(PracticeType.tryFrom(null)).isEmpty();
        assertThat(PracticeType.tryFrom("  ")).isEmpty();
        assertThat(PracticeType.tryFrom("UNKNOWN_VALUE")).isEmpty();
        assertThat(PracticeType.tryFrom("  drag-drop  ")).contains(PracticeType.DRAG_DROP);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> void assertEnumLookupChecked(Class<?> enumClass) {
        assertEnumLookup((Class<E>) enumClass);
    }

    private static <E extends Enum<E>> void assertEnumLookup(Class<E> enumClass) {
        E firstConstant = enumClass.getEnumConstants()[0];
        String lowercaseName = firstConstant.name().toLowerCase(Locale.ROOT);
        assertThat(EnumLookups.tryFrom(enumClass, null))
                .as("EnumLookups.tryFrom(" + enumClass.getSimpleName() + ", null)")
                .isEmpty();
        assertThat(EnumLookups.tryFrom(enumClass, "  "))
                .as("EnumLookups.tryFrom(" + enumClass.getSimpleName() + ", blank)")
                .isEmpty();
        assertThat(EnumLookups.tryFrom(enumClass, "UNKNOWN_VALUE"))
                .as("EnumLookups.tryFrom(" + enumClass.getSimpleName() + ", unknown)")
                .isEmpty();
        assertThat(EnumLookups.tryFrom(enumClass, "  " + lowercaseName + "  "))
                .as("EnumLookups.tryFrom(" + enumClass.getSimpleName() + ") must be case- and whitespace-insensitive")
                .contains(firstConstant);
    }

    private static List<Path> javaSources(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !directory.equals(ENTITY_SOURCE_ROOT)
                            || (!path.startsWith(ENTITY_SOURCE_ROOT.resolve("base"))
                                    && !path.startsWith(ENUM_SOURCE_ROOT)
                                    && !path.startsWith(ENTITY_SOURCE_ROOT.resolve("value"))))
                    .sorted()
                    .toList();
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
