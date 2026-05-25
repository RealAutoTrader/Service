import { WebSocketGateway, WebSocketServer } from '@nestjs/websockets';
import { Server } from 'socket.io';
import { RunCompletedSummary, WarningSummary } from '../dto/trade-event.dto';

@WebSocketGateway({
  cors: {
    origin: '*',
  },
})
export class DashboardGateway {
  @WebSocketServer()
  private server!: Server;

  emitRunCompleted(summary: RunCompletedSummary): void {
    this.server.emit('trade.run.completed', summary);
  }

  emitWarning(warning: WarningSummary): void {
    this.server.emit('trade.warning', warning);
  }

  emitSummaryUpdated(summary: unknown): void {
    this.server.emit('dashboard.summary.updated', summary);
  }
}
