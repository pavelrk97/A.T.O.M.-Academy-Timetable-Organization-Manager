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

Туда же добавь имя compose-проекта — иначе деплой может поднять параллельный стек, который Caddy не обслуживает:

```bash
echo 'COMPOSE_PROJECT_NAME=atom' >> .env
```

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

### Фронтенд — автоматически (CI/CD)

Фронт деплоится сам через GitHub Actions (`.github/workflows/deploy-frontend.yml`): пушишь в `main` или `engl-version` → Actions собирает образ, кладёт в GHCR (`ghcr.io/pavelrk97/atom-frontend`), заходит по SSH и делает `docker compose pull frontend` + `up -d frontend`. Руками ничего делать не нужно.

Секреты репозитория для деплоя: `SSH_HOST`, `SSH_USER`, `SSH_KEY`. Образ в GHCR — публичный, поэтому сервер тянет его без логина.

### Бэкенд — вручную

Java-сервисы (`api-gateway`, `schedule-service`, `identity-service`, `import-service`) пока в CI не собираются, обновляются на сервере:

```bash
git pull
docker compose -f docker-compose.prod.yml up --build -d \
  api-gateway schedule-service identity-service import-service
```
