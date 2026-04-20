'use client'

import { useEffect, useMemo, useState } from 'react'
import {
  CalendarPlus2,
  ClipboardPaste,
  Copy,
  History,
  Plus,
  Save,
  Search,
  Trash2,
  UsersRound,
  ZoomIn,
  ZoomOut,
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
import { cn } from '@/lib/utils'

interface LessonAdminEditorProps {
  groups: GroupDto[]
  users: User[]
  canManageGroups: boolean
  onChanged: () => Promise<void>
  range: {
    from: string
    to: string
  }
}

interface GroupCreateFormState {
  code: string
  location: string
  course: string
}

interface CellCoord {
  groupId: string
  date: string
}

interface ClipboardLessonDraft {
  orderNumber?: number | null
  title: string
  durationHours: number
  note?: string | null
  type?: LessonType | null
  instructorIds: string[]
}

interface ClipboardCellSnapshot {
  rowOffset: number
  columnOffset: number
  hasDay: boolean
  lessons: ClipboardLessonDraft[]
}

interface ClipboardSnapshot {
  rows: number
  columns: number
  cells: ClipboardCellSnapshot[]
}

const LESSON_TYPE_OPTIONS: LessonType[] = ['LECTURE', 'SELF_STUDY', 'ASSESSMENT']
const INTERNAL_IMPORTED_USERNAME_PREFIX = 'imported-'
const MAX_DATES = 100
const SLOT_COUNT = 8
const MIN_ZOOM = 80
const MAX_ZOOM = 135

function lessonTypeLabel(type: LessonType | string | null | undefined) {
  switch (type) {
    case 'LECTURE':
      return 'Лекция'
    case 'SELF_STUDY':
      return 'Самостоятельная'
    case 'ASSESSMENT':
      return 'Контроль'
    default:
      return 'Занятие'
  }
}

function shortLessonTypeLabel(type: LessonType | string | null | undefined) {
  switch (type) {
    case 'LECTURE':
      return 'Лек'
    case 'SELF_STUDY':
      return 'Сам'
    case 'ASSESSMENT':
      return 'Конт'
    default:
      return 'Зан'
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

function sortLessons(lessons: LessonEditorDto[]) {
  return [...(lessons || [])].sort(
    (left, right) => (left.orderNumber || 0) - (right.orderNumber || 0)
  )
}

function normalizeKey(value?: string | null) {
  return (value || '').trim().toLowerCase()
}

function normalizeCourseValue(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : null
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

function enumerateDates(from: string, to: string) {
  if (!from || !to) {
    return []
  }

  const dates: string[] = []
  const fromDate = new Date(from)
  const toDate = new Date(to)

  if (Number.isNaN(fromDate.getTime()) || Number.isNaN(toDate.getTime()) || fromDate > toDate) {
    return []
  }

  const cursor = new Date(fromDate)
  while (cursor <= toDate && dates.length < MAX_DATES) {
    dates.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }

  return dates
}

function daysInRange(from: string, to: string) {
  const fromDate = new Date(from)
  const toDate = new Date(to)
  if (Number.isNaN(fromDate.getTime()) || Number.isNaN(toDate.getTime()) || fromDate > toDate) {
    return 0
  }
  return Math.floor((toDate.getTime() - fromDate.getTime()) / (1000 * 60 * 60 * 24)) + 1
}

function createEmptyLessonForm(groupId?: string, dayId?: string, orderNumber = 1): LessonMutationPayload {
  return {
    title: '',
    orderNumber,
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

function clampZoom(value: number) {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value))
}

function cellKey(groupId: string, date: string) {
  return `${groupId}::${date}`
}

function parseCellKey(value: string): CellCoord {
  const [groupId, date] = value.split('::')
  return { groupId, date }
}

function formatDateCaption(date: string) {
  return new Date(date).toLocaleDateString('ru-RU', {
    weekday: 'short',
    day: '2-digit',
    month: 'short',
  })
}

function cellStatusLabel(hasDay: boolean, lessonsCount: number) {
  if (!hasDay) {
    return 'нет дня'
  }
  if (lessonsCount === 0) {
    return '0/8'
  }
  return `${lessonsCount}/8`
}

export function LessonAdminEditor({
  groups,
  users,
  canManageGroups,
  onChanged,
  range,
}: LessonAdminEditorProps) {
  const instructorOptions = useMemo(() => {
    const uniqueUsers = new Map<string, User>()

    for (const user of users.filter((item) => item.canTeach)) {
      const key = normalizeKey(user.fullName || user.username)
      const current = uniqueUsers.get(key)
      if (!current || comparePreferredInstructor(user, current) < 0) {
        uniqueUsers.set(key, user)
      }
    }

    return [...uniqueUsers.values()].sort((left, right) =>
      instructorLabel(left).localeCompare(instructorLabel(right), 'ru')
    )
  }, [users])

  const instructorIdByName = useMemo(() => {
    const mapping = new Map<string, string>()
    for (const user of instructorOptions) {
      mapping.set(normalizeKey(user.fullName), user.id)
      mapping.set(normalizeKey(user.displayName), user.id)
      mapping.set(normalizeKey(user.username), user.id)
    }
    return mapping
  }, [instructorOptions])

  const [groupSearch, setGroupSearch] = useState('')
  const [instructorSearch, setInstructorSearch] = useState('')
  const [zoom, setZoom] = useState(100)
  const [activeCell, setActiveCell] = useState<CellCoord | null>(null)
  const [anchorCell, setAnchorCell] = useState<CellCoord | null>(null)
  const [selectedCellKeys, setSelectedCellKeys] = useState<string[]>([])
  const [clipboard, setClipboard] = useState<ClipboardSnapshot | null>(null)
  const [selectedLessonId, setSelectedLessonId] = useState<string | null>(null)
  const [selectedSlotOrder, setSelectedSlotOrder] = useState(1)
  const [form, setForm] = useState<LessonMutationPayload>(createEmptyLessonForm())
  const [history, setHistory] = useState<LessonHistoryEntry[]>([])
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [creatingDay, setCreatingDay] = useState(false)
  const [creatingGroup, setCreatingGroup] = useState(false)
  const [pasting, setPasting] = useState(false)
  const [groupForm, setGroupForm] = useState<GroupCreateFormState>(createEmptyGroupForm())
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const totalRangeDays = daysInRange(range.from, range.to)
  const visibleDates = useMemo(() => enumerateDates(range.from, range.to), [range.from, range.to])
  const rangeOverflow = totalRangeDays > MAX_DATES

  const filteredGroups = useMemo(
    () =>
      groups
        .slice()
        .sort((left, right) => left.code.localeCompare(right.code, 'ru'))
        .filter((group) => {
          const query = groupSearch.trim().toLowerCase()
          if (!query) return true
          return (
            group.code.toLowerCase().includes(query) ||
            (group.location || '').toLowerCase().includes(query) ||
            (group.course || '').toLowerCase().includes(query)
          )
        }),
    [groupSearch, groups]
  )

  const filteredInstructorOptions = useMemo(() => {
    const query = normalizeKey(instructorSearch)
    if (!query) {
      return instructorOptions
    }

    return instructorOptions.filter((user) =>
      [user.fullName, user.displayName, user.username, instructorLabel(user)].some((value) =>
        normalizeKey(value).includes(query)
      )
    )
  }, [instructorOptions, instructorSearch])

  const rowIndexByGroupId = useMemo(
    () => new Map(filteredGroups.map((group, index) => [group.id, index])),
    [filteredGroups]
  )
  const columnIndexByDate = useMemo(
    () => new Map(visibleDates.map((date, index) => [date, index])),
    [visibleDates]
  )

  const activeGroup = activeCell
    ? filteredGroups.find((group) => group.id === activeCell.groupId) || null
    : filteredGroups[0] || null
  const activeDate = activeCell?.date || visibleDates[0] || range.from
  const activeDay = activeGroup?.days.find((day) => day.date === activeDate) || null
  const activeLessons = useMemo(() => sortLessons(activeDay?.lessons || []), [activeDay])
  const activeSelectionLabel = activeGroup ? `${activeGroup.code} • ${activeDate}` : 'Ячейка не выбрана'

  const selectedBounds = useMemo(() => {
    const keys = selectedCellKeys.length
      ? selectedCellKeys
      : activeCell
        ? [cellKey(activeCell.groupId, activeCell.date)]
        : []

    if (!keys.length) {
      return null
    }

    const rows = keys
      .map((key) => rowIndexByGroupId.get(parseCellKey(key).groupId))
      .filter((value): value is number => value !== undefined)
    const columns = keys
      .map((key) => columnIndexByDate.get(parseCellKey(key).date))
      .filter((value): value is number => value !== undefined)

    if (!rows.length || !columns.length) {
      return null
    }

    return {
      minRow: Math.min(...rows),
      maxRow: Math.max(...rows),
      minColumn: Math.min(...columns),
      maxColumn: Math.max(...columns),
    }
  }, [activeCell, columnIndexByDate, rowIndexByGroupId, selectedCellKeys])

  const slotEntries = useMemo(() => {
    const byOrder = new Map<number, LessonEditorDto>()
    for (const lesson of activeLessons) {
      const order = lesson.orderNumber || 1
      if (!byOrder.has(order)) {
        byOrder.set(order, lesson)
      }
    }

    return Array.from({ length: SLOT_COUNT }, (_, index) => {
      const order = index + 1
      return {
        order,
        lesson: byOrder.get(order) || null,
      }
    })
  }, [activeLessons])

  useEffect(() => {
    if (!filteredGroups.length || !visibleDates.length) {
      setActiveCell(null)
      setAnchorCell(null)
      setSelectedCellKeys([])
      return
    }

    if (!activeCell) {
      const nextCell = { groupId: filteredGroups[0].id, date: visibleDates[0] }
      setActiveCell(nextCell)
      setAnchorCell(nextCell)
      setSelectedCellKeys([cellKey(nextCell.groupId, nextCell.date)])
      setSelectedSlotOrder(1)
      return
    }

    const activeGroupVisible = filteredGroups.some((group) => group.id === activeCell.groupId)
    const activeDateVisible = visibleDates.includes(activeCell.date)
    if (!activeGroupVisible || !activeDateVisible) {
      const nextCell = { groupId: filteredGroups[0].id, date: visibleDates[0] }
      setActiveCell(nextCell)
      setAnchorCell(nextCell)
      setSelectedCellKeys([cellKey(nextCell.groupId, nextCell.date)])
      setSelectedSlotOrder(1)
      setSelectedLessonId(null)
    }
  }, [activeCell, filteredGroups, visibleDates])

  useEffect(() => {
    if (!activeGroup) {
      setForm(createEmptyLessonForm())
      return
    }

    const lessonStillSelected = selectedLessonId
      ? activeLessons.some((lesson) => lesson.id === selectedLessonId)
      : false

    if (!lessonStillSelected) {
      setSelectedLessonId(null)
      setHistory([])
      setForm(createEmptyLessonForm(activeGroup.id, activeDay?.id || '', selectedSlotOrder))
    }
  }, [activeDay?.id, activeGroup, activeLessons, selectedLessonId, selectedSlotOrder])

  useEffect(() => {
    if (!selectedLessonId) {
      setHistory([])
      return
    }

    const lessonId = selectedLessonId
    let cancelled = false

    async function loadHistory() {
      setLoadingHistory(true)
      try {
        const nextHistory = await lessonsApi.getHistory(lessonId)
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

    void loadHistory()

    return () => {
      cancelled = true
    }
  }, [selectedLessonId])

  useEffect(() => {
    function handleKeyboard(event: KeyboardEvent) {
      if (!(event.ctrlKey || event.metaKey)) {
        return
      }

      const target = event.target as HTMLElement | null
      const isEditable =
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement ||
        target instanceof HTMLSelectElement ||
        Boolean(target?.isContentEditable)
      if (isEditable) {
        return
      }

      const key = event.key.toLowerCase()
      const isCopyShortcut = event.code === 'KeyC' || key === 'c' || key === 'с'
      const isPasteShortcut = event.code === 'KeyV' || key === 'v' || key === 'м'

      if (isCopyShortcut) {
        if (!activeCell) {
          return
        }
        event.preventDefault()
        handleCopySelection()
      }
      if (isPasteShortcut) {
        if (!clipboard || !activeCell) {
          return
        }
        event.preventDefault()
        void handlePasteSelection()
      }
    }

    document.addEventListener('keydown', handleKeyboard, true)
    return () => document.removeEventListener('keydown', handleKeyboard, true)
  }, [activeCell, clipboard, filteredGroups, selectedBounds, visibleDates])

  function findDay(group: GroupDto, date: string) {
    return (group.days || []).find((day) => day.date === date) || null
  }

  function resolveInstructorIds(lesson: LessonEditorDto) {
    const explicit = (lesson.instructorIds || []).filter(Boolean)
    if (explicit.length) {
      return explicit
    }

    return (lesson.instructorNames || [])
      .map((name) => instructorIdByName.get(normalizeKey(name)) || null)
      .filter((value): value is string => Boolean(value))
  }

  function buildRectKeys(fromCell: CellCoord, toCell: CellCoord) {
    const fromRow = rowIndexByGroupId.get(fromCell.groupId)
    const toRow = rowIndexByGroupId.get(toCell.groupId)
    const fromColumn = columnIndexByDate.get(fromCell.date)
    const toColumn = columnIndexByDate.get(toCell.date)

    if (
      fromRow === undefined ||
      toRow === undefined ||
      fromColumn === undefined ||
      toColumn === undefined
    ) {
      return []
    }

    const rowStart = Math.min(fromRow, toRow)
    const rowEnd = Math.max(fromRow, toRow)
    const columnStart = Math.min(fromColumn, toColumn)
    const columnEnd = Math.max(fromColumn, toColumn)
    const keys: string[] = []

    for (let row = rowStart; row <= rowEnd; row += 1) {
      for (let column = columnStart; column <= columnEnd; column += 1) {
        keys.push(cellKey(filteredGroups[row].id, visibleDates[column]))
      }
    }

    return keys
  }

  function activateCell(next: CellCoord, mode: 'replace' | 'toggle' | 'range') {
    const nextKey = cellKey(next.groupId, next.date)
    setActiveCell(next)
    setError('')
    setSuccess('')

    if (mode === 'range' && anchorCell) {
      setSelectedCellKeys(buildRectKeys(anchorCell, next))
      return
    }

    setAnchorCell(next)

    if (mode === 'toggle') {
      setSelectedCellKeys((current) =>
        current.includes(nextKey)
          ? current.filter((value) => value !== nextKey)
          : [...current, nextKey]
      )
      return
    }

    setSelectedCellKeys([nextKey])
    setSelectedLessonId(null)
    setSelectedSlotOrder(1)
  }

  function loadLessonIntoForm(lesson: LessonEditorDto, groupId: string, dayId: string, date: string) {
    const slotOrder = lesson.orderNumber || 1
    setActiveCell({ groupId, date })
    setAnchorCell({ groupId, date })
    setSelectedCellKeys([cellKey(groupId, date)])
    setSelectedLessonId(lesson.id)
    setSelectedSlotOrder(slotOrder)
    setForm({
      version: lesson.version,
      orderNumber: slotOrder,
      title: lesson.title || '',
      lecturer: lesson.lecturer || null,
      lecturers: lesson.lecturers || [],
      durationHours: lesson.durationHours || 1,
      note: lesson.note || '',
      type: (lesson.type as LessonType) || 'LECTURE',
      dayId,
      groupId,
      instructorIds: lesson.instructorIds || resolveInstructorIds(lesson),
      instructorNames: lesson.instructorNames || [],
    })
    setError('')
    setSuccess('')
  }

  function selectSlot(order: number) {
    if (!activeGroup) {
      return
    }

    const existing = slotEntries.find((entry) => entry.order === order)?.lesson
    if (existing && activeDay?.id) {
      loadLessonIntoForm(existing, activeGroup.id, activeDay.id, activeDate)
      return
    }

    setSelectedLessonId(null)
    setSelectedSlotOrder(order)
    setHistory([])
    setForm(createEmptyLessonForm(activeGroup.id, activeDay?.id || '', order))
    setError('')
    setSuccess('')
  }

  function toggleInstructor(userId: string) {
    setForm((current) => ({
      ...current,
      instructorIds: current.instructorIds.includes(userId)
        ? current.instructorIds.filter((value) => value !== userId)
        : [...current.instructorIds, userId],
    }))
  }

  async function ensureDayExists(group: GroupDto, date: string) {
    const latestGroup = await groupsApi.getById(group.id)
    const existingDay = findDay(latestGroup, date)
    if (existingDay?.id) {
      return existingDay.id
    }

    const payload = buildGroupPayload(latestGroup)
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

  async function createEmptyDay() {
    if (!activeGroup || !activeDate) {
      return
    }

    setCreatingDay(true)
    setError('')
    setSuccess('')
    try {
      await ensureDayExists(activeGroup, activeDate)
      await onChanged()
      setSuccess(`Пустой день ${activeDate} создан.`)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось создать пустой день.'
      )
    } finally {
      setCreatingDay(false)
    }
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
      setSuccess(`Группа ${createdGroup.code} создана.`)
      const firstDate = visibleDates[0] || range.from
      setActiveCell({ groupId: createdGroup.id, date: firstDate })
      setAnchorCell({ groupId: createdGroup.id, date: firstDate })
      setSelectedCellKeys([cellKey(createdGroup.id, firstDate)])
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

  async function handleSaveLesson() {
    if (!activeGroup || !activeDate) {
      setError('Сначала выбери ячейку расписания.')
      return
    }

    if (!form.title.trim()) {
      setError('Название занятия не может быть пустым.')
      return
    }

    if (!form.instructorIds.length) {
      setError('Выбери хотя бы одного инструктора.')
      return
    }

    setSaving(true)
    setError('')
    setSuccess('')
    try {
      const dayId = await ensureDayExists(activeGroup, activeDate)
      const payload: LessonMutationPayload = {
        ...form,
        title: form.title.trim(),
        note: form.note?.trim() || null,
        groupId: activeGroup.id,
        dayId,
        durationHours: Number(form.durationHours) || 1,
        orderNumber: selectedSlotOrder,
        instructorNames: [],
        lecturers: [],
        lecturer: null,
      }

      const savedLesson = selectedLessonId
        ? await lessonsApi.update(selectedLessonId, payload)
        : await lessonsApi.create(payload)

      await onChanged()
      setSelectedLessonId(savedLesson.id)
      setSelectedSlotOrder(savedLesson.orderNumber || selectedSlotOrder)
      setForm((current) => ({
        ...current,
        version: savedLesson.version,
        orderNumber: savedLesson.orderNumber || current.orderNumber,
        dayId: savedLesson.dayId || current.dayId,
        groupId: savedLesson.groupId || current.groupId,
      }))
      setSuccess(selectedLessonId ? 'Изменения по занятию сохранены.' : 'Занятие создано.')
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

  async function handleDeleteLesson() {
    if (!selectedLessonId || form.version === null || form.version === undefined) {
      return
    }

    setDeleting(true)
    setError('')
    setSuccess('')
    try {
      await lessonsApi.delete(selectedLessonId, form.version)
      await onChanged()
      setSelectedLessonId(null)
      setHistory([])
      setForm(createEmptyLessonForm(activeGroup?.id, activeDay?.id || '', selectedSlotOrder))
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

  function buildClipboardSnapshot() {
    if (!selectedBounds) {
      return null
    }

    const cells: ClipboardCellSnapshot[] = []
    for (let row = selectedBounds.minRow; row <= selectedBounds.maxRow; row += 1) {
      for (let column = selectedBounds.minColumn; column <= selectedBounds.maxColumn; column += 1) {
        const group = filteredGroups[row]
        const date = visibleDates[column]
        const day = findDay(group, date)
        const lessons = sortLessons(day?.lessons || []).map((lesson) => ({
          orderNumber: lesson.orderNumber,
          title: lesson.title,
          durationHours: lesson.durationHours,
          note: lesson.note || null,
          type: (lesson.type as LessonType) || 'LECTURE',
          instructorIds: resolveInstructorIds(lesson),
        }))

        cells.push({
          rowOffset: row - selectedBounds.minRow,
          columnOffset: column - selectedBounds.minColumn,
          hasDay: Boolean(day?.id),
          lessons,
        })
      }
    }

    return {
      rows: selectedBounds.maxRow - selectedBounds.minRow + 1,
      columns: selectedBounds.maxColumn - selectedBounds.minColumn + 1,
      cells,
    }
  }

  function handleCopySelection() {
    const snapshot = buildClipboardSnapshot()
    if (!snapshot) {
      return
    }
    setClipboard(snapshot)
    setSuccess(`Скопировано ${snapshot.rows}×${snapshot.columns} ячеек.`)
    setError('')
  }

  async function overwriteCell(group: GroupDto, date: string, snapshot: ClipboardCellSnapshot) {
    const day = findDay(group, date)
    const existingLessons = sortLessons(day?.lessons || [])

    for (const lesson of existingLessons) {
      await lessonsApi.delete(lesson.id, lesson.version)
    }

    if (!snapshot.hasDay && snapshot.lessons.length === 0) {
      return
    }

    const dayId = day?.id || (await ensureDayExists(group, date))
    for (const lesson of snapshot.lessons.slice(0, SLOT_COUNT)) {
      if (!lesson.instructorIds.length) {
        continue
      }
      await lessonsApi.create({
        title: lesson.title,
        orderNumber: lesson.orderNumber || 1,
        durationHours: lesson.durationHours,
        note: lesson.note || null,
        type: lesson.type || 'LECTURE',
        dayId,
        groupId: group.id,
        instructorIds: lesson.instructorIds,
        instructorNames: [],
        lecturers: [],
        lecturer: null,
      })
    }
  }

  async function handlePasteSelection() {
    if (!clipboard || !activeCell) {
      return
    }

    const startRow = rowIndexByGroupId.get(activeCell.groupId)
    const startColumn = columnIndexByDate.get(activeCell.date)
    if (startRow === undefined || startColumn === undefined) {
      return
    }

    setPasting(true)
    setError('')
    setSuccess('')

    try {
      for (const snapshot of clipboard.cells) {
        const targetRow = startRow + snapshot.rowOffset
        const targetColumn = startColumn + snapshot.columnOffset

        if (targetRow >= filteredGroups.length || targetColumn >= visibleDates.length) {
          continue
        }

        await overwriteCell(filteredGroups[targetRow], visibleDates[targetColumn], snapshot)
      }

      await onChanged()

      const nextSelection: string[] = []
      for (let rowOffset = 0; rowOffset < clipboard.rows; rowOffset += 1) {
        for (let columnOffset = 0; columnOffset < clipboard.columns; columnOffset += 1) {
          const targetRow = startRow + rowOffset
          const targetColumn = startColumn + columnOffset
          if (targetRow >= filteredGroups.length || targetColumn >= visibleDates.length) {
            continue
          }
          nextSelection.push(cellKey(filteredGroups[targetRow].id, visibleDates[targetColumn]))
        }
      }

      setSelectedCellKeys(nextSelection)
      setSuccess(`Вставлено ${clipboard.rows}×${clipboard.columns} ячеек.`)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось вставить выбранный диапазон.'
      )
    } finally {
      setPasting(false)
    }
  }

  const groupColumnWidth = Math.round(180 * (zoom / 100))
  const cellWidth = Math.round(118 * (zoom / 100))
  const cellHeight = Math.round(88 * (zoom / 100))

  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Сетка редактирования занятий</h3>
          <p className="mt-1 max-w-3xl text-sm text-muted-foreground">
            Один день — одна ячейка. По клику открывается редактор дня с 8 слотами, а диапазоны
            можно копировать и вставлять между группами.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={groupSearch}
              onChange={(event) => setGroupSearch(event.target.value)}
              placeholder="Поиск по группе, локации, курсу"
              className="w-72 pl-9"
            />
          </div>

          <div className="flex items-center gap-2 rounded-xl border border-border bg-white px-3 py-2">
            <ZoomOut className="h-4 w-4 text-muted-foreground" />
            <input
              type="range"
              min={MIN_ZOOM}
              max={MAX_ZOOM}
              step={5}
              value={zoom}
              onChange={(event) => setZoom(clampZoom(Number(event.target.value)))}
              className="w-28 accent-primary"
            />
            <ZoomIn className="h-4 w-4 text-muted-foreground" />
            <span className="min-w-10 text-right text-xs font-medium text-slate-700">
              {zoom}%
            </span>
          </div>

          <Button variant="outline" onClick={handleCopySelection} disabled={!activeCell}>
            <Copy className="mr-2 h-4 w-4" />
            Копировать
          </Button>
          <Button
            variant="outline"
            onClick={() => void handlePasteSelection()}
            disabled={!clipboard || !activeCell || pasting}
          >
            <ClipboardPaste className="mr-2 h-4 w-4" />
            {pasting ? 'Вставляю...' : 'Вставить'}
          </Button>
        </div>
      </div>

      {canManageGroups ? (
        <div className="mt-5 rounded-2xl border border-border bg-slate-50 p-4">
          <div className="mb-3 text-sm font-semibold text-slate-950">Создать группу</div>
          <div className="grid gap-3 xl:grid-cols-[minmax(180px,1fr)_minmax(180px,1fr)_minmax(140px,180px)_auto]">
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
              value={groupForm.course}
              onChange={(event) =>
                setGroupForm((current) => ({ ...current, course: event.target.value }))
              }
              placeholder="Курс или поток"
            />
            <Button onClick={() => void handleCreateGroup()} disabled={creatingGroup}>
              <Plus className="mr-2 h-4 w-4" />
              {creatingGroup ? 'Создаю...' : 'Создать группу'}
            </Button>
          </div>
        </div>
      ) : null}

      {rangeOverflow ? (
        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
          Показаны первые {MAX_DATES} дней диапазона. Сузь период, если нужен более точный обзор.
        </div>
      ) : null}

      {error ? (
        <div className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      ) : null}

      {success ? (
        <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          {success}
        </div>
      ) : null}

      {!filteredGroups.length || !visibleDates.length ? (
        <div className="mt-5 rounded-2xl border border-dashed border-border px-5 py-12 text-center text-sm text-muted-foreground">
          Нет групп или дат в выбранном диапазоне.
        </div>
      ) : (
        <>
          <div className="mt-5 rounded-2xl border border-border bg-slate-50 p-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <div className="text-sm text-muted-foreground">
                Активная ячейка: <span className="font-semibold text-slate-950">{activeSelectionLabel}</span>
              </div>
              <div className="text-xs text-muted-foreground">
                Shift — диапазон, Ctrl/Cmd — множественный выбор, Ctrl/Cmd+C / Ctrl/Cmd+V — перенос серии дней
              </div>
            </div>

            <div className="max-w-full overflow-auto">
              <div
                className="grid w-max min-w-full"
                style={{
                  gridTemplateColumns: `${groupColumnWidth}px repeat(${visibleDates.length}, ${cellWidth}px)`,
                }}
              >
                <div className="sticky left-0 top-0 z-20 border-b border-r border-border bg-slate-100 px-4 py-3">
                  <div className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                    Группа
                  </div>
                  <div className="mt-1 text-sm text-slate-700">Дни и ячейки операций</div>
                </div>

                {visibleDates.map((date) => (
                  <div
                    key={date}
                    className="sticky top-0 z-10 border-b border-r border-border bg-slate-50 px-2 py-3 text-center"
                  >
                    <div className="text-sm font-semibold text-slate-950">
                      {new Date(date).toLocaleDateString('ru-RU', { day: '2-digit' })}
                    </div>
                    <div className="text-[11px] uppercase tracking-wide text-slate-500">
                      {new Date(date).toLocaleDateString('ru-RU', { weekday: 'short' })}
                    </div>
                    <div className="text-[11px] text-slate-500">
                      {new Date(date).toLocaleDateString('ru-RU', { month: 'short' })}
                    </div>
                  </div>
                ))}

                {filteredGroups.map((group) => (
                  <div key={group.id} className="contents">
                    <div className="sticky left-0 z-10 border-b border-r border-border bg-white px-4 py-3">
                      <div className="text-sm font-semibold text-slate-950">{group.code}</div>
                      <div className="mt-1 text-xs text-muted-foreground">
                        {group.location || 'Без локации'}
                        {group.course ? ` • курс ${group.course}` : ''}
                      </div>
                    </div>

                    {visibleDates.map((date) => {
                      const day = findDay(group, date)
                      const lessons = sortLessons(day?.lessons || [])
                      const isActive = activeCell?.groupId === group.id && activeCell?.date === date
                      const isSelected = selectedCellKeys.includes(cellKey(group.id, date))

                      return (
                        <button
                          key={cellKey(group.id, date)}
                          type="button"
                          onClick={(event) => {
                            if (event.shiftKey) {
                              activateCell({ groupId: group.id, date }, 'range')
                              return
                            }
                            if (event.ctrlKey || event.metaKey) {
                              activateCell({ groupId: group.id, date }, 'toggle')
                              return
                            }
                            activateCell({ groupId: group.id, date }, 'replace')
                          }}
                          className={cn(
                            'border-b border-r border-border px-2 py-2 text-left transition-colors',
                            isActive
                              ? 'bg-primary/10'
                              : isSelected
                                ? 'bg-sky-50'
                                : 'bg-white hover:bg-slate-50'
                          )}
                        >
                          <div
                            className={cn(
                              'flex h-full flex-col rounded-lg border px-2 py-2',
                              day?.id
                                ? 'border-slate-200 bg-slate-50'
                                : 'border-dashed border-slate-200 bg-white'
                            )}
                            style={{ minHeight: `${cellHeight}px` }}
                          >
                            <div className="mb-1 flex items-center justify-between gap-2">
                              <span className="text-[10px] font-medium uppercase tracking-wide text-slate-500">
                                {formatDateCaption(date)}
                              </span>
                              <span className="rounded-full bg-white px-2 py-0.5 text-[10px] font-medium text-slate-600">
                                {cellStatusLabel(Boolean(day?.id), lessons.length)}
                              </span>
                            </div>

                            {lessons.length === 0 ? (
                              <div className="flex flex-1 items-center justify-center text-center text-[11px] text-slate-400">
                                {day?.id ? 'Пустой день' : 'Кликни для редактирования'}
                              </div>
                            ) : (
                              <div className="space-y-1">
                                {lessons.slice(0, 2).map((lesson) => (
                                  <div
                                    key={lesson.id}
                                    className="rounded-md border border-slate-200 bg-white px-2 py-1"
                                  >
                                    <div className="flex items-center justify-between gap-1">
                                      <span className="text-[10px] font-semibold text-primary">
                                        {lesson.orderNumber || '—'}
                                      </span>
                                      <span className="text-[10px] font-medium text-slate-500">
                                        {shortLessonTypeLabel(lesson.type)}
                                      </span>
                                    </div>
                                    <div className="line-clamp-2 text-[11px] font-medium text-slate-950">
                                      {lesson.title}
                                    </div>
                                  </div>
                                ))}
                                {lessons.length > 2 ? (
                                  <div className="text-[10px] font-medium text-slate-500">
                                    +{lessons.length - 2} еще
                                  </div>
                                ) : null}
                              </div>
                            )}
                          </div>
                        </button>
                      )
                    })}
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="mt-5 space-y-5">
            <div className="rounded-2xl border border-border bg-slate-50 p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold text-slate-950">Редактор выбранного дня</div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {activeSelectionLabel}. Внутри дня доступны 8 слотов занятий.
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    onClick={() => void createEmptyDay()}
                    disabled={!activeGroup || creatingDay || Boolean(activeDay?.id)}
                  >
                    <CalendarPlus2 className="mr-2 h-4 w-4" />
                    {creatingDay ? 'Создаю...' : 'Пустой день'}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => selectSlot(selectedSlotOrder)}
                    disabled={!activeGroup}
                  >
                    <Plus className="mr-2 h-4 w-4" />
                    Новый слот
                  </Button>
                </div>
              </div>

              <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                {slotEntries.map(({ order, lesson }) => {
                  const isSelected = selectedSlotOrder === order
                  const lessonNames =
                    (lesson?.instructorNames || lesson?.lecturers || []).join(', ') || 'Без инструктора'

                  return (
                    <button
                      key={order}
                      type="button"
                      onClick={() => selectSlot(order)}
                      className={cn(
                        'rounded-2xl border px-3 py-3 text-left transition-colors',
                        isSelected
                          ? 'border-primary bg-primary/5 shadow-sm'
                          : 'border-border bg-white hover:bg-slate-50'
                      )}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="text-sm font-semibold text-slate-950">Слот {order}</div>
                        <div className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-600">
                          {lesson ? lessonTypeLabel(lesson.type) : 'Пусто'}
                        </div>
                      </div>
                      {lesson ? (
                        <div className="mt-3 space-y-1.5">
                          <div className="line-clamp-2 text-sm font-medium text-slate-950">
                            {lesson.title}
                          </div>
                          <div className="text-xs text-muted-foreground">{lesson.durationHours} ч</div>
                          <div className="line-clamp-2 text-xs text-muted-foreground">
                            {lessonNames}
                          </div>
                        </div>
                      ) : (
                        <div className="mt-3 rounded-xl border border-dashed border-slate-200 px-3 py-5 text-center text-xs text-muted-foreground">
                          Свободный слот
                        </div>
                      )}
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="grid gap-5 xl:grid-cols-[1.2fr_0.8fr]">
              <div className="rounded-2xl border border-border bg-slate-50 p-4">
                <div className="mb-4 flex items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-semibold text-slate-950">
                      {selectedLessonId ? 'Редактирование занятия' : 'Новый слот занятия'}
                    </div>
                    <div className="text-xs text-muted-foreground">
                      Слот {selectedSlotOrder} в дне {activeDate}
                    </div>
                  </div>
                  <div className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-600">
                    slot {selectedSlotOrder}
                  </div>
                </div>

                <label className="space-y-2">
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

                <div className="mt-4 grid gap-4 md:grid-cols-2">
                  <label className="space-y-2">
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

                  <label className="space-y-2">
                    <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Часы
                    </span>
                    <Input
                      type="number"
                      min={1}
                      max={24}
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
                    Примечание
                  </span>
                  <Textarea
                    value={form.note || ''}
                    onChange={(event) =>
                      setForm((current) => ({ ...current, note: event.target.value }))
                    }
                    placeholder="Аудитория, комментарий, перенос и другие детали"
                    rows={4}
                  />
                </label>

                <div className="mt-4 space-y-3">
                  <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <UsersRound className="h-4 w-4" />
                    Инструкторы
                  </div>
                  <div className="relative">
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                    <Input
                      value={instructorSearch}
                      onChange={(event) => setInstructorSearch(event.target.value)}
                      placeholder="Поиск по фамилии или логину"
                      className="pl-9"
                    />
                  </div>
                  <div className="grid max-h-64 gap-2 overflow-auto rounded-xl border border-border bg-white p-3">
                    {filteredInstructorOptions.map((user) => (
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
                            {instructorLabel(user)}
                          </div>
                          <div className="text-xs text-muted-foreground">
                            {user.position || user.role}
                          </div>
                        </div>
                      </label>
                    ))}
                    {filteredInstructorOptions.length === 0 ? (
                      <div className="rounded-lg border border-dashed border-border px-3 py-4 text-sm text-muted-foreground">
                        Ничего не найдено по введённой фамилии.
                      </div>
                    ) : null}
                  </div>
                </div>

                <div className="mt-5 flex flex-wrap gap-2">
                  <Button onClick={() => void handleSaveLesson()} disabled={saving || !activeGroup}>
                    <Save className="mr-2 h-4 w-4" />
                    {saving ? 'Сохраняю...' : selectedLessonId ? 'Сохранить' : 'Создать'}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() =>
                      setForm(createEmptyLessonForm(activeGroup?.id, activeDay?.id || '', selectedSlotOrder))
                    }
                    disabled={!activeGroup}
                  >
                    <Plus className="mr-2 h-4 w-4" />
                    Очистить слот
                  </Button>
                  {selectedLessonId ? (
                    <Button
                      variant="destructive"
                      onClick={() => void handleDeleteLesson()}
                      disabled={deleting}
                    >
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
                    Выбери заполненный слот, и здесь появится его аудит.
                  </div>
                ) : history.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-border bg-white px-4 py-6 text-sm text-muted-foreground">
                    История пока пустая.
                  </div>
                ) : (
                  <div className="space-y-2">
                    {history.slice(0, 12).map((entry) => (
                      <div key={entry.id} className="rounded-xl border border-border bg-white px-3 py-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="text-sm font-medium text-slate-950">{entry.action}</div>
                          <div className="text-xs text-muted-foreground">
                            {formatHistoryDate(entry.changedAt)}
                          </div>
                        </div>
                        <div className="mt-2 text-sm text-slate-700">{entry.changedBy || 'system'}</div>
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
        </>
      )}
    </section>
  )
}
