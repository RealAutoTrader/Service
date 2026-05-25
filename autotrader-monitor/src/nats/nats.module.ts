import { Module } from '@nestjs/common';
import { DashboardModule } from '../dashboard/dashboard.module';
import { AlertModule } from '../alert/alert.module';
import { MetricsModule } from '../metrics/metrics.module';
import { NatsCoreModule } from './nats-core.module';
import { NatsLagService } from './nats-lag.service';
import { NatsMonitorController } from './nats-monitor.controller';
import { TradeEventConsumerService } from './trade-event-consumer.service';

@Module({
  imports: [NatsCoreModule, DashboardModule, AlertModule, MetricsModule],
  controllers: [NatsMonitorController],
  providers: [NatsLagService, TradeEventConsumerService],
  exports: [NatsLagService],
})
export class NatsModule {}
