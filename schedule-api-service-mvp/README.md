# schedule-api-service-mvp

‘асадный API-сервис дл€ `schedule-import-parser-core-mvp`.

Ќазначение:
- поднимать внешний API на `http://localhost:8081`
- проксировать публичные и защищенные запросы в core-сервис на `http://localhost:8080`
- держать внешний контракт отдельно от внутреннего backend

„то уже проксируетс€:
- `GET /api/public/schedule`
- `GET /api/auth/me`
- `GET/POST/PUT /api/users`
- `GET/POST/PUT/DELETE /api/groups`
- `GET/POST/PUT/DELETE /api/lessons`
- `GET /api/lessons/{id}/history`
- `GET /api/workload`
- `POST /api/import/json`
- `POST /api/import/csv`

 ак запускать:
1. —начала запустить `schedule-import-parser-core-mvp` на `8080`
2. «атем запустить этот сервис на `8081`

јвторизаци€:
- API-сервис просто пробрасывает заголовок `Authorization` в core
- дл€ защищенных запросов используйте Basic Auth учеток из core:
  - `admin / admin123`
  - `editor / editor123`
  - `instructor / instructor123`

ѕримечани€:
- импорт файлов проксируетс€ через `WebClient`, не через Feign, чтобы избежать проблем multipart
- Maven wrapper отсутствует
- автоматическую сборку в текущем окружении проверить не удалось, потому что `mvn` не установлен
