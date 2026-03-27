# api-gateway

Внешняя точка входа в систему A.T.O.M.

## Назначение
- принимает клиентские запросы на `http://localhost:8081`
- маршрутизирует их в downstream-сервисы
- сохраняет единый внешний контракт `api/*`

## Маршрутизация
- `identity-service`:
  `GET /api/auth/me`, `GET/POST/PUT /api/users`
- `schedule-service`:
  `GET /api/public/schedule`, `GET/POST/PUT/DELETE /api/groups`,
  `GET/POST/PUT/DELETE /api/lessons`, `GET /api/lessons/{id}/history`,
  `GET /api/workload`
- `import-service`:
  `POST /api/import/csv`

## Downstream сервисы
- `schedule-service` -> `http://localhost:8080`
- `identity-service` -> `http://localhost:8082`
- `import-service` -> `http://localhost:8083`

## Авторизация
- Gateway не валидирует пользователей сам
- заголовок `Authorization` пробрасывается дальше в нужный сервис
