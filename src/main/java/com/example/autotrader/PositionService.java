package com.example.autotrader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PositionService {

    private final int initialPositionQty;
    private final ConcurrentMap<String, Integer> positions = new ConcurrentHashMap<>();

    public PositionService(
            @Value("${app.trade.initial-position-qty:0}") int initialPositionQty
    ) {
        this.initialPositionQty = Math.max(initialPositionQty, 0);
    }

    /**
     * DB 도입 전 임시 포지션 저장소.
     * 다음 단계에서 이 메서드를 MySQL positions 테이블 또는 KIS 잔고 조회로 교체하면 된다.
     */
    public int getPositionQty(String symbol) {
        validateSymbol(symbol);
        return positions.computeIfAbsent(symbol, ignored -> initialPositionQty);
    }

    /**
     * 현재는 주문 접수 성공(success=true)을 기준으로 포지션을 추정 갱신한다.
     * 실제 체결 여부와 부분 체결은 체결 조회 API 도입 후 별도로 반영해야 한다.
     */
    public int applyAcceptedOrder(String symbol, Dtos.TradeSide side, int quantity, boolean accepted) {
        validateSymbol(symbol);

        if (!accepted || side == null || side == Dtos.TradeSide.NONE || quantity <= 0) {
            return getPositionQty(symbol);
        }

        return positions.compute(symbol, (ignored, currentValue) -> {
            int current = currentValue == null ? initialPositionQty : currentValue;

            if (side == Dtos.TradeSide.BUY) {
                return current + quantity;
            }

            if (side == Dtos.TradeSide.SELL) {
                return Math.max(0, current - quantity);
            }

            return current;
        });
    }

    private void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol은 필수입니다.");
        }
    }
}
