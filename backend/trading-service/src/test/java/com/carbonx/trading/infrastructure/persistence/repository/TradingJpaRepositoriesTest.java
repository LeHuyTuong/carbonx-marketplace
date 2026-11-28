package com.carbonx.trading.infrastructure.persistence.repository;

import com.carbonx.trading.infrastructure.persistence.entity.PlatformSettingJpaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trading_jpa;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class TradingJpaRepositoriesTest {

    @Autowired
    private PlatformSettingJpaRepository settingRepository;

    @Autowired
    private MarketListingJpaRepository listingRepository;

    @Autowired
    private TradeOrderJpaRepository orderRepository;

    @Test
    void derivedTradingAndSettingQueriesExecuteCleanly() {
        settingRepository.save(PlatformSettingJpaEntity.feeRate(
                "trading.fee_rate", "0.0350", "Commission", 1L));

        assertThat(settingRepository.findById("trading.fee_rate"))
                .isPresent()
                .get()
                .extracting(PlatformSettingJpaEntity::getSettingValue)
                .isEqualTo("0.0350");

        assertThat(listingRepository.findByStatus("LISTED", Sort.by("pricePerCredit"))).isEmpty();
        assertThat(listingRepository.findBySellerCompanyIdOrderByIdDesc(42L)).isEmpty();
        assertThat(orderRepository.findByBuyerCompanyIdOrderByIdDesc(42L)).isEmpty();
    }
}
