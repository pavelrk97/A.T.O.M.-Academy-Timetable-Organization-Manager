# identity-service

Сервис пользователей и Basic Auth.

## Что внутри
- `GET /api/auth/me`
- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{id}`
- `GET /internal/users/by-username/{username}`

## Ответственность
- хранение пользователей
- роли `ADMIN`, `EDITOR`, `INSTRUCTOR`
- тестовые учетки для dev
- user details для других сервисов

## Примечания
- сервис инициализирует `admin`, `editor`, `instructor`
- использует общий DB instance текущей итерации
