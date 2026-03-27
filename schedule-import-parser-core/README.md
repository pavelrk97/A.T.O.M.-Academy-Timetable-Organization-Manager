# schedule-import-parser-core

Backend-сервис MVP системы управления расписанием академии.

## Что есть
- публичный просмотр расписания: `GET /api/public/schedule`
- Basic Auth
- роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`
- пользователи: `GET/POST/PUT /api/users`
- группы: `GET/POST/PUT/DELETE /api/groups`
- занятия: `GET/POST/PUT/DELETE /api/lessons`
- история изменений: `GET /api/lessons/{id}/history`
- защита от конфликтов по `version`
- импорт только CSV: `POST /api/import/csv`
- workload: `GET /api/workload`

## Дефолтные пользователи
- `admin / admin123`
- `editor / editor123`
- `instructor / instructor123`

## Правила редактирования
- `ADMIN` и `EDITOR` могут создавать, менять и удалять занятия
- `INSTRUCTOR` не может создавать, менять и удалять занятия
- `PUBLIC` только чтение

## Примечания
- профиль по умолчанию: `dev` (H2 in-memory)
- Maven wrapper отсутствует
