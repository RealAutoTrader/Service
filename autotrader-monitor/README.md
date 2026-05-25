# autotrader-monitor

AutoTrader 트레이딩 시스템의 모니터링 서비스입니다. NATS JetStream에서 `trade.*` 이벤트를 소비하여 대시보드 API, 실시간 WebSocket, Slack 알림, Redis 캐시, Prometheus 메트릭을 제공합니다.

## 아키텍처

```
NATS JetStream (trade.*)
        │
        ▼
TradeEventConsumer
  ├── DashboardService  →  REST API + WebSocket (Socket.IO)
  ├── AlertService      →  Slack Webhook
  └── MetricsService    →  Prometheus /metrics
        │
        └── Redis (선택적 캐시)
```

## 기능

| 기능 | 설명 |
|------|------|
| **NATS JetStream 소비** | `trade.*` 이벤트를 durable consumer로 구독, Explicit Ack |
| **REST API** | `/dashboard/summary` — 누적 통계 및 최근 실행 결과 |
| **WebSocket** | Socket.IO 기반 실시간 이벤트 푸시 |
| **Slack 알림** | `trade.order.rejected`, `trade.data-quality.checked` 이상 감지 시 Webhook 전송 |
| **Prometheus 메트릭** | `/metrics` 엔드포인트 (Counter, Gauge, Histogram) |
| **Redis 캐시** | 대시보드 상태 영속화 (선택) |
| **헬스체크** | `/health` |

## 소비하는 NATS 이벤트

| Subject | 설명 |
|---------|------|
| `trade.run.completed` | 트레이딩 사이클 완료 |
| `trade.order.accepted` | 주문 수락 |
| `trade.order.rejected` | 주문 거절 → critical 알림 |
| `trade.order.skipped` | 주문 건너뜀 |
| `trade.data-quality.checked` | 데이터 품질 검사 (BLOCKED/WARNING 시 알림) |

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/dashboard/summary` | 대시보드 전체 요약 |
| `GET` | `/nats/lag` | JetStream consumer lag 조회 |
| `GET` | `/metrics` | Prometheus 메트릭 |
| `GET` | `/health` | 헬스체크 |

### WebSocket 이벤트 (Socket.IO)

| 이벤트 | 설명 |
|--------|------|
| `trade.run.completed` | 트레이딩 사이클 완료 시 |
| `trade.warning` | 경고/장애 감지 시 |
| `dashboard.summary.updated` | 대시보드 상태 변경 시 |

## 환경 변수

`.env.example`을 복사하여 `.env`를 구성합니다.

```bash
cp .env.example .env
```

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `PORT` | `3001` | HTTP 서버 포트 |
| `NATS_URL` | `nats://localhost:4222` | NATS 서버 주소 |
| `NATS_STREAM_NAME` | `TRADE_EVENTS` | JetStream 스트림명 |
| `NATS_SUBJECT_FILTER` | `trade.>` | 구독 필터 |
| `NATS_DURABLE_NAME` | `autotrader-monitor-v2` | Durable consumer 이름 |
| `REDIS_ENABLED` | `true` | Redis 캐시 활성화 |
| `REDIS_URL` | `redis://localhost:6379` | Redis 연결 주소 |
| `SLACK_ALERT_ENABLED` | `false` | Slack 알림 활성화 |
| `SLACK_WEBHOOK_URL` | — | Slack Incoming Webhook URL |
| `SLACK_MIN_LEVEL` | `warning` | 최소 알림 레벨 (`info` / `warning` / `critical`) |
| `METRICS_ENABLED` | `true` | Prometheus 메트릭 활성화 |

## 실행

### 로컬 개발

```bash
npm install
npm run start:dev
```

### Docker Compose (전체 스택)

Redis + Prometheus + Grafana 포함 전체 모니터링 스택을 실행합니다.

```bash
cd deploy
docker compose -f docker-compose.monitor-v2.yml up -d
```

| 서비스 | 포트 | 설명 |
|--------|------|------|
| autotrader-monitor | `3001` | 모니터 API / WebSocket |
| Redis | `6379` | 캐시 |
| Prometheus | `9090` | 메트릭 수집 |
| Grafana | `3000` | 대시보드 (admin / admin) |

### 프로덕션 빌드

```bash
npm run build
npm run start:prod
```

## Prometheus 메트릭

| 메트릭 | 타입 | 설명 |
|--------|------|------|
| `autotrader_monitor_events_total` | Counter | 처리한 NATS 이벤트 수 (subject 라벨) |
| `autotrader_monitor_event_process_failures_total` | Counter | 이벤트 처리 실패 수 |
| `autotrader_monitor_warnings_total` | Counter | 감지된 경고 수 (subject, level 라벨) |
| `autotrader_monitor_runs_completed_total` | Counter | 완료된 트레이딩 사이클 수 |
| `autotrader_monitor_slack_alerts_total` | Counter | Slack 알림 전송 결과 (sent/failed/skipped) |
| `autotrader_monitor_event_processing_seconds` | Histogram | 이벤트 처리 소요 시간 |
| `autotrader_monitor_nats_consumer_pending` | Gauge | JetStream consumer pending 메시지 수 |
| `autotrader_monitor_nats_consumer_lag_by_sequence` | Gauge | Consumer sequence 기준 lag |
| `autotrader_monitor_redis_up` | Gauge | Redis 연결 상태 (1=up, 0=down) |

## 프로젝트 구조

```
src/
├── app.module.ts
├── main.ts
├── alert/          # Slack 알림, 경고 감지
├── dashboard/      # REST API, WebSocket Gateway, 상태 관리
├── dto/            # 공유 타입 (TradeEventDto, DashboardSummary 등)
├── health/         # 헬스체크
├── metrics/        # Prometheus 메트릭
├── nats/           # JetStream 연결, Consumer, Lag 조회
└── redis/          # Redis 캐시
deploy/
├── docker-compose.monitor-v2.yml
├── prometheus/
└── grafana/
```