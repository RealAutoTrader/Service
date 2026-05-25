import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  connect,
  ConnectionOptions,
  JetStreamClient,
  JetStreamManager,
  NatsConnection,
  RetentionPolicy,
  StorageType,
} from 'nats';
import { NatsConsumerLagSummary } from '../dto/trade-event.dto';

@Injectable()
export class NatsJetStreamService implements OnModuleInit, OnModuleDestroy {
  private connection: NatsConnection | null = null;
  private jetStreamClient: JetStreamClient | null = null;
  private jetStreamManager: JetStreamManager | null = null;

  constructor(private readonly configService: ConfigService) {}

  async onModuleInit(): Promise<void> {
    await this.connect();
    await this.ensureStream();
  }

  async onModuleDestroy(): Promise<void> {
    if (!this.connection) {
      return;
    }

    await this.connection.drain();
    await this.connection.closed();
    this.connection = null;
    this.jetStreamClient = null;
    this.jetStreamManager = null;
  }

  getJetStream(): JetStreamClient {
    if (!this.jetStreamClient) {
      throw new Error('JetStream client is not initialized');
    }
    return this.jetStreamClient;
  }

  getJetStreamManager(): JetStreamManager {
    if (!this.jetStreamManager) {
      throw new Error('JetStream manager is not initialized');
    }
    return this.jetStreamManager;
  }

  getStreamName(): string {
    return this.configService.get<string>('NATS_STREAM_NAME') ?? 'TRADE_EVENTS';
  }

  getSubjectFilter(): string {
    return this.configService.get<string>('NATS_SUBJECT_FILTER') ?? 'trade.>';
  }

  getDurableName(): string {
    return this.configService.get<string>('NATS_DURABLE_NAME') ?? 'autotrader-monitor-v2';
  }

  async getConsumerLagSummary(): Promise<NatsConsumerLagSummary> {
    const streamName = this.getStreamName();
    const durableName = this.getDurableName();
    const subjectFilter = this.getSubjectFilter();
    const updatedAt = new Date().toISOString();

    try {
      const manager = this.getJetStreamManager();
      const consumers = (manager as any).consumers;
      const info = await consumers.info(streamName, durableName);

      const deliveredConsumerSeq = this.asNumber(info?.delivered?.consumer_seq);
      const ackFloorConsumerSeq = this.asNumber(info?.ack_floor?.consumer_seq);
      const lagByConsumerSeq =
        deliveredConsumerSeq !== null && ackFloorConsumerSeq !== null
          ? Math.max(deliveredConsumerSeq - ackFloorConsumerSeq, 0)
          : null;

      return {
        streamName,
        durableName,
        subjectFilter,
        numPending: this.asNumber(info?.num_pending),
        numAckPending: this.asNumber(info?.num_ack_pending),
        numRedelivered: this.asNumber(info?.num_redelivered),
        numWaiting: this.asNumber(info?.num_waiting),
        deliveredConsumerSeq,
        ackFloorConsumerSeq,
        lagByConsumerSeq,
        updatedAt,
      };
    } catch (error) {
      return {
        streamName,
        durableName,
        subjectFilter,
        numPending: null,
        numAckPending: null,
        numRedelivered: null,
        numWaiting: null,
        deliveredConsumerSeq: null,
        ackFloorConsumerSeq: null,
        lagByConsumerSeq: null,
        updatedAt,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  private async connect(): Promise<void> {
    const servers = this.configService.get<string>('NATS_URL') ?? 'nats://localhost:4222';
    const options: ConnectionOptions = {
      servers,
      name: 'autotrader-monitor-service-v2',
      reconnect: true,
      maxReconnectAttempts: -1,
    };

    this.connection = await connect(options);
    this.jetStreamClient = this.connection.jetstream();
    this.jetStreamManager = await this.connection.jetstreamManager();

    console.log(`connected to NATS: ${servers}`);
  }

  private async ensureStream(): Promise<void> {
    if (!this.jetStreamManager) {
      throw new Error('JetStream manager is not initialized');
    }

    const createIfMissing = this.readBooleanEnv('NATS_CREATE_STREAM_IF_MISSING', true);
    const streamName = this.getStreamName();
    const subjectFilter = this.getSubjectFilter();

    try {
      await this.jetStreamManager.streams.info(streamName);
      console.log(`JetStream stream ready: ${streamName}`);
      return;
    } catch (error) {
      if (!createIfMissing) {
        throw new Error(
          `JetStream stream '${streamName}' was not found and NATS_CREATE_STREAM_IF_MISSING=false`,
        );
      }
    }

    await this.jetStreamManager.streams.add({
      name: streamName,
      subjects: [subjectFilter],
      storage: StorageType.File,
      retention: RetentionPolicy.Limits,
    });

    console.log(`JetStream stream created: ${streamName}, subjects=[${subjectFilter}]`);
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

  private readBooleanEnv(key: string, defaultValue: boolean): boolean {
    const value = this.configService.get<string>(key);
    if (value === undefined || value === null || value.length === 0) {
      return defaultValue;
    }
    return value.toLowerCase() === 'true';
  }
}
