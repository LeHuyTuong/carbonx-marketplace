package com.carbonx.mobilebff;

import com.carbonx.common.arch.CleanCodeArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ServiceArchitectureTest {
    @Test
    void mobileBffMustRemainStatelessAndObeyCleanCodeRules() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.carbonx.mobilebff");

        CleanCodeArchRules.verifyAll(classes);

        noClasses()
                .should().beAnnotatedWith(Entity.class)
                .allowEmptyShould(true)
                .as("Stateless BFF Rule: mobile-bff must never own JPA @Entity tables or database state")
                .check(classes);
    }
}
