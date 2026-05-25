import { Controller, Get } from '@nestjs/common';
import { NatsLagService } from './nats-lag.service';

@Controller('nats')
export class NatsMonitorController {
  constructor(private readonly natsLagService: NatsLagService) {}

  @Get('lag')
  async lag() {
    return this.natsLagService.refresh();
  }
}
