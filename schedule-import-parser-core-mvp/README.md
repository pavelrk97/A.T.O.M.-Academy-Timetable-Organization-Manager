# schedule-import-parser-core-mvp

–абоча€ копи€ backend-сервиса под MVP системы управлени€ расписанием академии.

„то уже есть:
- публичный просмотр расписани€: `GET /api/public/schedule`
- Basic Auth дл€ личного кабинета
- роли: `ADMIN`, `EDITOR`, `INSTRUCTOR`
- управление пользовател€ми: `GET/POST/PUT /api/users`
- управление группами: `GET/POST/PUT/DELETE /api/groups`
- управление зан€ти€ми: `GET/POST/PUT/DELETE /api/lessons`
- истори€ изменений зан€тий: `GET /api/lessons/{id}/history`
- защита от конфликтов по `version`
- import JSON/CSV: `POST /api/import/json`, `POST /api/import/csv`
- подсчет часов: `GET /api/workload`

ƒефолтные пользователи:
- `admin / admin123`
- `editor / editor123`
- `instructor / instructor123`

ѕравила редактировани€:
- `ADMIN` и `EDITOR` могут создавать, мен€ть и удал€ть зан€ти€
- `INSTRUCTOR` может мен€ть только `note` у своих зан€тий
- публичный доступ только на чтение

ѕрофиль по умолчанию:
- `dev` с H2 in-memory базой

ѕримечани€:
- Maven wrapper в проекте отсутствует
- в текущем окружении не было установленного `mvn`, поэтому автоматическую сборку здесь не удалось прогнать
- `schedule-api-service` не синхронизировалс€ с новой моделью и не €вл€етс€ об€зательным дл€ запуска MVP-копии
