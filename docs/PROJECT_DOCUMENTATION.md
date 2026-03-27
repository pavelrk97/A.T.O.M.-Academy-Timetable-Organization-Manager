# A.T.O.M. Project Documentation

## 1. Назначение
A.T.O.M. (Academy Timetable Organization Manager) — система управления расписанием академии.

Текущая версия проекта оформлена как минимально инвазивная микросервисная архитектура:
- единая внешняя точка входа через API Gateway;
- отдельный сервис пользователей и Basic Auth;
- отдельный сервис импорта CSV;
- отдельный сервис расписания, workload и аудита.

## 2. Сервисы и порты
- `schedule-api-service` — API Gateway (`8081`)
- `schedule-import-parser-core` — `schedule-service` (`8080`)
- `identity-service` — пользователи и Basic Auth (`8082`)
- `import-service` — внешний CSV import ingress (`8083`)

## 3. Архитектурный поток
### 3.1 Чтение расписания
1. Клиент -> `api-gateway` (`8081`)
2. Gateway -> `schedule-service` (`8080`)
3. `schedule-service` отвечает данными расписания

### 3.2 Авторизация
1. Клиент отправляет `Basic Auth` в Gateway
2. Gateway пробрасывает `Authorization` в нужный downstream-сервис
3. `schedule-service` и `import-service` запрашивают user details у `identity-service` через внутренний endpoint
4. `identity-service` остаётся единственной внешней точкой работы с пользователями

### 3.3 Импорт CSV
1. Клиент -> `api-gateway` -> `import-service`
2. `import-service` проверяет, что пользователь имеет роль `ADMIN`
3. `import-service` пересылает multipart CSV во внутренний endpoint `schedule-service`
4. `schedule-service` парсит CSV и сохраняет группы, дни, занятия и связи с инструкторами

## 4. Ответственность сервисов
### 4.1 API Gateway
- внешний API для клиента;
- проксирование запросов;
- сохранение старого внешнего контракта `api/*`.

### 4.2 identity-service
- `GET /api/auth/me`;
- `GET/POST/PUT /api/users`;
- внутренний endpoint user details для других сервисов.

### 4.3 schedule-service
- публичное расписание;
- CRUD групп;
- CRUD занятий;
- optimistic locking;
- аудит изменений занятий;
- workload;
- внутренний CSV import endpoint.

### 4.4 import-service
- внешний endpoint `POST /api/import/csv`;
- отдельный runtime для ingress/import сценария;
- orchestration между клиентом и внутренним import endpoint `schedule-service`.

## 5. Роли и права
Роли:
- `ADMIN`
- `EDITOR`
- `INSTRUCTOR`
- `PUBLIC` (без ЛК)

### 5.1 ADMIN
Имеет право:
- управление пользователями;
- импорт CSV;
- создание/изменение/удаление групп;
- создание/изменение/удаление занятий;
- просмотр истории и workload.

### 5.2 EDITOR
Имеет право:
- просмотр расписания;
- создание/изменение/удаление групп;
- создание/изменение/удаление занятий;
- просмотр истории и workload.

Не имеет права:
- управление пользователями;
- импорт CSV.

### 5.3 INSTRUCTOR
Имеет право:
- просмотр расписания;
- просмотр workload.

Не имеет права:
- создавать, менять и удалять занятия;
- управлять пользователями;
- импортировать данные.

### 5.4 PUBLIC
Имеет право:
- только читать публичное расписание.

## 6. Доменная модель
Ключевые сущности:
- `User`
- `Group`
- `Day`
- `Lesson`
- `ChangeLog`

Принцип инструкторов:
- отдельная сущность `Instructor` не вводится;
- инструкторы хранятся как пользователи;
- связь занятий с инструкторами: `lesson_instructors`.

## 7. Конфликты правок
Используется optimistic locking через `@Version`.

Правило:
- update/delete занятия допускается только с актуальной версией;
- при рассинхроне возвращается `409 Conflict`.

## 8. Аудит изменений
На создание, изменение и удаление занятий пишется аудит в `change_logs`.

## 9. Учет часов
Workload = агрегирование `durationHours` по назначенным инструкторам.

Подтвержденное правило:
- если на занятие назначено несколько инструкторов, каждому начисляется полная `durationHours`;
- деление часов между инструкторами не применяется.

## 10. Импорт данных
Источник:
- только CSV из Google Sheets.

Внешний endpoint:
- `POST http://localhost:8081/api/import/csv`

Внутренний endpoint:
- `POST http://localhost:8080/internal/import/csv`

JSON-импорт удалён из контракта и вычищен из кодовой базы.

## 11. Эндпоинты
### 11.1 API Gateway (`http://localhost:8081`)
- `GET /api/public/schedule`
- `GET /api/auth/me`
- `GET/POST/PUT /api/users`
- `GET/POST/PUT/DELETE /api/groups`
- `GET/POST/PUT/DELETE /api/lessons`
- `GET /api/lessons/{id}/history`
- `GET /api/workload`
- `POST /api/import/csv`

### 11.2 identity-service (`http://localhost:8082`)
- `GET /api/auth/me`
- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{id}`
- `GET /internal/users/by-username/{username}`

### 11.3 schedule-service (`http://localhost:8080`)
- `GET /api/public/schedule`
- `GET /api/groups`
- `GET /api/groups/{id}`
- `POST /api/groups`
- `PUT /api/groups/{id}`
- `DELETE /api/groups/{id}`
- `GET /api/lessons`
- `GET /api/lessons/{id}`
- `POST /api/lessons`
- `PUT /api/lessons/{id}`
- `DELETE /api/lessons/{id}?version=...`
- `GET /api/lessons/{id}/history`
- `GET /api/workload`
- `POST /internal/import/csv`

### 11.4 import-service (`http://localhost:8083`)
- `POST /api/import/csv`

## 12. Хранение данных
### 12.1 Dev
- Java 21
- Spring Boot 3.2.5
- общий file-based H2 instance:
  `jdbc:h2:file:~/atom-shared-db;AUTO_SERVER=TRUE;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE`

### 12.2 Prod
- PostgreSQL
- один DB instance для сервисов текущей итерации.

## 13. Тестовые учетки
- `admin / admin123`
- `editor / editor123`
- `instructor / instructor123`

Тестовые пользователи инициализируются в `identity-service`.

## 14. Порядок запуска
1. Запустить `identity-service` на `8082`
2. Запустить `schedule-service` на `8080`
3. Запустить `import-service` на `8083`
4. Запустить `api-gateway` на `8081`

## 15. Быстрая проверка
1. `GET http://localhost:8081/api/public/schedule`
2. `GET http://localhost:8081/api/auth/me` c `admin/admin123`
3. `POST http://localhost:8081/api/import/csv` c `admin/admin123`
4. `GET http://localhost:8081/api/groups` c `admin/admin123`
5. `GET http://localhost:8081/api/workload` c `admin/admin123`

## 16. Текущий статус
Реализовано:
- gateway + identity-service + schedule-service + import-service;
- Basic Auth через `identity-service`;
- публичное чтение расписания;
- CRUD групп и занятий;
- аудит и optimistic locking;
- импорт только CSV;
- workload по инструкторам;
- удаление JSON-import хвостов из кода и контракта.

Открытые задачи:
- JWT вместо Basic Auth;
- Swagger/OpenAPI;
- расширение тестов;
- более строгая изоляция владения таблицей `users` между сервисами;
- docker-compose для локального запуска.
