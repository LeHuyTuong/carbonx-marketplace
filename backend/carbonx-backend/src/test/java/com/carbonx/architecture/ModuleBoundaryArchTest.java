package com.carbonx.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces DDD Modular Monolith boundaries defined in MASTER-PLAN.md (Layer 9).
 * Modules may ONLY depend on another module's public "..api.." package or "..shared..".
 */
class ModuleBoundaryArchTest {

    private static final List<String> MODULES = List.of(
            "identity", "mrv", "registry", "trading", "ledger", "notification"
    );

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.carbonx");
    }

    @Test
    void modulesMustNotAccessOtherModulesInternals() {
        for (String source : MODULES) {
            for (String target : MODULES) {
                if (source.equals(target)) {
                    continue;
                }
                ArchRule rule = noClasses()
                        .that().resideInAPackage("com.carbonx." + source + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                "com.carbonx." + target + ".domain..",
                                "com.carbonx." + target + ".application..",
                                "com.carbonx." + target + ".infrastructure.."
                        )
                        .allowEmptyShould(true)
                        .as(String.format("Module '%s' must only access '%s' through its public 'api' package", source, target));
                rule.check(importedClasses);
            }
        }
    }

    @Test
    void sharedKernelMustNotDependOnAnyBusinessModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.carbonx.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carbonx.identity..",
                        "com.carbonx.mrv..",
                        "com.carbonx.registry..",
                        "com.carbonx.trading..",
                        "com.carbonx.ledger..",
                        "com.carbonx.notification.."
                )
                .allowEmptyShould(true)
                .as("Shared kernel must remain independent of all 6 business modules");
        rule.check(importedClasses);
    }
}
