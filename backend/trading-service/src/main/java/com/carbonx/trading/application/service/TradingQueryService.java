package com.carbonx.trading.application.service;

import com.carbonx.trading.application.port.in.TradingQueryUseCase;
import com.carbonx.trading.application.port.out.MarketListingRepositoryPort;
import com.carbonx.trading.application.port.out.PlatformSettingRepositoryPort;
import com.carbonx.trading.application.port.out.TradeOrderRepositoryPort;
import com.carbonx.trading.domain.setting.TradingFeeSetting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side of the trading CQRS split; never writes and never calls a remote service. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TradingQueryService implements TradingQueryUseCase {

    private final MarketListingRepositoryPort listingRepository;
    private final TradeOrderRepositoryPort orderRepository;
    private final PlatformSettingRepositoryPort platformSettingRepository;

    @Override
    public List<ListingView> listActiveMarketplace(Long projectIdFilter, String sortBy) {
        return listingRepository.findActive(projectIdFilter, sortBy).stream()
                .map(listing -> new ListingView(listing.id(), listing.batchId(), listing.batchCodeSnap(),
                        listing.projectTitleSnap(), listing.sellerCompanyId(), listing.availableQty(),
                        listing.pricePerCredit(), listing.status().name()))
                .toList();
    }

    @Override
    public List<ListingView> listMyListings(Long sellerCompanyId) {
        if (sellerCompanyId == null) {
            return List.of();
        }
        return listingRepository.findBySellerCompanyId(sellerCompanyId).stream()
                .map(listing -> new ListingView(listing.id(), listing.batchId(), listing.batchCodeSnap(),
                        listing.projectTitleSnap(), listing.sellerCompanyId(), listing.availableQty(),
                        listing.pricePerCredit(), listing.status().name()))
                .toList();
    }

    @Override
    public List<OrderView> listMyOrders(Long buyerCompanyId) {
        if (buyerCompanyId == null) {
            return List.of();
        }
        return orderRepository.findByBuyerCompanyId(buyerCompanyId).stream()
                .map(order -> new OrderView(order.id(), order.orderCode(), order.batchId(), order.quantity(),
                        order.grossAmount(), order.platformFee(), order.status().name(), order.settledAt()))
                .toList();
    }

    @Override
    public TradingFeeSetting getTradingFeeSetting() {
        return platformSettingRepository.getTradingFeeSetting();
    }
}
