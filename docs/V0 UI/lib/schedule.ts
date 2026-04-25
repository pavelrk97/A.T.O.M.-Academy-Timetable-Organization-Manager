import type {
  GroupDto,
  ScheduleEntry,
  ScheduleGridData,
  ScheduleGridDayCell,
  ScheduleGridGroupRow,
  ScheduleGridLessonCell,
} from './types'

function sortDates(dates: Iterable<string>) {
  return Array.from(new Set(dates)).sort((left, right) => left.localeCompare(right))
}

function mapEntryToLesson(entry: ScheduleEntry): ScheduleGridLessonCell {
  return {
    lessonId: entry.lessonId,
    version: entry.version,
    orderNumber: entry.orderNumber,
    title: entry.title,
    type: entry.type,
    durationHours: entry.durationHours,
    note: entry.note,
    instructorNames: entry.instructorNames || [],
  }
}

export function buildGridFromEntries(entries: ScheduleEntry[]): ScheduleGridData {
  const dates = sortDates(entries.map((entry) => entry.date))
  const groupsMap = new Map<string, { groupId: string; groupCode: string; location?: string | null; days: Map<string, ScheduleGridLessonCell[]> }>()

  for (const entry of entries) {
    const existing = groupsMap.get(entry.groupId) || {
      groupId: entry.groupId,
      groupCode: entry.groupCode,
      location: entry.location,
      days: new Map<string, ScheduleGridLessonCell[]>(),
    }
    const dayLessons = existing.days.get(entry.date) || []
    dayLessons.push(mapEntryToLesson(entry))
    dayLessons.sort((left, right) => (left.orderNumber || 0) - (right.orderNumber || 0))
    existing.days.set(entry.date, dayLessons)
    groupsMap.set(entry.groupId, existing)
  }

  const groups: ScheduleGridGroupRow[] = Array.from(groupsMap.values())
    .sort((left, right) => left.groupCode.localeCompare(right.groupCode, 'ru'))
    .map((group) => ({
      groupId: group.groupId,
      groupCode: group.groupCode,
      location: group.location,
      course: null,
      days: dates.map(
        (date): ScheduleGridDayCell => ({
          date,
          lessons: group.days.get(date) || [],
        })
      ),
    }))

  return { dates, groups }
}

export function buildGridFromGroups(groups: GroupDto[]): ScheduleGridData {
  const dates = sortDates(
    groups.flatMap((group) => (group.days || []).map((day) => day.date))
  )

  const rows: ScheduleGridGroupRow[] = groups
    .slice()
    .sort((left, right) => left.code.localeCompare(right.code, 'ru'))
    .map((group) => ({
      groupId: group.id,
      groupCode: group.code,
      location: group.location,
      course: group.course,
      days: dates.map((date) => {
        const day = (group.days || []).find((item) => item.date === date)
        return {
          dayId: day?.id,
          date,
          lessons: (day?.lessons || [])
            .slice()
            .sort((left, right) => (left.orderNumber || 0) - (right.orderNumber || 0))
            .map((lesson) => ({
              lessonId: lesson.id,
              version: lesson.version,
              orderNumber: lesson.orderNumber,
              title: lesson.title,
              type: lesson.type,
              durationHours: lesson.durationHours,
              note: lesson.note,
              instructorNames: lesson.instructorNames || lesson.lecturers || [],
            })),
        }
      }),
    }))

  return { dates, groups: rows }
}

export function filterGridByInstructor(
  grid: ScheduleGridData,
  instructorQuery: string
): ScheduleGridData {
  const normalizedQuery = instructorQuery.trim().toLowerCase()
  if (!normalizedQuery) {
    return grid
  }

  const filteredGroups = grid.groups
    .map((group) => ({
      ...group,
      days: group.days.map((day) => ({
        ...day,
        lessons: day.lessons.filter((lesson) =>
          lesson.instructorNames.some((name) =>
            name.toLowerCase().includes(normalizedQuery)
          )
        ),
      })),
    }))
    .filter((group) => group.days.some((day) => day.lessons.length > 0))

  const visibleDates = grid.dates.filter((date, index) =>
    filteredGroups.some((group) => group.days[index]?.lessons.length > 0)
  )

  const compactGroups = filteredGroups.map((group) => ({
    ...group,
    days: group.days.filter((day) => visibleDates.includes(day.date)),
  }))

  return {
    dates: visibleDates,
    groups: compactGroups,
  }
}

export function limitGrid(
  grid: ScheduleGridData,
  limits = { maxGroups: 20, maxDates: 100, maxLessonsPerCell: 8 }
) {
  const limitedDates = grid.dates.slice(0, limits.maxDates)
  const limitedGroups = grid.groups.slice(0, limits.maxGroups).map((group) => ({
    ...group,
    days: group.days
      .filter((day) => limitedDates.includes(day.date))
      .map((day) => ({
        ...day,
        lessons: day.lessons.slice(0, limits.maxLessonsPerCell),
      })),
  }))

  return {
    data: {
      dates: limitedDates,
      groups: limitedGroups,
    },
    meta: {
      totalGroups: grid.groups.length,
      shownGroups: limitedGroups.length,
      totalDates: grid.dates.length,
      shownDates: limitedDates.length,
    },
  }
}

export function toDateInputValue(value?: string | null) {
  return value ? value.slice(0, 10) : ''
}
