import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { NestExpressApplication } from '@nestjs/platform-express';
import { join } from 'path';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, {
    cors: true,
  });

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
    }),
  );

  app.useStaticAssets(join(__dirname, '..', 'public'));

  const port = Number(process.env.PORT ?? 3001);
  await app.listen(port);

  console.log(`NestJS Monitor Service is running on http://localhost:${port}`);
}

bootstrap().catch((error) => {
  console.error('failed to bootstrap NestJS Monitor Service', error);
  process.exit(1);
});
