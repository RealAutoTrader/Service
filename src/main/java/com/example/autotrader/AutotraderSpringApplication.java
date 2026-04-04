
package com.example.autotrader;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class AutotraderSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutotraderSpringApplication.class, args);
	}

	@Bean
	public CommandLineRunner run(KisClient kisClient, CppClient cppClient) {
		return args -> {
			String symbol = "005930";
			int window = 9;

			try {
				List<Integer> closePrices = kisClient.getRecentClosePrices(symbol, window);
				Dtos.NowPriceResult nowPriceResult = kisClient.resolveNowPrice(symbol);

				Dtos.AnalyticsRequest request = new Dtos.AnalyticsRequest(
						symbol,
						window,
						closePrices,
						nowPriceResult.price()
				);

				Dtos.AnalyticsResponse response = cppClient.calculateZScore(request);

				System.out.println("===== 입력 =====");
				System.out.println("symbol = " + symbol);
				System.out.println("window = " + window);
				System.out.println("close_prices = " + closePrices);
				System.out.println("now_price = " + nowPriceResult.price());
				System.out.println("now_price_source = " + nowPriceResult.source());
				System.out.println("market_closed = " + nowPriceResult.marketClosed());

				System.out.println("===== C++ 결과 =====");
				System.out.println("signal = " + response.signal());
				System.out.println("z_score = " + response.zScore());
				System.out.println("rolling_mean = " + response.rollingMean());
				System.out.println("rolling_stddev = " + response.rollingStddev());
				System.out.println("reason = " + response.reason());
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
	}
}