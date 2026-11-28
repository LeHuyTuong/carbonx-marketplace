package com.carbonx.trading;

import com.carbonx.common.arch.CleanCodeArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ServiceArchitectureTest {
    @Test
    void serviceMustObeyDddCqrsAndCleanCodeRules() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.carbonx.trading");
        CleanCodeArchRules.verifyAll(classes);
    }
}
