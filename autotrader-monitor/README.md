# AutoTrader Monitor Service v2

Spring Boot Trading Server가 NATS JetStream에 발행하는 `trade.*` 이벤트를 NestJS가 durable consumer로 소비하고, 운영 모니터링에 필요한 기능을 제공합니다.

## v2에서 추가된 기능

```text
1. Slack 알림
   - 주문 거절
   - 데이터 품질 WARNING / BLOCKED
   - 선택적으로 주문 접수 / 주문 스킵 알림

2. Redis 캐시
   - dashboard summary 저장
   - 최근 실행 목록 / 경고 목록 / count 유지
   - NestJS 재시작 후 Redis에서 summary 복원

3. Prometheus metrics
   - GET /metrics
   - 이벤트 처리량
   - 경고 수
   - run completed 수
   - Slack 알림 성공/실패
   - Redis 연결 상태
   - NATS consumer lag

4. Grafana 대시보드
   - Prometheus datasource 자동 provisioning
   - 기본 AutoTrader dashboard JSON 포함

5. NATS consumer lag 표시
   - GET /nats/lag
   - /dashboard/summary 안에도 consumerLag 포함
```

## 구조

```text
Spring Boot Trading Server
  └─ trade.* 이벤트 publish

NATS JetStream
  └─ TRADE_EVENTS stream

NestJS Monitor Service v2
  ├─ durable consumer로 trade.* consume
  ├─ Redis에 dashboard summary cache
  ├─ Slack alert 전송
  ├─ /metrics Prometheus endpoint 제공
  ├─ /nats/lag consumer lag 제공
  ├─ /dashboard/summary 제공
  └─ WebSocket으로 프론트에 실시간 push
```

## 로컬 실행

이미 아래가 켜져 있어야 합니다.

```text
1. Spring Boot Trading Server
2. C++ zscore server
3. NATS JetStream
```

설치:

```bash
cp .env.example .env
npm install
npm run start:dev
```

요약 API:

```bash
curl http://localhost:3001/dashboard/summary
```

Consumer lag:

```bash
curl http://localhost:3001/nats/lag
```

Prometheus metrics:

```bash
curl http://localhost:3001/metrics
```

WebSocket 테스트 페이지:

```text
http://localhost:3001/socket-test.html
```

## Spring 매매 실행 테스트

Spring 서버가 켜진 상태에서:

```bash
curl -X POST http://localhost:8080/api/trading/runs \
  -H 'Content-Type: application/json' \
  -d '{"symbol":"005930","window":9,"budget_krw":300000,"live_order_enabled":false}'
```

그 후 NestJS 콘솔, `/dashboard/summary`, `/metrics`, socket-test 페이지를 확인합니다.

## Redis 캐시

`.env`:

```env
REDIS_ENABLED=true
REDIS_URL=redis://localhost:6379
REDIS_KEY_PREFIX=autotrader:monitor
REDIS_CACHE_TTL_SECONDS=86400
```

Redis가 켜져 있으면 dashboard summary가 아래 key에 저장됩니다.

```text
autotrader:monitor:dashboard:summary
```

확인:

```bash
redis-cli GET autotrader:monitor:dashboard:summary
```

Redis가 꺼져 있으면 서비스는 memory cache로 계속 동작합니다.

## Slack 알림

Slack Incoming Webhook URL을 `.env`에 넣습니다.

```env
SLACK_ALERT_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
SLACK_MIN_LEVEL=warning
SLACK_NOTIFY_ORDER_ACCEPTED=false
SLACK_NOTIFY_ORDER_SKIPPED=false
```

기본적으로 아래 이벤트에서 알림을 보냅니다.

```text
trade.order.rejected              critical
trade.data-quality.checked         warning 또는 critical
```

`SLACK_MIN_LEVEL=critical`로 두면 critical 이벤트만 보냅니다.

## Prometheus / Grafana 포함 실행

Docker Compose v2 파일에는 Redis, Monitor, Prometheus, Grafana가 포함되어 있습니다.

```bash
docker compose -f deploy/docker-compose.monitor-v2.yml up -d --build
```

접속:

```text
Monitor    http://localhost:3001
Prometheus http://localhost:9090
Grafana    http://localhost:3000
```

Grafana 기본 계정:

```text
admin / admin
```

Grafana에는 Prometheus datasource와 `AutoTrader Monitor v2` dashboard가 provisioning됩니다.

## Docker Desktop에서 NATS URL 주의

Spring/NATS를 다른 compose로 띄우고 Monitor만 Docker로 띄우면, 컨테이너 내부의 `localhost`는 컨테이너 자신입니다.

Mac/Windows Docker Desktop에서는 `.env` 또는 compose에서 아래처럼 둡니다.

```env
NATS_URL=nats://host.docker.internal:4222
```

EC2에서 같은 Docker network 안에 NATS가 있으면 아래처럼 서비스명을 쓰면 됩니다.

```env
NATS_URL=nats://nats:4222
```

## 주요 API

```text
GET /health
GET /dashboard/summary
GET /nats/lag
GET /metrics
GET /socket-test.html
```

## Prometheus 주요 metric

```text
autotrader_monitor_events_total
autotrader_monitor_event_process_failures_total
autotrader_monitor_warnings_total
autotrader_monitor_runs_completed_total
autotrader_monitor_slack_alerts_total
autotrader_monitor_event_processing_seconds
autotrader_monitor_nats_consumer_pending
autotrader_monitor_nats_consumer_ack_pending
autotrader_monitor_nats_consumer_redelivered
autotrader_monitor_nats_consumer_lag_by_sequence
autotrader_monitor_redis_up
```

## 운영상 주의

- Slack webhook URL은 절대 Git에 올리지 마세요.
- NATS 4222, 8222는 AWS에서 외부 공개하지 않는 것이 안전합니다.
- Redis는 v2 기준 dashboard cache 용도입니다. 원장 데이터는 여전히 Spring/MySQL이 기준입니다.
- Prometheus/Grafana는 MVP에서는 EC2 compose에 포함해도 되지만, 운영 고도화 시 별도 모니터링 서버나 managed service로 분리할 수 있습니다.
