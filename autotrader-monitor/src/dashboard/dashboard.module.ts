import { Module } from '@nestjs/common';
import { RedisCacheModule } from '../redis/redis.module';
import { DashboardController } from './dashboard.controller';
import { DashboardGateway } from './dashboard.gateway';
import { DashboardService } from './dashboard.service';

@Module({
  imports: [RedisCacheModule],
  controllers: [DashboardController],
  providers: [DashboardService, DashboardGateway],
  exports: [DashboardService, DashboardGateway],
})
export class DashboardModule {}
