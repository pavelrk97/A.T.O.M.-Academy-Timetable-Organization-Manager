'use client'

import { useEffect, useMemo, useState } from 'react'
import {
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
import { lessonsApi } from '@/lib/api'
import type {
  GroupDto,
  LessonEditorDto,
  LessonHistoryEntry,
  LessonMutationPayload,
  LessonType,
  User,
} from '@/lib/types'

interface LessonAdminEditorProps {
  groups: GroupDto[]
  users: User[]
  onChanged: () => Promise<void>
}

const LESSON_TYPE_OPTIONS: LessonType[] = [
  'LECTURE',
  'SEMINAR',
  'LAB',
  'PRACTICE',
  'SELF_STUDY',
  'ASSESSMENT',
]

function lessonTypeLabel(type: LessonType) {
  switch (type) {
    case 'LECTURE':
      return 'Лекция'
    case 'SEMINAR':
      return 'Семинар'
    case 'LAB':
      return 'Лабораторная'
    case 'PRACTICE':
      return 'Практика'
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

export function LessonAdminEditor({
  groups,
  users,
  onChanged,
}: LessonAdminEditorProps) {
  const instructorOptions = useMemo(
    () =>
      [...users]
        .filter((user) => user.canTeach)
        .sort((left, right) =>
          (left.fullName || left.username).localeCompare(right.fullName || right.username, 'ru')
        ),
    [users]
  )

  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [selectedDayId, setSelectedDayId] = useState('')
  const [selectedLessonId, setSelectedLessonId] = useState<string | null>(null)
  const [pendingLessonId, setPendingLessonId] = useState<string | null>(null)
  const [form, setForm] = useState<LessonMutationPayload>(createEmptyForm())
  const [history, setHistory] = useState<LessonHistoryEntry[]>([])
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const selectedGroup = useMemo(
    () => groups.find((group) => group.id === selectedGroupId) || groups[0] || null,
    [groups, selectedGroupId]
  )

  const dayOptions = useMemo(
    () => sortDays(selectedGroup?.days || []),
    [selectedGroup]
  )

  const selectedDay = useMemo(
    () => dayOptions.find((day) => day.id === selectedDayId) || dayOptions[0] || null,
    [dayOptions, selectedDayId]
  )

  const dayLessons = useMemo(
    () => sortLessons(selectedDay?.lessons || []),
    [selectedDay]
  )

  useEffect(() => {
    if (!groups.length) {
      setSelectedGroupId('')
      setSelectedDayId('')
      setSelectedLessonId(null)
      setForm(createEmptyForm())
      return
    }

    if (!selectedGroup || selectedGroup.id !== selectedGroupId) {
      const nextGroup = groups[0]
      setSelectedGroupId(nextGroup.id)
      const nextDay = sortDays(nextGroup.days || [])[0]
      setSelectedDayId(nextDay?.id || '')
      setSelectedLessonId(null)
      setForm(createEmptyForm(nextGroup.id, nextDay?.id))
    }
  }, [groups, selectedGroup, selectedGroupId])

  useEffect(() => {
    if (!selectedGroup) {
      return
    }

    if (!selectedDay || selectedDay.id !== selectedDayId) {
      const nextDay = sortDays(selectedGroup.days || [])[0]
      setSelectedDayId(nextDay?.id || '')
      setSelectedLessonId(null)
      setForm((current) => ({
        ...createEmptyForm(selectedGroup.id, nextDay?.id),
        orderNumber: current.orderNumber || 1,
        durationHours: current.durationHours || 2,
      }))
    }
  }, [selectedDay, selectedDayId, selectedGroup])

  useEffect(() => {
    if (!pendingLessonId || !groups.length) {
      return
    }

    for (const group of groups) {
      for (const day of group.days || []) {
        const lesson = (day.lessons || []).find((item) => item.id === pendingLessonId)
        if (lesson) {
          setSelectedGroupId(group.id)
          setSelectedDayId(day.id)
          loadLessonIntoForm(lesson, group.id, day.id)
          setPendingLessonId(null)
          return
        }
      }
    }

    setPendingLessonId(null)
  }, [groups, pendingLessonId])

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

  function loadLessonIntoForm(lesson: LessonEditorDto, groupId: string, dayId: string) {
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
    setForm(
      createEmptyForm(selectedGroup?.id, selectedDay?.id || '')
    )
    setError('')
    setSuccess('')
  }

  function toggleInstructor(userId: string) {
    setForm((current) => {
      const exists = current.instructorIds.includes(userId)
      return {
        ...current,
        instructorIds: exists
          ? current.instructorIds.filter((id) => id !== userId)
          : [...current.instructorIds, userId],
      }
    })
  }

  async function handleSubmit() {
    if (!selectedGroup || !selectedDay) {
      setError('Сначала выбери группу и день.')
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

    const payload: LessonMutationPayload = {
      ...form,
      title: form.title.trim(),
      note: form.note?.trim() || null,
      groupId: selectedGroup.id,
      dayId: selectedDay.id,
      durationHours: Number(form.durationHours) || 1,
      orderNumber: Number(form.orderNumber) || 1,
      instructorNames: instructorOptions
        .filter((user) => form.instructorIds.includes(user.id))
        .map((user) => user.fullName || user.username),
      lecturers: [],
      lecturer: null,
    }

    try {
      const savedLesson = selectedLessonId
        ? await lessonsApi.update(selectedLessonId, payload)
        : await lessonsApi.create(payload)

      setPendingLessonId(savedLesson.id)
      await onChanged()
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
      setPendingLessonId(null)
      await onChanged()
      setForm(createEmptyForm(selectedGroup?.id, selectedDay?.id))
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

  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Редактор расписания</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Выбирай группу и день, загружай существующее занятие в форму или создавай новое
            прямо из кабинета.
          </p>
        </div>
        <Button variant="outline" onClick={startCreate}>
          <Plus className="mr-2 h-4 w-4" />
          Новое занятие
        </Button>
      </div>

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
                }}
                className="h-10 w-full rounded-xl border border-border bg-white px-3 text-sm"
              >
                {groups.map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.code} {group.location ? `· ${group.location}` : ''}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                День
              </span>
              <select
                value={selectedDay?.id || ''}
                onChange={(event) => {
                  setSelectedDayId(event.target.value)
                  setSelectedLessonId(null)
                  setForm((current) => ({
                    ...current,
                    dayId: event.target.value,
                  }))
                }}
                className="h-10 w-full rounded-xl border border-border bg-white px-3 text-sm"
              >
                {dayOptions.map((day) => (
                  <option key={day.id} value={day.id}>
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

          <div className="rounded-2xl border border-border bg-slate-50 p-4">
            <div className="mb-3 flex items-center justify-between gap-3">
              <div>
                <div className="text-sm font-semibold text-slate-950">Занятия выбранного дня</div>
                <div className="text-xs text-muted-foreground">
                  Нажми на строку, чтобы загрузить занятие в форму.
                </div>
              </div>
              <div className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-600">
                {(selectedDay?.lessons || []).length} шт.
              </div>
            </div>

            <div className="max-h-[340px] space-y-2 overflow-auto">
              {dayLessons.length === 0 ? (
                <div className="rounded-xl border border-dashed border-border bg-white px-4 py-6 text-sm text-muted-foreground">
                  На этот день занятий пока нет.
                </div>
              ) : (
                dayLessons.map((lesson) => (
                  <button
                    key={lesson.id}
                    type="button"
                    onClick={() =>
                      loadLessonIntoForm(lesson, selectedGroup?.id || '', selectedDay?.id || '')
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
                  Всё идёт через реальные `/api/lessons` и audit history.
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
                      checked={form.instructorIds.includes(user.id)}
                      onChange={() => toggleInstructor(user.id)}
                      className="mt-1"
                    />
                    <div>
                      <div className="text-sm font-medium text-slate-950">
                        {user.fullName || user.username}
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
              <Button onClick={handleSubmit} disabled={saving}>
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
