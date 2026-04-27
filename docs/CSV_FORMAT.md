# Формат CSV для импорта расписания

Документ описывает контракт CSV, который понимает `ScheduleCsvParser`
(`schedule-import-parser-core/src/main/java/ru/parser/ScheduleCsvParser.java`),
и подробно — алгоритм его работы.

Импорт идёт через:

- **Ручной**: `POST /api/import/csv` (multipart, `ADMIN`).
- **Автоматический**: scheduler в `schedule-service` тянет CSV по `auto_import_settings.source_url` (обычно — публичный экспорт Google Sheets).

---

## Содержание

1. [Общая структура CSV](#1-общая-структура-csv)
2. [Заголовок группы](#2-заголовок-группы-колонка-0)
3. [Ячейка дня — что внутри](#3-ячейка-дня--что-внутри)
4. [Алгоритм парсинга ячейки](#4-алгоритм-парсинга-ячейки)
5. [Активный course code](#5-активный-course-code-наследование-между-ячейками)
6. [Список известных инструкторов (хардкод)](#6-список-известных-инструкторов-хардкод)
7. [Полный пример](#7-полный-пример)
8. [Ограничения и крайние случаи](#8-ограничения-и-крайние-случаи)
9. [Тесты](#9-тесты)

---

## 1. Общая структура CSV

CSV — широкая таблица: **строки = группы**, **колонки = даты**.

| Строка | Содержит |
|---|---|
| `0` | Дни недели (`пн`, `вт`, …). Не используются парсером, но должны быть. |
| `1` | Даты в формате `<день>.<месяц>.` (`27.апр.`, `1.янв.`, `5.мая`). Месяц — русское сокращение (см. `DateParser.MONTHS`). |
| `2..N` | По одной строке на группу; колонка 0 — заголовок группы, остальные — ячейки дней. |

**Правила:**
- Пустые ячейки игнорируются.
- Ячейки в колонках, у которых пустая дата в строке `1` — **пропускаются** с предупреждением в логе:
  `Skipping CSV cell without date header: group=…, column=…, preview=…`
- Внутри-ячеечные переносы строк — через `\n` (CSV-кавычки обязательны).

---

## 2. Заголовок группы (колонка 0)

Многострочная ячейка:

```
гр. <номер> [(<пометка>)]
<location>
```

Парсер:
- `code` = первая строка целиком (`"гр. 87"`, `"гр. 174 (резерв НС АС)"`).
- `location` = вторая строка (если есть).

```csv
"гр. 87
А308"
```

→ `Group { code: "гр. 87", location: "А308", course: null }`

---

## 3. Ячейка дня — что внутри

Ячейка состоит из строк (после `split("\\n")`). Каждая строка — один из шести **типов токенов**:

| Тип | Регулярка / правило | Пример |
|---|---|---|
| **`СП`** | `line == "СП"` | `СП` |
| **Course code** | `^[A-Z&]{1,5}\d{2}$` (с опц. `:` в конце) | `OE00`, `I&C02`, `CS01:` |
| **Instructor name** | `INSTRUCTORS.contains(line)` (полное равенство) | `Меркель`, `Иванов С` |
| **Assessment title** | `lower(line_no_dur).contains(lower(key))` для одного из ключей `ASSESSMENT_TITLES` | `Intermediate Examination (пересдача)` |
| **Duration line** | `\((\d+)\s*ч\)` — группа цифр + `ч` в скобках где-то в строке | `Меркель (2ч)`, `Title (4 ч)` |
| **Plain text** | всё остальное | `System of chemical reagents... (KBD-1)` |

Для типа **Duration line** парсер вычисляет:
- `hours` — число из захвата;
- `text` = строка с **вырезанным** токеном `(Nч)` (всё, что было в строке, кроме самой длительности).

`text` дальше анализируется отдельно — там может быть имя инструктора, остаток названия, или ничего.

Ключи `ASSESSMENT_TITLES`:
```
Intermediate Examination
Промежуточный контроль
Entry Level Test
Examination
Entermidiate examination   ← опечатка из реальных данных
```

Сравнение **case-insensitive** и **по подстроке**, поэтому матчат:
- `Intermediate Examination (пересдача)` → ключ `Intermediate Examination`,
- `Intermediate examination (8ч)` → тот же ключ,
- `Промежуточный контроль (8ч)` → ключ `Промежуточный контроль`.

---

## 4. Алгоритм парсинга ячейки

`parseCell(cell, day)` — детерминированная state machine. Состояние:

| Переменная | Назначение |
|---|---|
| `order` | Порядковый номер следующего создаваемого урока в дне (1, 2, 3 …) |
| `selfStudy` | После маркера `СП` — все следующие занятия типа `SELF_STUDY` |
| `inAssessment` | Между assessment-title и следующим обычным занятием все строки `<имя> (Nч)` добавляются как инструкторы текущего ассесмент-урока |
| `currentAssessment` | Ссылка на текущий ассесмент-урок (для добавления инструкторов и часов) |
| `pendingInstructor` | Имя инструктора, увиденное на отдельной строке — относится к **следующему** уроку |
| `pendingTitle` | StringBuilder с накопленным названием из plain-text строк — относится к **следующему** уроку |
| `lines[]` | Массив строк ячейки. Lookahead помечает уже использованные элементы пустой строкой `""` — внешний цикл их пропускает |

### 4.1 Главный цикл — что делает с каждой строкой

```
for i in 0..lines.length:
    line = trim(lines[i])
    if line.empty: continue                           # пустые скипаем

    case line:
      "СП"                                → selfStudy = true; continue
      matches COURSE_CODE                 → day.meta.courseCode = line; continue
      INSTRUCTORS.contains(line)          → pendingInstructor = line; continue

    # Проверяем длительность и считаем lineWithoutDuration
    (hasDuration, lineWithoutDuration) = parseDuration(line)

    # Ассесмент: сначала проверяем — ассесмент-маркер может быть как с длительностью, так и без
    if matchesAssessment(lineWithoutDuration):
        createAssessmentLesson(title=lineWithoutDuration,
                               hours = hasDuration ? N : 0,
                               type = ASSESSMENT,
                               lecturers = pendingInstructor ? [pendingInstructor] : [])
        inAssessment = true
        currentAssessment = (just created)
        pendingInstructor = null
        pendingTitle = ""
        continue

    if !hasDuration:
        # plain-text → копим в pendingTitle (через пробел, если уже что-то накопили)
        pendingTitle += (" " if pendingTitle.notEmpty else "") + line
        continue

    # дальше — строка с длительностью, не assessment
    hours = N
    text  = lineWithoutDuration

    if inAssessment:
        # внутри ассесмента: каждая (Nч)-строка трактуется как инструктор (или 1-инстр + часы)
        found = findInstructors(text)         # подстрочный поиск по INSTRUCTORS
        if found.empty && pendingInstructor:
            found = [pendingInstructor]
        currentAssessment.lecturers += dedupe(found)
        if currentAssessment.hours == 0: currentAssessment.hours = hours
        pendingInstructor = null
        continue

    # обычный урок (LECTURE или SELF_STUDY)
    lesson = newLesson(order=order++, hours=hours,
                       type = selfStudy ? SELF_STUDY : LECTURE)

    instructorsInText = findInstructors(text)
    if instructorsInText.notEmpty:
        # вариант B: «<Инструктор> (Nч) [<хвост>]»
        lecturer = instructorsInText[0]
        titleFromText = text.replace(lecturer, "").trim()
        if titleFromText.empty:
            # имя стояло один на строке — название берём из контекста
            if pendingTitle.notEmpty:
                title = pendingTitle              # «название было ВЫШЕ»
            else:
                title = consumeFollowingTitle()   # «название будет НИЖЕ» (lookahead)
        else:
            # внутри строки осталось ещё что-то — это название
            title = pendingTitle.notEmpty ? pendingTitle + " " + titleFromText : titleFromText
    else:
        # вариант A: «<Title> (Nч)», инструктор — из pendingInstructor (стоял отдельной строкой)
        title    = pendingTitle.notEmpty ? pendingTitle + " " + text : text
        lecturer = pendingInstructor

    lesson.title = title
    lesson.lecturer = lecturer
    day.lessons += lesson

    # сброс контекста
    pendingInstructor = null
    pendingTitle = ""
    inAssessment = false
```

### 4.2 Lookahead — `consumeFollowingTitle`

Когда строка `<Имя> (Nч)` сама не несёт названия, парсер «съедает» следующие plain-text строки до первой граничной:

```
for j in i+1..lines.length:
    n = trim(lines[j])
    if n.empty:           continue                       # пропускаем пустые
    if n == "СП":         break
    if INSTRUCTORS.has(n): break
    if n matches COURSE_CODE: break
    if DURATION matches n: break
    if matchesAssessment(n): break

    title += (" " if title.notEmpty else "") + n
    lines[j] = ""           # ВАЖНО: метим строку «съеденной»,
                            # чтобы внешний цикл её пропустил
```

Так работает Format B с многострочным названием:
```
Меркель (2ч)              ← создаём урок, lecturer=Меркель
System of chemical        ← consumeFollowingTitle: добавили
reagents preparation      ← добавили
(KBD-1)                   ← добавили
Меркель (3ч)              ← граница! следующий урок
...
```

→ `title = "System of chemical reagents preparation (KBD-1)"`.

### 4.3 Граф состояний

```
                      ┌────────────────────────────────────────┐
                      │         регулярный режим               │
                      │  selfStudy: false | true               │
                      │  pendingInstructor / pendingTitle      │
                      └─────────────┬───────────┬──────────────┘
                                    │           │
                  встречен СП ──────┘           │
                  selfStudy:=true               │
                                                │
                  встречен                      │
                  assessment-title              │   обычный урок
                  ──────────────────┐           │   создаётся,
                                    ▼           │   контекст сбрасывается
                          ┌──────────────────┐  │
                          │  ассесмент       │  │
                          │  собирает        │──┘   (после первого
                          │  инструкторов    │       не-assessment-(Nч)
                          │  по (Nч)-строкам │       выходим)
                          └──────────────────┘
```

### 4.4 Что попадёт в БД из урока

| Поле в БД | Откуда |
|---|---|
| `order_number` | счётчик в ячейке (1, 2, 3…) |
| `title` | как описано выше (text / pendingTitle / lookahead) |
| `lecturer` | строковое имя — для legacy и UI |
| `lecturers` (в `lesson_lecturers`) | в текущем парсере **не заполняется** для обычных уроков, для ассесмента — туда добавляются все собранные имена |
| `duration_hours` | число из `(Nч)` |
| `type` | `LECTURE` / `SELF_STUDY` / `ASSESSMENT` |
| `note` | парсер не пишет, всегда `null` |

После сохранения занятий **отдельный шаг** мапит `lecturer` (строку) на `User` по `User.fullName` и пишет связь в `lesson_instructors` (FK). Это уже не парсер, а `ImportService`.

---

## 5. Активный course code (наследование между ячейками)

Если в ячейке нет course-code, парсер берёт **последний** code из предыдущих ячеек **той же группы**. Имитирует поведение Google Sheets, где одна ячейка задаёт код для соседних дней.

```
[E03 ...]   ← ячейка 1: code=E03
[ ... ]     ← ячейка 2 без code: считается E03
[I&C02 ...] ← ячейка 3: переключается на I&C02
```

Если у `Day` так и не появился `courseCode` (ни в самой ячейке, ни унаследованно) — **день не сохраняется**.

---

## 6. Список известных инструкторов (хардкод)

⚠️ **Это активное ограничение текущей версии.**

Распознавание ФИО на отдельной строке работает по **зашитому в код** `Set<String>` (см. `INSTRUCTORS` в начале `ScheduleCsvParser.java`):

```java
private static final Set<String> INSTRUCTORS = Set.of(
    "Бращенко","Волкова","Майстренко","Мухамбеталин","Трушейкин","Брянский",
    "Коновалов","Костылев","Алексеева","Голубенко","Гонтов","Иванов",
    "Кадчик","Канищев","Ким","Иванов С","Смирнов","Климов","Павленко",
    "Алексеев","Виноградов","Гончаров","Корепанова","Меняйло","Расписенко",
    "Шорохов","Вакуров","Бунда","Вишняков","Егоров","Коваленко","Баринов",
    "Киблер","Левковицкая","Фарейтор","Чирков","Климова","Салимжанова",
    "Ивахно","Короткова","Меркель","Кузнецов Д","Харламова","Загузин",
    "Лошманов","Name"
);
```

Список используется в трёх местах парсера:

1. `INSTRUCTORS.contains(line)` — определяет, что строка целиком является именем инструктора (тогда → `pendingInstructor`).
2. `findInstructors(text)` — ищет имя как **подстроку** в `text` (например `"Меркель"` внутри `"Меркель (2ч)"`).
3. В lookahead `consumeFollowingTitle` — имя инструктора служит **границей**, на которой накопление названия останавливается.

### 6.1 Как добавить нового преподавателя сейчас

1. Добавить `User` через UI кабинета (`Управление пользователями`) с `canTeach=true`.
2. Открыть `schedule-import-parser-core/src/main/java/ru/parser/ScheduleCsvParser.java`.
3. Дописать ФИО в `Set.of(...)`.
4. Закоммитить, запушить.
5. На сервере: `git pull && docker compose -f docker-compose.prod.yml up -d --build schedule-service`.

Иначе строки вида `"Новенький"` парсер примет за обычный plain-text и упустит как имя.

### 6.2 Известные коллизии в списке

`findInstructors(text)` ищет подстрокой. Это создаёт неоднозначности:

- `"Иванов"` — префикс `"Иванов С"`. Если в тексте `"Иванов (3ч)"`, в результат попадут оба. Сейчас `findInstructors` возвращает `List`, а парсер берёт `[0]` — **первый по порядку итерации Set'а**, что не детерминировано (`Set.of(...)` гарантирует ровно один порядок, но он implementation-defined).
- Аналогично `"Кузнецов"` (в коде нет) vs `"Кузнецов Д"` — если когда-нибудь добавят первого, они начнут конфликтовать.

Это ещё один аргумент за переход на **динамический список** с точным `equals`-матчингом по слову, а не подстрокой.

### 6.3 План переезда на динамический список

Идея: на старте `schedule-service` (или перед каждым импортом) парсер получает список ФИО из `users` где `can_teach=true`. Тогда:

- `INSTRUCTORS` → не статика, а зависимость, прокидываемая в `ScheduleCsvParser.parse(...)`.
- Добавил пользователя → следующий импорт его подхватил.
- Перекатывать код больше не нужно.

Реализация — отдельный коммит, ~50 строк (см. §16.3 «Дальше» в [`PROJECT_DOCUMENTATION.md`](PROJECT_DOCUMENTATION.md)).

---

## 7. Полный пример

### Вход (CSV)

```csv
,пн,вт,ср,чт,пт,сб,вс,пн
,27.апр.,28.апр.,29.апр.,30.апр.,1.мая,2.мая,4.мая,5.мая
"гр. 87
А308","OE00

Intermediate Examination (пересдача)

Бращенко (3ч)
Костылев (3ч)
Климов (3ч)",,,,,,,
"гр. 174 (резерв НС АС)
Б202",,,,,"CH03
Меркель (2ч)
System of chemical reagents preparation (KBD-1)
Меркель (3ч)
Primary coolant treatment system (KBF)
СП
Меркель (1ч)
Liquid radioactive waste storage system (KPK)",,,
```

### Что сохранится

**Группа `гр. 87` (location `А308`), день `2026-04-27`, courseCode `OE00`:**

| # | type | title | duration | lecturers |
|---|---|---|---|---|
| 1 | ASSESSMENT | `Intermediate Examination (пересдача)` | 3 ч | `Бращенко`, `Костылев`, `Климов` |

**Группа `гр. 174 (резерв НС АС)` (location `Б202`), день `2026-04-30`, courseCode `CH03`:**

| # | type | title | duration | lecturer |
|---|---|---|---|---|
| 1 | LECTURE     | `System of chemical reagents preparation (KBD-1)` | 2 ч | Меркель |
| 2 | LECTURE     | `Primary coolant treatment system (KBF)` | 3 ч | Меркель |
| 3 | SELF_STUDY  | `Liquid radioactive waste storage system (KPK)` | 1 ч | Меркель |

### Пошаговая трассировка для гр.174

| i | line | hasDuration | действие |
|---|---|---|---|
| 0 | `CH03` | — | course code → `day.meta.courseCode = "CH03"` |
| 1 | `Меркель (2ч)` | да | text=`Меркель`, instr=Меркель, titleFromText=`""` → lookahead → консьюмим строки 2 → создаём LECTURE «System...», 2ч, Меркель |
| 2 | `System of chemical reagents preparation (KBD-1)` | — | помечена пустой в lookahead, скипаем |
| 3 | `Меркель (3ч)` | да | то же → lookahead → строка 4 → LECTURE «Primary coolant», 3ч, Меркель |
| 4 | `Primary coolant treatment system (KBF)` | — | помечена пустой |
| 5 | `СП` | — | `selfStudy = true` |
| 6 | `Меркель (1ч)` | да | то же → lookahead → строка 7 → SELF_STUDY «Liquid radioactive», 1ч, Меркель |
| 7 | `Liquid radioactive waste storage system (KPK)` | — | помечена пустой |

---

## 8. Ограничения и крайние случаи

| Случай | Поведение | Что делать |
|---|---|---|
| `Имя (8)` без `ч` | Не считается длительностью, попадает в pendingTitle | Использовать `(8ч)` или `(8 ч)` |
| Многострочный assessment-заголовок (`Intermediate\nExamination`) | Ни одна из строк не матчит ни ключ полностью, ни как подстрока — пропускается | Держать ассесмент-заголовок одной строкой |
| Нет course-code и нет унаследованного | День **не сохраняется** | Поставить курс хотя бы в одной соседней ячейке |
| Незнакомое ФИО на отдельной строке | Принимается за plain-text → попадает в pendingTitle следующего урока | Добавить ФИО в `INSTRUCTORS` (см. §6) |
| Опечатка в дате (`32.янв.`) | `DateParser.parse` бросает исключение, день **не сохраняется** | Поправить CSV |
| Пустой `(Nч)` в строке без текста (`(2ч)`) | text=`""`, instructorsInText=пусто → создастся урок с пустым title и без lecturer | Не должно встречаться в реальных данных |

---

## 9. Тесты

`schedule-import-parser-core/src/test/java/ru/parser/ScheduleCsvParserTest.java`:

| Тест | Что проверяет |
|---|---|
| `parse_ignoresNonEmptyCellsWithoutDateHeader` | Ячейки в колонках без даты в заголовке → не сохраняются, лог-warning |
| `parse_treatsEntryLevelTestAsAssessmentInsteadOfNamelessLecture` | `ELT00 / Entry Level Test / Name (8ч)` → ASSESSMENT, не безымянная лекция |
| `parse_handlesInstructorFirstFormat` | Format B (гр.174): `Меркель (Nч)` + название на следующей строке + `СП`-блок |
| `parse_handlesAssessmentWithSuffixAndMultipleInstructors` | гр.87: `Intermediate Examination (пересдача)` собирает 3 инструкторов из (3ч)-строк |
| `parse_handlesCourseCodeWithTrailingColon` | `CS01:` распознаётся как course code |

Запуск:

```bash
mvn -B -pl schedule-import-parser-core test -Dtest=ScheduleCsvParserTest
```

---

**Связанные документы:**

- 🏠 [README.md](../README.md)
- 📖 [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md) — полная документация проекта
- 🚢 [PRODUCTION_DEPLOY.md](PRODUCTION_DEPLOY.md)
