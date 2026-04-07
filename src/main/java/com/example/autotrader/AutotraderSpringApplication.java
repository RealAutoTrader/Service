//
//package com.example.autotrader;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.annotation.Bean;
//
//import java.time.OffsetDateTime;
//import java.util.List;
//
//@SpringBootApplication
//public class AutotraderSpringApplication {
//
//	private static final Logger log = LoggerFactory.getLogger(AutotraderSpringApplication.class);
//
//	public static void main(String[] args) {
//		SpringApplication.run(AutotraderSpringApplication.class, args);
//	}
//
//	@Bean
//	public CommandLineRunner run(
//			KisClient kisClient,
//			CppClient cppClient,
//			TradeAuditWriter tradeAuditWriter,
//			@Value("${app.trade.live-order-enabled:false}") boolean liveOrderEnabled,
//			@Value("${app.trade.budget-krw:300000}") int budgetKrw,
//			@Value("${app.trade.order-dvsn:01}") String orderDvsn,
//			@Value("${app.trade.order-unpr:0}") String orderUnpr
//	) {
//		return args -> {
//			String symbol = "005930";
//			int window = 9;
//
//			Dtos.OrderResult orderResult = null;
//			boolean orderAttempted = false;
//			boolean orderExecuted = false;
//			String orderSkipReason = null;
//			int orderQty = 0;
//
//			try {
//				List<Integer> closePrices = kisClient.getRecentClosePrices(symbol, window);
//				int lastClose = closePrices.get(closePrices.size() - 1);
//
//				Dtos.NowPriceResult nowPriceResult = kisClient.resolveNowPrice(symbol, lastClose);
//
//				Dtos.AnalyticsRequest request = new Dtos.AnalyticsRequest(
//						symbol,
//						window,
//						closePrices,
//						nowPriceResult.price()
//				);
//
//				Dtos.AnalyticsResponse response = cppClient.calculateZScore(request);
//
//				log.info("===== 입력 =====");
//				log.info("symbol={}", symbol);
//				log.info("window={}", window);
//				log.info("close_prices={}", closePrices);
//				log.info("now_price={}", nowPriceResult.price());
//				log.info("now_price_source={}", nowPriceResult.source());
//				log.info("market_closed={}", nowPriceResult.marketClosed());
//
//				log.info("===== C++ 결과 =====");
//				log.info("signal={}", response.signal());
//				log.info("z_score={}", response.zScore());
//				log.info("rolling_mean={}", response.rollingMean());
//				log.info("rolling_stddev={}", response.rollingStddev());
//				log.info("reason={}", response.reason());
//
//				if (nowPriceResult.price() != null && nowPriceResult.price() > 0) {
//					orderQty = budgetKrw / nowPriceResult.price();
//				}
//
//				if (!"BUY".equalsIgnoreCase(response.signal())) {
//					orderSkipReason = "signal is not BUY";
//				} else if (nowPriceResult.marketClosed()) {
//					orderSkipReason = "market closed";
//				} else if (orderQty < 1) {
//					orderSkipReason = "budget is too small for 1 share";
//				} else if (!liveOrderEnabled) {
//					orderSkipReason = "live-order-enabled=false";
//				} else {
//					orderAttempted = true;
//					orderResult = kisClient.placeCashBuyOrder(symbol, orderQty, orderDvsn, orderUnpr);
//					orderExecuted = orderResult.success();
//
//					if (orderExecuted) {
//						log.info("===== 주문 성공 =====");
//						log.info("ord_no={}", orderResult.ordNo());
//						log.info("msg1={}", orderResult.msg1());
//					} else {
//						log.warn("===== 주문 실패 =====");
//						log.warn("rt_cd={}", orderResult.rtCd());
//						log.warn("msg_cd={}", orderResult.msgCd());
//						log.warn("msg1={}", orderResult.msg1());
//					}
//				}
//
//				if (orderSkipReason != null) {
//					log.info("===== 주문 스킵 =====");
//					log.info("skip_reason={}", orderSkipReason);
//					log.info("order_qty={}", orderQty);
//				}
//
//				Dtos.TradeRunAudit audit = new Dtos.TradeRunAudit(
//						OffsetDateTime.now().toString(),
//						symbol,
//						window,
//						closePrices,
//						nowPriceResult.price(),
//						nowPriceResult.source(),
//						nowPriceResult.marketClosed(),
//						response.signal(),
//						response.zScore(),
//						response.rollingMean(),
//						response.rollingStddev(),
//						response.reason(),
//						budgetKrw,
//						orderQty,
//						orderAttempted,
//						orderExecuted,
//						orderSkipReason,
//						orderResult
//				);
//
//				tradeAuditWriter.write(audit);
//				log.info("trade audit saved");
//
//			} catch (Exception e) {
//				log.error("run failed", e);
//			}
//		};
//	}
//}



package com.example.autotrader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.OffsetDateTime;
import java.util.List;

@SpringBootApplication
public class AutotraderSpringApplication {

	private static final Logger log = LoggerFactory.getLogger(AutotraderSpringApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(AutotraderSpringApplication.class, args);
	}

	@Bean
	public CommandLineRunner run(
			KisClient kisClient,
			CppClient cppClient,
			TradeAuditWriter tradeAuditWriter,
			@Value("${app.trade.live-order-enabled:false}") boolean liveOrderEnabled,
			@Value("${app.trade.budget-krw:300000}") int budgetKrw,
			@Value("${app.trade.order-dvsn:01}") String orderDvsn,
			@Value("${app.trade.order-unpr:0}") String orderUnpr
	) {
		return args -> {
			String symbol = "005930";
			int window = 9;

			Dtos.OrderResult orderResult = null;
			boolean orderAttempted = false;
			boolean orderExecuted = false;
			String orderSkipReason = null;
			int orderQty = 0;

			try {
				List<Integer> closePrices = kisClient.getRecentClosePrices(symbol, window);
				int lastClose = closePrices.get(closePrices.size() - 1);

				Dtos.NowPriceResult nowPriceResult = kisClient.resolveNowPrice(symbol, lastClose);

				Dtos.AnalyticsRequest request = new Dtos.AnalyticsRequest(
						symbol,
						window,
						closePrices,
						nowPriceResult.price()
				);

				Dtos.AnalyticsResponse response = cppClient.calculateZScore(request);

				if (nowPriceResult.price() != null && nowPriceResult.price() > 0) {
					orderQty = budgetKrw / nowPriceResult.price();
				}

				if (!"BUY".equalsIgnoreCase(response.signal())) {
					orderSkipReason = "signal is not BUY";
				} else if (nowPriceResult.marketClosed()) {
					orderSkipReason = "market closed";
				} else if (orderQty < 1) {
					orderSkipReason = "budget is too small for 1 share";
				} else if (!liveOrderEnabled) {
					orderSkipReason = "live-order-enabled=false";
				} else {
					orderAttempted = true;
					orderResult = kisClient.placeCashBuyOrder(symbol, orderQty, orderDvsn, orderUnpr);
					orderExecuted = orderResult.success();
				}

				printInputSection(symbol, window, closePrices, nowPriceResult);
				printAnalyticsSection(response);

				if (orderAttempted && orderResult != null) {
					printOrderResultSection(orderExecuted, orderResult);
				} else {
					printOrderSkipSection(orderSkipReason, orderQty);
				}

				Dtos.TradeRunAudit audit = new Dtos.TradeRunAudit(
						OffsetDateTime.now().toString(),
						symbol,
						window,
						closePrices,
						nowPriceResult.price(),
						nowPriceResult.source(),
						nowPriceResult.marketClosed(),
						response.signal(),
						response.zScore(),
						response.rollingMean(),
						response.rollingStddev(),
						response.reason(),
						budgetKrw,
						orderQty,
						orderAttempted,
						orderExecuted,
						orderSkipReason,
						orderResult
				);

				tradeAuditWriter.write(audit);

				// 파일 로그용 짧은 요약
				log.info("trade audit saved: symbol={}, signal={}, orderAttempted={}, orderExecuted={}",
						symbol, response.signal(), orderAttempted, orderExecuted);

			} catch (Exception e) {
				System.out.println("===== 실행 실패 =====");
				System.out.println(e.getMessage());
				log.error("run failed", e);
			}
		};
	}

	private void printInputSection(String symbol, int window, List<Integer> closePrices, Dtos.NowPriceResult nowPriceResult) {
		System.out.println("===== 입력 =====");
		System.out.println("symbol = " + symbol);
		System.out.println("window = " + window);
		System.out.println("close_prices = " + closePrices);
		System.out.println("now_price = " + nowPriceResult.price());
		System.out.println("now_price_source = " + nowPriceResult.source());
		System.out.println("market_closed = " + nowPriceResult.marketClosed());
	}

	private void printAnalyticsSection(Dtos.AnalyticsResponse response) {
		System.out.println("===== C++ 결과 =====");
		System.out.println("signal = " + response.signal());
		System.out.println("z_score = " + response.zScore());
		System.out.println("rolling_mean = " + response.rollingMean());
		System.out.println("rolling_stddev = " + response.rollingStddev());
		System.out.println("reason = " + response.reason());
	}

	private void printOrderSkipSection(String orderSkipReason, int orderQty) {
		System.out.println("===== 주문 스킵 =====");
		System.out.println("skip_reason = " + orderSkipReason);
		System.out.println("order_qty = " + orderQty);
	}

	private void printOrderResultSection(boolean orderExecuted, Dtos.OrderResult orderResult) {
		if (orderExecuted) {
			System.out.println("===== 주문 성공 =====");
		} else {
			System.out.println("===== 주문 실패 =====");
		}

		System.out.println("success = " + orderResult.success());
		System.out.println("rt_cd = " + orderResult.rtCd());
		System.out.println("msg_cd = " + orderResult.msgCd());
		System.out.println("msg1 = " + orderResult.msg1());
		System.out.println("ord_no = " + orderResult.ordNo());
		System.out.println("krx_fwdg_ord_orgno = " + orderResult.krxFwdgOrdOrgno());
	}
}