# schedule-api-service

Фасадный API-сервис для `schedule-import-parser-core`.

## Назначение
- внешний API на `http://localhost:8081`
- проксирование защищенных и публичных запросов в core `http://localhost:8080`

## Что проксируется
- `GET /api/public/schedule`
- `GET /api/auth/me`
- `GET/POST/PUT /api/users`
- `GET/POST/PUT/DELETE /api/groups`
- `GET/POST/PUT/DELETE /api/lessons`
- `GET /api/lessons/{id}/history`
- `GET /api/workload`
- `POST /api/import/csv`

## Запуск
1. Запустить `schedule-import-parser-core` на `8080`
2. Запустить `schedule-api-service` на `8081`

## Авторизация
- заголовок `Authorization` пробрасывается в core
- тестовые учетки из core: `admin`, `editor`, `instructor`
