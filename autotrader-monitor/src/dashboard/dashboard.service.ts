import { Injectable, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  DashboardSummary,
  NatsConsumerLagSummary,
  RunCompletedSummary,
  TradeEventDto,
  WarningSummary,
} from '../dto/trade-event.dto';
import { RedisCacheService } from '../redis/redis-cache.service';

@Injectable()
export class DashboardService implements OnModuleInit {
  private readonly recentLimit: number;
  private readonly warningLimit: number;
  private readonly recentRuns: RunCompletedSummary[] = [];
  private readonly warnings: WarningSummary[] = [];
  private readonly countsBySubject = new Map<string, number>();
  private readonly countsBySignal = new Map<string, number>();
  private readonly countsByOrderStatus = new Map<string, number>();
  private readonly countsByDataQualityStatus = new Map<string, number>();
  private totalEvents = 0;
  private lastEventAt: string | null = null;
  private consumerLag: NatsConsumerLagSummary | null = null;

  constructor(
    private readonly configService: ConfigService,
    private readonly redisCacheService: RedisCacheService,
  ) {
    this.recentLimit = Number(this.configService.get<string>('DASHBOARD_RECENT_LIMIT') ?? 20);
    this.warningLimit = Number(this.configService.get<string>('DASHBOARD_WARNING_LIMIT') ?? this.recentLimit);
  }

  async onModuleInit(): Promise<void> {
    const cached = await this.redisCacheService.loadDashboardSummary();
    if (!cached) {
      return;
    }

    this.totalEvents = cached.totalEvents ?? 0;
    this.lastEventAt = cached.lastEventAt ?? null;
    this.consumerLag = cached.consumerLag ?? null;
    this.recentRuns.splice(0, this.recentRuns.length, ...(cached.recentRuns ?? []));
    this.warnings.splice(0, this.warnings.length, ...(cached.warnings ?? []));
    this.replaceMap(this.countsBySubject, cached.countsBySubject ?? {});
    this.replaceMap(this.countsBySignal, cached.countsBySignal ?? {});
    this.replaceMap(this.countsByOrderStatus, cached.countsByOrderStatus ?? {});
    this.replaceMap(this.countsByDataQualityStatus, cached.countsByDataQualityStatus ?? {});
    console.log('dashboard summary restored from Redis cache');
  }

  async recordEvent(event: TradeEventDto, natsSubject: string): Promise<void> {
    this.totalEvents += 1;
    const subject = event.subject ?? natsSubject;
    this.increment(this.countsBySubject, subject);

    const occurredAt = this.getOccurredAt(event);
    if (occurredAt) {
      this.lastEventAt = occurredAt;
    }

    await this.persist();
  }

  async recordRunCompleted(event: TradeEventDto): Promise<RunCompletedSummary> {
    const payload = event.payload ?? {};
    const summary: RunCompletedSummary = {
      runId: this.getRunId(event),
      occurredAt: this.getOccurredAt(event),
      symbol: this.asString(payload['symbol']),
      signal: this.asString(payload['signal']),
      dataQualityStatus: this.asString(payload['data_quality_status']),
      orderSide: this.asString(payload['order_side']),
      orderQty: this.asNumber(payload['order_qty']),
      orderAttempted: this.asBoolean(payload['order_attempted']),
      orderExecuted: this.asBoolean(payload['order_executed']),
      orderSkipReason: this.asString(payload['order_skip_reason']),
      raw: event,
    };

    if (summary.signal) {
      this.increment(this.countsBySignal, summary.signal);
    }

    const orderStatus = this.resolveOrderStatus(summary);
    this.increment(this.countsByOrderStatus, orderStatus);

    if (summary.dataQualityStatus) {
      this.increment(this.countsByDataQualityStatus, summary.dataQualityStatus);
    }

    this.recentRuns.unshift(summary);
    this.trim(this.recentRuns, this.recentLimit);
    await this.persist();
    return summary;
  }

  async recordWarning(warning: WarningSummary): Promise<void> {
    this.warnings.unshift(warning);
    this.trim(this.warnings, this.warningLimit);
    await this.persist();
  }

  async setConsumerLag(summary: NatsConsumerLagSummary): Promise<void> {
    this.consumerLag = summary;
    await this.persist();
  }

  getSummary(): DashboardSummary {
    return {
      status: 'OK',
      cache: this.redisCacheService.isConnected() ? 'redis' : 'memory',
      totalEvents: this.totalEvents,
      lastEventAt: this.lastEventAt,
      countsBySubject: this.mapToObject(this.countsBySubject),
      countsBySignal: this.mapToObject(this.countsBySignal),
      countsByOrderStatus: this.mapToObject(this.countsByOrderStatus),
      countsByDataQualityStatus: this.mapToObject(this.countsByDataQualityStatus),
      recentRuns: this.recentRuns,
      warnings: this.warnings,
      consumerLag: this.consumerLag,
    };
  }

  resolveOrderStatus(summary: RunCompletedSummary): string {
    if (summary.orderAttempted && summary.orderExecuted) {
      return 'EXECUTED_OR_ACCEPTED';
    }
    if (summary.orderAttempted && !summary.orderExecuted) {
      return 'REJECTED_OR_FAILED';
    }
    return 'SKIPPED';
  }

  private async persist(): Promise<void> {
    await this.redisCacheService.saveDashboardSummary(this.getSummary());
  }

  private increment(map: Map<string, number>, key: string): void {
    map.set(key, (map.get(key) ?? 0) + 1);
  }

  private trim<T>(items: T[], limit: number): void {
    if (items.length > limit) {
      items.splice(limit);
    }
  }

  private mapToObject(map: Map<string, number>): Record<string, number> {
    return Object.fromEntries(map.entries());
  }

  private replaceMap(map: Map<string, number>, value: Record<string, number>): void {
    map.clear();
    for (const [key, count] of Object.entries(value)) {
      map.set(key, count);
    }
  }

  private getRunId(event: TradeEventDto): string | null {
    return this.asString(event.run_id ?? event.runId);
  }

  private getOccurredAt(event: TradeEventDto): string | null {
    return this.asString(event.occurred_at ?? event.occurredAt);
  }

  private asString(value: unknown): string | null {
    return typeof value === 'string' && value.length > 0 ? value : null;
  }

  private asNumber(value: unknown): number | null {
    if (typeof value === 'number') {
      return value;
    }
    if (typeof value === 'string' && value.trim().length > 0 && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return null;
  }

  private asBoolean(value: unknown): boolean | null {
    if (typeof value === 'boolean') {
      return value;
    }
    if (value === 'true') {
      return true;
    }
    if (value === 'false') {
      return false;
    }
    return null;
  }
}
