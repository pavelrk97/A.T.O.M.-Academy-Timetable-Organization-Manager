# A.T.O.M. Project Documentation

## 1. Назначение
A.T.O.M. (Academy Timetable Organization Manager) — система управления расписанием академии.

Цель:
- единый источник актуального расписания;
- ролевой доступ к данным;
- контроль конфликтов правок;
- история изменений;
- стартовая загрузка из CSV Google Sheets.

## 2. Архитектура
Актуальные модули:
- `schedule-import-parser-core` — основной backend и работа с БД (`8080`)
- `schedule-api-service` — фасадный API (`8081`)

Поток:
1. Клиент -> `schedule-api-service` (`8081`)
2. Фасад -> `schedule-import-parser-core` (`8080`)
3. Core исполняет бизнес-логику и отвечает

## 3. Технологии
Core:
- Java 21
- Spring Boot 3.2.5
- Spring Web / Security / Data JPA / Validation
- H2 (dev), PostgreSQL (prod)
- OpenCSV

API facade:
- Java 21
- Spring Boot 3.2.5
- Spring Web, OpenFeign, WebClient

## 4. Роли и права
Роли:
- `ADMIN`
- `EDITOR`
- `INSTRUCTOR`
- `PUBLIC` (без ЛК)

### 4.1 ADMIN
Имеет право:
- управление пользователями
- импорт CSV
- создание/изменение/удаление групп
- создание/изменение/удаление занятий
- просмотр истории и workload

### 4.2 EDITOR
Имеет право:
- просмотр расписания
- создание/изменение/удаление групп
- создание/изменение/удаление занятий
- просмотр истории и workload

Не имеет права:
- управление пользователями
- импорт CSV

### 4.3 INSTRUCTOR
Имеет право:
- смотреть расписание
- смотреть workload

Не имеет права:
- создавать занятия
- менять занятия
- удалять занятия
- управлять пользователями
- импортировать данные

### 4.4 PUBLIC
Имеет право:
- только читать публичное расписание

## 5. Доменная модель
Ключевые сущности:
- `User`
- `Group`
- `Day`
- `Lesson`
- `ChangeLog`

Принцип инструкторов:
- отдельная сущность `Instructor` не вводится
- инструкторы — это пользователи
- связь занятий с инструкторами: `lesson_instructors`

## 6. Конфликты правок
Используется optimistic locking через `@Version`.

Правило:
- update/delete занятия допускается только с актуальной версией
- при рассинхроне возвращается `409 Conflict`

Ключевой файл:
- `schedule-import-parser-core/src/main/java/ru/service/LessonService.java`

## 7. История изменений
На создание/изменение/удаление занятий пишется аудит в `change_logs`.

Ключевые файлы:
- `schedule-import-parser-core/src/main/java/ru/service/AuditService.java`
- `schedule-import-parser-core/src/main/java/ru/repository/ChangeLogRepository.java`

## 8. Учет часов
Учет часов = агрегирование `durationHours` по назначенным инструкторам.

Подтвержденное правило:
- если на занятие назначено несколько инструкторов (например, ПК/экзамен), каждому начисляется полная `durationHours`
- деление часов между инструкторами не применяется

Ключевой файл:
- `schedule-import-parser-core/src/main/java/ru/service/LessonService.java`

## 9. Импорт данных
Источник:
- только CSV из Google Sheets

Текущий endpoint:
- `POST /api/import/csv`

JSON-импорт исключен из текущего контракта.

## 10. Эндпоинты core (`http://localhost:8080`)
Public:
- `GET /api/public/schedule`

Auth:
- `GET /api/auth/me`

Users:
- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{id}`

Groups:
- `GET /api/groups`
- `GET /api/groups/{id}`
- `POST /api/groups`
- `PUT /api/groups/{id}`
- `DELETE /api/groups/{id}`

Lessons:
- `GET /api/lessons`
- `GET /api/lessons/{id}`
- `POST /api/lessons`
- `PUT /api/lessons/{id}`
- `DELETE /api/lessons/{id}?version=...`
- `GET /api/lessons/{id}/history`

Workload:
- `GET /api/workload`

Import:
- `POST /api/import/csv`

## 11. Эндпоинты API facade (`http://localhost:8081`)
Проксируются те же сценарии:
- `GET /api/public/schedule`
- `GET /api/auth/me`
- `GET/POST/PUT /api/users`
- `GET/POST/PUT/DELETE /api/groups`
- `GET/POST/PUT/DELETE /api/lessons`
- `GET /api/lessons/{id}/history`
- `GET /api/workload`
- `POST /api/import/csv`

## 12. Авторизация
Текущий механизм: `Basic Auth`.

Тестовые учетки:
- `admin / admin123`
- `editor / editor123`
- `instructor / instructor123`

## 13. Быстрая проверка
1. `GET http://localhost:8081/api/public/schedule`
2. `GET http://localhost:8081/api/auth/me` (admin)
3. `POST http://localhost:8081/api/import/csv` (admin)
4. `GET http://localhost:8081/api/groups` (admin)
5. `GET http://localhost:8081/api/workload` (admin)

## 14. Просмотр БД (dev)
H2 console:
- `http://localhost:8080/h2-console`
- JDBC: `jdbc:h2:mem:schedule;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- user: `sa`
- password: пусто

## 15. Текущий статус
Реализовано:
- core + facade рабочие
- ролевая модель и ограничения доступа
- публичное чтение расписания
- CRUD групп и занятий (для ADMIN/EDITOR)
- аудит и контроль конфликтов
- импорт CSV
- workload по инструкторам

Открытые задачи:
- JWT вместо Basic Auth
- Swagger/OpenAPI
- расширение тестов
- нормализация маппинга инструкторов при импорте
