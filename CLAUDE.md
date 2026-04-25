# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## О проекте

A.T.O.M. (Academy Timetable Organization Manager) — микросервисная система для управления учебным расписанием с ролевым доступом, учётом нагрузки и аудит-логом. Документация и интерфейс на русском языке.

## Команды

### Бэкенд (Maven multi-module, Java 21)

```bash
mvn -B clean package -DskipTests                          # Собрать все модули
mvn -B -pl schedule-import-parser-core -am test           # Тесты одного модуля
mvn test                                                  # Запустить все тесты
```

### Docker

```bash
docker compose up --build                                 # Dev-окружение
docker compose -f docker-compose.prod.yml up --build -d  # Продакшн
```

### Фронтенд (`docs/V0 UI/`)

```bash
cd "docs/V0 UI"
pnpm install
pnpm run dev    # Dev-сервер
pnpm run build  # Сборка для продакшна
pnpm run lint   # ESLint
```

## Архитектура

Четыре сервиса на Spring Boot 3.2.5 используют одну базу PostgreSQL с миграциями Flyway:

| Сервис | Модуль | Порт | Ответственность |
|---|---|---|---|
| API Gateway | `schedule-api-service` | 8081 | Единая точка входа; проксирует запросы и агрегирует данные «личного кабинета» (`my-*`) |
| Schedule Service | `schedule-import-parser-core` | 8080 | Основной CRUD: группы, дни, занятия; импорт CSV; расчёт нагрузки; аудит/уведомления |
| Identity Service | `identity-service` | 8082 | Аутентификация (Basic; JWT подготовлен), роли, профили пользователей |
| Import Service | `import-service` | 8083 | Приём файлов (лимит 200 МБ); оркестрирует импорт через schedule-service |

**Путь запроса:** `Клиент → API Gateway (8081) → нужный сервис`

**Межсервисное взаимодействие** — Spring Cloud OpenFeign с API-ключами в заголовках (`IDENTITY_INTERNAL_API_KEY`, `SCHEDULE_INTERNAL_API_KEY`).

**Фронтенд** — отдельное Next.js 16 приложение в `docs/V0 UI/` на Tailwind CSS и Radix UI.

В продакшне Caddy (`docker/Caddyfile`) терминирует HTTPS и роутит `/api/*` на gateway, `/` — на фронтенд.

## База данных

Схема находится в `db/migration/` (Flyway V1–V3). Основные таблицы: `users`, `groups`, `days`, `lessons`, `lesson_instructors`, `user_groups`, `change_logs`, `notifications`. Оптимистичная блокировка через `@Version` защищает от конкурентных изменений занятий.

Для тестов используется H2, для dev/prod — PostgreSQL. Дефолтные значения для разработки в `.env.example`.

## Роли и тестовые пользователи

Роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`, `PUBLIC`

| Логин | Пароль | Роль |
|---|---|---|
| admin | admin123 | ADMIN |
| editor | editor123 | EDITOR |
| instructor | instructor123 | INSTRUCTOR |

## Ключевые конфигурации

- **`.env` / `.env.example`** — все секреты и URL сервисов для Docker Compose
- **`docker/service.Dockerfile`** — многоэтапная сборка (Maven 3.9.9 → Alpine JRE 21): собирает весь parent POM, затем копирует JAR нужного сервиса
- Каждый сервис имеет свой `src/main/resources/application.yml` со Spring-профилями `dev` и `prod`
- Архив импорта хранится в `/app/data/import-archive` (Docker volume)

## Тестирование API

Postman-коллекции находятся в `postman/`, включая smoke-коллекцию для быстрой проверки после изменений.

Полная документация эндпоинтов с требованиями по авторизации — в `docs/PROJECT_DOCUMENTATION.md`.
