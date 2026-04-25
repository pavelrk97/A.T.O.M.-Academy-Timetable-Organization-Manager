# Production Deploy

Этот сценарий предназначен для VPS с публичным доменом и Docker.

## Что наружу публикуется

- `80/tcp`
- `443/tcp`

Внутренние сервисы `postgres`, `identity-service`, `schedule-service`, `import-service` и `api-gateway` наружу не публикуются.

## Быстрый запуск

1. Скопируй шаблон production-переменных:

```bash
cp .env.production.example .env
```

2. Замени в `.env`:

- `PUBLIC_HOSTNAME`
- `ACME_EMAIL`
- `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `IDENTITY_INTERNAL_API_KEY`
- `SCHEDULE_INTERNAL_API_KEY`

3. Подними production-стек:

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

4. Проверь состояние:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f reverse-proxy
```

## Маршрутизация

- `/` -> frontend (`Next.js production`)
- `/api/*` -> `api-gateway`

## HTTPS

`reverse-proxy` использует Caddy и автоматически выпускает TLS-сертификат для `PUBLIC_HOSTNAME`, если:

- домен уже направлен на сервер
- открыты `80/tcp` и `443/tcp`
- нет другого процесса, который занимает эти порты

## Обновление

```bash
git pull
docker compose -f docker-compose.prod.yml up --build -d
```
