# A.T.O.M. Project Documentation

## 1. Обзор проекта

**A.T.O.M. (Academy Timetable Organization Manager)** — система управления расписанием академии.

Назначение системы:
- централизованное хранение расписания;
- просмотр актуального расписания;
- редактирование по ролям;
- фиксация изменений;
- предотвращение конфликтов одновременных правок;
- импорт стартовых данных из Google Sheets CSV.

Проект развивается как backend-first решение. На текущем этапе актуальная рабочая структура состоит из двух модулей:
- `schedule-import-parser-core` — основной backend с бизнес-логикой и БД;
- `schedule-api-service` — фасадный API-сервис поверх core.

Именно эти модули теперь считаются основными и должны использоваться для дальнейшей разработки.

---

## 2. Бизнес-цель

Система должна заменить ручную работу с Google Sheets как с основным рабочим инструментом.

Google Sheets в проекте рассматривается как:
- источник стартовых данных;
- разовый импорт;
- не целевая рабочая среда.

Целевое состояние:
- расписание хранится в БД;
- пользователи работают через приложение;
- у каждого изменения есть автор, время и контекст;
- нельзя незаметно затереть чужую правку.

---

## 3. MVP-объем

В MVP входят:
- ведение расписания;
- просмотр расписания;
- фильтрация по группе;
- фильтрация по инструктору;
- роли доступа;
- публичный просмотр без авторизации;
- импорт CSV;
- история изменений занятий;
- предотвращение конфликтов правок;
- учет часов по инструкторам.

Вне текущего MVP:
- уведомления;
- автоинтеграция с Google Sheets;
- расширенная аналитика;
- экспорт отчетов;
- полноценный frontend;
- JWT-авторизация.

---

## 4. Архитектура

### 4.1 Текущая структура модулей

Актуальные рабочие модули:
- [schedule-import-parser-core](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core)
- [schedule-api-service](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-api-service)

Назначение модулей:
- `schedule-import-parser-core`
  - хранение данных;
  - бизнес-логика;
  - импорт;
  - права доступа;
  - аудит изменений;
  - workload;
  - H2/PostgreSQL;
- `schedule-api-service`
  - внешний фасад;
  - проксирование запросов на core;
  - внешний вход для Postman/будущего фронта;
  - проксирование импорта файлов;

### 4.2 Порты

- core: `http://localhost:8080`
- api facade: `http://localhost:8081`

### 4.3 Взаимодействие сервисов

Поток запросов:
1. клиент обращается в `schedule-api-service` на `8081`;
2. фасадный сервис проксирует запрос в `schedule-import-parser-core` на `8080`;
3. core исполняет бизнес-логику и работает с БД;
4. результат возвращается обратно клиенту.

Исключение:
- публичное расписание может читаться напрямую из core;
- но для внешнего контракта рекомендуется использовать `8081`.

---

## 5. Технологический стек

### 5.1 Core`r`n- Java 21`r`n- Spring Boot 3.2.5
- Spring Web
- Spring Data JPA
- Spring Security
- Validation
- H2 для dev
- PostgreSQL для prod
- OpenCSV

### 5.2 API facade`r`n- Java 21`r`n- Spring Boot 3.2.5
- Spring Web
- OpenFeign
- WebClient
- Validation

### 5.3 Инструменты
- Postman для ручной проверки API
- H2 Console для просмотра dev-БД
- Docker Compose присутствует в старом core-модуле как задел под PostgreSQL

---

## 6. Роли и права

Роли в системе:
- `ADMIN`
- `EDITOR`
- `INSTRUCTOR`
- публичный неавторизованный просмотр без роли в БД

### 6.1 ADMIN
Имеет право:
- смотреть все данные;
- создавать и менять пользователей;
- импортировать CSV/JSON;
- создавать, менять и удалять группы;
- создавать, менять и удалять занятия;
- смотреть историю изменений;
- смотреть workload.

### 6.2 EDITOR
Имеет право:
- смотреть расписание;
- создавать, менять и удалять группы;
- создавать, менять и удалять занятия;
- смотреть историю изменений;
- смотреть workload.

Не имеет права:
- управлять пользователями;
- выполнять импорт, если ограничение сохранено только для администратора.

### 6.3 INSTRUCTOR
Имеет право:
- смотреть расписание;
- смотреть workload;
- менять только назначенные ему занятия;
- в текущей реализации менять только поле `note` у своих занятий.

Не имеет права:
- создавать занятия;
- удалять занятия;
- управлять пользователями;
- импортировать данные.

### 6.4 Public
Имеет право:
- только читать публичное расписание.

---

## 7. Модель данных

Основная модель:
- `User`
- `Group`
- `Day`
- `Lesson`
- `ChangeLog`
- `Notification` как задел под future scope

### 7.1 User
Файл: [User.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\model\User.java)

Поля:
- `id`
- `username`
- `password`
- `fullName`
- `email`
- `role`
- `isActive`
- `canTeach`

Принятое решение:
- инструктор не выделяется в отдельную сущность;
- любой пользователь может быть преподавателем;
- признак преподавания задается `canTeach`;
- сейчас `canTeach = true` по умолчанию.

### 7.2 Group
Файл: [Group.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\model\Group.java)

Поля:
- `id`
- `code`
- `location`
- `course`

Связи:
- одна группа содержит много дней;
- пользователи могут быть связаны с группами через `user_groups`.

### 7.3 Day
Файл: [Day.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\model\Day.java)

Поля:
- `id`
- `date`
- `meta`

Связи:
- день принадлежит группе;
- день содержит занятия.

### 7.4 Lesson
Файл: [Lesson.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\model\Lesson.java)

Поля:
- `id`
- `version`
- `orderNumber`
- `title`
- `lecturer`
- `lecturers`
- `durationHours`
- `note`
- `type`

Связи:
- занятие принадлежит дню;
- занятие имеет `assignedInstructors` через `lesson_instructors`;
- строковые имена инструкторов сохранены как переходный слой для миграции.

### 7.5 ChangeLog
Файл: [ChangeLog.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\model\ChangeLog.java)

Хранит:
- тип сущности;
- id сущности;
- действие `CREATED/UPDATED/DELETED`;
- кто изменил;
- слепок до изменения;
- слепок после изменения;
- комментарий.

---

## 8. Принципы хранения инструкторов

Принятое решение:
- не создавать отдельную сущность `Instructor`;
- хранить инструкторов как пользователей;
- связывать занятия с пользователями через таблицу `lesson_instructors`.

Причина:
- проще авторизация;
- проще разграничение прав;
- проще workload;
- проще история изменений;
- проще миграция к полноценному приложению.

Переходный слой сохранен:
- `lecturer`
- `lesson_lecturers`

Это нужно, чтобы легче перевести импорт с CSV и постепенно очищать строковые фамилии.

---

## 9. Авторизация

### 9.1 Текущий механизм

Сейчас используется `Basic Auth`.

Тестовые учетные записи создаются автоматически в core через [DataInitializer.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\DataInitializer.java)

Учетки:
- `admin / admin123`
- `editor / editor123`
- `instructor / instructor123`

### 9.2 Почему пока не JWT

JWT пока не внедрялся, потому что:
- сначала нужно было собрать рабочую доменную и API-модель;
- Basic Auth проще для ручной проверки и первичного MVP;
- для фронта позже рекомендуется перейти на JWT.

---

## 10. Защита от конфликтов

Основа механизма:
- у сущностей используется `@Version`;
- у `Lesson` перед обновлением и удалением проверяется `version`;
- если версия не совпадает, возвращается конфликт.

Текущее поведение:
- при устаревшей версии update/delete должны завершаться ошибкой `409 Conflict`.

Файл: [LessonService.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\LessonService.java)

---

## 11. История изменений

История изменений реализована для занятий.

При действиях:
- создание занятия;
- обновление занятия;
- удаление занятия;

сохраняется audit-запись в `change_logs`.

Реализация:
- [AuditService.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\AuditService.java)
- [ChangeLogRepository.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\repository\ChangeLogRepository.java)

---

## 12. Учет часов

Учет часов реализован как агрегирование `durationHours` по назначенным инструкторам.

Текущее правило:
- если инструктор назначен на занятие, ему добавляется полная длительность занятия;
- деление часов между несколькими инструкторами сейчас не реализовано.

Файл: [LessonService.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\LessonService.java)

Это решение нужно будет подтвердить как бизнес-правило. Если часы должны делиться, сервис надо менять.

---

## 13. Импорт данных

### 13.1 Источник

Источник стартовых данных:
- CSV из Google Sheets
- JSON как технический формат импорта

### 13.2 Текущее поведение

При импорте:
- парсятся группы, дни и занятия;
- строковые имена инструкторов извлекаются из CSV;
- система пытается сопоставить инструктора с пользователем по имени;
- если пользователь не найден, создается новый пользователь с ролью `INSTRUCTOR`.

Файл: [JsonImportService.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\JsonImportService.java)

### 13.3 Ограничения текущего импорта

- сопоставление инструкторов идет по имени;
- возможны дубли при разных написаниях имени;
- нет отдельного интерфейса ручного маппинга нераспознанных фамилий;
- нет отчета по коллизиям и неоднозначностям.

---

## 14. Актуальные эндпоинты core-сервиса

Base URL:
- `http://localhost:8080`

### 14.1 Public
- `GET /api/public/schedule`

Параметры:
- `groupCode`
- `instructorId`
- `from`
- `to`

### 14.2 Auth
- `GET /api/auth/me`

### 14.3 Users
- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{id}`

### 14.4 Groups
- `GET /api/groups`
- `GET /api/groups/{id}`
- `POST /api/groups`
- `PUT /api/groups/{id}`
- `DELETE /api/groups/{id}`

### 14.5 Lessons
- `GET /api/lessons`
- `GET /api/lessons/{id}`
- `POST /api/lessons`
- `PUT /api/lessons/{id}`
- `DELETE /api/lessons/{id}?version=...`
- `GET /api/lessons/{id}/history`

### 14.6 Workload
- `GET /api/workload`

Параметры:
- `instructorId`
- `from`
- `to`

### 14.7 Import
- `POST /api/import/json`
- `POST /api/import/csv`

---

## 15. Актуальные эндпоинты API facade

Base URL:
- `http://localhost:8081`

Фасад проксирует те же сценарии:
- `GET /api/public/schedule`
- `GET /api/auth/me`
- `GET/POST/PUT /api/users`
- `GET/POST/PUT/DELETE /api/groups`
- `GET/POST/PUT/DELETE /api/lessons`
- `GET /api/lessons/{id}/history`
- `GET /api/workload`
- `POST /api/import/json`
- `POST /api/import/csv`

Файл: [CoreClient.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-api-service\src\main\java\ru\client\CoreClient.java)

Рекомендуемый внешний вход для клиентов:
- использовать именно `8081`, а не напрямую `8080`.

---

## 16. Примеры ручной проверки

### 16.1 Проверка public API
```http
GET http://localhost:8081/api/public/schedule
```

### 16.2 Проверка текущего пользователя
```http
GET http://localhost:8081/api/auth/me
Authorization: Basic <base64>
```

### 16.3 Импорт CSV
```http
POST http://localhost:8081/api/import/csv
Authorization: Basic <base64>
Content-Type: multipart/form-data
file=<csv>
```

### 16.4 Проверка workload
```http
GET http://localhost:8081/api/workload
Authorization: Basic <base64>
```

---

## 17. Просмотр БД

В dev используется H2 in-memory база.

H2 Console:
- `http://localhost:8080/h2-console`

Параметры подключения:
- JDBC URL: `jdbc:h2:mem:schedule;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- User: `sa`
- Password: пусто

Минимальные SQL-запросы:
```sql
select * from users;
select * from groups;
select * from days;
select * from lessons;
select * from lesson_instructors;
select * from lesson_lecturers;
select * from change_logs;
```

---

## 18. Что уже сделано в проекте

Реализовано:
- рабочий core MVP модуль;
- рабочий API facade MVP модуль;
- роли и разграничение доступа;
- public read-only schedule;
- CRUD групп;
- CRUD занятий;
- история изменений занятий;
- optimistic locking по занятиям;
- импорт CSV/JSON;
- workload по инструкторам;
- привязка инструкторов к пользователям;
- дефолт `canTeach = true`.

---

## 19. Что еще не завершено

Не завершено или требует уточнения:
- JWT вместо Basic Auth;
- полноценный login endpoint;
- фронтовый контракт как зафиксированная версия API;
- покрытие тестами;
- Swagger/OpenAPI;
- автоматические миграции схемы;
- нормализация инструкторов при неоднозначном импорте;
- более точные права инструктора;
- UI для ручной работы с конфликтами;
- отчеты по импорту;
- уведомления.

---

## 20. Риски и ограничения

### 20.1 Сборка не проверялась Maven-ом в текущем окружении
Причина:
- в среде отсутствует `mvn`.

Следствие:
- код сверялся вручную и логически синхронизирован;
- полную compile-проверку надо выполнить локально.

### 20.2 Импорт по имени ненадежен
Если один и тот же инструктор записан по-разному, возможны дубли.

### 20.3 Basic Auth не финальный вариант
Для настоящего фронта лучше перейти на JWT.

### 20.4 Workload может не совпасть с будущими правилами академии
Если часы должны распределяться между несколькими инструкторами, текущую реализацию надо менять.

---

## 21. Рекомендуемый следующий этап

Приоритетный следующий этап:
1. локально проверить сборку обоих MVP-модулей;
2. добавить JWT-авторизацию;
3. зафиксировать frontend contract;
4. добавить Swagger/OpenAPI;
5. расширить тесты на роли, импорт, конфликты и историю;
6. решить бизнес-правило по workload для нескольких инструкторов;
7. добавить ручной разбор конфликтов маппинга инструкторов при импорте.

---

## 22. Что должно быть в документации проекта дальше

Проектная документация должна содержать минимум следующие разделы.

### 22.1 Product
- цель продукта;
- кто пользователи;
- бизнес-сценарии;
- что входит в MVP;
- что не входит в MVP.

### 22.2 Architecture
- список модулей;
- зоны ответственности каждого модуля;
- схема взаимодействия;
- порты;
- окружения.

### 22.3 Domain
- сущности;
- связи;
- правила доступа;
- жизненный цикл занятия;
- правила учета часов.

### 22.4 API
- полный список эндпоинтов;
- форматы запросов;
- форматы ответов;
- коды ошибок;
- примеры вызовов.

### 22.5 Security
- способ авторизации;
- роли;
- матрица прав;
- ограничения на операции.

### 22.6 Import
- формат CSV;
- правила маппинга;
- правила обработки ошибок;
- ограничения.

### 22.7 Operations
- как запускать локально;
- как проверять БД;
- как тестировать через Postman;
- как переключать dev/prod.

### 22.8 Quality
- что покрыто тестами;
- что не покрыто;
- известные риски;
- backlog технического долга.

---

## 23. Файлы, которые стоит считать ключевыми

### Core MVP
- [SecurityConfig.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\config\SecurityConfig.java)
- [User.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\model\User.java)
- [Lesson.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\model\Lesson.java)
- [LessonService.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\LessonService.java)
- [JsonImportService.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\JsonImportService.java)
- [AuditService.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\AuditService.java)
- [DataInitializer.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-import-parser-core\src\main\java\ru\service\DataInitializer.java)

### API MVP
- [CoreClient.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-api-service\src\main\java\ru\client\CoreClient.java)
- [ImportController.java](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-api-service\src\main\java\ru\controller\ImportController.java)
- [README.md](D:\Proger\A.T.O.M.-Academy-Timetable-Organization-Manager\schedule-api-service\README.md)

---

## 24. Статус на текущий момент

Текущее состояние проекта:
- backend-контур MVP собран;
- public/read и protected/write сценарии есть;
- система уже может использоваться для ручной проверки и дальнейшей итеративной разработки;
- проект еще не приведен к production-уровню по security, тестам и стабильности импорта.

