package com.example.autotrader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CppClient {

    private final RestClient client;
    private final String signalPath;

    public CppClient(
            @Value("${app.cpp.base-url}") String baseUrl,
            @Value("${app.cpp.signal-path}") String signalPath
    ) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.signalPath = signalPath;
    }

    public Dtos.AnalyticsResponse calculateZScore(Dtos.AnalyticsRequest request) {
        Dtos.AnalyticsResponse response = client.post()
                .uri(signalPath)
                .body(request)
                .retrieve()
                .body(Dtos.AnalyticsResponse.class);

        if (response == null) {
            throw new IllegalStateException("C++ 서버 응답이 비어 있습니다.");
        }

        return response;
    }
}