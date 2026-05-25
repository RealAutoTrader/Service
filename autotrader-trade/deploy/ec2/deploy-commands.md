# EC2 배포 명령 예시

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER
newgrp docker

mkdir -p ~/autotrader/deploy/ec2 ~/autotrader/deploy/nginx
cd ~/autotrader/deploy/ec2
cp .env.example .env
vi .env

docker compose -f docker-compose.ec2.yml pull
docker compose -f docker-compose.ec2.yml up -d

docker compose -f docker-compose.ec2.yml logs -f spring-app
```

NATS JetStream 동작 확인:

```bash
docker exec -it autotrader-nats nats stream ls
# nats CLI가 이미지에 없으면 별도 nats-box 컨테이너를 사용한다.
```
