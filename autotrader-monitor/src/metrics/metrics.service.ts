import { Injectable, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  collectDefaultMetrics,
  Counter,
  Gauge,
  Histogram,
  Registry,
} from 'prom-client';
import { NatsConsumerLagSummary, WarningSummary } from '../dto/trade-event.dto';

@Injectable()
export class MetricsService implements OnModuleInit {
  readonly registry = new Registry();

  private eventsTotal!: Counter<string>;
  private eventFailuresTotal!: Counter<string>;
  private warningsTotal!: Counter<string>;
  private runsCompletedTotal!: Counter<string>;
  private slackAlertsTotal!: Counter<string>;
  private eventProcessingSeconds!: Histogram<string>;
  private consumerPendingGauge!: Gauge<string>;
  private consumerAckPendingGauge!: Gauge<string>;
  private consumerRedeliveredGauge!: Gauge<string>;
  private consumerLagBySeqGauge!: Gauge<string>;
  private redisUpGauge!: Gauge<string>;

  constructor(private readonly configService: ConfigService) {}

  onModuleInit(): void {
    const defaultsEnabled = this.readBooleanEnv('METRICS_DEFAULTS_ENABLED', true);
    if (defaultsEnabled) {
      collectDefaultMetrics({ register: this.registry, prefix: 'autotrader_monitor_' });
    }

    this.eventsTotal = new Counter({
      name: 'autotrader_monitor_events_total',
      help: 'Total number of NATS trade events processed by monitor service.',
      labelNames: ['subject'],
      registers: [this.registry],
    });

    this.eventFailuresTotal = new Counter({
      name: 'autotrader_monitor_event_process_failures_total',
      help: 'Total number of NATS trade event processing failures.',
      labelNames: ['subject'],
      registers: [this.registry],
    });

    this.warningsTotal = new Counter({
      name: 'autotrader_monitor_warnings_total',
      help: 'Total number of warning events detected by monitor service.',
      labelNames: ['subject', 'level'],
      registers: [this.registry],
    });

    this.runsCompletedTotal = new Counter({
      name: 'autotrader_monitor_runs_completed_total',
      help: 'Total number of trade.run.completed events processed by signal.',
      labelNames: ['symbol', 'signal', 'order_status'],
      registers: [this.registry],
    });

    this.slackAlertsTotal = new Counter({
      name: 'autotrader_monitor_slack_alerts_total',
      help: 'Total number of Slack alert attempts.',
      labelNames: ['status'],
      registers: [this.registry],
    });

    this.eventProcessingSeconds = new Histogram({
      name: 'autotrader_monitor_event_processing_seconds',
      help: 'Time spent processing a single NATS trade event.',
      labelNames: ['subject', 'result'],
      buckets: [0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2, 5],
      registers: [this.registry],
    });

    this.consumerPendingGauge = new Gauge({
      name: 'autotrader_monitor_nats_consumer_pending',
      help: 'JetStream durable consumer pending messages.',
      labelNames: ['stream', 'durable'],
      registers: [this.registry],
    });

    this.consumerAckPendingGauge = new Gauge({
      name: 'autotrader_monitor_nats_consumer_ack_pending',
      help: 'JetStream durable consumer ack pending messages.',
      labelNames: ['stream', 'durable'],
      registers: [this.registry],
    });

    this.consumerRedeliveredGauge = new Gauge({
      name: 'autotrader_monitor_nats_consumer_redelivered',
      help: 'JetStream durable consumer redelivered messages.',
      labelNames: ['stream', 'durable'],
      registers: [this.registry],
    });

    this.consumerLagBySeqGauge = new Gauge({
      name: 'autotrader_monitor_nats_consumer_lag_by_sequence',
      help: 'Approximate JetStream consumer lag calculated from delivered and ack floor consumer sequence.',
      labelNames: ['stream', 'durable'],
      registers: [this.registry],
    });

    this.redisUpGauge = new Gauge({
      name: 'autotrader_monitor_redis_up',
      help: 'Redis connection status. 1 means up, 0 means down or disabled.',
      registers: [this.registry],
    });
  }

  incEvent(subject: string): void {
    this.eventsTotal.inc({ subject });
  }

  incEventFailure(subject: string): void {
    this.eventFailuresTotal.inc({ subject });
  }

  incWarning(warning: WarningSummary): void {
    this.warningsTotal.inc({ subject: warning.subject, level: warning.level });
  }

  incRunCompleted(symbol: string | null, signal: string | null, orderStatus: string): void {
    this.runsCompletedTotal.inc({
      symbol: symbol ?? 'UNKNOWN',
      signal: signal ?? 'UNKNOWN',
      order_status: orderStatus,
    });
  }

  incSlackAlert(status: 'sent' | 'failed' | 'skipped'): void {
    this.slackAlertsTotal.inc({ status });
  }

  startEventTimer(subject: string): (result: 'success' | 'failure') => void {
    const end = this.eventProcessingSeconds.startTimer({ subject });
    return (result: 'success' | 'failure') => end({ result });
  }

  setConsumerLag(summary: NatsConsumerLagSummary): void {
    const labels = { stream: summary.streamName, durable: summary.durableName };
    if (summary.numPending !== null) {
      this.consumerPendingGauge.set(labels, summary.numPending);
    }
    if (summary.numAckPending !== null) {
      this.consumerAckPendingGauge.set(labels, summary.numAckPending);
    }
    if (summary.numRedelivered !== null) {
      this.consumerRedeliveredGauge.set(labels, summary.numRedelivered);
    }
    if (summary.lagByConsumerSeq !== null) {
      this.consumerLagBySeqGauge.set(labels, summary.lagByConsumerSeq);
    }
  }

  setRedisUp(up: boolean): void {
    this.redisUpGauge.set(up ? 1 : 0);
  }

  async renderMetrics(): Promise<string> {
    return this.registry.metrics();
  }

  contentType(): string {
    return this.registry.contentType;
  }

  private readBooleanEnv(key: string, defaultValue: boolean): boolean {
    const value = this.configService.get<string>(key);
    if (value === undefined || value === null || value.length === 0) {
      return defaultValue;
    }
    return value.toLowerCase() === 'true';
  }
}
