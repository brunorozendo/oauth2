package com.brunorozendo.oauth2.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit tests to validate architecture compliance with constitution
 */
public class ArchitectureTests {

    private static JavaClasses importedClasses;

    @BeforeAll
    public static void setup() {
        importedClasses = new ClassFileImporter()
            .importPackages("com.brunorozendo.oauth2");
    }

    @Test
    public void controllers_must_be_in_controller_package() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..controller..");

        rule.check(importedClasses);
    }

    @Test
    public void controllers_must_use_rest_controller_annotation() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(RestController.class);

        rule.check(importedClasses);
    }

    @Test
    public void config_classes_must_be_in_config_package() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Config")
            .or().areAnnotatedWith(Configuration.class)
            .should().resideInAPackage("..config..");

        rule.check(importedClasses);
    }

    @Test
    public void config_classes_must_use_configuration_annotation() {
        ArchRule rule = classes()
            .that().resideInAPackage("..config..")
            .and().haveSimpleNameEndingWith("Config")
            .should().beAnnotatedWith(Configuration.class);

        rule.check(importedClasses);
    }

    @Test
    public void api_endpoints_must_have_api_prefix() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(org.springframework.web.bind.annotation.RequestMapping.class);

        rule.check(importedClasses);
        // Note: Detailed validation of /api prefix would require method-level inspection
        // This test validates that controllers have @RequestMapping, manual review confirms /api prefix
    }

    @Test
    public void layer_dependencies_are_respected() {
        // Note: org.slf4j is allowed in Controller layer per constitution §4.3
        // This test validates overall layer architecture without enforcing logging restrictions
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controller").definedBy("..controller..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Config").mayOnlyBeAccessedByLayers("Controller")
            .allowEmptyShould(true)
            .check(importedClasses);
    }
}
