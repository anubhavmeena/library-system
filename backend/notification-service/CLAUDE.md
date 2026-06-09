# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Pure event-driven service — **no HTTP endpoints exposed to clients**. Consumes Kafka events and delivers notifications via Twilio WhatsApp and SendGrid email. Logs every delivery attempt (success or failure) to the `notification_logs` table.

## Port & Entry

- Port: **8085** (actuator health only — no business routes)
- Main class: `NotificationServiceApplication`
- Dependencies: PostgreSQL (notification_logs), Kafka (consumer), Twilio (WhatsApp), SendGrid (email)

## Build & Run

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

## Kafka Topics Consumed

| Topic | Consumer Group | Factory Bean | Event DTO |
|---|---|---|---|
| `booking-confirmed` | `notification-booking-group` | `bookingKafkaListenerContainerFactory` | `BookingConfirmedEvent` |
| `user-registered` | `notification-booking-group` | `bookingKafkaListenerContainerFactory` | `BookingConfirmedEvent` (reused shape) |
| `renewal-reminder` | `notification-reminder-group` | `reminderKafkaListenerContainerFactory` | `RenewalReminderEvent` |

Two separate `ConcurrentKafkaListenerContainerFactory` beans are defined in `KafkaConfig`:
- `bookingKafkaListenerContainerFactory` — concurrency 3, BATCH ack mode
- `reminderKafkaListenerContainerFactory` — concurrency 2, BATCH ack mode

`spring.json.use.type.headers: false` is set in consumer config — deserialization relies on the listener method's `@Payload` type, not Kafka message headers.
`spring.json.trusted.packages: "com.library.*"` is required for Jackson deserialization of event DTOs.

## Notification Actions per Event

| Event | Student WhatsApp | Student Email | Admin WhatsApp | Admin Email |
|---|---|---|---|---|
| `booking-confirmed` | ✅ | ✅ | ✅ (if `ADMIN_WHATSAPP` set) | ✅ always |
| `user-registered` | ✅ | ✅ | ✗ | ✗ |
| `renewal-reminder` | ✅ | ✅ | ✗ | ✗ |

Renewal reminders use urgency escalation: `⚠️ URGENT` label when `daysRemaining <= 3`, otherwise `⏰ Reminder`.

## Dev Mode (no credentials)

Both `WhatsAppService` and `EmailService` check for blank credentials at startup (`@PostConstruct`) and fall back silently:
- `TWILIO_ACCOUNT_SID` blank → `[DEV] WhatsApp → ...` logged to console, no actual send
- `SENDGRID_API_KEY` blank → `[DEV] Email → ...` logged to console, no actual send

A `NotificationLog` entry is still written for every send attempt even in dev mode. DB write failures in the log path are swallowed (`try-catch` in `saveLog()`) so a logging failure never crashes the consumer.

## Error Handling in Consumers

`NotificationConsumer` wraps each `notificationService.*` call in `try-catch`. Exceptions are logged but **not rethrown** — this prevents a failed notification from causing Kafka to redeliver the message indefinitely. If you add a dead-letter topic (e.g. `booking-confirmed.DLT`), add the rethrow there.

## `NotificationLog` Table

Every delivery attempt is persisted with: `userId`, `recipient`, `message` (truncated to 1000 chars for emails), `event`, `channel` (EMAIL/WHATSAPP), `status` (SENT/FAILED), `errorMessage`, `sentAt`, `createdAt`.

## Key Config

| Property | Env var | Default |
|---|---|---|
| `twilio.account-sid` | `TWILIO_ACCOUNT_SID` | _(blank = dev)_ |
| `twilio.auth-token` | `TWILIO_AUTH_TOKEN` | _(blank)_ |
| `twilio.whatsapp-from` | `TWILIO_WHATSAPP_FROM` | `whatsapp:+14155238886` (sandbox) |
| `sendgrid.api-key` | `SENDGRID_API_KEY` | _(blank = dev)_ |
| `notification.admin-email` | `ADMIN_EMAIL` | `admin@targetzone.co.in` |
| `notification.admin-whatsapp` | `ADMIN_WHATSAPP` | _(blank)_ |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |

## WhatsApp Number Formatting

`WhatsAppService.formatNumber()` strips all non-numeric characters except `+`, then prepends `+91` if the number doesn't already start with `+`. Store Indian numbers as 10-digit strings (no country code).
