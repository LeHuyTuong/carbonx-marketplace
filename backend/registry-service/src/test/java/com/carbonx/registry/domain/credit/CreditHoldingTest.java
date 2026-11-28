package com.carbonx.registry.domain.credit;

import com.carbonx.common.domain.SerialRange;
import com.carbonx.registry.domain.shared.InsufficientCreditsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditHoldingTest {

    private static CreditHolding holdingOf100() {
        return CreditHolding.issue(5L, 7L, 100, 1);
    }

    @Test
    @DisplayName("A new holding owns the whole serial range and nothing is locked")
    void issueKeepsTheWholeRangeAvailable() {
        CreditHolding holding = holdingOf100();

        assertThat(holding.availableQty()).isEqualTo(100);
        assertThat(holding.lockedQty()).isZero();
        assertThat(holding.retiredQty()).isZero();
        assertThat(holding.serialRangeLabel()).isEqualTo("000001..000100");
    }

    @Test
    @DisplayName("Locking the 80% sold portion keeps 000081..000100 available for retirement")
    void lockingTheSoldPortionLeavesTheRetainedTail() {
        CreditHolding locked = holdingOf100().lock(80);

        assertThat(locked.availableQty()).isEqualTo(20);
        assertThat(locked.lockedQty()).isEqualTo(80);
        assertThat(locked.lockedSerialRange()).isEqualTo(new SerialRange(1, 80));
        assertThat(locked.availableSerialRange()).isEqualTo(new SerialRange(81, 100));
    }

    @Test
    @DisplayName("Transferring the locked credits moves the head of the range to the buyer")
    void transferringLockedCreditsConsumesTheHead() {
        CreditHolding seller = holdingOf100().lock(80).transferLocked(80);

        assertThat(seller.availableQty()).isEqualTo(20);
        assertThat(seller.lockedQty()).isZero();
        assertThat(seller.retiredQty()).isZero();
        assertThat(seller.serialRangeLabel()).isEqualTo("000081..000100");
    }

    @Test
    @DisplayName("A partial transfer leaves the rest of the reservation locked at the head")
    void partialTransferKeepsTheRemainingReservation() {
        CreditHolding seller = holdingOf100().lock(80).transferLocked(40);

        assertThat(seller.lockedQty()).isEqualTo(40);
        assertThat(seller.lockedSerialRange()).isEqualTo(new SerialRange(41, 80));
        assertThat(seller.availableSerialRange()).isEqualTo(new SerialRange(81, 100));
    }

    @Test
    @DisplayName("Releasing a reservation restores the serials to the seller")
    void releaseRestoresAvailability() {
        CreditHolding released = holdingOf100().lock(80).release(80);

        assertThat(released.availableQty()).isEqualTo(100);
        assertThat(released.lockedQty()).isZero();
        assertThat(released.serialRangeLabel()).isEqualTo("000001..000100");
    }

    @Test
    @DisplayName("Retirement consumes the tail of the available block and keeps the history")
    void retiringConsumesTheTail() {
        CreditHolding seller = holdingOf100().lock(80).transferLocked(80);
        SerialRange retirementRange = seller.retirementRange(20);
        CreditHolding retired = seller.retire(20);

        assertThat(retirementRange).isEqualTo(new SerialRange(81, 100));
        assertThat(retired.availableQty()).isZero();
        assertThat(retired.retiredQty()).isEqualTo(20);
    }

    @Test
    @DisplayName("A holding can never be overdrawn")
    void quantitiesCanNeverBeOverdrawn() {
        CreditHolding holding = holdingOf100().lock(80);

        assertThatThrownBy(() -> holding.lock(21)).isInstanceOf(InsufficientCreditsException.class);
        assertThatThrownBy(() -> holding.transferLocked(81)).isInstanceOf(InsufficientCreditsException.class);
        assertThatThrownBy(() -> holding.release(81)).isInstanceOf(InsufficientCreditsException.class);
        assertThatThrownBy(() -> holding.retire(21)).isInstanceOf(InsufficientCreditsException.class);
    }

    @Test
    @DisplayName("A company only owns its own holdings")
    void ownershipIsCheckedByCompany() {
        CreditHolding holding = holdingOf100();

        assertThat(holding.owns(7L)).isTrue();
        assertThat(holding.owns(8L)).isFalse();
    }
}
