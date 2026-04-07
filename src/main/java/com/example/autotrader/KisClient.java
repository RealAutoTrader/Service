//
//package com.example.autotrader;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestClient;
//import tools.jackson.databind.JsonNode;
//
//import java.time.Instant;
//import java.time.LocalDate;
//import java.time.LocalTime;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.List;
//import java.util.Map;
//
//@Component
//public class KisClient {
//
//    // 한국 시간 기준
//    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
//    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
//
//    private final RestClient client;
//
//    // KIS 인증/시세 관련 설정값
//    private final String appKey;
//    private final String appSecret;
//    private final String tokenPath;
//
//    private final String dailyPricePath;
//    private final String dailyPriceTrId;
//
//    private final String currentPricePath;
//    private final String currentPriceTrId;
//
//    // access token 캐시
//    // KIS는 토큰을 매 호출마다 새로 발급하면 안 되므로,
//    // 한 번 발급한 토큰을 메모리에 보관해서 재사용한다.
//    private String cachedAccessToken;
//    private Instant tokenExpiredAt;
//
//    public KisClient(
//            @Value("${app.kis.base-url}") String baseUrl,
//            @Value("${app.kis.app-key}") String appKey,
//            @Value("${app.kis.app-secret}") String appSecret,
//            @Value("${app.kis.token-path}") String tokenPath,
//            @Value("${app.kis.daily-price-path}") String dailyPricePath,
//            @Value("${app.kis.daily-price-tr-id}") String dailyPriceTrId,
//            @Value("${app.kis.current-price-path}") String currentPricePath,
//            @Value("${app.kis.current-price-tr-id}") String currentPriceTrId
//    ) {
//        this.client = RestClient.builder()
//                .baseUrl(baseUrl)
//                .build();
//
//        this.appKey = appKey;
//        this.appSecret = appSecret;
//        this.tokenPath = tokenPath;
//        this.dailyPricePath = dailyPricePath;
//        this.dailyPriceTrId = dailyPriceTrId;
//        this.currentPricePath = currentPricePath;
//        this.currentPriceTrId = currentPriceTrId;
//    }
//
//    /**
//     * 최근 count개 종가를 반환한다.
//     *
//     * 예:
//     * getRecentClosePrices("005930", 9)
//     * -> 최근 9개 영업일 종가 리스트 반환
//     */
//    public List<Integer> getRecentClosePrices(String symbol, int count) {
//        if (symbol == null || symbol.isBlank()) {
//            throw new IllegalArgumentException("symbol은 필수입니다.");
//        }
//
//        if (count <= 0) {
//            throw new IllegalArgumentException("count는 1 이상이어야 합니다.");
//        }
//
//        LocalDate end = LocalDate.now(KST);
//
//        // 주말/공휴일이 섞여도 충분히 count개를 확보할 수 있도록
//        // 넉넉하게 과거 날짜부터 조회한다.
//        LocalDate start = end.minusDays(Math.max(40, count * 5L));
//
//        List<Integer> allClosePrices = getDailyClosePrices(
//                symbol,
//                start.format(YYYYMMDD),
//                end.format(YYYYMMDD)
//        );
//
//        if (allClosePrices.size() < count) {
//            throw new IllegalStateException(
//                    "최근 종가가 부족합니다. 요청 개수=" + count + ", 실제 개수=" + allClosePrices.size()
//            );
//        }
//
//        return new ArrayList<>(
//                allClosePrices.subList(allClosePrices.size() - count, allClosePrices.size())
//        );
//    }
//
//    /**
//     * 현재가를 가져오되,
//     * 장 마감/비영업일/현재가 조회 실패 시에는 최근 종가 1개를 fallback으로 사용한다.
//     *
//     * 반환값:
//     * - price: 실제 사용할 가격
//     * - source: 어디서 가져온 가격인지
//     * - marketClosed: 정규장 시간이 아닌지 여부
//     */
//    public Dtos.NowPriceResult resolveNowPrice(String symbol) {
//        if (symbol == null || symbol.isBlank()) {
//            throw new IllegalArgumentException("symbol은 필수입니다.");
//        }
//
//        LocalTime now = LocalTime.now(KST);
//
//        // 정규장 시간만 간단히 체크
//        boolean withinRegularMarketTime =
//                !now.isBefore(LocalTime.of(9, 0)) &&
//                        !now.isAfter(LocalTime.of(15, 30));
//
//        // 장중으로 보이는 시간이면 현재가 조회를 먼저 시도한다.
//        // 하지만 휴일/공휴일/장중 API 일시 오류일 수 있으니 실패 시 직전 종가로 fallback 한다.
//        if (withinRegularMarketTime) {
//            try {
//                int currentPrice = getCurrentPrice(symbol);
//                return new Dtos.NowPriceResult(
//                        currentPrice,
//                        "REGULAR_CURRENT_PRICE",
//                        false
//                );
//            } catch (Exception e) {
//                int lastClose = getRecentClosePrices(symbol, 1).get(0);
//                return new Dtos.NowPriceResult(
//                        lastClose,
//                        "CURRENT_PRICE_UNAVAILABLE_USE_LAST_CLOSE",
//                        false
//                );
//            }
//        }
//
//        // 장 마감 이후 / 장 시작 전
//        int lastClose = getRecentClosePrices(symbol, 1).get(0);
//        return new Dtos.NowPriceResult(
//                lastClose,
//                "MARKET_CLOSED_USE_LAST_CLOSE",
//                true
//        );
//    }
//
//    /**
//     * KIS 현재가 API를 호출해서 현재가 1개를 반환한다.
//     */
//    public int getCurrentPrice(String symbol) {
//        if (symbol == null || symbol.isBlank()) {
//            throw new IllegalArgumentException("symbol은 필수입니다.");
//        }
//
//        String accessToken = getAccessToken();
//
//        JsonNode response = client.get()
//                .uri(uriBuilder -> uriBuilder
//                        .path(currentPricePath)
//                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
//                        .queryParam("FID_INPUT_ISCD", symbol)
//                        .build())
//                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
//                .header("appkey", appKey)
//                .header("appsecret", appSecret)
//                .header("tr_id", currentPriceTrId)
//                .header("custtype", "P")
//                .retrieve()
//                .body(JsonNode.class);
//
//        if (response == null) {
//            throw new IllegalStateException("현재가 응답이 비어 있습니다.");
//        }
//
//        String currentPrice = response.path("output").path("stck_prpr").asText();
//
//        if (currentPrice == null || currentPrice.isBlank()) {
//            String msgCd = response.path("msg_cd").asText();
//            String msg1 = response.path("msg1").asText();
//            throw new IllegalStateException("현재가 조회 실패: " + msgCd + " / " + msg1);
//        }
//
//        return Integer.parseInt(currentPrice);
//    }
//
//    /**
//     * KIS 일봉 시세 API를 호출해서 [시작일 ~ 종료일] 종가 리스트를 반환한다.
//     */
//    public List<Integer> getDailyClosePrices(String symbol, String startDate, String endDate) {
//        if (symbol == null || symbol.isBlank()) {
//            throw new IllegalArgumentException("symbol은 필수입니다.");
//        }
//        if (startDate == null || startDate.isBlank()) {
//            throw new IllegalArgumentException("startDate는 필수입니다.");
//        }
//        if (endDate == null || endDate.isBlank()) {
//            throw new IllegalArgumentException("endDate는 필수입니다.");
//        }
//
//        String accessToken = getAccessToken();
//
//        JsonNode response = client.get()
//                .uri(uriBuilder -> uriBuilder
//                        .path(dailyPricePath)
//                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
//                        .queryParam("FID_INPUT_ISCD", symbol)
//                        .queryParam("FID_INPUT_DATE_1", startDate)
//                        .queryParam("FID_INPUT_DATE_2", endDate)
//                        .queryParam("FID_PERIOD_DIV_CODE", "D")
//                        .queryParam("FID_ORG_ADJ_PRC", "1")
//                        .build())
//                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
//                .header("appkey", appKey)
//                .header("appsecret", appSecret)
//                .header("tr_id", dailyPriceTrId)
//                .header("custtype", "P")
//                .retrieve()
//                .body(JsonNode.class);
//
//        if (response == null) {
//            throw new IllegalStateException("일봉 응답이 비어 있습니다.");
//        }
//
//        JsonNode output2 = response.path("output2");
//
//        if (!output2.isArray()) {
//            String msgCd = response.path("msg_cd").asText();
//            String msg1 = response.path("msg1").asText();
//            throw new IllegalStateException("일봉 조회 실패: " + msgCd + " / " + msg1);
//        }
//
//        record PricePoint(String date, Integer closePrice) {}
//
//        List<PricePoint> rows = new ArrayList<>();
//
//        for (JsonNode row : output2) {
//            String date = row.path("stck_bsop_date").asText();
//            String close = row.path("stck_clpr").asText();
//
//            if (date == null || date.isBlank()) {
//                continue;
//            }
//            if (close == null || close.isBlank()) {
//                continue;
//            }
//
//            rows.add(new PricePoint(date, Integer.parseInt(close)));
//        }
//
//        // 날짜 오름차순 정렬
//        rows.sort(Comparator.comparing(PricePoint::date));
//
//        List<Integer> closePrices = new ArrayList<>();
//        for (PricePoint row : rows) {
//            closePrices.add(row.closePrice());
//        }
//
//        return closePrices;
//    }
//
//    /**
//     * access token을 가져온다.
//     *
//     * - 이미 토큰이 있으면 재사용
//     * - 없거나 만료 직전이면 새로 발급
//     *
//     * synchronized:
//     * 여러 스레드가 동시에 들어와도 토큰 발급이 중복되지 않게 하기 위함
//     */
//    private synchronized String getAccessToken() {
//        if (cachedAccessToken != null && tokenExpiredAt != null) {
//            // 만료 1분 전까진 재사용
//            if (Instant.now().isBefore(tokenExpiredAt.minusSeconds(60))) {
//                return cachedAccessToken;
//            }
//        }
//
//        JsonNode response = client.post()
//                .uri(tokenPath)
//                .contentType(MediaType.APPLICATION_JSON)
//                .body(Map.of(
//                        "grant_type", "client_credentials",
//                        "appkey", appKey,
//                        "appsecret", appSecret
//                ))
//                .retrieve()
//                .body(JsonNode.class);
//
//        if (response == null) {
//            throw new IllegalStateException("토큰 발급 응답이 비어 있습니다.");
//        }
//
//        String accessToken = response.path("access_token").asText();
//        long expiresIn = response.path("expires_in").asLong(0);
//
//        if (accessToken == null || accessToken.isBlank()) {
//            String msgCd = response.path("msg_cd").asText();
//            String msg1 = response.path("msg1").asText();
//            throw new IllegalStateException("토큰 발급 실패: " + msgCd + " / " + msg1);
//        }
//
//        cachedAccessToken = accessToken;
//
//        // expires_in이 있으면 그 값을 쓰고,
//        // 없으면 일단 보수적으로 1시간 뒤 만료로 둔다.
//        if (expiresIn > 0) {
//            tokenExpiredAt = Instant.now().plusSeconds(expiresIn);
//        } else {
//            tokenExpiredAt = Instant.now().plusSeconds(3600);
//        }
//
//        System.out.println("새 access token 발급 완료");
//        return cachedAccessToken;
//    }
//}


package com.example.autotrader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KisClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient client;

    private final String appKey;
    private final String appSecret;
    private final String tokenPath;

    private final String dailyPricePath;
    private final String dailyPriceTrId;

    private final String currentPricePath;
    private final String currentPriceTrId;

    private final String hashkeyPath;
    private final String orderCashPath;
    private final String orderBuyTrId;

    private final String cano;
    private final String acntPrdtCd;

    private String cachedAccessToken;
    private Instant tokenExpiredAt;

    public KisClient(
            @Value("${app.kis.base-url}") String baseUrl,
            @Value("${app.kis.app-key}") String appKey,
            @Value("${app.kis.app-secret}") String appSecret,
            @Value("${app.kis.token-path}") String tokenPath,
            @Value("${app.kis.daily-price-path}") String dailyPricePath,
            @Value("${app.kis.daily-price-tr-id}") String dailyPriceTrId,
            @Value("${app.kis.current-price-path}") String currentPricePath,
            @Value("${app.kis.current-price-tr-id}") String currentPriceTrId,
            @Value("${app.kis.hashkey-path}") String hashkeyPath,
            @Value("${app.kis.order-cash-path}") String orderCashPath,
            @Value("${app.kis.order-buy-tr-id}") String orderBuyTrId,
            @Value("${app.kis.cano}") String cano,
            @Value("${app.kis.acnt-prdt-cd}") String acntPrdtCd
    ) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.appKey = appKey;
        this.appSecret = appSecret;
        this.tokenPath = tokenPath;
        this.dailyPricePath = dailyPricePath;
        this.dailyPriceTrId = dailyPriceTrId;
        this.currentPricePath = currentPricePath;
        this.currentPriceTrId = currentPriceTrId;
        this.hashkeyPath = hashkeyPath;
        this.orderCashPath = orderCashPath;
        this.orderBuyTrId = orderBuyTrId;
        this.cano = cano;
        this.acntPrdtCd = acntPrdtCd;
    }

    public List<Integer> getRecentClosePrices(String symbol, int count) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol은 필수입니다.");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count는 1 이상이어야 합니다.");
        }

        LocalDate end = LocalDate.now(KST);
        LocalDate start = end.minusDays(Math.max(40, count * 5L));

        List<Integer> allClosePrices = getDailyClosePrices(
                symbol,
                start.format(YYYYMMDD),
                end.format(YYYYMMDD)
        );

        if (allClosePrices.size() < count) {
            throw new IllegalStateException(
                    "최근 종가가 부족합니다. 요청 개수=" + count + ", 실제 개수=" + allClosePrices.size()
            );
        }

        return new ArrayList<>(
                allClosePrices.subList(allClosePrices.size() - count, allClosePrices.size())
        );
    }

    /**
     * 장중이면 현재가 시도.
     * 장 마감이면 이미 받아온 lastClose를 그대로 사용.
     * 현재가 API 실패 시에도 lastClose로 fallback.
     */
    public Dtos.NowPriceResult resolveNowPrice(String symbol, int lastClose) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol은 필수입니다.");
        }

        LocalTime now = LocalTime.now(KST);

        boolean withinRegularMarketTime =
                !now.isBefore(LocalTime.of(9, 0)) &&
                        !now.isAfter(LocalTime.of(15, 30));

        if (withinRegularMarketTime) {
            try {
                int currentPrice = getCurrentPrice(symbol);
                return new Dtos.NowPriceResult(
                        currentPrice,
                        "REGULAR_CURRENT_PRICE",
                        false
                );
            } catch (Exception e) {
                return new Dtos.NowPriceResult(
                        lastClose,
                        "CURRENT_PRICE_UNAVAILABLE_USE_LAST_CLOSE",
                        false
                );
            }
        }

        return new Dtos.NowPriceResult(
                lastClose,
                "MARKET_CLOSED_USE_LAST_CLOSE",
                true
        );
    }

    public int getCurrentPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol은 필수입니다.");
        }

        String accessToken = getAccessToken();

        JsonNode response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(currentPricePath)
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", symbol)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", currentPriceTrId)
                .header("custtype", "P")
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("현재가 응답이 비어 있습니다.");
        }

        String currentPrice = response.path("output").path("stck_prpr").asText();

        if (currentPrice == null || currentPrice.isBlank()) {
            String msgCd = response.path("msg_cd").asText();
            String msg1 = response.path("msg1").asText();
            throw new IllegalStateException("현재가 조회 실패: " + msgCd + " / " + msg1);
        }

        return Integer.parseInt(currentPrice);
    }

    public List<Integer> getDailyClosePrices(String symbol, String startDate, String endDate) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol은 필수입니다.");
        }
        if (startDate == null || startDate.isBlank()) {
            throw new IllegalArgumentException("startDate는 필수입니다.");
        }
        if (endDate == null || endDate.isBlank()) {
            throw new IllegalArgumentException("endDate는 필수입니다.");
        }

        String accessToken = getAccessToken();

        JsonNode response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(dailyPricePath)
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", symbol)
                        .queryParam("FID_INPUT_DATE_1", startDate)
                        .queryParam("FID_INPUT_DATE_2", endDate)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "1")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", dailyPriceTrId)
                .header("custtype", "P")
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("일봉 응답이 비어 있습니다.");
        }

        JsonNode output2 = response.path("output2");

        if (!output2.isArray()) {
            String msgCd = response.path("msg_cd").asText();
            String msg1 = response.path("msg1").asText();
            throw new IllegalStateException("일봉 조회 실패: " + msgCd + " / " + msg1);
        }

        record PricePoint(String date, Integer closePrice) {}

        List<PricePoint> rows = new ArrayList<>();

        for (JsonNode row : output2) {
            String date = row.path("stck_bsop_date").asText();
            String close = row.path("stck_clpr").asText();

            if (date == null || date.isBlank()) continue;
            if (close == null || close.isBlank()) continue;

            rows.add(new PricePoint(date, Integer.parseInt(close)));
        }

        rows.sort(Comparator.comparing(PricePoint::date));

        List<Integer> closePrices = new ArrayList<>();
        for (PricePoint row : rows) {
            closePrices.add(row.closePrice());
        }

        return closePrices;
    }

    public Dtos.OrderResult placeCashBuyOrder(String symbol, int quantity, String ordDvsn, String ordUnpr) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol은 필수입니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 1 이상이어야 합니다.");
        }

        String accessToken = getAccessToken();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("CANO", cano);
        body.put("ACNT_PRDT_CD", acntPrdtCd);
        body.put("PDNO", symbol);
        body.put("ORD_DVSN", ordDvsn);
        body.put("ORD_QTY", String.valueOf(quantity));
        body.put("ORD_UNPR", ordUnpr);

        String hashKey = issueHashKey(body);

        JsonNode response = client.post()
                .uri(orderCashPath)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", orderBuyTrId)
                .header("custtype", "P")
                .header("hashkey", hashKey)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("주문 응답이 비어 있습니다.");
        }

        String rtCd = response.path("rt_cd").asText();
        String msgCd = response.path("msg_cd").asText();
        String msg1 = response.path("msg1").asText();

        JsonNode output = response.path("output");
        String ordNo = firstNonBlank(
                output.path("ODNO").asText(),
                output.path("odno").asText()
        );
        String orgNo = firstNonBlank(
                output.path("KRX_FWDG_ORD_ORGNO").asText(),
                output.path("krx_fwdg_ord_orgno").asText()
        );

        boolean success = "0".equals(rtCd);

        return new Dtos.OrderResult(
                success,
                rtCd,
                msgCd,
                msg1,
                ordNo,
                orgNo,
                response.toString()
        );
    }

    private String issueHashKey(Map<String, String> body) {
        JsonNode response = client.post()
                .uri(hashkeyPath)
                .contentType(MediaType.APPLICATION_JSON)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("hashkey 응답이 비어 있습니다.");
        }

        String hash = response.path("HASH").asText();
        if (hash == null || hash.isBlank()) {
            throw new IllegalStateException("hashkey 발급 실패: " + response);
        }

        return hash;
    }

    private synchronized String getAccessToken() {
        if (cachedAccessToken != null && tokenExpiredAt != null) {
            if (Instant.now().isBefore(tokenExpiredAt.minusSeconds(60))) {
                return cachedAccessToken;
            }
        }

        JsonNode response = client.post()
                .uri(tokenPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", appKey,
                        "appsecret", appSecret
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("토큰 발급 응답이 비어 있습니다.");
        }

        String accessToken = response.path("access_token").asText();
        long expiresIn = response.path("expires_in").asLong(0);

        if (accessToken == null || accessToken.isBlank()) {
            String msgCd = response.path("msg_cd").asText();
            String msg1 = response.path("msg1").asText();
            throw new IllegalStateException("토큰 발급 실패: " + msgCd + " / " + msg1);
        }

        cachedAccessToken = accessToken;
        tokenExpiredAt = expiresIn > 0
                ? Instant.now().plusSeconds(expiresIn)
                : Instant.now().plusSeconds(3600);

        return cachedAccessToken;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}