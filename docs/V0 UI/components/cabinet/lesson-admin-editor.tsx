'use client'

import { useEffect, useMemo, useState } from 'react'
import {
  CalendarPlus2,
  Clock3,
  History,
  PencilLine,
  Plus,
  Save,
  Trash2,
  UsersRound,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { groupsApi, lessonsApi } from '@/lib/api'
import type {
  DayMutationPayload,
  GroupDto,
  GroupMutationPayload,
  LessonEditorDto,
  LessonHistoryEntry,
  LessonMutationPayload,
  LessonType,
  User,
} from '@/lib/types'

interface LessonAdminEditorProps {
  groups: GroupDto[]
  users: User[]
  canManageGroups: boolean
  onChanged: () => Promise<void>
}

interface GroupCreateFormState {
  code: string
  location: string
  course: string
}

interface PendingSelection {
  groupId: string
  date: string
  lessonId?: string | null
}

const LESSON_TYPE_OPTIONS: LessonType[] = ['LECTURE', 'SELF_STUDY', 'ASSESSMENT']
const INTERNAL_IMPORTED_USERNAME_PREFIX = 'imported-'

function lessonTypeLabel(type: LessonType) {
  switch (type) {
    case 'LECTURE':
      return 'Лекция'
    case 'SELF_STUDY':
      return 'Самостоятельная работа'
    case 'ASSESSMENT':
      return 'Контроль'
    default:
      return type
  }
}

function formatHistoryDate(value?: string | null) {
  if (!value) {
    return '—'
  }

  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function sortDays(days: GroupDto['days']) {
  return [...(days || [])].sort((left, right) => left.date.localeCompare(right.date))
}

function sortLessons(lessons: LessonEditorDto[]) {
  return [...(lessons || [])].sort(
    (left, right) => (left.orderNumber || 0) - (right.orderNumber || 0)
  )
}

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function createEmptyForm(groupId?: string, dayId?: string): LessonMutationPayload {
  return {
    title: '',
    orderNumber: 1,
    durationHours: 2,
    note: '',
    type: 'LECTURE',
    dayId: dayId || '',
    groupId: groupId || '',
    instructorIds: [],
    lecturers: [],
    instructorNames: [],
    lecturer: null,
  }
}

function createEmptyGroupForm(): GroupCreateFormState {
  return {
    code: '',
    location: '',
    course: '',
  }
}

function normalizeInstructorKey(value?: string | null) {
  return (value || '').trim().toLowerCase()
}

function instructorLabel(user: User) {
  return user.displayName || user.fullName || user.username
}

function comparePreferredInstructor(left: User, right: User) {
  const leftImported = left.username.startsWith(INTERNAL_IMPORTED_USERNAME_PREFIX)
  const rightImported = right.username.startsWith(INTERNAL_IMPORTED_USERNAME_PREFIX)

  if (leftImported !== rightImported) {
    return leftImported ? 1 : -1
  }

  if (left.role !== right.role) {
    if (left.role === 'INSTRUCTOR') return -1
    if (right.role === 'INSTRUCTOR') return 1
  }

  if (left.active !== right.active) {
    return left.active ? -1 : 1
  }

  if (left.editorAccess !== right.editorAccess) {
    return left.editorAccess ? -1 : 1
  }

  return instructorLabel(left).localeCompare(instructorLabel(right), 'ru')
}

function buildGroupPayload(group: GroupDto): GroupMutationPayload {
  return {
    id: group.id,
    code: group.code,
    location: group.location ?? null,
    course: group.course ?? null,
    days: (group.days || []).map<DayMutationPayload>((day) => ({
      id: day.id ?? null,
      date: day.date,
      meta: day.meta || {},
      lessons: day.lessons || [],
    })),
  }
}

function normalizeCourseValue(value: string) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

export function LessonAdminEditor({
  groups,
  users,
  canManageGroups,
  onChanged,
}: LessonAdminEditorProps) {
  const instructorOptions = useMemo(
    () => {
      const uniqueUsers = new Map<string, User>()

      for (const user of users.filter((item) => item.canTeach)) {
        const key = normalizeInstructorKey(user.fullName || user.username)
        const current = uniqueUsers.get(key)
        if (!current || comparePreferredInstructor(user, current) < 0) {
          uniqueUsers.set(key, user)
        }
      }

      return [...uniqueUsers.values()].sort((left, right) =>
        instructorLabel(left).localeCompare(instructorLabel(right), 'ru')
      )
    },
    [users]
  )

  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [selectedDate, setSelectedDate] = useState(todayIso())
  const [selectedLessonId, setSelectedLessonId] = useState<string | null>(null)
  const [pendingSelection, setPendingSelection] = useState<PendingSelection | null>(null)
  const [form, setForm] = useState<LessonMutationPayload>(createEmptyForm())
  const [history, setHistory] = useState<LessonHistoryEntry[]>([])
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [creatingDay, setCreatingDay] = useState(false)
  const [creatingGroup, setCreatingGroup] = useState(false)
  const [groupForm, setGroupForm] = useState<GroupCreateFormState>(createEmptyGroupForm())
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const selectedGroup = useMemo(
    () => groups.find((group) => group.id === selectedGroupId) || groups[0] || null,
    [groups, selectedGroupId]
  )

  const dayOptions = useMemo(() => sortDays(selectedGroup?.days || []), [selectedGroup])

  const selectedDay = useMemo(
    () => dayOptions.find((day) => day.date === selectedDate) || null,
    [dayOptions, selectedDate]
  )

  const dayLessons = useMemo(() => sortLessons(selectedDay?.lessons || []), [selectedDay])

  useEffect(() => {
    if (!groups.length) {
      setSelectedGroupId('')
      setSelectedDate(todayIso())
      setSelectedLessonId(null)
      setForm(createEmptyForm())
      return
    }

    const nextGroup = groups.find((group) => group.id === selectedGroupId) || groups[0]
    if (nextGroup.id !== selectedGroupId) {
      setSelectedGroupId(nextGroup.id)
      return
    }

    if (!selectedDate) {
      setSelectedDate(sortDays(nextGroup.days || [])[0]?.date || todayIso())
    }
  }, [groups, selectedDate, selectedGroupId])

  useEffect(() => {
    if (!selectedGroup) {
      return
    }

    if (!selectedDate) {
      setSelectedDate(sortDays(selectedGroup.days || [])[0]?.date || todayIso())
    }
  }, [selectedDate, selectedGroup])

  useEffect(() => {
    if (!pendingSelection) {
      return
    }

    const group = groups.find((item) => item.id === pendingSelection.groupId)
    if (!group) {
      return
    }

    const day = (group.days || []).find((item) => item.date === pendingSelection.date) || null

    setSelectedGroupId(group.id)
    setSelectedDate(pendingSelection.date)

    if (pendingSelection.lessonId && day) {
      const lesson = (day.lessons || []).find((item) => item.id === pendingSelection.lessonId)
      if (lesson) {
        loadLessonIntoForm(lesson, group.id, day.id || '', day.date)
      }
    } else {
      setSelectedLessonId(null)
      setHistory([])
      setForm(createEmptyForm(group.id, day?.id || ''))
    }

    setPendingSelection(null)
  }, [groups, pendingSelection])

  useEffect(() => {
    if (!selectedLessonId) {
      setHistory([])
      return
    }

    let cancelled = false

    async function loadHistory() {
      setLoadingHistory(true)
      try {
        const nextHistory = await lessonsApi.getHistory(selectedLessonId)
        if (!cancelled) {
          setHistory(nextHistory)
        }
      } catch {
        if (!cancelled) {
          setHistory([])
        }
      } finally {
        if (!cancelled) {
          setLoadingHistory(false)
        }
      }
    }

    loadHistory()

    return () => {
      cancelled = true
    }
  }, [selectedLessonId])

  function loadLessonIntoForm(
    lesson: LessonEditorDto,
    groupId: string,
    dayId: string,
    dayDate: string
  ) {
    setSelectedGroupId(groupId)
    setSelectedDate(dayDate)
    setSelectedLessonId(lesson.id)
    setForm({
      version: lesson.version,
      orderNumber: lesson.orderNumber || 1,
      title: lesson.title || '',
      lecturer: lesson.lecturer || null,
      lecturers: lesson.lecturers || [],
      durationHours: lesson.durationHours || 1,
      note: lesson.note || '',
      type: (lesson.type as LessonType) || 'LECTURE',
      dayId,
      groupId,
      instructorIds: lesson.instructorIds || [],
      instructorNames: lesson.instructorNames || [],
    })
    setError('')
    setSuccess('')
  }

  function startCreate() {
    setSelectedLessonId(null)
    setHistory([])
    setForm(createEmptyForm(selectedGroup?.id, selectedDay?.id || ''))
    setError('')
    setSuccess('')
  }

  function toggleInstructor(userId: string) {
    const selectedUser = instructorOptions.find((user) => user.id === userId)
    if (!selectedUser) {
      return
    }

    const targetKey = normalizeInstructorKey(selectedUser.fullName || selectedUser.username)

    setForm((current) => {
      const pairs = current.instructorIds.map((id, index) => ({
        id,
        name:
          current.instructorNames?.[index] ||
          users.find((user) => user.id === id)?.fullName ||
          users.find((user) => user.id === id)?.username ||
          '',
      }))
      const exists = pairs.some((pair) => normalizeInstructorKey(pair.name) === targetKey)

      const nextPairs = exists
        ? pairs.filter((pair) => normalizeInstructorKey(pair.name) !== targetKey)
        : [
            ...pairs.filter((pair) => normalizeInstructorKey(pair.name) !== targetKey),
            {
              id: selectedUser.id,
              name: selectedUser.fullName || selectedUser.username,
            },
          ]

      return {
        ...current,
        instructorIds: nextPairs.map((pair) => pair.id),
        instructorNames: nextPairs.map((pair) => pair.name),
      }
    })
  }

  async function ensureDayExists(group: GroupDto, date: string) {
    const existingDay = (group.days || []).find((day) => day.date === date)
    if (existingDay?.id) {
      return existingDay.id
    }

    const payload = buildGroupPayload(group)
    payload.days = [
      ...payload.days,
      {
        date,
        meta: {},
        lessons: [],
      },
    ]

    const updatedGroup = await groupsApi.update(group.id, payload)
    const createdDay = (updatedGroup.days || []).find((day) => day.date === date)

    if (!createdDay?.id) {
      throw new Error('Не удалось создать день для выбранной даты.')
    }

    return createdDay.id
  }

  async function handleCreateGroup() {
    if (!groupForm.code.trim()) {
      setError('Код группы обязателен.')
      return
    }

    setCreatingGroup(true)
    setError('')
    setSuccess('')

    try {
      const createdGroup = await groupsApi.create({
        code: groupForm.code.trim(),
        location: groupForm.location.trim() || null,
        course: normalizeCourseValue(groupForm.course),
        days: [],
      })

      await onChanged()
      setGroupForm(createEmptyGroupForm())
      setPendingSelection({
        groupId: createdGroup.id,
        date: todayIso(),
      })
      setSuccess(`Группа ${createdGroup.code} создана.`)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось создать группу.'
      )
    } finally {
      setCreatingGroup(false)
    }
  }

  async function handleCreateEmptyDay() {
    if (!selectedGroup) {
      setError('Сначала выбери группу.')
      return
    }

    if (!selectedDate) {
      setError('Укажи дату дня.')
      return
    }

    setCreatingDay(true)
    setError('')
    setSuccess('')

    try {
      await ensureDayExists(selectedGroup, selectedDate)
      await onChanged()
      setPendingSelection({
        groupId: selectedGroup.id,
        date: selectedDate,
      })
      setSuccess('Пустой день создан.')
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось создать день.'
      )
    } finally {
      setCreatingDay(false)
    }
  }

  async function handleSubmit() {
    if (!selectedGroup) {
      setError('Сначала выбери группу.')
      return
    }

    if (!selectedDate) {
      setError('Укажи дату занятия.')
      return
    }

    if (!form.title.trim()) {
      setError('Название занятия не может быть пустым.')
      return
    }

    if (!form.instructorIds.length) {
      setError('Нужно выбрать хотя бы одного инструктора.')
      return
    }

    setSaving(true)
    setError('')
    setSuccess('')

    try {
      const dayId = await ensureDayExists(selectedGroup, selectedDate)
      const payload: LessonMutationPayload = {
        ...form,
        title: form.title.trim(),
        note: form.note?.trim() || null,
        groupId: selectedGroup.id,
        dayId,
        durationHours: Number(form.durationHours) || 1,
        orderNumber: Number(form.orderNumber) || 1,
        instructorNames: form.instructorNames || [],
        lecturers: [],
        lecturer: null,
      }

      const savedLesson = selectedLessonId
        ? await lessonsApi.update(selectedLessonId, payload)
        : await lessonsApi.create(payload)

      await onChanged()
      setPendingSelection({
        groupId: selectedGroup.id,
        date: selectedDate,
        lessonId: savedLesson.id,
      })
      setSuccess(
        selectedLessonId
          ? 'Изменения по занятию сохранены.'
          : 'Новое занятие добавлено в расписание.'
      )
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось сохранить занятие.'
      )
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (!selectedLessonId || form.version === undefined || form.version === null) {
      return
    }

    setDeleting(true)
    setError('')
    setSuccess('')

    try {
      await lessonsApi.delete(selectedLessonId, form.version)
      setSelectedLessonId(null)
      setHistory([])
      await onChanged()
      setPendingSelection(
        selectedGroup
          ? {
              groupId: selectedGroup.id,
              date: selectedDate,
            }
          : null
      )
      setForm(createEmptyForm(selectedGroup?.id, selectedDay?.id || ''))
      setSuccess('Занятие удалено.')
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось удалить занятие.'
      )
    } finally {
      setDeleting(false)
    }
  }

  const selectedDayExists = Boolean(selectedDay?.id)

  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Редактор расписания</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Выбирай группу и любую дату, создавай при необходимости пустой день и добавляй занятия с назначением преподавателей.
          </p>
        </div>
        <Button variant="outline" onClick={startCreate}>
          <Plus className="mr-2 h-4 w-4" />
          Новое занятие
        </Button>
      </div>

      {canManageGroups ? (
        <div className="mt-5 rounded-2xl border border-border bg-slate-50 p-4">
          <div className="mb-3 text-sm font-semibold text-slate-950">Новая группа</div>
          <div className="grid gap-4 md:grid-cols-[1.3fr_1fr_0.7fr_auto]">
            <Input
              value={groupForm.code}
              onChange={(event) =>
                setGroupForm((current) => ({ ...current, code: event.target.value }))
              }
              placeholder="Код группы"
            />
            <Input
              value={groupForm.location}
              onChange={(event) =>
                setGroupForm((current) => ({ ...current, location: event.target.value }))
              }
              placeholder="Локация"
            />
            <Input
              type="number"
              min={1}
              value={groupForm.course}
              onChange={(event) =>
                setGroupForm((current) => ({ ...current, course: event.target.value }))
              }
              placeholder="Курс"
            />
            <Button onClick={handleCreateGroup} disabled={creatingGroup}>
              <Plus className="mr-2 h-4 w-4" />
              {creatingGroup ? 'Создаю...' : 'Создать группу'}
            </Button>
          </div>
        </div>
      ) : null}

      <div className="mt-5 grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
        <div className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Группа
              </span>
              <select
                value={selectedGroup?.id || ''}
                onChange={(event) => {
                  setSelectedGroupId(event.target.value)
                  setSelectedLessonId(null)
                  setHistory([])
                  setError('')
                  setSuccess('')
                }}
                className="h-10 w-full rounded-xl border border-border bg-white px-3 text-sm"
              >
                {groups.map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.code}
                    {group.location ? ` · ${group.location}` : ''}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                День из существующих
              </span>
              <select
                value={selectedDay?.date || ''}
                onChange={(event) => {
                  setSelectedDate(event.target.value)
                  setSelectedLessonId(null)
                  setHistory([])
                  setForm((current) => ({
                    ...current,
                    dayId:
                      dayOptions.find((day) => day.date === event.target.value)?.id || '',
                  }))
                }}
                className="h-10 w-full rounded-xl border border-border bg-white px-3 text-sm"
              >
                <option value="">Не выбран</option>
                {dayOptions.map((day) => (
                  <option key={day.id || day.date} value={day.date}>
                    {new Date(day.date).toLocaleDateString('ru-RU', {
                      day: '2-digit',
                      month: 'long',
                      year: 'numeric',
                    })}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="grid gap-4 md:grid-cols-[1fr_auto]">
            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Любая дата
              </span>
              <Input
                type="date"
                value={selectedDate}
                onChange={(event) => {
                  setSelectedDate(event.target.value)
                  setSelectedLessonId(null)
                  setHistory([])
                  setForm((current) => ({ ...current, dayId: '' }))
                }}
              />
            </label>

            {canManageGroups ? (
              <div className="space-y-2">
                <span className="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Пустой день
                </span>
                <Button
                  variant="outline"
                  onClick={handleCreateEmptyDay}
                  disabled={!selectedGroup || !selectedDate || creatingDay || selectedDayExists}
                >
                  <CalendarPlus2 className="mr-2 h-4 w-4" />
                  {creatingDay ? 'Создаю...' : 'Создать день'}
                </Button>
              </div>
            ) : null}
          </div>

          <div className="rounded-xl border border-border bg-slate-50 px-4 py-3 text-sm text-slate-700">
            {selectedDayExists
              ? `Для ${selectedDate} уже есть день расписания. Можно создавать или редактировать занятия.`
              : canManageGroups
                ? `Для ${selectedDate} дня ещё нет. Администратор может создать пустой день отдельно, а сохранение занятия создаст его автоматически.`
                : `Для ${selectedDate} дня ещё нет. При сохранении занятия день будет создан автоматически в выбранной группе.`}
          </div>

          <div className="rounded-2xl border border-border bg-slate-50 p-4">
            <div className="mb-3 flex items-center justify-between gap-3">
              <div>
                <div className="text-sm font-semibold text-slate-950">Занятия выбранной даты</div>
                <div className="text-xs text-muted-foreground">
                  Нажми на строку, чтобы загрузить занятие в форму.
                </div>
              </div>
              <div className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-600">
                {dayLessons.length} шт.
              </div>
            </div>

            <div className="max-h-[340px] space-y-2 overflow-auto">
              {dayLessons.length === 0 ? (
                <div className="rounded-xl border border-dashed border-border bg-white px-4 py-6 text-sm text-muted-foreground">
                  На эту дату занятий пока нет.
                </div>
              ) : (
                dayLessons.map((lesson) => (
                  <button
                    key={lesson.id}
                    type="button"
                    onClick={() =>
                      loadLessonIntoForm(
                        lesson,
                        selectedGroup?.id || '',
                        selectedDay?.id || '',
                        selectedDate
                      )
                    }
                    className={`w-full rounded-xl border px-3 py-3 text-left transition-colors ${
                      selectedLessonId === lesson.id
                        ? 'border-primary bg-primary/5'
                        : 'border-border bg-white hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="text-xs font-semibold text-primary">
                          Пара №{lesson.orderNumber || '—'}
                        </div>
                        <div className="truncate text-sm font-medium text-slate-950">
                          {lesson.title}
                        </div>
                        <div className="mt-1 text-xs text-muted-foreground">
                          {(lesson.instructorNames || []).join(', ') || 'Без инструкторов'}
                        </div>
                      </div>
                      <div className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-700">
                        {lesson.durationHours} ч
                      </div>
                    </div>
                  </button>
                ))
              )}
            </div>
          </div>
        </div>

        <div className="space-y-4">
          <div className="rounded-2xl border border-border bg-slate-50 p-4">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <div className="text-sm font-semibold text-slate-950">
                  {selectedLessonId ? 'Редактирование занятия' : 'Создание занятия'}
                </div>
                <div className="text-xs text-muted-foreground">
                  Создание и обновление идут через реальные `/api/lessons`, история берётся из audit trail.
                </div>
              </div>
              {selectedLessonId ? (
                <div className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-600">
                  version {form.version ?? 0}
                </div>
              ) : null}
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <label className="space-y-2">
                <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Порядок
                </span>
                <Input
                  type="number"
                  min={1}
                  value={form.orderNumber ?? ''}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      orderNumber: Number(event.target.value) || 1,
                    }))
                  }
                />
              </label>

              <label className="space-y-2">
                <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Длительность, часы
                </span>
                <Input
                  type="number"
                  min={1}
                  value={form.durationHours}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      durationHours: Number(event.target.value) || 1,
                    }))
                  }
                />
              </label>
            </div>

            <label className="mt-4 block space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Название занятия
              </span>
              <Input
                value={form.title}
                onChange={(event) =>
                  setForm((current) => ({ ...current, title: event.target.value }))
                }
                placeholder="Например, Instrumentation Hardware"
              />
            </label>

            <label className="mt-4 block space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Тип занятия
              </span>
              <select
                value={form.type || 'LECTURE'}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    type: event.target.value as LessonType,
                  }))
                }
                className="h-10 w-full rounded-xl border border-border bg-white px-3 text-sm"
              >
                {LESSON_TYPE_OPTIONS.map((type) => (
                  <option key={type} value={type}>
                    {lessonTypeLabel(type)}
                  </option>
                ))}
              </select>
            </label>

            <label className="mt-4 block space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Примечание
              </span>
              <Textarea
                value={form.note || ''}
                onChange={(event) =>
                  setForm((current) => ({ ...current, note: event.target.value }))
                }
                placeholder="Опционально: аудитория, комментарий, перенос и т.п."
                rows={4}
              />
            </label>

            <div className="mt-4 space-y-3">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <UsersRound className="h-4 w-4" />
                Инструкторы
              </div>
              <div className="grid max-h-52 gap-2 overflow-auto rounded-xl border border-border bg-white p-3">
                {instructorOptions.map((user) => (
                  <label
                    key={user.id}
                    className="flex items-start gap-3 rounded-lg px-2 py-2 hover:bg-slate-50"
                  >
                    <input
                      type="checkbox"
                      checked={
                        form.instructorIds.includes(user.id) ||
                        (user.fullName
                          ? (form.instructorNames || []).some(
                              (name) => normalizeInstructorKey(name) === normalizeInstructorKey(user.fullName)
                            )
                          : false)
                      }
                      onChange={() => toggleInstructor(user.id)}
                      className="mt-1"
                    />
                    <div>
                      <div className="text-sm font-medium text-slate-950">
                        {instructorLabel(user)}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {user.position || user.role}
                      </div>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {error ? (
              <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-3 text-sm text-red-700">
                {error}
              </div>
            ) : null}

            {success ? (
              <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-3 text-sm text-emerald-700">
                {success}
              </div>
            ) : null}

            <div className="mt-5 flex flex-wrap gap-2">
              <Button onClick={handleSubmit} disabled={saving || !selectedGroup}>
                <Save className="mr-2 h-4 w-4" />
                {saving
                  ? 'Сохраняю...'
                  : selectedLessonId
                    ? 'Сохранить изменения'
                    : 'Создать занятие'}
              </Button>
              <Button variant="outline" onClick={startCreate}>
                <Plus className="mr-2 h-4 w-4" />
                Очистить форму
              </Button>
              {selectedLessonId ? (
                <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
                  <Trash2 className="mr-2 h-4 w-4" />
                  {deleting ? 'Удаляю...' : 'Удалить'}
                </Button>
              ) : null}
            </div>
          </div>

          <div className="rounded-2xl border border-border bg-slate-50 p-4">
            <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-950">
              <History className="h-4 w-4 text-primary" />
              История изменений
            </div>
            {loadingHistory ? (
              <div className="text-sm text-muted-foreground">Загружаю историю...</div>
            ) : !selectedLessonId ? (
              <div className="rounded-xl border border-dashed border-border bg-white px-4 py-6 text-sm text-muted-foreground">
                Выбери занятие, и здесь появятся его изменения.
              </div>
            ) : history.length === 0 ? (
              <div className="rounded-xl border border-dashed border-border bg-white px-4 py-6 text-sm text-muted-foreground">
                История пока пустая или backend ещё не вернул записи.
              </div>
            ) : (
              <div className="space-y-2">
                {history.slice(0, 12).map((entry) => (
                  <div
                    key={entry.id}
                    className="rounded-xl border border-border bg-white px-3 py-3"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="inline-flex items-center gap-2 text-sm font-medium text-slate-950">
                        <PencilLine className="h-4 w-4 text-primary" />
                        {entry.action}
                      </div>
                      <div className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <Clock3 className="h-3.5 w-3.5" />
                        {formatHistoryDate(entry.changedAt)}
                      </div>
                    </div>
                    <div className="mt-2 text-sm text-slate-700">
                      {entry.changedBy || 'system'}
                    </div>
                    {entry.comment ? (
                      <div className="mt-1 text-xs text-muted-foreground">{entry.comment}</div>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  )
}
