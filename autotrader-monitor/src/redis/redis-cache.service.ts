import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';
import { DashboardSummary } from '../dto/trade-event.dto';

@Injectable()
export class RedisCacheService implements OnModuleInit, OnModuleDestroy {
  private client: Redis | null = null;
  private enabled = false;
  private prefix = 'autotrader:monitor';
  private ttlSeconds = 86400;
  private connected = false;

  constructor(private readonly configService: ConfigService) {}

  async onModuleInit(): Promise<void> {
    this.enabled = this.readBooleanEnv('REDIS_ENABLED', true);
    this.prefix = this.configService.get<string>('REDIS_KEY_PREFIX') ?? 'autotrader:monitor';
    this.ttlSeconds = Number(this.configService.get<string>('REDIS_CACHE_TTL_SECONDS') ?? 86400);

    if (!this.enabled) {
      console.log('Redis cache disabled: REDIS_ENABLED=false');
      return;
    }

    const url = this.configService.get<string>('REDIS_URL') ?? 'redis://localhost:6379';
    this.client = new Redis(url, {
      lazyConnect: true,
      maxRetriesPerRequest: 2,
      enableReadyCheck: true,
    });

    this.client.on('connect', () => {
      this.connected = true;
      console.log(`connected to Redis: ${this.maskRedisUrl(url)}`);
    });

    this.client.on('error', (error) => {
      this.connected = false;
      console.error('Redis error', error);
    });

    try {
      await this.client.connect();
    } catch (error) {
      this.connected = false;
      console.error('failed to connect Redis. running with memory cache only', error);
    }
  }

  async onModuleDestroy(): Promise<void> {
    if (!this.client) {
      return;
    }
    await this.client.quit();
    this.client = null;
    this.connected = false;
  }

  isEnabled(): boolean {
    return this.enabled;
  }

  isConnected(): boolean {
    return this.connected;
  }

  async loadDashboardSummary(): Promise<DashboardSummary | null> {
    if (!this.isUsable()) {
      return null;
    }

    const raw = await this.client!.get(this.key('dashboard:summary'));
    if (!raw) {
      return null;
    }

    return JSON.parse(raw) as DashboardSummary;
  }

  async saveDashboardSummary(summary: DashboardSummary): Promise<void> {
    if (!this.isUsable()) {
      return;
    }

    const value = JSON.stringify(summary);
    if (this.ttlSeconds > 0) {
      await this.client!.set(this.key('dashboard:summary'), value, 'EX', this.ttlSeconds);
      return;
    }
    await this.client!.set(this.key('dashboard:summary'), value);
  }

  async ping(): Promise<'UP' | 'DOWN' | 'DISABLED'> {
    if (!this.enabled) {
      return 'DISABLED';
    }
    if (!this.client) {
      return 'DOWN';
    }
    try {
      const result = await this.client.ping();
      return result === 'PONG' ? 'UP' : 'DOWN';
    } catch {
      return 'DOWN';
    }
  }

  private isUsable(): boolean {
    return this.enabled && this.connected && this.client !== null;
  }

  private key(suffix: string): string {
    return `${this.prefix}:${suffix}`;
  }

  private readBooleanEnv(key: string, defaultValue: boolean): boolean {
    const value = this.configService.get<string>(key);
    if (value === undefined || value === null || value.length === 0) {
      return defaultValue;
    }
    return value.toLowerCase() === 'true';
  }

  private maskRedisUrl(url: string): string {
    return url.replace(/:\/\/[^:@]+:[^@]+@/, '://***:***@');
  }
}
