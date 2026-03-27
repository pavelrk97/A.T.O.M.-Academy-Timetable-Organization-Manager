# import-service

Внешний сервис импорта CSV.

## Что внутри
- `POST /api/import/csv`

## Ответственность
- принимает CSV от клиента
- проверяет `ADMIN` через `Basic Auth`
- пересылает файл во внутренний import endpoint `schedule-service`

## Примечания
- JSON-import отсутствует
- бизнес-логика сохранения расписания выполняется в `schedule-service`
