package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;

/**
 * Spring wiring coverage for {@link SchemaResetConfig}: verifies the {@code @ConditionalOnExpression} gate, refusal
 * under both {@code prod} and {@code production} profiles even when Flyway is disabled, and blank-database rejection.
 */
class SchemaResetConfigTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchemaResetConfig.class)
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void beanIsNotRegisteredWhenFlywayIsEnabledOrDatabaseIsEmpty() {
        runner.withPropertyValues("app.schema-reset.database=ai_chinese_learning_test", "spring.flyway.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SchemaResetConfig.SchemaReset.class);
                });

        runner.withPropertyValues("app.schema-reset.database=", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SchemaResetConfig.SchemaReset.class);
                });
    }

    @Test
    void startupFailsWhenProdOrProductionProfileIsActiveEvenWithFlywayDisabled() {
        for (String profile : new String[] { "prod", "production" }) {
            runner.withPropertyValues(
                            "spring.profiles.active=" + profile,
                            "app.schema-reset.database=ai_chinese_learning_test",
                            "spring.flyway.enabled=false")
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Refusing to reset schema while the prod profile is active."));
        }
    }

    @Test
    void untrimmedBlankDatabaseFailsContextStartupThroughAfterPropertiesSet() {
        // TestPropertyValues.of(Map) preserves raw whitespace (unlike Pair.parse("k=   ") which trims), exercising the
        // real Spring Environment wiring when DEMO_DB_NAME or quoted YAML holds spaces.
        runner.withInitializer(ctx -> TestPropertyValues.of(Map.of(
                        "app.schema-reset.database", "   ",
                        "spring.flyway.enabled", "false"))
                .applyTo(ctx))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasRootCauseMessage("app.schema-reset.database must not be blank."));
    }
}
