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
import java.util.UUID;

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
			DataQualityService dataQualityService,
			PositionService positionService,
			TradeDecisionService tradeDecisionService,
			@Value("${app.trade.live-order-enabled:false}") boolean liveOrderEnabled,
			@Value("${app.trade.budget-krw:300000}") int budgetKrw,
			@Value("${app.trade.order-dvsn:01}") String orderDvsn,
			@Value("${app.trade.order-unpr:0}") String orderUnpr,
			@Value("${app.trade.symbol:005930}") String symbol,
			@Value("${app.trade.window:9}") int window
	) {
		return args -> {
			String runId = UUID.randomUUID().toString();

			Dtos.OrderResult orderResult = null;
			boolean orderAttempted = false;
			boolean orderExecuted = false;
			String orderSkipReason = null;
			int positionQtyBefore = 0;
			int positionQtyAfterEstimate = 0;

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

				Dtos.DataQualityResult dataQualityResult = dataQualityService.check(
						lastClose,
						nowPriceResult,
						response
				);

				positionQtyBefore = positionService.getPositionQty(symbol);
				positionQtyAfterEstimate = positionQtyBefore;

				Dtos.TradeDecision decision = tradeDecisionService.decide(
						response,
						nowPriceResult,
						dataQualityResult,
						budgetKrw,
						positionQtyBefore,
						liveOrderEnabled
				);

				orderSkipReason = decision.orderSkipReason();

				if (decision.shouldAttemptOrder()) {
					orderAttempted = true;

					try {
						if (decision.orderSide() == Dtos.TradeSide.BUY) {
							orderResult = kisClient.placeCashBuyOrder(
									symbol,
									decision.orderQty(),
									orderDvsn,
									orderUnpr
							);
						} else if (decision.orderSide() == Dtos.TradeSide.SELL) {
							orderResult = kisClient.placeCashSellOrder(
									symbol,
									decision.orderQty(),
									orderDvsn,
									orderUnpr
							);
						}

						orderExecuted = orderResult != null && orderResult.success();

						if (orderExecuted) {
							positionQtyAfterEstimate = positionService.applyAcceptedOrder(
									symbol,
									decision.orderSide(),
									decision.orderQty(),
									true
							);
						} else if (orderResult != null) {
							orderSkipReason = "kis order rejected: " + orderResult.msgCd() + " / " + orderResult.msg1();
						}
					} catch (Exception orderException) {
						orderSkipReason = "order api exception: "
								+ orderException.getClass().getSimpleName()
								+ " - "
								+ orderException.getMessage();
						log.error("order api failed: runId={}, symbol={}, side={}, qty={}",
								runId, symbol, decision.orderSide(), decision.orderQty(), orderException);
					}
				}

				printInputSection(symbol, window, closePrices, lastClose, nowPriceResult);
				printAnalyticsSection(response);
				printDataQualitySection(dataQualityResult);
				printPositionSection(positionQtyBefore, positionQtyAfterEstimate);
				printOrderPlanSection(decision, liveOrderEnabled);

				if (orderAttempted && orderResult != null) {
					printOrderResultSection(orderExecuted, orderResult);
				} else {
					printOrderSkipSection(orderSkipReason, decision.orderQty());
				}

				Dtos.TradeRunAudit audit = new Dtos.TradeRunAudit(
						OffsetDateTime.now().toString(),
						runId,
						symbol,
						window,
						closePrices,
						lastClose,
						nowPriceResult.price(),
						nowPriceResult.source(),
						nowPriceResult.marketClosed(),
						response.signal(),
						response.zScore(),
						response.rollingMean(),
						response.rollingStddev(),
						response.reason(),
						dataQualityResult.status(),
						dataQualityResult.reasons(),
						dataQualityResult.priceChangeRate(),
						budgetKrw,
						decision.orderSide(),
						decision.orderQty(),
						positionQtyBefore,
						positionQtyAfterEstimate,
						orderAttempted,
						orderExecuted,
						orderSkipReason,
						orderResult
				);

				tradeAuditWriter.write(audit);

				log.info(
						"trade audit saved: runId={}, symbol={}, signal={}, side={}, qty={}, attempted={}, executed={}, skipReason={}",
						runId,
						symbol,
						response.signal(),
						decision.orderSide(),
						decision.orderQty(),
						orderAttempted,
						orderExecuted,
						orderSkipReason
				);
			} catch (Exception e) {
				System.out.println("===== 실행 실패 =====");
				System.out.println(e.getMessage());
				log.error("run failed: runId={}, symbol={}", runId, symbol, e);
			}
		};
	}

	private void printInputSection(
			String symbol,
			int window,
			List<Integer> closePrices,
			int lastClose,
			Dtos.NowPriceResult nowPriceResult
	) {
		System.out.println("===== 입력 =====");
		System.out.println("symbol = " + symbol);
		System.out.println("window = " + window);
		System.out.println("close_prices = " + closePrices);
		System.out.println("last_close = " + lastClose);
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

	private void printDataQualitySection(Dtos.DataQualityResult dataQualityResult) {
		System.out.println("===== 데이터 품질 점검 =====");
		System.out.println("status = " + dataQualityResult.status());
		System.out.println("price_change_rate = " + dataQualityResult.priceChangeRate());
		System.out.println("reasons = " + dataQualityResult.reasons());
	}

	private void printPositionSection(int positionQtyBefore, int positionQtyAfterEstimate) {
		System.out.println("===== 포지션 =====");
		System.out.println("position_qty_before = " + positionQtyBefore);
		System.out.println("position_qty_after_estimate = " + positionQtyAfterEstimate);
	}

	private void printOrderPlanSection(Dtos.TradeDecision decision, boolean liveOrderEnabled) {
		System.out.println("===== 주문 판단 =====");
		System.out.println("live_order_enabled = " + liveOrderEnabled);
		System.out.println("order_side = " + decision.orderSide());
		System.out.println("order_qty = " + decision.orderQty());
		System.out.println("order_skip_reason = " + decision.orderSkipReason());
	}

	private void printOrderSkipSection(String orderSkipReason, int orderQty) {
		System.out.println("===== 주문 스킵 =====");
		System.out.println("skip_reason = " + orderSkipReason);
		System.out.println("order_qty = " + orderQty);
	}

	private void printOrderResultSection(boolean orderExecuted, Dtos.OrderResult orderResult) {
		if (orderExecuted) {
			System.out.println("===== 주문 접수 성공 =====");
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
