package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * The profiles packaged in the jar hold no AI provider credentials: they come from the environment or from the
 * private, unpackaged {@code backend/config/application-local.yml} (README), never from a file every build ships.
 */
class PackagedCredentialsTests {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    /** Canonicalized (relaxed-binding) credential leaf names that must never hold literal values in packaged profiles. */
    private static final Set<String> CANONICAL_CREDENTIAL_KEYS = Set.of("apikey", "apikeyfile", "secretid", "secretkey");
    /** An environment variable with an empty default, such as {@code ${AI_API_KEY:}}. */
    private static final String FROM_ENVIRONMENT = "\\$\\{[A-Z0-9_]+:}";

    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();
    private final PropertiesPropertySourceLoader propertiesLoader = new PropertiesPropertySourceLoader();

    @Test
    void packagedProfilesTakeAiCredentialsFromTheEnvironmentOnly() throws IOException {
        List<Path> profiles;
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            profiles = files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                            .matches("application.*\\.(yml|yaml|properties)"))
                    .toList();
        }
        List<String> literalCredentials = new ArrayList<>();
        for (Path profile : profiles) {
            PropertySourceLoader loader = profile.getFileName().toString().endsWith(".properties")
                    ? propertiesLoader
                    : yamlLoader;
            List<PropertySource<?>> sources = loader.load(
                    profile.getFileName().toString(), new FileSystemResource(profile));
            for (PropertySource<?> source : sources) {
                if (source instanceof EnumerablePropertySource<?> enumerable) {
                    for (String key : enumerable.getPropertyNames()) {
                        String leaf = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
                        String canonicalLeaf = leaf.replaceAll("[-_]", "").toLowerCase(Locale.ROOT);
                        Object value = enumerable.getProperty(key);
                        if (CANONICAL_CREDENTIAL_KEYS.contains(canonicalLeaf)
                                && !String.valueOf(value).matches(FROM_ENVIRONMENT)) {
                            literalCredentials.add(RESOURCES.relativize(profile) + " " + key);
                        }
                    }
                }
            }
        }

        assertThat(profiles).isNotEmpty();
        // Places only: a failure must not print the credential itself.
        assertThat(literalCredentials).isEmpty();
    }
}
