# A.T.O.M.

**Academy Timetable Organization Manager** — система управления учебным расписанием с ролевым доступом, импортом из CSV, учётом нагрузки преподавателей, асинхронным аудит-логом изменений (события через Kafka) и двуязычным интерфейсом (RU/EN).

Микросервисная архитектура на **Spring Boot 3.2.5 (Java 21)** + фронтенд на **Next.js 16**, единая база PostgreSQL, миграции Flyway, авторизация через JWT.

В продакшне крутится на [https://prk97.ru](https://prk97.ru) (Caddy + автоматический Let's Encrypt). Фронт деплоится автоматически через GitHub Actions → GHCR (см. [`docs/PRODUCTION_DEPLOY.md`](docs/PRODUCTION_DEPLOY.md)).

---

## Содержание

- [Стек](#стек)
- [Архитектура](#архитектура)
- [Быстрый старт (Docker)](#быстрый-старт-docker)
- [Дальше читать](#дальше-читать)

---

## Стек

| Слой | Технологии |
|---|---|
| Backend | Java 21, Spring Boot 3.2.5, Spring Security (OAuth2 Resource Server / JWT), Spring Data JPA, Spring Cloud OpenFeign |
| База | PostgreSQL 16, Flyway (миграции `db/migration/V1..V5`) |
| События | Apache Kafka 3.8 (KRaft, без ZooKeeper), spring-kafka — асинхронный аудит изменений занятий |
| Импорт | OpenCSV, scheduled auto-import, manual upload |
| Frontend | Next.js 16, React 19, Tailwind CSS, Radix UI, i18n RU/EN |
| Прокси (prod) | Caddy 2.10 (HTTPS, ACME) |
| Контейнеризация | Docker Compose (отдельные dev и prod compose-файлы) |
| CI/CD | GitHub Actions → GHCR, автодеплой фронта по SSH |

---

## Архитектура

Четыре микросервиса делят одну PostgreSQL-базу. Внешний клиент всегда ходит через **API Gateway**.

```
       ┌─────────────┐
       │   Browser   │
       └──────┬──────┘
              │ HTTPS
       ┌──────▼──────┐
       │   Caddy     │  reverse-proxy (только в prod)
       │  80/443     │  /  → frontend  •  /api/* → api-gateway
       └──────┬──────┘
              │
   ┌──────────┴──────────┐
   │                     │
┌──▼────────┐    ┌───────▼─────────┐
│ frontend  │    │   api-gateway   │  :8081
│ Next.js   │    └────────┬────────┘
└───────────┘             │ Feign + JWT
              ┌───────────┼────────────────────┐
              │           │                    │
       ┌──────▼─────┐ ┌──▼────────┐  ┌────────▼────────┐
       │ identity   │ │ schedule  │  │ import-service  │
       │ :8082      │ │ :8080     │  │ :8083           │
       └──────┬─────┘ └─────┬─────┘  └────────┬────────┘
              │             │                  │
              └─────────────┼──────────────────┘
                            │
                  ┌─────────┴─────────┐
                  │                   │
             ┌────▼─────┐      ┌──────▼──────┐
             │ Postgres │      │    Kafka    │  события аудита
             └──────────┘      │    :9092    │  (только schedule-service)
                               └─────────────┘
```

| Сервис | Порт (внутри сети) | Ответственность |
|---|---|---|
| `api-gateway` (`schedule-api-service`) | 8081 | Единый внешний вход; проксирует, агрегирует `my-*` для ЛК |
| `schedule-service` (`schedule-import-parser-core`) | 8080 | CRUD групп/дней/занятий, импорт CSV, workload, auto-import, аудит |
| `identity-service` | 8082 | Аутентификация (JWT HS256), пользователи, роли, журнал входов |
| `import-service` | 8083 | Внешний ingress `POST /api/import/csv` (лимит 200 МБ) |

**Межсервисное взаимодействие** — Spring Cloud OpenFeign, **JWT** пользователя пробрасывается в `Authorization`, плюс служебные заголовки `X-Internal-API-Key` (`IDENTITY_INTERNAL_API_KEY`, `SCHEDULE_INTERNAL_API_KEY`) для internal-эндпоинтов.

### Аудит через события (Kafka)

Изменения занятий пишутся в аудит-лог асинхронно:

```
LessonService ──▶ topic atom.lesson-audit ──▶ consumer group schedule-service-audit ──▶ change_logs
```

- Продюсер публикует событие **после commit'а** транзакции (иначе при откате в топик улетел бы аудит несостоявшегося изменения); ключ сообщения — id занятия, поэтому события одного занятия обрабатываются по порядку.
- Формат — JSON (`spring-kafka` JsonSerializer/JsonDeserializer, десериализация ограничена доверенным пакетом `ru.dto`).
- Управляется флагом `ATOM_KAFKA_ENABLED` (по умолчанию `false` — аудит пишется синхронно в БД, Kafka-бины не создаются). В dev-compose флаг включён, брокер поднимается автоматически.

---

## Быстрый старт (Docker)

### Dev — backend без HTTPS и фронтенда в compose

```bash
cp .env.example .env
docker compose up --build
```

После старта:
- API Gateway → http://localhost:8081
- identity-service → http://localhost:8082
- schedule-service → http://localhost:8080
- import-service → http://localhost:8083
- PostgreSQL → localhost:5432
- Kafka → localhost:9092

### Backend (Maven multi-module)

```bash
mvn -B clean package -DskipTests              # собрать всё
mvn -B -pl schedule-import-parser-core -am test    # тесты конкретного модуля
mvn test                                       # все тесты
```

### Frontend

```bash
cd frontend
pnpm install
pnpm run dev      # http://localhost:3000
pnpm run build    # production build
pnpm run lint     # ESLint
```

В dev фронтенд проксирует `/api/*` на `http://localhost:8081`. Конфиг в `frontend/next.config.mjs` (или см. `lib/api.ts`).

### Базовые команды Docker

```bash
docker compose logs -f schedule-service        # логи одного сервиса
docker compose ps                              # статус
docker compose -f docker-compose.prod.yml up -d --build frontend   # пересобрать только фронт
```

Посмотреть события аудита в топике с самого начала:

```bash
docker exec -it atom-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic atom.lesson-audit --from-beginning
```

### Postman

В папке [`postman/`](postman/) лежат коллекции, в т.ч. `smoke` для быстрой проверки после изменений.

---

## Дальше читать

- 📖 [`docs/PROJECT_DOCUMENTATION.md`](docs/PROJECT_DOCUMENTATION.md) — **полная документация**: домен, роли, все эндпоинты, схема БД, frontend
- 📥 [`docs/CSV_FORMAT.md`](docs/CSV_FORMAT.md) — спецификация формата CSV для импорта расписания
- 🚢 [`docs/PRODUCTION_DEPLOY.md`](docs/PRODUCTION_DEPLOY.md) — production deploy с Caddy / HTTPS

---
