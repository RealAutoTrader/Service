import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { consumerOpts, createInbox, JetStreamSubscription, StringCodec } from 'nats';
import { AlertService } from '../alert/alert.service';
import { DashboardGateway } from '../dashboard/dashboard.gateway';
import { DashboardService } from '../dashboard/dashboard.service';
import { TradeEventDto } from '../dto/trade-event.dto';
import { MetricsService } from '../metrics/metrics.service';
import { NatsJetStreamService } from './nats-jetstream.service';
import { NatsLagService } from './nats-lag.service';

@Injectable()
export class TradeEventConsumerService implements OnModuleInit, OnModuleDestroy {
  private readonly stringCodec = StringCodec();
  private subscription: JetStreamSubscription | null = null;
  private consuming = false;

  constructor(
    private readonly natsService: NatsJetStreamService,
    private readonly dashboardService: DashboardService,
    private readonly dashboardGateway: DashboardGateway,
    private readonly alertService: AlertService,
    private readonly metricsService: MetricsService,
    private readonly natsLagService: NatsLagService,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.subscribe();
  }

  async onModuleDestroy(): Promise<void> {
    this.consuming = false;
    this.subscription?.unsubscribe();
  }

  private async subscribe(): Promise<void> {
    const jetStream = this.natsService.getJetStream();
    const streamName = this.natsService.getStreamName();
    const subjectFilter = this.natsService.getSubjectFilter();
    const durableName = this.natsService.getDurableName();

    const opts = consumerOpts();
    opts.durable(durableName);
    opts.manualAck();
    opts.ackExplicit();
    opts.deliverAll();
    opts.filterSubject(subjectFilter);
    opts.deliverTo(createInbox());

    this.subscription = await jetStream.subscribe(subjectFilter, opts);
    this.consuming = true;

    console.log(
      `subscribed to JetStream: stream=${streamName}, subject=${subjectFilter}, durable=${durableName}`,
    );

    void this.consumeLoop(this.subscription);
  }

  private async consumeLoop(subscription: JetStreamSubscription): Promise<void> {
    try {
      for await (const message of subscription) {
        if (!this.consuming) {
          break;
        }

        let subject = message.subject;
        const endTimer = this.metricsService.startEventTimer(subject);

        try {
          const event = this.decodeEvent(message.data);
          subject = event.subject ?? message.subject;
          this.metricsService.incEvent(subject);

          await this.dashboardService.recordEvent(event, message.subject);

          if (subject === 'trade.run.completed') {
            const summary = await this.dashboardService.recordRunCompleted(event);
            const orderStatus = this.dashboardService.resolveOrderStatus(summary);
            this.metricsService.incRunCompleted(summary.symbol, summary.signal, orderStatus);
            this.dashboardGateway.emitRunCompleted(summary);
            this.dashboardGateway.emitSummaryUpdated(this.dashboardService.getSummary());
          }

          const warning = this.alertService.buildWarningIfNeeded(event, message.subject);
          if (warning) {
            this.alertService.warn(warning);
            this.metricsService.incWarning(warning);
            await this.dashboardService.recordWarning(warning);
            this.dashboardGateway.emitWarning(warning);
            this.dashboardGateway.emitSummaryUpdated(this.dashboardService.getSummary());

            const slackStatus = await this.alertService.notifySlack(warning);
            this.metricsService.incSlackAlert(slackStatus);
          }

          message.ack();
          endTimer('success');
          void this.natsLagService.refresh();
        } catch (error) {
          this.metricsService.incEventFailure(subject);
          endTimer('failure');
          console.error('failed to process NATS trade event', error);
          message.nak();
        }
      }
    } catch (error) {
      if (this.consuming) {
        console.error('NATS JetStream consume loop stopped unexpectedly', error);
      }
    }
  }

  private decodeEvent(data: Uint8Array): TradeEventDto {
    const text = this.stringCodec.decode(data);
    const parsed = JSON.parse(text) as TradeEventDto;

    if (!parsed || typeof parsed !== 'object') {
      throw new Error('invalid trade event payload');
    }

    return parsed;
  }
}
