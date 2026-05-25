import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { NatsModule } from './nats/nats.module';
import { DashboardModule } from './dashboard/dashboard.module';
import { AlertModule } from './alert/alert.module';
import { HealthModule } from './health/health.module';
import { MetricsModule } from './metrics/metrics.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env'],
    }),
    DashboardModule,
    AlertModule,
    MetricsModule,
    NatsModule,
    HealthModule,
  ],
})
export class AppModule {}
