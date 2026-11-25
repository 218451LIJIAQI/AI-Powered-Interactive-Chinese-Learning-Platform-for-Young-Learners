package com.example.aichinese.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared Spring Boot profile stack for demo-seeder integration tests: runs the real {@code demo} seeders on the
 * isolated {@code test} database with {@code demo-test} credentials and upload directories so all demo test classes
 * share one cached {@code ApplicationContext}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles({"demo", "test", "demo-test"})
public @interface DemoProfileTest {
}
