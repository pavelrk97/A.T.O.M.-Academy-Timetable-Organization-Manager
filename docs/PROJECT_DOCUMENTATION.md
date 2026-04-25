# A.T.O.M. Project Documentation

## 1. Назначение
A.T.O.M. (Academy Timetable Organization Manager) — система для хранения, импорта, просмотра и редактирования академического расписания.

Текущая версия проекта оформлена как практичная микросервисная схема:
- единая внешняя точка входа через API Gateway;
- отдельный сервис пользователей и Basic Auth;
- отдельный сервис импорта CSV;
- отдельный сервис расписания, workload, аудита и personal-cabinet API;
- общий parent `pom.xml` и единый стек на Java 21 / Spring Boot 3.2.5.

## 2. Что уже реализовано
- проект приведён к Java 21 и Spring Boot 3.2.5;
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
- исправлен gateway-прокид дат `from/to`;
- исправлены проблемы с immutable-коллекциями в import/update сценариях;
- добавлен backend для личного кабинета:
  - self-profile API
  - смена пароля
  - full schedule grid
  - instructor-only schedule grid
  - workload calendar с днями и занятиями
  - notifications feed
  - агрегирующий dashboard endpoint
- добавлены тесты на:
  - повторный CSV-import;
  - workload и ограничения роли `INSTRUCTOR`;
  - прокидку дат через gateway;
  - CRUD занятия, аудит и version conflict;
  - self-profile / password change;
  - my-cabinet schedule/workload/notifications.

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
3. `schedule-service` возвращает данные расписания.

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

### 5.4 Личный кабинет
1. UI ходит только в `api-gateway`.
2. Gateway собирает профиль из `identity-service`.
3. Gateway собирает персональное расписание, workload и notifications из `schedule-service`.
4. Для стартового экрана UI может взять всё через один `dashboard` endpoint, а не собирать данные из 4-5 отдельных запросов.

## 6. Ответственность сервисов
### 6.1 API Gateway
- единая внешняя точка входа;
- внешний API для фронта/Postman/клиентов;
- проксирование запросов в downstream-сервисы;
- агрегирующие my-endpoints для кабинета.

### 6.2 identity-service
- хранение пользователей;
- роли и Basic Auth;
- `GET /api/auth/me`;
- CRUD пользователей на уровне `GET/POST/PUT`;
- self-service профиль и смена пароля;
- внутренний endpoint для lookup по username.

### 6.3 schedule-service
- публичное чтение расписания;
- CRUD групп;
- CRUD занятий;
- optimistic locking;
- аудит изменений занятий;
- расчёт workload;
- full schedule grid;
- instructor-only schedule grid;
- workload calendar;
- notifications feed;
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
- `Notification` (модель уже есть, но текущий feed кабинета пока вычисляется как lightweight-коллекция ссылок на дни)

Принципы модели:
- отдельная сущность `Instructor` не используется;
- инструкторы хранятся как `User` с ролью `INSTRUCTOR`;
- связь занятий с инструкторами идёт через `lesson_instructors`;
- аудит занятий хранится в `change_logs`.

## 9. Профиль пользователя
Поля профиля:
- `username`
- `fullName`
- `email`
- `phone` optional
- `position` optional
- `department` optional
- `role`
- `active`
- `canTeach`

Поля `phone`, `position`, `department`:
- не обязательны;
- заполняются самим пользователем через self-profile API.

## 10. Роли и права
### 10.1 ADMIN
Может:
- читать расписание;
- управлять пользователями;
- импортировать CSV;
- создавать/изменять/удалять группы;
- создавать/изменять/удалять занятия;
- смотреть workload;
- смотреть историю изменений занятий;
- пользоваться личным кабинетом.

### 10.2 EDITOR
Может:
- читать расписание;
- создавать/изменять/удалять группы;
- создавать/изменять/удалять занятия;
- смотреть workload;
- смотреть историю изменений занятий;
- пользоваться личным кабинетом.

Не может:
- управлять пользователями;
- импортировать CSV.

### 10.3 INSTRUCTOR
Может:
- читать расписание;
- смотреть workload;
- пользоваться личным кабинетом;
- редактировать свой профиль;
- менять свой пароль;
- смотреть только своё instructor-view расписание и свои workload-данные.

Не может:
- создавать/изменять/удалять занятия;
- управлять пользователями;
- импортировать CSV.

### 10.4 PUBLIC
Может:
- только читать публичное расписание без авторизации.

## 11. Конфликты и аудит
### 11.1 Optimistic locking
Используется `@Version`.

Правило:
- update/delete занятия разрешён только с актуальной версией;
- при устаревшей версии возвращается `409 Conflict`.

### 11.2 Аудит
На создание, изменение и удаление занятий пишется аудит в `change_logs`.

Что хранится:
- действие (`CREATED`, `UPDATED`, `DELETED`);
- кто изменил;
- JSON-снимок до изменения;
- JSON-снимок после изменения;
- комментарий.

## 12. Workload
Workload = суммарная нагрузка по назначенным инструкторам.

Подтверждённое правило:
- если у занятия несколько инструкторов, каждый получает полную `durationHours`;
- деление часов между инструкторами не применяется.

Для кабинета добавлен workload-calendar:
- принимает период `from/to`;
- возвращает общие часы;
- возвращает список дней, в которые у инструктора есть занятия;
- для каждого дня возвращает список занятий, чтобы UI мог вставить их в календарь и свободные дни заполнить другими задачами.

## 13. Импорт данных
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

## 14. Personal Cabinet API
### 14.1 Зачем нужен отдельный my-API
Цель:
- не заставлять фронт собирать кабинет из 4-5 отдельных запросов;
- дать UI табличное расписание в виде, близком к Google Sheets;
- дать персональный workload-календарь;
- дать notifications feed;
- дать self-profile и смену пароля.

### 14.2 Schedule grid
Формат ответа:
- сверху идёт список `dates`;
- слева идут `groups`;
- у каждой группы есть список `days`, выровненный по этим датам;
- внутри дня лежат `lessons`:
  - предмет
  - инструкторы
  - длительность
  - тип
  - note

Этот формат рассчитан на UI, где таблица рендерится как календарная сетка.

### 14.3 Instructor-only grid
Отдельный endpoint отдаёт тот же grid-формат, но:
- остаются только те занятия, где стоит текущий инструктор;
- пустые группы отфильтровываются;
- UI может показывать персональный вид расписания без дополнительной сборки на фронте.

### 14.4 Notifications
Пока это не почта и не push.

Текущая реализация:
- коллекция ссылок на дни, где у инструктора есть занятия;
- message + date + link;
- link сейчас ведёт на instructor-grid, отфильтрованный по конкретному дню.

## 15. Эндпоинты
### 15.1 Внешние эндпоинты через API Gateway (`http://localhost:8081`)

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

#### Personal cabinet
- `GET /api/me/profile`
  - auth: Basic Auth
- `PUT /api/me/profile`
  - auth: Basic Auth
  - body:
    - `fullName` required
    - `email` optional
    - `phone` optional
    - `position` optional
    - `department` optional
- `PUT /api/me/password`
  - auth: Basic Auth
  - body:
    - `currentPassword`
    - `newPassword`
- `GET /api/me/schedule/grid`
  - auth: Basic Auth
  - query params:
    - `from` optional, формат `yyyy-MM-dd`
    - `to` optional, формат `yyyy-MM-dd`
- `GET /api/me/schedule/instructor-grid`
  - auth: Basic Auth
  - query params:
    - `from` optional, формат `yyyy-MM-dd`
    - `to` optional, формат `yyyy-MM-dd`
- `GET /api/me/workload/calendar`
  - auth: Basic Auth
  - query params:
    - `from` optional, формат `yyyy-MM-dd`
    - `to` optional, формат `yyyy-MM-dd`
- `GET /api/me/notifications`
  - auth: Basic Auth
  - query params:
    - `from` optional, формат `yyyy-MM-dd`
    - `to` optional, формат `yyyy-MM-dd`
- `GET /api/me/dashboard`
  - auth: Basic Auth
  - query params:
    - `from` optional, формат `yyyy-MM-dd`
    - `to` optional, формат `yyyy-MM-dd`
  - возвращает агрегат:
    - `profile`
    - `instructorSchedule`
    - `workload`
    - `notifications`

### 15.2 identity-service (`http://localhost:8082`)
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
- `GET /api/me/profile`
  - auth: Basic Auth
- `PUT /api/me/profile`
  - auth: Basic Auth
- `PUT /api/me/password`
  - auth: Basic Auth
- `GET /internal/users/by-username/{username}`
  - internal only
  - используется `schedule-service` и `import-service`

### 15.3 schedule-service (`http://localhost:8080`)
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
- `GET /api/me/schedule/grid`
- `GET /api/me/schedule/instructor-grid`
- `GET /api/me/workload/calendar`
- `GET /api/me/notifications`
- `POST /internal/import/csv`

Примечание:
- внешний клиент обычно не ходит сюда напрямую;
- каноническая внешняя точка входа — `api-gateway`.

### 15.4 import-service (`http://localhost:8083`)
- `POST /api/import/csv`
  - auth: Basic Auth
  - роли: `ADMIN`
  - multipart field:
    - `file`

## 16. Хранение данных
### 16.1 Dev
- file-based H2
- текущий URL:
  - `jdbc:h2:file:~/atom-shared-db;AUTO_SERVER=TRUE;MODE=PostgreSQL`
- `schedule-service` и `identity-service` в dev смотрят в общий H2-файл

### 16.2 Prod
- PostgreSQL
- текущая схема настроек ориентирована на один PostgreSQL instance

## 17. Тестовые учётки
- `admin / admin123`
- `editor / editor123`
- `instructor / instructor123`

Тестовые пользователи инициализируются в `identity-service`.

## 18. Порядок запуска
1. `identity-service` (`8082`)
2. `schedule-service` (`8080`)
3. `import-service` (`8083`)
4. `api-gateway` (`8081`)

## 19. Быстрая проверка
### 19.1 Публичное расписание
- `GET http://localhost:8081/api/public/schedule`

### 19.2 Текущий пользователь
- `GET http://localhost:8081/api/auth/me`
- Basic Auth: `admin/admin123`

### 19.3 Импорт CSV
- `POST http://localhost:8081/api/import/csv`
- Basic Auth: `admin/admin123`
- `multipart/form-data`, поле `file`

### 19.4 Личный кабинет
- `GET http://localhost:8081/api/me/dashboard?from=2026-01-01&to=2026-01-31`
- Basic Auth: `instructor/instructor123`

### 19.5 Полное grid-расписание
- `GET http://localhost:8081/api/me/schedule/grid?from=2026-01-01&to=2026-01-31`
- Basic Auth: `instructor/instructor123`

### 19.6 Instructor-only grid
- `GET http://localhost:8081/api/me/schedule/instructor-grid?from=2026-01-01&to=2026-01-31`
- Basic Auth: `instructor/instructor123`

### 19.7 Workload calendar
- `GET http://localhost:8081/api/me/workload/calendar?from=2026-01-01&to=2026-01-31`
- Basic Auth: `instructor/instructor123`

### 19.8 Смена пароля
- `PUT http://localhost:8081/api/me/password`
- Basic Auth: текущие credentials пользователя

## 20. Тесты
На текущий момент в проекте уже есть тесты на:
- повторный CSV-import без развала связей;
- workload и ограничения роли `INSTRUCTOR`;
- прокидку дат `from/to` через gateway;
- CRUD-сценарий занятия с аудитом и stale version conflict;
- self-profile update и password change;
- my-cabinet grid/workload/notifications.

## 21. Текущий статус и ограничения
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
- personal cabinet API;
- dashboard endpoint для UI;
- обновлённая тестовая база под ключевые сценарии.

Текущие архитектурные ограничения:
- в dev используется общий H2-файл;
- в текущей итерации сервисы ещё не разведены по полностью независимым БД;
- используется Basic Auth, а не JWT;
- id пользователей между `identity-service` и `schedule-service` не синхронизированы как единый cross-service identity key;
- instructor-view в расписании сейчас безопасно завязан на `fullName` текущего пользователя из `identity-service`;
- notifications feed пока реализован как lightweight-коллекция ссылок, а не полноценная доставка.

## 22. Направления следующей итерации
- переход с Basic Auth на JWT;
- OpenAPI / Swagger;
- `docker-compose` для локального запуска всей схемы;
- более строгая изоляция данных по сервисам;
- единый cross-service identifier для instructor assignment;
- real notification delivery:
  - email
  - mark-as-read
  - delivery history
- расширение тестов на HTTP-контракт и межсервисные сценарии.
