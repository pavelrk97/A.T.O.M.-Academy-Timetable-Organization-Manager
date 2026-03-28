# A.T.O.M. Project Documentation

## 1. Назначение
A.T.O.M. (Academy Timetable Organization Manager) — система для хранения, импорта, просмотра и редактирования академического расписания.

Текущая версия проекта оформлена как практичная микросервисная схема без радикального переписывания домена:
- единая внешняя точка входа через API Gateway;
- отдельный сервис пользователей и Basic Auth;
- отдельный сервис импорта CSV;
- отдельный сервис расписания, workload и аудита;
- общий parent `pom.xml` и единый стек на Java 21 / Spring Boot 3.2.5.

## 2. Что уже реализовано
- проект перенесён на Java 21 и Spring Boot 3.2.5;
- собран root parent/aggregator Maven POM;
- выделены 4 runtime-сервиса:
  - `api-gateway`
  - `schedule-service`
  - `identity-service`
  - `import-service`
- внешний API заведён через gateway;
- Basic Auth вынесен в `identity-service`;
- роли приведены к актуальному набору:
  - `ADMIN`
  - `EDITOR`
  - `INSTRUCTOR`
- `INSTRUCTOR` оставлен только на чтение расписания и workload;
- импорт оставлен только CSV;
- JSON-import удалён из внешнего контракта и вычищен из кодовой базы;
- инструкторы хранятся как обычные пользователи, отдельной модели `Instructor` нет;
- workload считает полную `durationHours` каждому назначенному инструктору, без деления часов;
- добавлены optimistic locking и аудит изменений занятий;
- починен gateway-прокид даты `from/to`;
- починены проблемы с immutable-коллекциями в import/update сценариях;
- добавлены тесты на:
  - повторный CSV-import;
  - workload и ограничения роли `INSTRUCTOR`;
  - прокидку дат через gateway;
  - сценарий CRUD занятия, аудит и version conflict.

## 3. Структура проекта
- `schedule-import-parser-core` — основной backend, фактически `schedule-service`
- `schedule-api-service` — `api-gateway`
- `identity-service` — пользователи, роли, Basic Auth
- `import-service` — внешний ingress для CSV-import
- `docs/PROJECT_DOCUMENTATION.md` — основной актуальный документ
- `postman/` — Postman-коллекции и сценарии smoke-проверки

## 4. Сервисы и порты
- `schedule-api-service` -> `api-gateway` -> `8081`
- `schedule-import-parser-core` -> `schedule-service` -> `8080`
- `identity-service` -> `8082`
- `import-service` -> `8083`

## 5. Архитектурный поток
### 5.1 Чтение расписания
1. Клиент вызывает `api-gateway`.
2. Gateway проксирует запрос в `schedule-service`.
3. `schedule-service` возвращает расписание.

### 5.2 Аутентификация и роли
1. Клиент передаёт `Basic Auth`.
2. Gateway пробрасывает `Authorization` дальше.
3. `schedule-service` и `import-service` берут user details через внутренний endpoint `identity-service`.
4. `identity-service` остаётся источником истины по пользователям и ролям.

### 5.3 Импорт CSV
1. Клиент вызывает `POST /api/import/csv` через gateway.
2. Gateway пересылает запрос в `import-service`.
3. `import-service` проверяет роль `ADMIN`.
4. `import-service` пересылает multipart-файл во внутренний endpoint `schedule-service`.
5. `schedule-service` парсит CSV, сохраняет группы, дни, занятия и связи с инструкторами.

## 6. Ответственность сервисов
### 6.1 API Gateway
- единая внешняя точка входа;
- внешний API для фронта/Postman/клиентов;
- проксирование запросов в downstream-сервисы;
- сохранение единого публичного контракта `api/*`.

### 6.2 identity-service
- хранение пользователей;
- роли и Basic Auth;
- `GET /api/auth/me`;
- CRUD пользователей на уровне `GET/POST/PUT`;
- внутренний endpoint для lookup по username.

### 6.3 schedule-service
- публичное чтение расписания;
- CRUD групп;
- CRUD занятий;
- optimistic locking;
- аудит изменений занятий;
- расчёт workload;
- внутренний CSV import endpoint.

### 6.4 import-service
- внешний endpoint `POST /api/import/csv`;
- отдельный runtime для import ingress;
- orchestration между клиентом и внутренним import endpoint `schedule-service`.

## 7. Технологический стек
### 7.1 Общий стек
- Java 21
- Spring Boot 3.2.5
- Maven multi-module build
- Lombok
- JUnit 5

### 7.2 api-gateway
- Spring Web
- Spring Cloud OpenFeign
- Spring Validation
- Spring Actuator
- Spring WebFlux / WebClient

### 7.3 schedule-service
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- H2
- PostgreSQL
- OpenCSV

### 7.4 identity-service
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- H2
- PostgreSQL

### 7.5 import-service
- Spring Web
- Spring Security
- Spring Validation
- Spring WebFlux / WebClient

### 7.6 Тестовый стек
- Spring Boot Starter Test
- Mockito
- `@DataJpaTest`
- `@WebMvcTest`

## 8. Доменная модель
Ключевые сущности:
- `User`
- `Group`
- `Day`
- `Lesson`
- `ChangeLog`

Принципы модели:
- отдельная сущность `Instructor` не используется;
- инструкторы хранятся как `User` с ролью `INSTRUCTOR`;
- связь занятий с инструкторами идёт через `lesson_instructors`;
- аудит занятий хранится в `change_logs`.

## 9. Роли и права
### 9.1 ADMIN
Может:
- читать расписание;
- управлять пользователями;
- импортировать CSV;
- создавать/изменять/удалять группы;
- создавать/изменять/удалять занятия;
- смотреть workload;
- смотреть историю изменений занятий.

### 9.2 EDITOR
Может:
- читать расписание;
- создавать/изменять/удалять группы;
- создавать/изменять/удалять занятия;
- смотреть workload;
- смотреть историю изменений занятий.

Не может:
- управлять пользователями;
- импортировать CSV.

### 9.3 INSTRUCTOR
Может:
- читать расписание;
- смотреть workload.

Не может:
- создавать/изменять/удалять занятия;
- управлять пользователями;
- импортировать CSV.

### 9.4 PUBLIC
Может:
- только читать публичное расписание без авторизации.

## 10. Конфликты и аудит
### 10.1 Optimistic locking
Используется `@Version`.

Правило:
- update/delete занятия разрешён только с актуальной версией;
- при устаревшей версии возвращается `409 Conflict`.

### 10.2 Аудит
На создание, изменение и удаление занятий пишется аудит в `change_logs`.

Что хранится:
- действие (`CREATED`, `UPDATED`, `DELETED`);
- кто изменил;
- JSON-снимок до изменения;
- JSON-снимок после изменения;
- комментарий.

## 11. Workload
Workload = суммарная нагрузка по назначенным инструкторам.

Подтверждённое правило:
- если у занятия несколько инструкторов, каждый получает полную `durationHours`;
- деление часов между инструкторами не применяется.

## 12. Импорт данных
Источник:
- только CSV.

Поддерживаемый внешний контракт:
- `POST /api/import/csv`

Внутренний импортный маршрут:
- `POST /internal/import/csv`

JSON-import:
- удалён из внешнего контракта;
- удалён из кодовой базы;
- в документации больше не используется.

## 13. Эндпоинты
### 13.1 Внешние эндпоинты через API Gateway (`http://localhost:8081`)

#### Public schedule
- `GET /api/public/schedule`
  - auth: не нужен
  - query params:
    - `groupCode` optional
    - `instructorId` optional
    - `from` optional, формат `yyyy-MM-dd`
    - `to` optional, формат `yyyy-MM-dd`

#### Auth
- `GET /api/auth/me`
  - auth: Basic Auth
  - роли: любой аутентифицированный пользователь

#### Users
- `GET /api/users`
  - auth: Basic Auth
  - роли: `ADMIN`
- `POST /api/users`
  - auth: Basic Auth
  - роли: `ADMIN`
- `PUT /api/users/{id}`
  - auth: Basic Auth
  - роли: `ADMIN`

#### Groups
- `GET /api/groups`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`
- `GET /api/groups/{id}`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`
- `POST /api/groups`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`
- `PUT /api/groups/{id}`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`
- `DELETE /api/groups/{id}`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`

#### Lessons
- `GET /api/lessons`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`
  - query params:
    - `groupCode` optional
    - `instructorId` optional
    - `from` optional, формат `yyyy-MM-dd`
    - `to` optional, формат `yyyy-MM-dd`
- `GET /api/lessons/{id}`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`
- `POST /api/lessons`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`
- `PUT /api/lessons/{id}`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`
- `DELETE /api/lessons/{id}`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`
  - query params:
    - `version` required
- `GET /api/lessons/{id}/history`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`

#### Workload
- `GET /api/workload`
  - auth: Basic Auth
  - роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`
  - query params:
    - `instructorId` optional
    - `from` optional, формат `yyyy-MM-dd`
    - `to` optional, формат `yyyy-MM-dd`
  - ограничение:
    - `INSTRUCTOR` может смотреть только свой workload

#### Import
- `POST /api/import/csv`
  - auth: Basic Auth
  - роли: `ADMIN`
  - content-type: `multipart/form-data`
  - form field:
    - `file`

### 13.2 identity-service (`http://localhost:8082`)
- `GET /api/auth/me`
  - auth: Basic Auth
- `GET /api/users`
  - auth: Basic Auth
  - роли: `ADMIN`
- `POST /api/users`
  - auth: Basic Auth
  - роли: `ADMIN`
- `PUT /api/users/{id}`
  - auth: Basic Auth
  - роли: `ADMIN`
- `GET /internal/users/by-username/{username}`
  - internal only
  - используется `schedule-service` и `import-service`

### 13.3 schedule-service (`http://localhost:8080`)
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

Примечание:
- внешний клиент обычно не ходит сюда напрямую;
- каноническая внешняя точка входа — `api-gateway`.

### 13.4 import-service (`http://localhost:8083`)
- `POST /api/import/csv`
  - auth: Basic Auth
  - роли: `ADMIN`
  - multipart field:
    - `file`

## 14. Хранение данных
### 14.1 Dev
- file-based H2
- текущий URL:
  - `jdbc:h2:file:~/atom-shared-db;AUTO_SERVER=TRUE;MODE=PostgreSQL`
- `schedule-service` и `identity-service` в dev смотрят в общий H2-файл

### 14.2 Prod
- PostgreSQL
- текущая схема настроек ориентирована на один PostgreSQL instance

## 15. Тестовые учётки
- `admin / admin123`
- `editor / editor123`
- `instructor / instructor123`

Тестовые пользователи инициализируются в `identity-service`.

## 16. Порядок запуска
1. `identity-service` (`8082`)
2. `schedule-service` (`8080`)
3. `import-service` (`8083`)
4. `api-gateway` (`8081`)

## 17. Быстрая проверка
### 17.1 Публичное расписание
- `GET http://localhost:8081/api/public/schedule`

### 17.2 Текущий пользователь
- `GET http://localhost:8081/api/auth/me`
- Basic Auth: `admin/admin123`

### 17.3 Импорт CSV
- `POST http://localhost:8081/api/import/csv`
- Basic Auth: `admin/admin123`
- `multipart/form-data`, поле `file`

### 17.4 Доступ к группам
- `GET http://localhost:8081/api/groups`
- Basic Auth: `admin/admin123`

### 17.5 Workload
- `GET http://localhost:8081/api/workload`
- Basic Auth: `admin/admin123`

## 18. Тесты
На текущий момент в проекте уже есть тесты на:
- повторный CSV-import без развала связей;
- workload и ограничения роли `INSTRUCTOR`;
- прокидку дат `from/to` через gateway;
- CRUD-сценарий занятия:
  - создание пользователя;
  - запрет на create для `INSTRUCTOR`;
  - смена роли на `EDITOR`;
  - создание и обновление занятия;
  - аудит;
  - stale version conflict.

## 19. Текущий статус и ограничения
Реально готово:
- 4 runtime-сервиса;
- рабочий gateway;
- Basic Auth через `identity-service`;
- публичное чтение расписания;
- CRUD групп;
- CRUD занятий;
- optimistic locking;
- аудит изменений занятий;
- импорт только CSV;
- workload по инструкторам;
- обновлённая тестовая база под ключевые сценарии.

Текущие архитектурные ограничения:
- в dev используется общий H2-файл;
- в текущей итерации сервисы ещё не разведены по полностью независимым БД;
- используется Basic Auth, а не JWT;
- нет Swagger/OpenAPI;
- нет `docker-compose` в корне проекта.

## 20. Направления следующей итерации
- переход с Basic Auth на JWT;
- OpenAPI / Swagger;
- `docker-compose` для локального запуска всей схемы;
- более строгая изоляция данных по сервисам;
- расширение тестов на HTTP-контракт и межсервисные сценарии.
