//package com.example.autotrader;
//
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//public class TradeService {
//
//    private final KisClient kisClient;
//    private final CppClient cppClient;
//
//    public TradeService(KisClient kisClient, CppClient cppClient) {
//        this.kisClient = kisClient;
//        this.cppClient = cppClient;
//    }
//
//    public Dtos.RunZScoreResponse runZScore(Dtos.RunZScoreRequest request) {
//        validate(request);
//
//        List<Integer> closePrices = kisClient.getDailyClosePrices(
//                request.symbol(),
//                request.startDate(),
//                request.endDate()
//        );
//
//        if (closePrices.size() < request.window()) {
//            throw new IllegalArgumentException(
//                    "조회된 종가 개수(" + closePrices.size() + ")가 window(" + request.window() + ")보다 작습니다."
//            );
//        }
//
//        Dtos.AnalyticsRequest analyticsRequest = new Dtos.AnalyticsRequest(
//                request.symbol(),
//                request.window(),
//                closePrices
//        );
//
//        Dtos.AnalyticsResponse analyticsResponse = cppClient.calculateZScore(analyticsRequest);
//
//        return new Dtos.RunZScoreResponse(
//                request.symbol(),
//                request.startDate(),
//                request.endDate(),
//                request.window(),
//                closePrices,
//                analyticsResponse
//        );
//    }
//
//    private void validate(Dtos.RunZScoreRequest request) {
//        if (request == null) {
//            throw new IllegalArgumentException("request가 null입니다.");
//        }
//
//        if (request.symbol() == null || request.symbol().isBlank()) {
//            throw new IllegalArgumentException("symbol은 필수입니다.");
//        }
//
//        if (request.startDate() == null || request.startDate().isBlank()) {
//            throw new IllegalArgumentException("start_date는 필수입니다.");
//        }
//
//        if (request.endDate() == null || request.endDate().isBlank()) {
//            throw new IllegalArgumentException("end_date는 필수입니다.");
//        }
//
//        if (request.window() == null || request.window() < 2) {
//            throw new IllegalArgumentException("window는 2 이상이어야 합니다.");
//        }
//    }
//}