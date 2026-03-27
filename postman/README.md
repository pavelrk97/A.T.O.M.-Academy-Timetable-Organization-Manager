# Postman

Эта папка предназначена для хранения Postman-коллекций и environment-файлов проекта.

Рекомендуемая структура:
- `A.T.O.M.smoke.postman_collection.json`
- `A.T.O.M.full.postman_collection.json`
- `local.postman_environment.json`

Что хранить здесь:
- smoke test коллекции
- ручные regression-наборы
- локальные environment-файлы без секретов

Что не хранить:
- реальные токены
- персональные секреты
- приватные пароли production-окружений
