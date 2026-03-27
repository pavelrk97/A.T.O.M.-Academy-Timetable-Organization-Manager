# schedule-service

Основной сервис домена расписания.

## Что внутри
- публичное расписание: `GET /api/public/schedule`
- группы: `GET/POST/PUT/DELETE /api/groups`
- занятия: `GET/POST/PUT/DELETE /api/lessons`
- история изменений: `GET /api/lessons/{id}/history`
- workload: `GET /api/workload`
- внутренний CSV import endpoint: `POST /internal/import/csv`

## Важные правила
- роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`
- `ADMIN` и `EDITOR` могут создавать, менять и удалять занятия
- `INSTRUCTOR` не может создавать, менять и удалять занятия
- optimistic locking по `version`
- аудит пишется на create/update/delete занятия
- workload начисляет полную `durationHours` каждому назначенному инструктору

## Авторизация
- сервис использует `Basic Auth`
- user details для аутентификации подтягиваются из `identity-service`

## Хранение данных
- dev: общий file-based H2 instance
- prod: PostgreSQL

## Примечания
- JSON-import удалён из кода
- профиль по умолчанию: `dev`
- Maven wrapper отсутствует
