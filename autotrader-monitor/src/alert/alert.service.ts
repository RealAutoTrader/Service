import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { TradeEventDto, WarningSummary } from '../dto/trade-event.dto';

@Injectable()
export class AlertService {
  constructor(private readonly configService: ConfigService) {}

  buildWarningIfNeeded(event: TradeEventDto, natsSubject: string): WarningSummary | null {
    const subject = event.subject ?? natsSubject;
    const payload = event.payload ?? {};

    if (subject === 'trade.order.rejected') {
      return {
        subject,
        runId: this.getRunId(event),
        occurredAt: this.getOccurredAt(event),
        level: 'critical',
        message: this.buildOrderRejectedMessage(payload),
        payload,
      };
    }

    if (subject === 'trade.data-quality.checked') {
      const status = this.asString(payload['data_quality_status']);
      if (status && status !== 'OK') {
        return {
          subject,
          runId: this.getRunId(event),
          occurredAt: this.getOccurredAt(event),
          level: status === 'BLOCKED' ? 'critical' : 'warning',
          message: `Data quality status is ${status}`,
          payload,
        };
      }
    }

    if (subject === 'trade.order.accepted' && this.readBooleanEnv('SLACK_NOTIFY_ORDER_ACCEPTED', false)) {
      return {
        subject,
        runId: this.getRunId(event),
        occurredAt: this.getOccurredAt(event),
        level: 'info',
        message: this.buildOrderAcceptedMessage(payload),
        payload,
      };
    }

    if (subject === 'trade.order.skipped' && this.readBooleanEnv('SLACK_NOTIFY_ORDER_SKIPPED', false)) {
      return {
        subject,
        runId: this.getRunId(event),
        occurredAt: this.getOccurredAt(event),
        level: 'info',
        message: this.buildOrderSkippedMessage(payload),
        payload,
      };
    }

    return null;
  }

  warn(warning: WarningSummary): void {
    console.warn(
      `[AutoTrader Warning] level=${warning.level}, subject=${warning.subject}, runId=${warning.runId}, message=${warning.message}`,
    );
  }

  async notifySlack(warning: WarningSummary): Promise<'sent' | 'skipped' | 'failed'> {
    const enabled = this.readBooleanEnv('SLACK_ALERT_ENABLED', false);
    const webhookUrl = this.configService.get<string>('SLACK_WEBHOOK_URL');
    const minLevel = this.configService.get<string>('SLACK_MIN_LEVEL') ?? 'warning';

    if (!enabled || !webhookUrl) {
      return 'skipped';
    }

    if (!this.shouldSendByLevel(warning.level, minLevel)) {
      return 'skipped';
    }

    try {
      const response = await fetch(webhookUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(this.buildSlackPayload(warning)),
      });

      if (!response.ok) {
        const body = await response.text().catch(() => '');
        console.error(`Slack webhook failed: status=${response.status}, body=${body}`);
        return 'failed';
      }

      return 'sent';
    } catch (error) {
      console.error('Slack webhook request failed', error);
      return 'failed';
    }
  }

  private buildSlackPayload(warning: WarningSummary): Record<string, unknown> {
    const emoji = warning.level === 'critical' ? ':rotating_light:' : warning.level === 'warning' ? ':warning:' : ':information_source:';
    const title = `${emoji} AutoTrader ${warning.level.toUpperCase()} - ${warning.subject}`;

    return {
      text: `${title}\nrunId=${warning.runId ?? '-'}\nmessage=${warning.message}`,
      blocks: [
        {
          type: 'section',
          text: {
            type: 'mrkdwn',
            text: `*${title}*`,
          },
        },
        {
          type: 'section',
          fields: [
            { type: 'mrkdwn', text: `*Subject*\n${warning.subject}` },
            { type: 'mrkdwn', text: `*Run ID*\n${warning.runId ?? '-'}` },
            { type: 'mrkdwn', text: `*Occurred At*\n${warning.occurredAt ?? '-'}` },
            { type: 'mrkdwn', text: `*Message*\n${warning.message}` },
          ],
        },
      ],
    };
  }

  private shouldSendByLevel(level: WarningSummary['level'], minLevel: string): boolean {
    const rank: Record<string, number> = {
      info: 1,
      warning: 2,
      critical: 3,
    };
    return (rank[level] ?? 0) >= (rank[minLevel] ?? 2);
  }

  private buildOrderRejectedMessage(payload: Record<string, unknown>): string {
    const msg1 = this.asString(payload['msg1']);
    const msgCd = this.asString(payload['msg_cd']);
    if (msg1 || msgCd) {
      return `Order rejected: msg_cd=${msgCd ?? '-'}, msg1=${msg1 ?? '-'}`;
    }
    return 'Order rejected';
  }

  private buildOrderAcceptedMessage(payload: Record<string, unknown>): string {
    const ordNo = this.asString(payload['ord_no']);
    return `Order accepted${ordNo ? `: ord_no=${ordNo}` : ''}`;
  }

  private buildOrderSkippedMessage(payload: Record<string, unknown>): string {
    const reason = this.asString(payload['order_skip_reason']);
    return `Order skipped${reason ? `: ${reason}` : ''}`;
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

  private readBooleanEnv(key: string, defaultValue: boolean): boolean {
    const value = this.configService.get<string>(key);
    if (value === undefined || value === null || value.length === 0) {
      return defaultValue;
    }
    return value.toLowerCase() === 'true';
  }
}
