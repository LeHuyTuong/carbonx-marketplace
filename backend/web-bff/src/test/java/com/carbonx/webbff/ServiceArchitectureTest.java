package com.carbonx.webbff;

import com.carbonx.common.arch.CleanCodeArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ServiceArchitectureTest {
    @Test
    void webBffMustRemainStatelessAndObeyCleanCodeRules() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.carbonx.webbff");

        CleanCodeArchRules.verifyAll(classes);

        noClasses()
                .should().beAnnotatedWith(Entity.class)
                .allowEmptyShould(true)
                .as("Stateless BFF Rule: web-bff must never own JPA @Entity tables or database state")
                .check(classes);
    }
}
