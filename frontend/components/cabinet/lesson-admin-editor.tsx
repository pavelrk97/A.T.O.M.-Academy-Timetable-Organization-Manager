'use client'

import { useEffect, useMemo, useState } from 'react'
import {
  ClipboardPaste,
  Copy,
  Pencil,
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
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { groupsApi, lessonsApi } from '@/lib/api'
import { useI18n } from '@/lib/i18n'
import type {
  DaySyncPayload,
  GroupDto,
  LessonEditorDto,
  LessonType,
  User,
} from '@/lib/types'
import { cn } from '@/lib/utils'
import { LessonCard } from '@/components/schedule/lesson-card'

interface LessonAdminEditorProps {
  groups: GroupDto[]
  users: User[]
  canManageGroups: boolean
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

interface DaySlotDraft {
  orderNumber: number
  lessonId?: string | null
  version?: number | null
  title: string
  durationHours: number
  note: string
  type: LessonType
  businessTrip: boolean
  instructorIds: string[]
}

interface DayDraft {
  key: string
  groupId: string
  groupCode: string
  date: string
  dayId?: string | null
  hasDay: boolean
  ensureDay: boolean
  slots: DaySlotDraft[]
}

interface ClipboardLessonDraft {
  orderNumber: number
  title: string
  durationHours: number
  note: string
  type: LessonType
  businessTrip: boolean
  instructorIds: string[]
}

interface ClipboardCellSnapshot {
  rowOffset: number
  columnOffset: number
  lessons: ClipboardLessonDraft[]
}

interface ClipboardSnapshot {
  rows: number
  columns: number
  cells: ClipboardCellSnapshot[]
}

interface DayEditorSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialDraft: DayDraft | null
  instructorOptions: User[]
  onApply: (draft: DayDraft) => void
}

const LESSON_TYPE_OPTIONS: LessonType[] = ['LECTURE', 'SELF_STUDY', 'ASSESSMENT']
const INTERNAL_IMPORTED_USERNAME_PREFIX = 'imported-'
const MAX_DATES = 100
const SLOT_COUNT = 8
const MIN_ZOOM = 80
const MAX_ZOOM = 135

function lessonTypeLabel(type: LessonType | string | null | undefined, t: (key: string) => string) {
  switch (type) {
    case 'LECTURE':
      return t('lesson.typeLecture')
    case 'SELF_STUDY':
      return t('lesson.typeSelfStudy')
    case 'ASSESSMENT':
      return t('lesson.typeAssessment')
    default:
      return t('lesson.typeDefault')
  }
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

function createEmptySlotDraft(orderNumber: number): DaySlotDraft {
  return {
    orderNumber,
    lessonId: null,
    version: null,
    title: '',
    durationHours: 2,
    note: '',
    type: 'LECTURE',
    businessTrip: false,
    instructorIds: [],
  }
}

function cloneSlot(slot: DaySlotDraft): DaySlotDraft {
  return {
    ...slot,
    instructorIds: [...slot.instructorIds],
  }
}

function cloneDayDraft(draft: DayDraft): DayDraft {
  return {
    ...draft,
    slots: draft.slots.map(cloneSlot),
  }
}

function findDay(group: GroupDto, date: string) {
  return (group.days || []).find((day) => day.date === date) || null
}

function dayLessonToSlot(
  lesson: LessonEditorDto,
  resolveInstructorIds: (lesson: LessonEditorDto) => string[]
): DaySlotDraft {
  return {
    orderNumber: lesson.orderNumber || 1,
    lessonId: lesson.id,
    version: lesson.version,
    title: lesson.title || '',
    durationHours: lesson.durationHours || 2,
    note: lesson.note || '',
    type: (lesson.type as LessonType) || 'LECTURE',
    businessTrip: Boolean(lesson.businessTrip),
    instructorIds: resolveInstructorIds(lesson),
  }
}

function buildDayDraft(
  group: GroupDto,
  date: string,
  resolveInstructorIds: (lesson: LessonEditorDto) => string[]
): DayDraft {
  const day = findDay(group, date)
  const lessonsByOrder = new Map<number, LessonEditorDto>()
  for (const lesson of sortLessons(day?.lessons || [])) {
    const order = lesson.orderNumber || 1
    if (!lessonsByOrder.has(order)) {
      lessonsByOrder.set(order, lesson)
    }
  }

  return {
    key: cellKey(group.id, date),
    groupId: group.id,
    groupCode: group.code,
    date,
    dayId: day?.id || null,
    hasDay: Boolean(day?.id),
    ensureDay: Boolean(day?.id),
    slots: Array.from({ length: SLOT_COUNT }, (_, index) => {
      const order = index + 1
      const lesson = lessonsByOrder.get(order)
      return lesson ? dayLessonToSlot(lesson, resolveInstructorIds) : createEmptySlotDraft(order)
    }),
  }
}

function draftToCellLessons(draft: DayDraft): LessonEditorDto[] {
  return draft.slots
    .filter((slot) => slot.title.trim())
    .map((slot) => ({
      id: slot.lessonId || `${draft.key}:${slot.orderNumber}`,
      version: slot.version || 0,
      orderNumber: slot.orderNumber,
      title: slot.title,
      durationHours: slot.durationHours,
      note: slot.note || null,
      type: slot.type,
      businessTrip: slot.businessTrip,
      instructorIds: slot.instructorIds,
      instructorNames: [],
      lecturers: [],
      lecturer: null,
      dayId: draft.dayId || null,
      groupId: draft.groupId,
    }))
}

function areStringArraysEqual(left: string[], right: string[]) {
  if (left.length !== right.length) {
    return false
  }

  const normalizedLeft = [...left].sort()
  const normalizedRight = [...right].sort()
  return normalizedLeft.every((value, index) => value === normalizedRight[index])
}

function areDraftsEqual(left: DayDraft, right: DayDraft) {
  return left.ensureDay === right.ensureDay && left.slots.every((slot, index) => {
    const other = right.slots[index]
    return (
      slot.orderNumber === other.orderNumber &&
      slot.title.trim() === other.title.trim() &&
      slot.durationHours === other.durationHours &&
      slot.note.trim() === other.note.trim() &&
      slot.type === other.type &&
      slot.businessTrip === other.businessTrip &&
      areStringArraysEqual(slot.instructorIds, other.instructorIds)
    )
  })
}

function getFilledSlots(draft: DayDraft) {
  return draft.slots.filter((slot) => slot.title.trim())
}

function slotHasExtraData(slot: DaySlotDraft) {
  return Boolean(slot.note.trim() || slot.instructorIds.length)
}

function validateDraft(draft: DayDraft, t: (key: string) => string): string | null {
  for (const slot of draft.slots) {
    const title = slot.title.trim()
    if (!title && slotHasExtraData(slot)) {
      return `${t('lesson.valFillOrClear1')} №${slot.orderNumber} ${t('lesson.valFor')} ${draft.groupCode} ${draft.date}.`
    }
    if (title && !slot.instructorIds.length) {
      return `${t('lesson.valInstructor1')} ${draft.groupCode} ${draft.date}, ${t('lesson.slotLower')} №${slot.orderNumber}.`
    }
    if (title && slot.durationHours <= 0) {
      return `${t('lesson.valHours1')} ${draft.groupCode} ${draft.date}, ${t('lesson.slotLower')} №${slot.orderNumber}.`
    }
  }
  return null
}

function cellStatusLabel(hasDay: boolean, lessonsCount: number, dirty: boolean, t: (key: string) => string) {
  if (dirty) {
    return `${t('lesson.draft')} ${lessonsCount}/8`
  }
  if (!hasDay) {
    return t('lesson.noDay')
  }
  return `${lessonsCount}/8`
}

function DayEditorSheet({
  open,
  onOpenChange,
  initialDraft,
  instructorOptions,
  onApply,
}: DayEditorSheetProps) {
  const { t } = useI18n()
  const [draft, setDraft] = useState<DayDraft | null>(initialDraft ? cloneDayDraft(initialDraft) : null)
  const [instructorSearch, setInstructorSearch] = useState('')

  useEffect(() => {
    setDraft(initialDraft ? cloneDayDraft(initialDraft) : null)
    setInstructorSearch('')
  }, [initialDraft])

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

  function updateSlot(orderNumber: number, patch: Partial<DaySlotDraft>) {
    setDraft((current) => {
      if (!current) {
        return current
      }
      return {
        ...current,
        slots: current.slots.map((slot) =>
          slot.orderNumber === orderNumber
            ? {
                ...slot,
                ...patch,
                instructorIds: patch.instructorIds ? [...patch.instructorIds] : slot.instructorIds,
              }
            : slot
        ),
      }
    })
  }

  function toggleInstructor(orderNumber: number, userId: string) {
    setDraft((current) => {
      if (!current) {
        return current
      }
      return {
        ...current,
        slots: current.slots.map((slot) => {
          if (slot.orderNumber !== orderNumber) {
            return slot
          }
          return {
            ...slot,
            instructorIds: slot.instructorIds.includes(userId)
              ? slot.instructorIds.filter((value) => value !== userId)
              : [...slot.instructorIds, userId],
          }
        }),
      }
    })
  }

  function clearSlot(orderNumber: number) {
    setDraft((current) => {
      if (!current) {
        return current
      }
      return {
        ...current,
        slots: current.slots.map((slot) =>
          slot.orderNumber === orderNumber
            ? {
                ...slot,
                title: '',
                durationHours: 2,
                note: '',
                type: slot.type || 'LECTURE',
                businessTrip: false,
                instructorIds: [],
              }
            : slot
        ),
      }
    })
  }

  function applyDraft() {
    if (!draft) {
      return
    }
    onApply({
      ...draft,
      ensureDay: true,
      slots: draft.slots.map((slot) => ({
        ...slot,
        title: slot.title,
        note: slot.note,
        instructorIds: [...slot.instructorIds],
      })),
    })
    onOpenChange(false)
  }

  if (!draft) {
    return null
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-[min(96vw,1100px)] overflow-y-auto sm:max-w-[1100px]">
        <SheetHeader>
          <SheetTitle>
            {t('lesson.dayOfGroup')} {draft.groupCode} • {draft.date}
          </SheetTitle>
          <SheetDescription>
            {t('lesson.sheetDesc')}
          </SheetDescription>
        </SheetHeader>

        <div className="space-y-4 px-4 pb-2">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-slate-50 px-4 py-3">
            <div className="text-sm text-slate-700">
              {draft.hasDay ? t('lesson.dayExists') : t('lesson.dayWillCreate')}
            </div>
            <div className="relative w-full max-w-sm">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={instructorSearch}
                onChange={(event) => setInstructorSearch(event.target.value)}
                placeholder={t('lesson.searchInstructor')}
                className="pl-9"
              />
            </div>
          </div>

          <div className="grid gap-4">
            {draft.slots.map((slot) => (
              <section key={slot.orderNumber} className="rounded-2xl border border-border bg-white p-4 shadow-sm">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-semibold text-slate-950">{t('lesson.slotCap')} №{slot.orderNumber}</div>
                    <div className="text-xs text-muted-foreground">
                      {slot.lessonId ? t('lesson.editExisting') : t('lesson.newSlot')}
                    </div>
                  </div>
                  <Button variant="outline" size="sm" onClick={() => clearSlot(slot.orderNumber)}>
                    <Trash2 className="mr-2 h-4 w-4" />
                    {t('lesson.clearSlot')}
                  </Button>
                </div>

                <div className="grid gap-4 lg:grid-cols-[1.2fr_140px_120px]">
                  <label className="space-y-2">
                    <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      {t('lesson.title')}
                    </span>
                    <Input
                      value={slot.title}
                      onChange={(event) => updateSlot(slot.orderNumber, { title: event.target.value })}
                      placeholder={t('lesson.titlePlaceholder')}
                    />
                  </label>

                  <label className="space-y-2">
                    <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      {t('lesson.type')}
                    </span>
                    <select
                      value={slot.type}
                      onChange={(event) =>
                        updateSlot(slot.orderNumber, { type: event.target.value as LessonType })
                      }
                      className="h-10 w-full rounded-xl border border-border bg-white px-3 text-sm"
                    >
                      {LESSON_TYPE_OPTIONS.map((type) => (
                        <option key={type} value={type}>
                          {lessonTypeLabel(type, t)}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="space-y-2">
                    <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      {t('lesson.hours')}
                    </span>
                    <Input
                      type="number"
                      min={1}
                      max={24}
                      value={slot.durationHours}
                      onChange={(event) =>
                        updateSlot(slot.orderNumber, {
                          durationHours: Number(event.target.value) || 1,
                        })
                      }
                    />
                  </label>
                </div>

                <label className="mt-4 flex items-center gap-3 text-sm">
                  <input
                    type="checkbox"
                    checked={slot.businessTrip}
                    onChange={(event) =>
                      updateSlot(slot.orderNumber, { businessTrip: event.target.checked })
                    }
                  />
                  <span>{t('lesson.businessTrip')}</span>
                </label>

                <label className="mt-4 block space-y-2">
                  <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    {t('lesson.comment')}
                  </span>
                  <Textarea
                    value={slot.note}
                    onChange={(event) => updateSlot(slot.orderNumber, { note: event.target.value })}
                    placeholder={t('lesson.notePlaceholder')}
                    className="min-h-20"
                  />
                </label>

                <div className="mt-4 space-y-2">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      {t('lesson.instructors')}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {t('multiselect.selectedCount')} {slot.instructorIds.length}
                    </span>
                  </div>
                  <div className="max-h-44 space-y-2 overflow-auto rounded-xl border border-border bg-slate-50 p-3">
                    {filteredInstructorOptions.map((user) => (
                      <label key={`${slot.orderNumber}:${user.id}`} className="flex items-center gap-3 text-sm">
                        <input
                          type="checkbox"
                          checked={slot.instructorIds.includes(user.id)}
                          onChange={() => toggleInstructor(slot.orderNumber, user.id)}
                        />
                        <span>{instructorLabel(user)}</span>
                      </label>
                    ))}
                    {!filteredInstructorOptions.length ? (
                      <div className="text-sm text-muted-foreground">{t('lesson.searchNoResults')}</div>
                    ) : null}
                  </div>
                </div>
              </section>
            ))}
          </div>
        </div>

        <SheetFooter className="border-t border-border bg-white">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('lesson.close')}
          </Button>
          <Button onClick={applyDraft}>
            <Save className="mr-2 h-4 w-4" />
            {t('lesson.applyToDraft')}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

interface GroupEditSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  target: GroupDto | null
  saving: boolean
  deleting: boolean
  onSave: (target: GroupDto, code: string, location: string, course: string) => void
  onDelete: (target: GroupDto) => void
}

function GroupEditSheet({
  open,
  onOpenChange,
  target,
  saving,
  deleting,
  onSave,
  onDelete,
}: GroupEditSheetProps) {
  const { t } = useI18n()
  const [code, setCode] = useState('')
  const [location, setLocation] = useState('')
  const [course, setCourse] = useState('')

  useEffect(() => {
    if (target) {
      setCode(target.code)
      setLocation(target.location || '')
      setCourse(target.course || '')
    }
  }, [target])

  if (!target) {
    return null
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-[min(96vw,520px)] overflow-y-auto sm:max-w-[520px]">
        <SheetHeader>
          <SheetTitle>{t('lesson.group')} {target.code}</SheetTitle>
          <SheetDescription>
            {t('lesson.groupEditDesc')}
          </SheetDescription>
        </SheetHeader>

        <div className="space-y-4 px-4 pb-2">
          <label className="block space-y-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">{t('lesson.groupCode')}</span>
            <Input value={code} onChange={(event) => setCode(event.target.value)} />
          </label>
          <label className="block space-y-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">{t('lesson.location')}</span>
            <Input value={location} onChange={(event) => setLocation(event.target.value)} placeholder={t('lesson.locationPlaceholder')} />
          </label>
          <label className="block space-y-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">{t('lesson.course')}</span>
            <Input value={course} onChange={(event) => setCourse(event.target.value)} placeholder={t('lesson.coursePlaceholder')} />
          </label>

          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800">
            {t('lesson.groupHasDays')} {(target.days || []).length}. {t('lesson.cascadeWarn')}
          </div>
        </div>

        <SheetFooter className="border-t border-border bg-white">
          <Button
            variant="destructive"
            onClick={() => onDelete(target)}
            disabled={deleting || saving}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            {deleting ? t('lesson.deleting') : t('lesson.deleteGroup')}
          </Button>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={saving || deleting}>
            {t('lesson.cancel')}
          </Button>
          <Button onClick={() => onSave(target, code, location, course)} disabled={saving || deleting}>
            <Save className="mr-2 h-4 w-4" />
            {saving ? t('lesson.saving') : t('lesson.save')}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

export function LessonAdminEditor({
  groups,
  users,
  canManageGroups,
  range,
}: LessonAdminEditorProps) {
  const { t, lang } = useI18n()
  const dateLocale = lang === 'en' ? 'en-US' : 'ru-RU'
  const [localGroups, setLocalGroups] = useState<GroupDto[]>(groups)
  const [groupSearch, setGroupSearch] = useState('')
  const [zoom, setZoom] = useState(100)
  const [activeCell, setActiveCell] = useState<CellCoord | null>(null)
  const [anchorCell, setAnchorCell] = useState<CellCoord | null>(null)
  const [selectedCellKeys, setSelectedCellKeys] = useState<string[]>([])
  const [clipboard, setClipboard] = useState<ClipboardSnapshot | null>(null)
  const [draftsByKey, setDraftsByKey] = useState<Record<string, DayDraft>>({})
  const [editorOpen, setEditorOpen] = useState(false)
  const [savingAll, setSavingAll] = useState(false)
  const [creatingGroup, setCreatingGroup] = useState(false)
  const [groupForm, setGroupForm] = useState<GroupCreateFormState>(createEmptyGroupForm())
  const [groupEditTarget, setGroupEditTarget] = useState<GroupDto | null>(null)
  const [groupEditSaving, setGroupEditSaving] = useState(false)
  const [groupEditDeleting, setGroupEditDeleting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    setLocalGroups(groups)
    const validGroupIds = new Set(groups.map((group) => group.id))
    setDraftsByKey((current) => {
      const next = Object.fromEntries(
        Object.entries(current).filter(([key]) => validGroupIds.has(parseCellKey(key).groupId))
      )
      return Object.keys(next).length === Object.keys(current).length ? current : next
    })
  }, [groups])

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

  function resolveInstructorIds(lesson: LessonEditorDto) {
    const explicit = (lesson.instructorIds || []).filter(Boolean)
    if (explicit.length) {
      return explicit
    }

    return (lesson.instructorNames || [])
      .map((name) => instructorIdByName.get(normalizeKey(name)) || null)
      .filter((value): value is string => Boolean(value))
  }

  const totalRangeDays = daysInRange(range.from, range.to)
  const visibleDates = useMemo(() => enumerateDates(range.from, range.to), [range.from, range.to])
  const rangeOverflow = totalRangeDays > MAX_DATES

  const filteredGroups = useMemo(
    () =>
      localGroups
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
    [groupSearch, localGroups]
  )

  const rowIndexByGroupId = useMemo(
    () => new Map(filteredGroups.map((group, index) => [group.id, index])),
    [filteredGroups]
  )
  const columnIndexByDate = useMemo(
    () => new Map(visibleDates.map((date, index) => [date, index])),
    [visibleDates]
  )

  function buildSourceDraft(group: GroupDto, date: string) {
    return buildDayDraft(group, date, resolveInstructorIds)
  }

  function getEffectiveDraft(group: GroupDto, date: string) {
    const key = cellKey(group.id, date)
    return draftsByKey[key] ? cloneDayDraft(draftsByKey[key]) : buildSourceDraft(group, date)
  }

  const activeGroup = activeCell
    ? filteredGroups.find((group) => group.id === activeCell.groupId) || null
    : filteredGroups[0] || null
  const activeDate = activeCell?.date || visibleDates[0] || range.from
  const activeDraft = activeGroup ? getEffectiveDraft(activeGroup, activeDate) : null
  const activeSelectionLabel = activeGroup
    ? `${activeGroup.code} • ${activeDate}`
    : t('lesson.noCellSelected')

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

  useEffect(() => {
    if (!filteredGroups.length || !visibleDates.length) {
      setActiveCell(null)
      setAnchorCell(null)
      setSelectedCellKeys([])
      setEditorOpen(false)
      return
    }

    if (!activeCell) {
      const nextCell = { groupId: filteredGroups[0].id, date: visibleDates[0] }
      setActiveCell(nextCell)
      setAnchorCell(nextCell)
      setSelectedCellKeys([cellKey(nextCell.groupId, nextCell.date)])
      return
    }

    const activeGroupVisible = filteredGroups.some((group) => group.id === activeCell.groupId)
    const activeDateVisible = visibleDates.includes(activeCell.date)
    if (!activeGroupVisible || !activeDateVisible) {
      const nextCell = { groupId: filteredGroups[0].id, date: visibleDates[0] }
      setActiveCell(nextCell)
      setAnchorCell(nextCell)
      setSelectedCellKeys([cellKey(nextCell.groupId, nextCell.date)])
      setEditorOpen(false)
    }
  }, [activeCell, filteredGroups, visibleDates])

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
  }, [activeCell, clipboard, filteredGroups, selectedBounds, visibleDates, draftsByKey])

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
      setEditorOpen(false)
      return
    }

    setAnchorCell(next)

    if (mode === 'toggle') {
      setSelectedCellKeys((current) =>
        current.includes(nextKey)
          ? current.filter((value) => value !== nextKey)
          : [...current, nextKey]
      )
      setEditorOpen(false)
      return
    }

    setSelectedCellKeys([nextKey])
    setEditorOpen(true)
  }

  function patchLocalGroup(updatedGroup: GroupDto) {
    setLocalGroups((current) => {
      const exists = current.some((group) => group.id === updatedGroup.id)
      if (!exists) {
        return [...current, updatedGroup]
      }
      return current.map((group) => (group.id === updatedGroup.id ? updatedGroup : group))
    })
  }

  function applyDraftToBuffer(nextDraft: DayDraft) {
    const sourceGroup = localGroups.find((group) => group.id === nextDraft.groupId)
    if (!sourceGroup) {
      return
    }

    const sourceDraft = buildSourceDraft(sourceGroup, nextDraft.date)
    const normalizedDraft = cloneDayDraft(nextDraft)

    setDraftsByKey((current) => {
      if (areDraftsEqual(sourceDraft, normalizedDraft)) {
        const { [normalizedDraft.key]: _removed, ...rest } = current
        return rest
      }

      return {
        ...current,
        [normalizedDraft.key]: normalizedDraft,
      }
    })

    setSuccess(`${t('lesson.draftUpdated')} ${nextDraft.groupCode} ${nextDraft.date}.`)
    setError('')
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
        const draft = getEffectiveDraft(group, date)
        cells.push({
          rowOffset: row - selectedBounds.minRow,
          columnOffset: column - selectedBounds.minColumn,
          lessons: getFilledSlots(draft).map((slot) => ({
            orderNumber: slot.orderNumber,
            title: slot.title,
            durationHours: slot.durationHours,
            note: slot.note,
            type: slot.type,
            businessTrip: slot.businessTrip,
            instructorIds: [...slot.instructorIds],
          })),
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
    setSuccess(`${t('lesson.copied')} ${snapshot.rows}x${snapshot.columns} ${t('lesson.cells')}`)
    setError('')
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

    const nextDrafts: Record<string, DayDraft> = {}

    for (const snapshot of clipboard.cells) {
      const targetRow = startRow + snapshot.rowOffset
      const targetColumn = startColumn + snapshot.columnOffset

      if (targetRow >= filteredGroups.length || targetColumn >= visibleDates.length) {
        continue
      }

      const targetGroup = filteredGroups[targetRow]
      const targetDate = visibleDates[targetColumn]
      const draft = getEffectiveDraft(targetGroup, targetDate)
      const nextSlots: DaySlotDraft[] = draft.slots.map((slot) => ({
        ...slot,
        title: '',
        durationHours: 2,
        note: '',
        type: slot.type || 'LECTURE',
        businessTrip: false,
        instructorIds: [],
      }))

      for (const lesson of snapshot.lessons.slice(0, SLOT_COUNT)) {
        const sourceSlot = draft.slots[lesson.orderNumber - 1] || createEmptySlotDraft(lesson.orderNumber)
        nextSlots[lesson.orderNumber - 1] = {
          ...sourceSlot,
          title: lesson.title,
          durationHours: lesson.durationHours,
          note: lesson.note,
          type: lesson.type,
          businessTrip: lesson.businessTrip,
          instructorIds: [...lesson.instructorIds] as string[],
        }
      }

      nextDrafts[draft.key] = {
        ...draft,
        ensureDay: draft.ensureDay || snapshot.lessons.length > 0,
        slots: nextSlots,
      }
    }

    Object.values(nextDrafts).forEach(applyDraftToBuffer)
    setSuccess(`${t('lesson.pasted')} ${clipboard.rows}x${clipboard.columns} ${t('lesson.cellsIntoDraft')}`)
  }

  async function syncDayDraft(draft: DayDraft) {
    const payload: DaySyncPayload = {
      groupId: draft.groupId,
      date: draft.date,
      ensureDay: draft.ensureDay || getFilledSlots(draft).length > 0,
      lessons: draft.slots.map((slot) => ({
        id: slot.lessonId || null,
        version: slot.version ?? null,
        orderNumber: slot.orderNumber,
        title: slot.title.trim(),
        durationHours: slot.durationHours,
        note: slot.note.trim() || null,
        type: slot.type,
        businessTrip: slot.businessTrip,
        instructorIds: [...slot.instructorIds],
      })),
    }

    return lessonsApi.syncDay(payload)
  }

  async function handleSaveAllChanges() {
    const dirtyDrafts = Object.values(draftsByKey)
    if (!dirtyDrafts.length) {
      return
    }

    for (const draft of dirtyDrafts) {
      const validationError = validateDraft(draft, t)
      if (validationError) {
        setError(validationError)
        setSuccess('')
        return
      }
    }

    setSavingAll(true)
    setError('')
    setSuccess('')

    try {
      const savedKeys: string[] = []

      for (const draft of dirtyDrafts.sort((left, right) => {
        if (left.groupCode === right.groupCode) {
          return left.date.localeCompare(right.date)
        }
        return left.groupCode.localeCompare(right.groupCode, 'ru')
      })) {
        const updatedGroup = await syncDayDraft(draft)
        patchLocalGroup(updatedGroup)
        savedKeys.push(draft.key)
      }

      setDraftsByKey((current) => {
        const next = { ...current }
        for (const key of savedKeys) {
          delete next[key]
        }
        return next
      })

      setSuccess(`${t('lesson.savedDays')} ${savedKeys.length}.`)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : t('lesson.errSaveBatch')
      )
    } finally {
      setSavingAll(false)
    }
  }

  async function handleCreateGroup() {
    if (!groupForm.code.trim()) {
      setError(t('lesson.codeRequired'))
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

      patchLocalGroup(createdGroup)
      setGroupForm(createEmptyGroupForm())
      setSuccess(`${t('lesson.group')} ${createdGroup.code} ${t('lesson.created')}`)

      const firstDate = visibleDates[0] || range.from
      const nextCell = { groupId: createdGroup.id, date: firstDate }
      setActiveCell(nextCell)
      setAnchorCell(nextCell)
      setSelectedCellKeys([cellKey(createdGroup.id, firstDate)])
      setEditorOpen(true)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : t('lesson.errCreate')
      )
    } finally {
      setCreatingGroup(false)
    }
  }

  async function handleUpdateGroupMeta(target: GroupDto, code: string, location: string, course: string) {
    if (!code.trim()) {
      setError(t('lesson.codeRequired'))
      return
    }
    setGroupEditSaving(true)
    setError('')
    setSuccess('')
    try {
      const updated = await groupsApi.update(target.id, {
        id: target.id,
        code: code.trim(),
        location: location.trim() || null,
        course: normalizeCourseValue(course),
        days: (target.days || []).map((day) => ({
          id: day.id || null,
          date: day.date,
          meta: day.meta,
          lessons: day.lessons,
        })),
      })
      patchLocalGroup(updated)
      setSuccess(`${t('lesson.group')} ${updated.code} ${t('lesson.updated')}`)
      setGroupEditTarget(null)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : t('lesson.errUpdate')
      )
    } finally {
      setGroupEditSaving(false)
    }
  }

  async function handleDeleteGroup(target: GroupDto) {
    if (!window.confirm(`${t('lesson.confirmDeleteA')} ${target.code} ${t('lesson.confirmDeleteB')}`)) {
      return
    }
    setGroupEditDeleting(true)
    setError('')
    setSuccess('')
    try {
      await groupsApi.delete(target.id)
      setLocalGroups((current) => current.filter((group) => group.id !== target.id))
      setDraftsByKey((current) => {
        const next: Record<string, DayDraft> = {}
        for (const [key, draft] of Object.entries(current)) {
          if (draft.groupId !== target.id) {
            next[key] = draft
          }
        }
        return next
      })
      if (activeCell?.groupId === target.id) {
        setActiveCell(null)
        setAnchorCell(null)
        setSelectedCellKeys([])
      }
      setSuccess(`${t('lesson.group')} ${target.code} ${t('lesson.deleted')}`)
      setGroupEditTarget(null)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : t('lesson.errDelete')
      )
    } finally {
      setGroupEditDeleting(false)
    }
  }

  const dirtyCount = Object.keys(draftsByKey).length
  // First column has a fixed minimum width — only date columns scale with zoom.
  const groupColumnWidth = 140
  const cellWidth = Math.round(118 * (zoom / 100))
  const cellHeight = Math.round(88 * (zoom / 100))

  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">{t('admin.sectionGridTitle')}</h3>
          <p className="mt-1 max-w-3xl text-sm text-muted-foreground">
            {t('lesson.gridDesc')}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={groupSearch}
              onChange={(event) => setGroupSearch(event.target.value)}
              placeholder={t('lesson.searchGroup')}
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
            />
            <ZoomIn className="h-4 w-4 text-muted-foreground" />
            <span className="min-w-10 text-right text-xs font-medium text-slate-700">{zoom}%</span>
          </div>

          <Button variant="outline" onClick={handleCopySelection} disabled={!selectedCellKeys.length}>
            <Copy className="mr-2 h-4 w-4" />
            {t('lesson.copy')}
          </Button>
          <Button variant="outline" onClick={() => void handlePasteSelection()} disabled={!clipboard || !activeCell}>
            <ClipboardPaste className="mr-2 h-4 w-4" />
            {t('lesson.paste')}
          </Button>
          <Button onClick={() => void handleSaveAllChanges()} disabled={!dirtyCount || savingAll}>
            <Save className="mr-2 h-4 w-4" />
            {savingAll ? t('lesson.saving') : `${t('lesson.saveAll')} (${dirtyCount})`}
          </Button>
        </div>
      </div>

      {rangeOverflow ? (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
          {t('lesson.showingFirst')} {MAX_DATES} {t('lesson.daysOfRange')}
        </div>
      ) : null}

      {error ? (
        <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      ) : null}

      {success ? (
        <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          {success}
        </div>
      ) : null}

      <div className="mt-5 grid gap-5 xl:grid-cols-[1fr_320px]">
        <div className="overflow-hidden rounded-2xl border border-border">
          <div className="overflow-auto">
            <div
              className="grid w-max min-w-full"
              style={{
                gridTemplateColumns: `${groupColumnWidth}px repeat(${visibleDates.length}, ${cellWidth}px)`,
              }}
            >
              <div className="sticky left-0 top-0 z-20 border-b border-r border-border bg-slate-100 px-4 py-3">
                <div className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                  {t('lesson.group')}
                </div>
                <div className="mt-1 text-sm text-slate-700">
                  {dirtyCount ? `${t('lesson.draftsCount')} ${dirtyCount}` : t('lesson.selectDayToEdit')}
                </div>
              </div>

              {visibleDates.map((date) => (
                <div
                  key={date}
                  className="sticky top-0 z-10 border-b border-r border-border bg-slate-50 px-2 py-3 text-center"
                >
                  <div className="text-sm font-semibold text-slate-950">
                    {new Date(date).toLocaleDateString(dateLocale, { day: '2-digit' })}
                  </div>
                  <div className="text-[11px] uppercase tracking-wide text-slate-500">
                    {new Date(date).toLocaleDateString(dateLocale, { weekday: 'short' })}
                  </div>
                  <div className="text-[11px] text-slate-500">
                    {new Date(date).toLocaleDateString(dateLocale, { month: 'short' })}
                  </div>
                </div>
              ))}

              {filteredGroups.map((group) => (
                <div key={group.id} className="contents">
                  <button
                    type="button"
                    onClick={() => {
                      if (canManageGroups) {
                        setGroupEditTarget(group)
                      }
                    }}
                    className={cn(
                      'sticky left-0 z-10 border-b border-r border-border bg-white px-2 py-3 text-left',
                      canManageGroups && 'hover:bg-slate-50 cursor-pointer transition-colors'
                    )}
                    title={canManageGroups ? t('lesson.clickToEditGroup') : undefined}
                  >
                    <div className="flex items-center gap-1">
                      <div className="text-sm font-semibold text-slate-950 truncate">{group.code}</div>
                      {canManageGroups ? (
                        <Pencil className="h-3 w-3 shrink-0 text-slate-400" />
                      ) : null}
                    </div>
                    <div className="mt-1 space-y-0.5 text-[11px] text-muted-foreground">
                      {group.location ? <div className="truncate">{group.location}</div> : null}
                      {group.course ? <div className="truncate">{t('lesson.course')} {group.course}</div> : null}
                    </div>
                  </button>

                  {visibleDates.map((date) => {
                    const key = cellKey(group.id, date)
                    const draft = getEffectiveDraft(group, date)
                    const lessons = draftToCellLessons(draft)
                    const dirty = Boolean(draftsByKey[key])
                    const selected = selectedCellKeys.includes(key)

                    return (
                      <button
                        key={key}
                        type="button"
                        onClick={(event) =>
                          activateCell(
                            { groupId: group.id, date },
                            event.shiftKey ? 'range' : event.ctrlKey || event.metaKey ? 'toggle' : 'replace'
                          )
                        }
                        className={cn(
                          'border-b border-r border-border px-2 py-2 text-left align-top transition-colors',
                          selected
                            ? 'bg-primary/10'
                            : dirty
                              ? 'bg-amber-50 hover:bg-amber-100'
                              : 'bg-white hover:bg-slate-50'
                        )}
                        style={{ minHeight: `${cellHeight}px` }}
                      >
                        <div className="mb-1 flex items-center justify-between gap-2">
                          <span className="text-[11px] font-medium text-slate-500">
                            {cellStatusLabel(draft.hasDay, lessons.length, dirty, t)}
                          </span>
                          {dirty ? (
                            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-medium text-amber-700">
                              {t('lesson.draft')}
                            </span>
                          ) : null}
                        </div>

                        {!lessons.length ? (
                          <div className="flex h-full items-center justify-center rounded-lg border border-dashed border-slate-200 px-2 text-center text-[11px] text-slate-400">
                            {t('lesson.empty')}
                          </div>
                        ) : (
                          <div className="space-y-1">
                            {lessons.slice(0, SLOT_COUNT).map((lesson) => (
                              <LessonCard
                                key={`${key}:${lesson.orderNumber}`}
                                lesson={{
                                  orderNumber: lesson.orderNumber,
                                  title: lesson.title,
                                  type: lesson.type,
                                  durationHours: lesson.durationHours,
                                  instructorNames: lesson.instructorNames,
                                }}
                                compact
                                dense
                              />
                            ))}
                          </div>
                        )}
                      </button>
                    )
                  })}
                </div>
              ))}
            </div>
          </div>
        </div>

        <aside className="space-y-4">
          <div className="rounded-2xl border border-border bg-slate-50 p-4">
            <div className="text-sm font-semibold text-slate-950">{t('lesson.currentCell')}</div>
            <div className="mt-1 text-sm text-muted-foreground">{activeSelectionLabel}</div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button
                variant="outline"
                onClick={() => setEditorOpen(true)}
                disabled={!activeGroup || !activeDate}
              >
                <UsersRound className="mr-2 h-4 w-4" />
                {t('lesson.openDay')}
              </Button>
              <Button
                variant="outline"
                onClick={() => {
                  if (!activeGroup || !activeDate) {
                    return
                  }
                  const emptyDraft = buildSourceDraft(activeGroup, activeDate)
                  emptyDraft.ensureDay = true
                  emptyDraft.slots = emptyDraft.slots.map((slot) => ({
                    ...slot,
                    title: '',
                    durationHours: 2,
                    note: '',
                    type: slot.type || 'LECTURE',
                    businessTrip: false,
                    instructorIds: [],
                  }))
                  applyDraftToBuffer(emptyDraft)
                }}
                disabled={!activeGroup || !activeDate}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {t('lesson.clearDay')}
              </Button>
            </div>
          </div>

          {canManageGroups ? (
            <div className="rounded-2xl border border-border bg-slate-50 p-4">
              <div className="text-sm font-semibold text-slate-950">{t('lesson.createGroup')}</div>
              <div className="mt-3 space-y-3">
                <Input
                  value={groupForm.code}
                  onChange={(event) =>
                    setGroupForm((current) => ({ ...current, code: event.target.value }))
                  }
                  placeholder={t('lesson.groupCode')}
                />
                <Input
                  value={groupForm.location}
                  onChange={(event) =>
                    setGroupForm((current) => ({ ...current, location: event.target.value }))
                  }
                  placeholder={t('lesson.location')}
                />
                <Input
                  value={groupForm.course}
                  onChange={(event) =>
                    setGroupForm((current) => ({ ...current, course: event.target.value }))
                  }
                  placeholder={t('lesson.course')}
                />
                <Button onClick={() => void handleCreateGroup()} disabled={creatingGroup}>
                  <Plus className="mr-2 h-4 w-4" />
                  {creatingGroup ? t('lesson.creating') : t('lesson.createGroup')}
                </Button>
              </div>
            </div>
          ) : null}
        </aside>
      </div>

      <DayEditorSheet
        open={editorOpen}
        onOpenChange={setEditorOpen}
        initialDraft={activeDraft}
        instructorOptions={instructorOptions}
        onApply={applyDraftToBuffer}
      />

      <GroupEditSheet
        open={Boolean(groupEditTarget)}
        onOpenChange={(open) => {
          if (!open) setGroupEditTarget(null)
        }}
        target={groupEditTarget}
        saving={groupEditSaving}
        deleting={groupEditDeleting}
        onSave={(target, code, location, course) =>
          void handleUpdateGroupMeta(target, code, location, course)
        }
        onDelete={(target) => void handleDeleteGroup(target)}
      />
    </section>
  )
}
