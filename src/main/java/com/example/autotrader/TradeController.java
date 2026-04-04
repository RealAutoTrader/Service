//package com.example.autotrader;
//
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/trading")
//public class TradeController {
//
//    private final TradeService tradeService;
//
//    public TradeController(TradeService tradeService) {
//        this.tradeService = tradeService;
//    }
//
//    @PostMapping("/run-zscore")
//    public Dtos.RunZScoreResponse runZScore(@RequestBody Dtos.RunZScoreRequest request) {
//        return tradeService.runZScore(request);
//    }
//}