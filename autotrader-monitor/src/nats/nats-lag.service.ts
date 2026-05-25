import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DashboardService } from '../dashboard/dashboard.service';
import { NatsConsumerLagSummary } from '../dto/trade-event.dto';
import { MetricsService } from '../metrics/metrics.service';
import { NatsJetStreamService } from './nats-jetstream.service';

@Injectable()
export class NatsLagService implements OnModuleInit, OnModuleDestroy {
  private timer: NodeJS.Timeout | null = null;
  private latest: NatsConsumerLagSummary | null = null;

  constructor(
    private readonly configService: ConfigService,
    private readonly natsService: NatsJetStreamService,
    private readonly dashboardService: DashboardService,
    private readonly metricsService: MetricsService,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.refresh();
    const intervalMs = Number(this.configService.get<string>('NATS_LAG_POLL_INTERVAL_MS') ?? 5000);
    this.timer = setInterval(() => {
      void this.refresh();
    }, intervalMs);
  }

  onModuleDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  async refresh(): Promise<NatsConsumerLagSummary> {
    const summary = await this.natsService.getConsumerLagSummary();
    this.latest = summary;
    this.metricsService.setConsumerLag(summary);
    await this.dashboardService.setConsumerLag(summary);
    return summary;
  }

  getLatest(): NatsConsumerLagSummary | null {
    return this.latest;
  }
}
