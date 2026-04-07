package com.example.autotrader;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record AnalyticsRequest(
            @JsonProperty("symbol")
            String symbol,

            @JsonProperty("window")
            Integer window,

            @JsonProperty("close_prices")
            List<Integer> closePrices,

            @JsonProperty("now_price")
            Integer nowPrice
    ) {
    }

    public record AnalyticsResponse(
            @JsonProperty("signal")
            String signal,

            @JsonProperty("z_score")
            Double zScore,

            @JsonProperty("rolling_mean")
            Double rollingMean,

            @JsonProperty("rolling_stddev")
            Double rollingStddev,

            @JsonProperty("reason")
            String reason
    ) {
    }

    public record NowPriceResult(
            Integer price,
            String source,
            boolean marketClosed
    ) {
    }

    public record OrderResult(
            @JsonProperty("success")
            boolean success,

            @JsonProperty("rt_cd")
            String rtCd,

            @JsonProperty("msg_cd")
            String msgCd,

            @JsonProperty("msg1")
            String msg1,

            @JsonProperty("ord_no")
            String ordNo,

            @JsonProperty("krx_fwdg_ord_orgno")
            String krxFwdgOrdOrgno,

            @JsonProperty("raw_response")
            String rawResponse
    ) {
    }

    public record TradeRunAudit(
            @JsonProperty("run_at")
            String runAt,

            @JsonProperty("symbol")
            String symbol,

            @JsonProperty("window")
            Integer window,

            @JsonProperty("close_prices")
            List<Integer> closePrices,

            @JsonProperty("now_price")
            Integer nowPrice,

            @JsonProperty("now_price_source")
            String nowPriceSource,

            @JsonProperty("market_closed")
            boolean marketClosed,

            @JsonProperty("signal")
            String signal,

            @JsonProperty("z_score")
            Double zScore,

            @JsonProperty("rolling_mean")
            Double rollingMean,

            @JsonProperty("rolling_stddev")
            Double rollingStddev,

            @JsonProperty("reason")
            String reason,

            @JsonProperty("budget_krw")
            Integer budgetKrw,

            @JsonProperty("order_qty")
            Integer orderQty,

            @JsonProperty("order_attempted")
            boolean orderAttempted,

            @JsonProperty("order_executed")
            boolean orderExecuted,

            @JsonProperty("order_skip_reason")
            String orderSkipReason,

            @JsonProperty("order_result")
            OrderResult orderResult
    ) {
    }
}