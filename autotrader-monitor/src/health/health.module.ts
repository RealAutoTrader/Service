import { Module } from '@nestjs/common';
import { RedisCacheModule } from '../redis/redis.module';
import { HealthController } from './health.controller';

@Module({
  imports: [RedisCacheModule],
  controllers: [HealthController],
})
export class HealthModule {}
