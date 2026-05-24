package com.example.autotrader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataQualityService {

    private final double abnormalGapRate;
    private final boolean blockOnDataWarning;

    public DataQualityService(
            @Value("${app.trade.abnormal-gap-rate:0.08}") double abnormalGapRate,
            @Value("${app.trade.block-on-data-warning:false}") boolean blockOnDataWarning
    ) {
        this.abnormalGapRate = abnormalGapRate;
        this.blockOnDataWarning = blockOnDataWarning;
    }

    public Dtos.DataQualityResult check(
            int lastClose,
            Dtos.NowPriceResult nowPriceResult,
            Dtos.AnalyticsResponse analyticsResponse
    ) {
        List<String> reasons = new ArrayList<>();
        Double priceChangeRate = null;

        if (nowPriceResult == null) {
            reasons.add("now price result is null");
            return result(reasons, null);
        }

        Integer nowPrice = nowPriceResult.price();

        if (nowPrice == null || nowPrice <= 0) {
            reasons.add("now price is missing or invalid");
        } else if (lastClose > 0) {
            priceChangeRate = (nowPrice - lastClose) / (double) lastClose;

            if (Math.abs(priceChangeRate) >= abnormalGapRate) {
                reasons.add(
                        "abnormal price gap detected; possible dividend ex-date/corporate action/split: rate="
                                + round4(priceChangeRate)
                );
            }
        }

        if (nowPriceResult.source() == null || !"REGULAR_CURRENT_PRICE".equals(nowPriceResult.source())) {
            reasons.add("now price source is not REGULAR_CURRENT_PRICE: " + nowPriceResult.source());
        }

        if (analyticsResponse == null) {
            reasons.add("analytics response is null");
        } else if (analyticsResponse.rollingStddev() == null || analyticsResponse.rollingStddev() == 0.0) {
            reasons.add("rolling stddev is zero or missing; signal reliability is low");
        }

        return result(reasons, priceChangeRate);
    }

    private Dtos.DataQualityResult result(List<String> reasons, Double priceChangeRate) {
        Dtos.DataQualityStatus status;

        if (reasons.isEmpty()) {
            status = Dtos.DataQualityStatus.OK;
        } else if (blockOnDataWarning) {
            status = Dtos.DataQualityStatus.BLOCKED;
        } else {
            status = Dtos.DataQualityStatus.WARNING;
        }

        return new Dtos.DataQualityResult(status, List.copyOf(reasons), priceChangeRate);
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
