import { Controller, Get } from '@nestjs/common';
import { RedisCacheService } from '../redis/redis-cache.service';

@Controller('health')
export class HealthController {
  constructor(private readonly redisCacheService: RedisCacheService) {}

  @Get()
  async health() {
    return {
      status: 'UP',
      service: 'autotrader-monitor-service-v2',
      now: new Date().toISOString(),
      redis: await this.redisCacheService.ping(),
    };
  }
}
