# A.T.O.M. Docker Run

Проект можно поднять одной командой через Docker Compose:

```bash
docker compose up --build
```

## Что входит в compose

- `postgres` - общая база данных PostgreSQL
- `migrations` - одноразовый запуск Flyway для инициализации схемы
- `identity-service` - пользователи, роли, профиль
- `schedule-service` - расписание, аудит, workload, импорт CSV
- `import-service` - прокси-импорт CSV
- `api-gateway` - внешний вход в API

## Быстрый старт

1. Скопируй пример переменных окружения:

```bash
cp .env.example .env
```

Для Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

2. Подними все сервисы:

```bash
docker compose up --build
```

3. Внешние адреса после старта:

- gateway: `http://localhost:8081`
- identity-service: `http://localhost:8082`
- schedule-service: `http://localhost:8080`
- import-service: `http://localhost:8083`
- postgres: `localhost:5432`

## Тестовые пользователи

Инициализируются автоматически при пустой базе:

- `admin / admin123`
- `editor / editor123`
- `instructor / instructor123`

## Полезные команды

Остановить сервисы:

```bash
docker compose down
```

Остановить и удалить volume с БД и архивом импорта:

```bash
docker compose down -v
```

Посмотреть логи конкретного сервиса:

```bash
docker compose logs -f schedule-service
```

## Переменные окружения

Базовые настройки лежат в `.env.example`:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `IDENTITY_INTERNAL_API_KEY`
- `SCHEDULE_INTERNAL_API_KEY`
- `ATOM_IMPORT_ARCHIVE_DIR`

## Как это работает

- `migrations` сначала накатывает SQL из `db/migration/V1__init_schema.sql`
- DB-сервисы в `prod` больше не создают таблицы сами, а только валидируют схему
- `schedule-service` хранит текущий и предыдущий CSV-источник в отдельном volume

## Фронт

Compose сейчас поднимает backend-контур. Фронт `docs/V0 UI` можно запускать отдельно и направлять на:

- `http://localhost:8081`
