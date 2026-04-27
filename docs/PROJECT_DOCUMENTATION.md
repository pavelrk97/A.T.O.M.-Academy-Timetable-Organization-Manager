# A.T.O.M. — Полная документация

> **A.T.O.M.** (Academy Timetable Organization Manager) — система хранения, импорта, просмотра и редактирования академического расписания с ролевым доступом, JWT-авторизацией, учётом нагрузки преподавателей и аудит-логом изменений.

Документ описывает текущее состояние кода. Дата актуализации: **апрель 2026** (после ветки `feature/mobile-ui-auto-refresh`).

---

## Содержание

1. [Назначение](#1-назначение)
2. [Архитектура](#2-архитектура)
3. [Технологический стек](#3-технологический-стек)
4. [Доменная модель](#4-доменная-модель)
5. [Аутентификация и роли](#5-аутентификация-и-роли)
6. [Внешний API (через gateway)](#6-внешний-api-через-gateway)
7. [Внутренние API сервисов](#7-внутренние-api-сервисов)
8. [Импорт CSV](#8-импорт-csv)
9. [Учёт нагрузки (workload)](#9-учёт-нагрузки-workload)
10. [Личный кабинет](#10-личный-кабинет)
11. [Аудит и optimistic locking](#11-аудит-и-optimistic-locking)
12. [Frontend](#12-frontend)
13. [База данных и миграции](#13-база-данных-и-миграции)
14. [Конфигурация и переменные окружения](#14-конфигурация-и-переменные-окружения)
15. [Тестирование](#15-тестирование)
16. [Текущие ограничения и план развития](#16-текущие-ограничения-и-план-развития)

---

## 1. Назначение

A.T.O.M. решает три практические задачи учебного центра:

1. **Импорт расписания** из «гугл-таблично» CSV в нормализованную реляционную модель (группа → день → занятие → инструкторы), с автоматическим распознаванием формата ячейки и устойчивостью к разнобою исходных данных.
2. **Редактирование** сетки расписания в табличном UI «Google Sheets-style»: ячейка-день, мультивыбор, copy/paste, группа = строка, дата = колонка.
3. **Прозрачность для инструкторов**: персональный кабинет с расписанием и нагрузкой, уведомления, история изменений по конкретному занятию.

Система рассчитана на преподавательский коллектив до сотен человек и расписание на горизонт нескольких месяцев.

---

## 2. Архитектура

### 2.1 Сервисы

| # | Сервис | Maven модуль | Внутр. порт | Ответственность |
|---|---|---|---|---|
| 1 | **api-gateway** | `schedule-api-service` | 8081 | Единый внешний вход. Проксирует запросы в downstream и агрегирует данные «личного кабинета» (`my-*`). |
| 2 | **schedule-service** | `schedule-import-parser-core` | 8080 | Основной CRUD: группы, дни, занятия. Парсер CSV. Расчёт workload. Auto-import. Аудит. Notifications. |
| 3 | **identity-service** | `identity-service` | 8082 | Аутентификация (JWT HS256), пользователи, роли, профили, журнал входов. |
| 4 | **import-service** | `import-service` | 8083 | Внешний ingress `POST /api/import/csv`, лимит 200 МБ, оркестрирует upload в schedule-service. |

В **production** перед gateway стоит **Caddy** (`reverse-proxy`), который терминирует HTTPS и роутит:

- `/` → `frontend` (Next.js prod build);
- `/api/*` → `api-gateway`.

### 2.2 Поток запроса

```
Browser → Caddy (HTTPS) → api-gateway → identity-service / schedule-service / import-service → PostgreSQL
                                ↑
                                JWT Bearer
```

### 2.3 Межсервисная связь

- **Spring Cloud OpenFeign** + WebClient для multipart.
- JWT пользователя пробрасывается в `Authorization: Bearer …` через `DownstreamAuthHeaderFactory`.
- Внутренние эндпоинты (`/internal/**`) защищены **shared API key** (`X-Internal-API-Key`) — `IDENTITY_INTERNAL_API_KEY`, `SCHEDULE_INTERNAL_API_KEY`.

### 2.4 Конфигурация в коде

- `pom.xml` — корневой parent / aggregator.
- Каждый сервис: свой `src/main/resources/application.yml` со Spring-профилями `dev` и `prod`.
- Архив CSV-импорта: volume `/app/data/import-archive`, переменная `ATOM_IMPORT_ARCHIVE_DIR`.

---

## 3. Технологический стек

### 3.1 Общий

- **Java 21**, Maven multi-module.
- **Spring Boot 3.2.5**, Spring Web, Spring Data JPA, Spring Validation, Spring Actuator.
- **Lombok**.
- **JUnit 5**, AssertJ, Mockito, `@DataJpaTest`, `@WebMvcTest`.

### 3.2 По сервисам

| Сервис | Дополнительно |
|---|---|
| api-gateway | Spring Security (`oauth2-resource-server`), Spring Cloud OpenFeign, WebFlux/WebClient |
| schedule-service | Spring Security, Spring Data JPA, **OpenCSV**, **Apache POI** (workload export в xlsx), PostgreSQL/H2 |
| identity-service | Spring Security, Spring Data JPA, **Nimbus JOSE+JWT**, BCrypt, PostgreSQL/H2 |
| import-service | Spring Security, Spring WebFlux/WebClient, multipart limit 200 MB |

### 3.3 Frontend

- **Next.js 16** (App Router, RSC), **React 19**.
- **Tailwind CSS**, **Radix UI** (Sheet, Accordion, Dialog, Tabs).
- API-клиент в `lib/api.ts` — `fetch` с автодобавлением `Authorization: Bearer <token>`.

---

## 4. Доменная модель

### 4.1 Сущности

| Сущность | Таблица | Ключевые поля |
|---|---|---|
| `User` | `users` | `id` (UUID), `username`, `password_hash`, `full_name`, `display_name`, `email`, `phone`, `position`, `department`, `role`, `active`, `can_teach`, `editor_access` |
| `Group` | `groups` | `id`, `code`, `location`, `course` |
| `Day` | `days` | `id`, `date`, `group_id`, `meta` (jsonb-like) |
| `Lesson` | `lessons` | `id`, `day_id`, `order_number`, `title`, `lecturer`, `duration_hours`, `note`, `type` (`LECTURE`/`SELF_STUDY`/`ASSESSMENT`), `version` |
| `lesson_lecturers` | связь | свободный список ФИО (legacy + импорт) |
| `lesson_instructors` | связь | many-to-many `lesson` ↔ `user` (нормализованные инструкторы) |
| `user_groups` | связь | many-to-many `user` ↔ `group` |
| `ChangeLog` | `change_logs` | `entity_type`, `entity_id`, `action`, `changed_by`, `changed_at`, `before_json`, `after_json`, `comment` |
| `Notification` | `notifications` | (легковесная коллекция; реализация вычисляется на лету по дням) |
| `AutoImportSettings` | `auto_import_settings` | `enabled`, `source_url`, `last_run_at`, `last_status`, `last_error`, `last_imported_groups`, `last_imported_lessons`, `next_run_at`, `updated_at`, `updated_by` |
| `UserLoginEvent` | `user_login_events` | `id`, `user_id`, `logged_at`, `ip_address`, `user_agent` |

### 4.2 Принципы

- Отдельной сущности `Instructor` нет — преподаватели хранятся как `User` с `can_teach=true`.
- При импорте CSV ФИО инструктора сначала записывается в `lesson_lecturers` (текстовый список), потом по совпадению `User.fullName` мапится в `lesson_instructors` (FK).
- На все CUD-операции по `Lesson` пишется запись в `change_logs`.
- Optimistic locking через `@Version` на `Lesson`.

### 4.3 Каскадирование при удалении

```
delete Group → CASCADE → all Days of the group
delete Day   → CASCADE → all Lessons of the day
delete Lesson →           remove from change_logs (FK), lesson_instructors, lesson_lecturers
```

`@OneToMany(cascade = ALL, orphanRemoval = true)` на `Group.days` и `Day.lessons`.

---

## 5. Аутентификация и роли

### 5.1 JWT-флоу

1. Клиент: `POST /api/auth/login { username, password }` → `{ tokenType, accessToken, expiresAt }`.
2. Gateway проксирует в `identity-service`, который:
   - валидирует пароль (BCrypt),
   - пишет запись в `user_login_events`,
   - подписывает HS256 JWT (`security.jwt.secret`, минимум 32 байта).
3. Клиент шлёт `Authorization: Bearer <accessToken>` во все защищённые запросы.
4. **Все** сервисы валидируют JWT через `oauth2ResourceServer` с тем же секретом (`JWT_SECRET` env).
5. JWT-claims: `sub` = userId (UUID), `roles: ["ADMIN" | "EDITOR" | "INSTRUCTOR"]`, плюс `editorAccess`.

### 5.2 Роли

| Роль | Что может |
|---|---|
| `ADMIN` | Всё: пользователи, импорт CSV, auto-import, CRUD групп/занятий, workload, аудит, статистика входов |
| `EDITOR` | CRUD групп/занятий, auto-import, workload по всем, история занятий |
| `INSTRUCTOR` | Чтение публичного расписания, свой кабинет, свой workload, своё расписание |
| `PUBLIC` | Только `GET /api/public/schedule` без авторизации |

**Дополнительный флаг `editorAccess`** — позволяет обычному `INSTRUCTOR`'у редактировать расписание (CRUD групп/занятий), не повышая роль до `EDITOR`. Используется для отдельных доверенных преподавателей.

Полный матрикс прав см. в `schedule-api-service/src/main/java/ru/config/SecurityConfig.java`.

### 5.3 Журнал входов

`identity-service`/`UserLoginTrackingService` пишет каждый успешный `POST /api/auth/login` в `user_login_events`:

- `user_id`, `logged_at`, `ip_address` (с разбором `X-Forwarded-For`), `user_agent` (обрезается до 512 символов).
- Запись идёт в **отдельной транзакции** (`REQUIRES_NEW`) и **не валит логин**, если БД временно недоступна.

`UserActivityService` агрегирует это в `GET /api/users/activity` (`ADMIN`-only): по каждому пользователю — последний вход, число входов за 30 дней, всего, сортировка по последнему входу.

---

## 6. Внешний API (через gateway)

База: `https://prk97.ru/api/...` (или `http://localhost:8081/api/...` в dev).

Где не указано иное — авторизация **JWT Bearer**.

### 6.1 Аутентификация

| Method | Path | Role | Описание |
|---|---|---|---|
| `POST` | `/api/auth/login` | — | `{ username, password }` → JWT |
| `GET`  | `/api/auth/me` | any | Текущий пользователь (`User` DTO) |

### 6.2 Публичное расписание

| Method | Path | Role | Описание |
|---|---|---|---|
| `GET` | `/api/public/schedule?groupCode=&from=&to=` | **PUBLIC** | Список `ScheduleEntry`. Без авторизации |

### 6.3 Пользователи

| Method | Path | Role | Описание |
|---|---|---|---|
| `GET`  | `/api/users` | ADMIN, EDITOR | Все пользователи |
| `POST` | `/api/users` | ADMIN | Создать пользователя |
| `PUT`  | `/api/users/{id}` | ADMIN | Обновить пользователя |
| `GET`  | `/api/users/activity` | **ADMIN** | Журнал входов (агрегат) |

### 6.4 Группы

| Method | Path | Role | Описание |
|---|---|---|---|
| `GET`    | `/api/groups` | any auth | Список групп с днями и занятиями |
| `GET`    | `/api/groups/{id}` | any auth | Группа по id |
| `POST`   | `/api/groups` | ADMIN, EDITOR | Создать (можно с пустым `days: []`) |
| `PUT`    | `/api/groups/{id}` | ADMIN, EDITOR | Обновить метаданные / дни |
| `DELETE` | `/api/groups/{id}` | ADMIN, EDITOR | Каскадно удалить с днями и занятиями |

### 6.5 Занятия

| Method | Path | Role | Описание |
|---|---|---|---|
| `GET`    | `/api/lessons?groupCode=&from=&to=` | any auth | Плоский список `ScheduleEntry` |
| `GET`    | `/api/lessons/{id}` | any auth | DTO для редактора (с `version`, `instructorIds`, `instructorNames`) |
| `POST`   | `/api/lessons` | ADMIN, EDITOR | Создать |
| `PUT`    | `/api/lessons/{id}` | ADMIN, EDITOR | Обновить (требует `version` в payload — иначе `409 Conflict`) |
| `DELETE` | `/api/lessons/{id}?version=N` | ADMIN, EDITOR | Удалить |
| `GET`    | `/api/lessons/{id}/history` | any auth | История изменений из `change_logs` |
| `POST`   | `/api/lessons/day-sync` | ADMIN, EDITOR | Пакетная замена всех занятий в дне (см. ниже) |

#### `POST /api/lessons/day-sync`

Используется UI-редактором: один RPC на день, заменяет весь набор занятий.

```jsonc
{
  "groupId": "uuid",
  "date": "2026-04-27",
  "ensureDay": true,         // если true — день будет создан, если ещё нет
  "lessons": [
    {
      "id": "uuid|null",     // null для новых
      "version": 0,
      "orderNumber": 1,
      "title": "...",
      "durationHours": 4,
      "note": null,
      "type": "LECTURE",
      "instructorIds": ["uuid", "..."]
    }
    // ...до 8 слотов
  ]
}
```

Возвращает обновлённый `GroupDto` целиком — UI заменяет локальное состояние группы одним апдейтом.

### 6.6 Импорт

| Method | Path | Role | Тело | Описание |
|---|---|---|---|---|
| `POST` | `/api/import/csv` | **ADMIN** | `multipart/form-data: file` | Ручной импорт |

### 6.7 Auto-Import

| Method | Path | Role | Описание |
|---|---|---|---|
| `GET`  | `/api/auto-import/settings` | any auth | Текущие настройки |
| `PUT`  | `/api/auto-import/settings` | ADMIN, EDITOR | `{ enabled, sourceUrl }` |
| `POST` | `/api/auto-import/run` | ADMIN, EDITOR | Запустить импорт прямо сейчас |

Планировщик в `schedule-service` тянет CSV по `sourceUrl` каждые N минут (см. `application.yml`). Источник — обычно публичный экспорт Google Sheets в CSV.

### 6.8 Workload

| Method | Path | Role | Описание |
|---|---|---|---|
| `GET` | `/api/workload?instructorId=&from=&to=` | any auth (с ограничением) | Список `{ instructorId, instructorName, totalHours }` |
| `GET` | `/api/workload/export?instructorId=&from=&to=` | **ADMIN** | xlsx-выгрузка через Apache POI |

`INSTRUCTOR` без `editorAccess` видит только свой workload.

**Правило учёта**: каждому инструктору, назначенному на занятие, начисляется **полная** `durationHours`. Часы между инструкторами **не делятся**.

### 6.9 Личный кабинет (`/api/me/**`)

См. [§10. Личный кабинет](#10-личный-кабинет).

---

## 7. Внутренние API сервисов

Внешние клиенты сюда **не ходят** — используется межсервисная связь.

### 7.1 identity-service (`:8082`)

- `POST /api/auth/login` — публичный.
- `GET /api/auth/me`, `GET /api/users`, `POST /api/users`, `PUT /api/users/{id}` — JWT.
- `GET /api/users/activity` — JWT, ADMIN.
- `GET /api/me/profile`, `PUT /api/me/profile`, `PUT /api/me/password` — JWT.
- `GET /internal/users/by-username/{username}` — `X-Internal-API-Key`. Используется schedule-service для resolving текущего пользователя.

### 7.2 schedule-service (`:8080`)

- Все `/api/**` эндпоинты, аналогичные gateway (см. §6).
- `POST /internal/import/csv` — `X-Internal-API-Key`. Сюда import-service пересылает multipart-файл.

### 7.3 import-service (`:8083`)

- `POST /api/import/csv` — JWT, ADMIN. Внешний ingress, лимит **200 МБ**, перенаправляет в schedule-service `/internal/import/csv`.

---

## 8. Импорт CSV

Поддерживается **только** CSV (JSON-импорт удалён в v3).

### 8.1 Два пути загрузки

1. **Ручной**: `POST /api/import/csv` (`ADMIN`) → import-service → schedule-service.
2. **Автоматический** (`auto-import`): scheduled-задача в schedule-service по `sourceUrl` (см. §6.7).

### 8.2 Парсер

Реализация: `schedule-import-parser-core/src/main/java/ru/parser/ScheduleCsvParser.java`.

Поддерживается:
- два формата ячейки (`Title (Nч) + Instructor` и `Instructor (Nч) + Title`),
- многострочные названия,
- assessment-titles по `contains` (`Intermediate Examination (пересдача)`, `Промежуточный контроль`, `Entry Level Test`, etc.),
- course-code с `:` (`CS01:`),
- маркер самостоятельной работы `СП`.

**Полная спецификация формата** — отдельный документ: [`docs/CSV_FORMAT.md`](CSV_FORMAT.md).

### 8.3 Архив

Каждый успешный импорт сохраняется в volume `/app/data/import-archive/` (env `ATOM_IMPORT_ARCHIVE_DIR`). Хранятся последние 2 версии для отката вручную.

---

## 9. Учёт нагрузки (workload)

`Workload = sum(durationHours)` по всем занятиям, на которых стоит конкретный инструктор, за период `from..to`.

### 9.1 Особенности

- Часы не делятся: если на занятии 3 инструктора, каждый получит full `durationHours`.
- `INSTRUCTOR` без `editorAccess` через `/api/workload` видит только себя.
- ADMIN может выгрузить xlsx через `/api/workload/export` (Apache POI).

### 9.2 Workload-calendar для ЛК

`/api/me/workload/calendar?from=&to=` возвращает:

```jsonc
{
  "instructorId": "uuid",
  "instructorName": "Меркель",
  "from": "2026-04-01",
  "to":   "2026-04-30",
  "totalHours": 47,
  "days": [
    { "dayId": "uuid", "date": "2026-04-30", "totalHours": 6,
      "lessons": [ { "lessonId": "...", "groupCode": "гр.174", "title": "...", "durationHours": 2 } ] }
  ]
}
```

UI рендерит это календарём, незаполненные дни остаются под другие задачи преподавателя.

---

## 10. Личный кабинет

База: `/api/me/**`. Все требуют JWT.

| Method | Path | Описание |
|---|---|---|
| `GET` | `/api/me/profile` | Свой профиль |
| `PUT` | `/api/me/profile` | Обновить displayName/email/phone/position/department |
| `PUT` | `/api/me/password` | `{ currentPassword, newPassword }` |
| `GET` | `/api/me/schedule/grid?from=&to=` | Grid-формат всего расписания (см. ниже) |
| `GET` | `/api/me/schedule/instructor-grid?from=&to=` | Grid только своих занятий |
| `GET` | `/api/me/workload/calendar?from=&to=` | Календарь нагрузки |
| `GET` | `/api/me/workload/export?from=&to=` | xlsx своей нагрузки |
| `GET` | `/api/me/notifications?from=&to=` | Лёгкие уведомления (ссылки на свои дни) |
| `GET` | `/api/me/dashboard?from=&to=` | **Агрегат**: profile + instructorSchedule + workload + notifications |

### 10.1 Schedule grid

Формат, на который рассчитан UI с табличным расписанием:

```jsonc
{
  "dates": ["2026-04-27", "2026-04-28", "..."],
  "groups": [
    {
      "groupId": "uuid", "groupCode": "гр.174", "location": "Б202", "course": "...",
      "days": [
        { "dayId": "uuid|null", "date": "2026-04-27", "lessons": [
            { "lessonId": "...", "version": 1, "orderNumber": 1, "title": "...",
              "type": "LECTURE", "durationHours": 4, "note": null,
              "instructorNames": ["Меркель"] }
          ] }
      ]
    }
  ]
}
```

`instructor-grid` — то же, но фильтр по текущему пользователю (либо по `editorAccess`).

### 10.2 Dashboard

Один эндпоинт = одно состояние стартового экрана ЛК:

```jsonc
{
  "profile": { /* User */ },
  "instructorSchedule": { /* ScheduleGridData */ },
  "workload":           { /* WorkloadCalendar */ },
  "notifications":      [ /* Notification[] */ ]
}
```

UI не собирает 4 запроса — один `/me/dashboard` и страница готова.

---

## 11. Аудит и optimistic locking

### 11.1 Optimistic locking

`Lesson.@Version int version`.

- Любой `PUT /api/lessons/{id}` или `DELETE` проверяет, что переданная `version` совпадает с текущей в БД.
- Несовпадение → `409 Conflict`. UI должен пере-загрузить занятие и предложить редактору смержить.

### 11.2 Audit (change_logs)

Каждое CUD-действие на занятии пишет запись:

```
entityType  = "Lesson"
entityId    = lesson UUID
action      = CREATED | UPDATED | DELETED
changedBy   = username
changedAt   = timestamp
beforeJson  = снимок до (для UPDATE/DELETE)
afterJson   = снимок после (для CREATE/UPDATE)
comment     = опц.
```

`GET /api/lessons/{id}/history` возвращает отсортированный список — UI показывает диффом.

### 11.3 user_login_events

Аналогично, но для входов. См. §5.3.

---

## 12. Frontend

Расположен в `docs/V0 UI/` (исторически назван так — это полноценное Next.js приложение, не статика).

### 12.1 Стек и структура

- **Next.js 16** App Router.
- TypeScript строгий, ESLint, Tailwind.
- Radix UI поверх Tailwind для шторок (`Sheet`), аккордеонов (`Accordion`), диалогов.

```
docs/V0 UI/
├── app/
│   ├── layout.tsx            ← глобальная вёрстка + AuthProvider
│   ├── page.tsx              ← редирект на /schedule или /cabinet
│   ├── login/page.tsx        ← /api/auth/login
│   ├── schedule/page.tsx     ← публичная сетка расписания
│   └── cabinet/page.tsx      ← ЛК (адаптивный к роли)
├── components/
│   ├── header.tsx
│   ├── schedule/
│   │   ├── schedule-grid.tsx     ← основная сетка (публичная и ЛК)
│   │   ├── schedule-filters.tsx
│   │   ├── lesson-card.tsx       ← shared карточка занятия
│   │   └── lesson-details.tsx    ← детальный просмотр дня
│   └── cabinet/
│       ├── admin-workspace.tsx   ← операции (Accordion)
│       ├── lesson-admin-editor.tsx
│       ├── auto-import-card.tsx
│       ├── user-admin-editor.tsx
│       ├── user-activity-card.tsx
│       ├── workload-calendar.tsx
│       └── ...
└── lib/
    ├── api.ts                ← fetch-клиент, getAuthHeaders, типизированные методы
    ├── auth-context.tsx      ← React context для access token
    ├── types.ts              ← все DTO согласованы с backend
    └── schedule.ts           ← buildGridFromEntries, filterGridByInstructor
```

### 12.2 Ключевые UX-решения

- **Schedule grid** одинаковый в публичном расписании и в редакторе операций — общий `<LessonCard>` (тип сверху, потом название, часы, инструктор).
- **Первая колонка фиксированной ширины** (72/140 px), не масштабируется зумом — больше места под дни на мобиле.
- **Мобильный поп-ап дня** — `Sheet` снизу, рендерится только при ширине < 1280 px (иначе на десктопе с боковой панелью оверлей затемнял бы экран).
- **Operations Accordion** — все секции операций (`Сетка`, `Auto-import`, `Пользователи`, `Ручной импорт`, `Активность`) свёрнуты по умолчанию, можно держать открытыми сколько угодно (Radix `type="multiple"`).
- **Group CRUD** прямо в редакторе: клик по коду группы открывает шторку с правкой `code/location/course` и каскадным удалением.

### 12.3 Авторизация на клиенте

`lib/api.ts`:

```ts
let accessToken: string | null = null
export function setAccessToken(token: string) { accessToken = token }
function getAuthHeaders() {
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
}
```

Хранение токена — в памяти SPA (не в localStorage по соображениям XSS). После reload пользователь логинится снова. При расширении — добавить httpOnly cookie + refresh-token endpoint.

---

## 13. База данных и миграции

### 13.1 Flyway

`db/migration/`:

| Файл | Что делает |
|---|---|
| `V1__init_schema.sql` | Базовая схема: users, groups, days, lessons, lesson_lecturers, lesson_instructors, user_groups, change_logs, notifications |
| `V2__add_editor_access_to_users.sql` | `users.editor_access boolean default false` |
| `V3__make_group_course_text.sql` | `groups.course` → text (раньше был short string) |
| `V4__auto_import_settings.sql` | `auto_import_settings` таблица (singleton-row) |
| `V5__user_login_events.sql` | `user_login_events` + индексы по `user_id` и `logged_at` |

### 13.2 Подключение

- **dev**: H2 file-based, `jdbc:h2:file:~/atom-shared-db;AUTO_SERVER=TRUE;MODE=PostgreSQL` (общий файл между сервисами для упрощения).
- **prod**: PostgreSQL 16. Один инстанс на все сервисы. Изоляция по namespace пока **не реализована** — все таблицы в одной схеме `public`.

### 13.3 ddl-auto

- `prod`: `validate` (Flyway единственный, кто меняет схему).
- `dev`: `validate` (миграции применяет отдельный bootstrap при старте каждого сервиса).

---

## 14. Конфигурация и переменные окружения

### 14.1 Общие (`.env`)

| Переменная | Назначение |
|---|---|
| `POSTGRES_HOST/PORT/DB/USER/PASSWORD` | подключение к БД |
| `JWT_SECRET` | HS256 ключ, **минимум 32 байта**. Один на все сервисы. |
| `IDENTITY_INTERNAL_API_KEY` | защищает internal эндпоинты identity-service |
| `SCHEDULE_INTERNAL_API_KEY` | защищает internal эндпоинты schedule-service |
| `IDENTITY_SERVICE_URL` | URL identity-service для feign-клиентов |
| `SCHEDULE_SERVICE_URL` | URL schedule-service |
| `ATOM_IMPORT_ARCHIVE_DIR` | путь архива импорта в контейнере |

### 14.2 Production-специфичные (`.env.production.example`)

| Переменная | Назначение |
|---|---|
| `PUBLIC_HOSTNAME` | домен для Caddy (`prk97.ru`) |
| `ACME_EMAIL` | email для Let's Encrypt |

---

## 15. Тестирование

### 15.1 Backend

```bash
mvn test                                                # все тесты
mvn -B -pl schedule-import-parser-core -am test         # модуль
mvn -B -pl schedule-import-parser-core test -Dtest=ScheduleCsvParserTest
```

Покрыты:

- `ScheduleCsvParserTest` — парсер CSV: оба формата (`Title→Instructor` и `Instructor→Title`), assessment с суффиксом, course-code с `:`, ELT00 (legacy), ячейки без даты.
- `CsvImportServiceIntegrationTest` — повторный импорт без развала FK.
- `LessonServiceTest` — CRUD, optimistic locking, ограничения роли `INSTRUCTOR`, аудит.
- `WorkloadServiceTest` — расчёт без деления часов.
- `PublicScheduleControllerTest` — прокидка дат через gateway.
- `MeControllerTest` — self-profile, password change, my-grid, my-workload.

### 15.2 Frontend

```bash
cd "docs/V0 UI" && pnpm run lint
```

Юнит-тестов на компоненты пока нет. TS-проверка — `tsc --noEmit -p tsconfig.json`.

### 15.3 Postman

Папка `postman/` с коллекцией smoke-тестов — быстрая проверка после деплоя.

---

## 16. Текущие ограничения и план развития

### 16.1 Реализовано

✅ JWT auth (HS256), refresh не нужен — короткоживущий access  
✅ 4 микросервиса + Caddy reverse-proxy + Next.js frontend  
✅ Полный CRUD групп, дней, занятий с optimistic locking и аудитом  
✅ Workload (включая xlsx-экспорт через Apache POI)  
✅ Personal cabinet API + dashboard-агрегатор  
✅ Auto-import по расписанию + manual import  
✅ Журнал входов и `/api/users/activity` для ADMIN  
✅ Production deploy с автоматическим Let's Encrypt  
✅ Парсер CSV для двух распространённых форматов ячеек  

### 16.2 Известные ограничения

- ❌ Все сервисы делят одну схему PostgreSQL — изоляции по namespace нет.
- ❌ `id` пользователя в JWT — UUID identity-service, но в `lesson_instructors.user_id` используется тот же UUID. **Cross-service identifier работает только потому, что таблицы в одной БД.** При разделении баз сломается.
- ❌ Notifications — лёгкая коллекция ссылок, не настоящая доставка (нет email/push, нет mark-as-read).
- ❌ Refresh-токенов нет, после `expiresAt` — повторный логин руками.
- ❌ Нет WebSocket-уведомлений о изменениях расписания (UI узнаёт только при ручном refresh).

### 16.3 Дальше

- 🔜 Refresh tokens / sliding session.
- 🔜 OpenAPI / Swagger UI на каждом сервисе.
- 🔜 Изоляция БД по сервисам (минимум — отдельные схемы `identity_*` / `schedule_*`).
- 🔜 Email-уведомления + mark-as-read на notifications.
- 🔜 Unit-тесты на ключевые компоненты фронта.
- 🔜 Прометей-метрики и Grafana дашборд.

---

**Связанные документы**:

- 📥 [`docs/CSV_FORMAT.md`](CSV_FORMAT.md) — формат CSV для импорта
- 🚢 [`docs/PRODUCTION_DEPLOY.md`](PRODUCTION_DEPLOY.md) — production deploy
- 🏠 [`README.md`](../README.md) — quick start
