package com.carbonx.common.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Reusable DDD, CQRS & Clean Code ArchUnit gate enforced on every microservice. */
public final class CleanCodeArchRules {
    private CleanCodeArchRules() {}

    public static void verifyAll(JavaClasses classes) {
        noClasses()
                .that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .allowEmptyShould(true)
                .as("Controllers must call CQRS *CommandUseCase / *QueryUseCase ports, never infrastructure/repositories directly")
                .check(classes);

        methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should().notHaveRawReturnType(
                        DescribedPredicate.describe("a JPA @Entity", c -> c.isAnnotatedWith(Entity.class))
                )
                .allowEmptyShould(true)
                .as("Controllers must never expose JPA @Entity types directly")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .allowEmptyShould(true)
                .as("Domain layer must stay pure and never depend on API or Infrastructure layers")
                .check(classes);

        fields()
                .should().notBeAnnotatedWith(Autowired.class)
                .allowEmptyShould(true)
                .as("Clean Code: constructor injection only, no @Autowired field injection")
                .check(classes);

        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                .allowEmptyShould(true)
                .check(classes);
    }
}
