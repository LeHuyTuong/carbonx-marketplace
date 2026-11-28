package com.carbonx.trading;

import com.carbonx.common.arch.CleanCodeArchRules;
import com.carbonx.trading.domain.saga.OrderSagaState;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceArchitectureTest {
    @Test
    void serviceMustObeyDddCqrsAndCleanCodeRules() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.carbonx.trading");
        CleanCodeArchRules.verifyAll(classes);
    }

    @Test
    void sagaSixStateLifecycleEnforcesValidTransitionsAndTerminalStates() {
        assertTrue(OrderSagaState.PENDING.canTransitionTo(OrderSagaState.EXECUTING));
        assertTrue(OrderSagaState.EXECUTING.canTransitionTo(OrderSagaState.COMPLETED));
        assertTrue(OrderSagaState.EXECUTING.canTransitionTo(OrderSagaState.FAILED));
        assertTrue(OrderSagaState.FAILED.canTransitionTo(OrderSagaState.COMPENSATING));
        assertTrue(OrderSagaState.COMPENSATING.canTransitionTo(OrderSagaState.COMPENSATED));

        assertFalse(OrderSagaState.PENDING.canTransitionTo(OrderSagaState.COMPLETED));
        assertFalse(OrderSagaState.COMPLETED.canTransitionTo(OrderSagaState.COMPENSATING));
        assertFalse(OrderSagaState.COMPENSATED.canTransitionTo(OrderSagaState.EXECUTING));
        assertTrue(OrderSagaState.COMPLETED.isTerminal());
        assertTrue(OrderSagaState.COMPENSATED.isTerminal());
    }
}
