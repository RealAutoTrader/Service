//package com.example.autotrader;
//
//import com.fasterxml.jackson.annotation.JsonProperty;
//
//import java.util.List;
//
//public final class Dtos {
//
//    private Dtos() {
//    }
//
//    // Spring에 들어오는 요청
//    public record RunZScoreRequest(
//            @JsonProperty("symbol")
//            String symbol,
//
//            @JsonProperty("start_date")
//            String startDate,
//
//            @JsonProperty("end_date")
//            String endDate,
//
//            @JsonProperty("window")
//            Integer window
//    ) {
//    }
//
//    // C++로 보내는 요청
//    public record AnalyticsRequest(
//            @JsonProperty("symbol")
//            String symbol,
//
//            @JsonProperty("window")
//            Integer window,
//
//            @JsonProperty("close_prices")
//            List<Integer> closePrices
//    ) {
//    }
//
//    // C++에서 받는 응답
//    public record AnalyticsResponse(
//            @JsonProperty("signal")
//            String signal,
//
//            @JsonProperty("z_score")
//            Double zScore,
//
//            @JsonProperty("rolling_mean")
//            Double rollingMean,
//
//            @JsonProperty("rolling_stddev")
//            Double rollingStddev,
//
//            @JsonProperty("reason")
//            String reason
//    ) {
//    }
//
//    // Spring 최종응답
//    public record RunZScoreResponse(
//            @JsonProperty("symbol")
//            String symbol,
//
//            @JsonProperty("start_date")
//            String startDate,
//
//            @JsonProperty("end_date")
//            String endDate,
//
//            @JsonProperty("window")
//            Integer window,
//
//            @JsonProperty("close_prices")
//            List<Integer> closePrices,
//
//            @JsonProperty("analytics")
//            AnalyticsResponse analytics
//    ) {
//    }
//}

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
}