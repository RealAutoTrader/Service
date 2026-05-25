export interface TradeEventDto {
  event_id?: string;
  eventId?: string;
  subject?: string;
  run_id?: string;
  runId?: string;
  occurred_at?: string;
  occurredAt?: string;
  payload?: Record<string, unknown>;
}

export interface RunCompletedSummary {
  runId: string | null;
  occurredAt: string | null;
  symbol: string | null;
  signal: string | null;
  dataQualityStatus: string | null;
  orderSide: string | null;
  orderQty: number | null;
  orderAttempted: boolean | null;
  orderExecuted: boolean | null;
  orderSkipReason: string | null;
  raw: TradeEventDto;
}

export interface WarningSummary {
  subject: string;
  runId: string | null;
  occurredAt: string | null;
  level: 'info' | 'warning' | 'critical';
  message: string;
  payload: Record<string, unknown>;
}

export interface NatsConsumerLagSummary {
  streamName: string;
  durableName: string;
  subjectFilter: string;
  numPending: number | null;
  numAckPending: number | null;
  numRedelivered: number | null;
  numWaiting: number | null;
  deliveredConsumerSeq: number | null;
  ackFloorConsumerSeq: number | null;
  lagByConsumerSeq: number | null;
  updatedAt: string;
  error?: string;
}

export interface DashboardSummary {
  status: 'OK';
  cache: 'memory' | 'redis';
  totalEvents: number;
  lastEventAt: string | null;
  countsBySubject: Record<string, number>;
  countsBySignal: Record<string, number>;
  countsByOrderStatus: Record<string, number>;
  countsByDataQualityStatus: Record<string, number>;
  recentRuns: RunCompletedSummary[];
  warnings: WarningSummary[];
  consumerLag: NatsConsumerLagSummary | null;
}
