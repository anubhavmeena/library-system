# Running All Services in Dev Mode

## Single Command (start everything)

```bash
docker-compose -f docker-compose.infra.yml -f docker-compose.services.yml up -d
```

This starts infra (Postgres, Redis, Zookeeper, Kafka) and all microservices + frontend in one shot.

## First Time Setup

```bash
cp .env.example .env   # fill in real creds if needed
```

Leaving Twilio / Razorpay / SendGrid keys empty is fine in dev:
- OTP: any input accepted as `123456`
- Payments: `dev_order_*` ID generated, HMAC verification skipped
- Notifications: logged only, not sent

## Common Variants

```bash
# Rebuild images after code changes
docker-compose -f docker-compose.infra.yml -f docker-compose.services.yml up -d --build

# Tail all logs
docker-compose -f docker-compose.services.yml logs -f

# Tail logs for a single service
docker-compose -f docker-compose.services.yml logs -f auth-service

# Tear down services (keep volumes)
docker-compose -f docker-compose.services.yml down
docker-compose -f docker-compose.infra.yml down

# Tear down + wipe all data volumes
docker-compose -f docker-compose.infra.yml down -v
```

## Access Points

| URL                        | What                  |
|----------------------------|-----------------------|
| http://localhost:3000      | Frontend              |
| http://localhost:8080      | API Gateway           |
| http://localhost:8090      | Kafka UI              |
