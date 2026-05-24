package com.example.autotrader;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TradeDecisionService {

    public Dtos.TradeDecision decide(
            Dtos.AnalyticsResponse analyticsResponse,
            Dtos.NowPriceResult nowPriceResult,
            Dtos.DataQualityResult dataQualityResult,
            int budgetKrw,
            int positionQtyBefore,
            boolean liveOrderEnabled
    ) {
        String signal = analyticsResponse == null ? null : analyticsResponse.signal();
        String normalizedSignal = signal == null ? "" : signal.toUpperCase(Locale.ROOT);

        if (normalizedSignal.isBlank()) {
            return skip(positionQtyBefore, "signal is missing");
        }

        if ("HOLD".equals(normalizedSignal)) {
            return skip(positionQtyBefore, "signal is HOLD");
        }

        if (nowPriceResult == null || nowPriceResult.price() == null || nowPriceResult.price() <= 0) {
            return skip(positionQtyBefore, "now price is missing or invalid");
        }

        if (nowPriceResult.marketClosed()) {
            return skip(positionQtyBefore, "market closed");
        }

        if (dataQualityResult != null && dataQualityResult.status() == Dtos.DataQualityStatus.BLOCKED) {
            return skip(positionQtyBefore, "data quality blocked: " + String.join(", ", dataQualityResult.reasons()));
        }

        if (!liveOrderEnabled) {
            Dtos.TradeSide side = toTradeSide(normalizedSignal);
            return new Dtos.TradeDecision(side, expectedQty(side, budgetKrw, nowPriceResult.price(), positionQtyBefore), positionQtyBefore, "live-order-enabled=false");
        }

        if ("BUY".equals(normalizedSignal)) {
            int orderQty = budgetKrw / nowPriceResult.price();

            if (orderQty < 1) {
                return new Dtos.TradeDecision(Dtos.TradeSide.BUY, orderQty, positionQtyBefore, "budget is too small for 1 share");
            }

            return new Dtos.TradeDecision(Dtos.TradeSide.BUY, orderQty, positionQtyBefore, null);
        }

        if ("SELL".equals(normalizedSignal)) {
            if (positionQtyBefore < 1) {
                return new Dtos.TradeDecision(Dtos.TradeSide.SELL, 0, positionQtyBefore, "no position to sell");
            }

            // MVP에서는 SELL 신호 발생 시 보유 추정 수량을 전량 청산한다.
            return new Dtos.TradeDecision(Dtos.TradeSide.SELL, positionQtyBefore, positionQtyBefore, null);
        }

        return skip(positionQtyBefore, "unsupported signal: " + signal);
    }

    private Dtos.TradeDecision skip(int positionQtyBefore, String reason) {
        return new Dtos.TradeDecision(Dtos.TradeSide.NONE, 0, positionQtyBefore, reason);
    }

    private Dtos.TradeSide toTradeSide(String normalizedSignal) {
        if ("BUY".equals(normalizedSignal)) {
            return Dtos.TradeSide.BUY;
        }
        if ("SELL".equals(normalizedSignal)) {
            return Dtos.TradeSide.SELL;
        }
        return Dtos.TradeSide.NONE;
    }

    private int expectedQty(Dtos.TradeSide side, int budgetKrw, int nowPrice, int positionQtyBefore) {
        if (side == Dtos.TradeSide.BUY) {
            return nowPrice > 0 ? budgetKrw / nowPrice : 0;
        }
        if (side == Dtos.TradeSide.SELL) {
            return Math.max(positionQtyBefore, 0);
        }
        return 0;
    }
}
