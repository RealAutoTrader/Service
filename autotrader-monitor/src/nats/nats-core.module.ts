import { Module } from '@nestjs/common';
import { NatsJetStreamService } from './nats-jetstream.service';

@Module({
  providers: [NatsJetStreamService],
  exports: [NatsJetStreamService],
})
export class NatsCoreModule {}
