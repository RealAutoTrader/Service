package com.example.autotrader;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public enum TradeSide {
        BUY,
        SELL,
        NONE
    }

    public enum DataQualityStatus {
        OK,
        WARNING,
        BLOCKED
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

    /**
     * 주문 API 호출 결과.
     * success=true는 "주문 접수 성공" 기준이다.
     * 실제 체결 완료 여부는 체결 조회 API를 붙인 뒤 별도 필드로 분리하는 것이 안전하다.
     */
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

    /**
     * 배당락/권리락/액면분할/현재가 fallback 같은 데이터 품질 이슈를 주문 판단 전에 기록한다.
     */
    public record DataQualityResult(
            @JsonProperty("status")
            DataQualityStatus status,

            @JsonProperty("reasons")
            List<String> reasons,

            @JsonProperty("price_change_rate")
            Double priceChangeRate
    ) {
    }

    /**
     * 주문 실행 전 계획.
     * orderSkipReason == null 이고 side가 BUY/SELL이면 실제 주문 시도 대상이다.
     */
    public record TradeDecision(
            @JsonProperty("order_side")
            TradeSide orderSide,

            @JsonProperty("order_qty")
            Integer orderQty,

            @JsonProperty("position_qty_before")
            Integer positionQtyBefore,

            @JsonProperty("order_skip_reason")
            String orderSkipReason
    ) {
        public boolean shouldAttemptOrder() {
            return orderSide != null
                    && orderSide != TradeSide.NONE
                    && orderQty != null
                    && orderQty > 0
                    && orderSkipReason == null;
        }
    }

    /**
     * 1회 실행 전체를 추적하는 감사 로그.
     * 이후 MySQL 도입 시 이 구조를 signal_runs/orders/data_quality_logs 등으로 나누면 된다.
     */
    public record TradeRunAudit(
            @JsonProperty("run_at")
            String runAt,

            @JsonProperty("run_id")
            String runId,

            @JsonProperty("symbol")
            String symbol,

            @JsonProperty("window")
            Integer window,

            @JsonProperty("close_prices")
            List<Integer> closePrices,

            @JsonProperty("last_close")
            Integer lastClose,

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

            @JsonProperty("data_quality_status")
            DataQualityStatus dataQualityStatus,

            @JsonProperty("data_quality_reasons")
            List<String> dataQualityReasons,

            @JsonProperty("price_change_rate")
            Double priceChangeRate,

            @JsonProperty("budget_krw")
            Integer budgetKrw,

            @JsonProperty("order_side")
            TradeSide orderSide,

            @JsonProperty("order_qty")
            Integer orderQty,

            @JsonProperty("position_qty_before")
            Integer positionQtyBefore,

            @JsonProperty("position_qty_after_estimate")
            Integer positionQtyAfterEstimate,

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
