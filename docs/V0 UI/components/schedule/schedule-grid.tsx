'use client'

import { useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, Clock3, GraduationCap, MapPin } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { ScheduleGridData, ScheduleGridLessonCell } from '@/lib/types'
import { limitGrid } from '@/lib/schedule'

interface ScheduleGridProps {
  data: ScheduleGridData
  onCellClick?: (
    groupCode: string,
    date: string,
    lessons: ScheduleGridLessonCell[],
    location?: string | null
  ) => void
  highlightInstructor?: string
  compact?: boolean
}

function formatDateLabel(date: string) {
  const value = new Date(date)
  return {
    day: value.toLocaleDateString('ru-RU', { day: '2-digit' }),
    weekday: value.toLocaleDateString('ru-RU', { weekday: 'short' }),
    month: value.toLocaleDateString('ru-RU', { month: 'short' }),
  }
}

function lessonTypeLabel(type?: string | null) {
  if (type === 'LECTURE') return 'Лекция'
  if (type === 'SEMINAR') return 'Семинар'
  if (type === 'LAB') return 'Лабораторная'
  if (type === 'PRACTICE') return 'Практика'
  if (type === 'SELF_STUDY') return 'Самостоятельно'
  if (type === 'ASSESSMENT') return 'Контроль'
  return 'Занятие'
}

function typeBadgeClass(type?: string | null) {
  if (type === 'LECTURE') return 'bg-primary/10 text-primary'
  if (type === 'SEMINAR') return 'bg-sky-100 text-sky-700'
  if (type === 'LAB') return 'bg-cyan-100 text-cyan-700'
  if (type === 'PRACTICE') return 'bg-emerald-100 text-emerald-700'
  if (type === 'SELF_STUDY') return 'bg-slate-200 text-slate-700'
  if (type === 'ASSESSMENT') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-700'
}

export function ScheduleGrid({
  data,
  onCellClick,
  highlightInstructor,
  compact = false,
}: ScheduleGridProps) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const { data: limitedData, meta } = limitGrid(data)
  const normalizedInstructor = (highlightInstructor || '').trim().toLowerCase()

  const scrollBy = (offset: number) => {
    viewportRef.current?.scrollBy({ left: offset, behavior: 'smooth' })
  }

  if (!limitedData.groups.length || !limitedData.dates.length) {
    return (
      <div className="flex min-h-[320px] items-center justify-center rounded-2xl border border-dashed border-border bg-white text-sm text-muted-foreground">
        По выбранным фильтрам занятий нет.
      </div>
    )
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
      <div className="flex items-center justify-between gap-4 border-b border-border bg-slate-50/80 px-4 py-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-950">Таблица расписания</h3>
          <p className="text-xs text-muted-foreground">
            Показано {meta.shownGroups} из {meta.totalGroups} групп и {meta.shownDates} из{' '}
            {meta.totalDates} дат.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => scrollBy(-420)}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" onClick={() => scrollBy(420)}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div ref={viewportRef} className="overflow-auto">
        <div
          className="grid min-w-max"
          style={{
            gridTemplateColumns: `240px repeat(${limitedData.dates.length}, minmax(${compact ? '170px' : '210px'}, 1fr))`,
          }}
        >
          <div className="sticky-corner border-b border-r border-border bg-slate-100 px-4 py-3">
            <div className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
              Группа
            </div>
            <div className="mt-1 text-sm text-slate-700">Даты и занятия</div>
          </div>

          {limitedData.dates.map((date) => {
            const label = formatDateLabel(date)
            return (
              <div
                key={date}
                className="sticky-header border-b border-r border-border bg-slate-50 px-3 py-3 text-center"
              >
                <div className="text-lg font-semibold text-slate-950">{label.day}</div>
                <div className="text-xs uppercase tracking-wide text-slate-500">{label.weekday}</div>
                <div className="text-xs text-slate-500">{label.month}</div>
              </div>
            )
          })}

          {limitedData.groups.map((group) => (
            <FragmentRow
              key={group.groupId}
              group={group}
              compact={compact}
              selectedKey={selectedKey}
              onSelect={(date, lessons) => {
                const key = `${group.groupId}:${date}`
                setSelectedKey(key)
                onCellClick?.(group.groupCode, date, lessons, group.location)
              }}
              highlightInstructor={normalizedInstructor}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

function FragmentRow({
  group,
  compact,
  selectedKey,
  onSelect,
  highlightInstructor,
}: {
  group: ScheduleGridData['groups'][number]
  compact: boolean
  selectedKey: string | null
  onSelect: (date: string, lessons: ScheduleGridLessonCell[]) => void
  highlightInstructor: string
}) {
  return (
    <>
      <div className="sticky-column border-b border-r border-border bg-white px-4 py-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="text-sm font-semibold text-slate-950">{group.groupCode}</div>
            <div className="mt-1 space-y-1 text-xs text-muted-foreground">
              {group.location ? (
                <div className="inline-flex items-center gap-1">
                  <MapPin className="h-3.5 w-3.5" />
                  {group.location}
                </div>
              ) : null}
              {group.course ? (
                <div className="inline-flex items-center gap-1">
                  <GraduationCap className="h-3.5 w-3.5" />
                  Курс {group.course}
                </div>
              ) : null}
            </div>
          </div>
        </div>
      </div>

      {group.days.map((day) => {
        const key = `${group.groupId}:${day.date}`
        const hasLessons = day.lessons.length > 0
        const cellHighlighted =
          highlightInstructor &&
          day.lessons.some((lesson) =>
            (lesson.instructorNames || []).some((name) =>
              name.toLowerCase().includes(highlightInstructor)
            )
          )

        return (
          <button
            key={key}
            type="button"
            onClick={() => onSelect(day.date, day.lessons)}
            className={cn(
              'schedule-cell border-b border-r border-border px-3 py-3 text-left transition-colors',
              selectedKey === key
                ? 'bg-primary/10'
                : cellHighlighted
                  ? 'bg-sky-50'
                  : 'bg-white hover:bg-slate-50'
            )}
          >
            {!hasLessons ? (
              <div className="flex min-h-[88px] items-center justify-center rounded-xl border border-dashed border-slate-200 text-xs text-slate-400">
                Пусто
              </div>
            ) : (
              <div className="space-y-2">
                {day.lessons.map((lesson) => (
                  <div
                    key={lesson.lessonId}
                    className={cn(
                      'rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 shadow-[inset_0_1px_0_rgba(255,255,255,0.8)]',
                      compact ? 'space-y-1' : 'space-y-2'
                    )}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="text-xs font-semibold text-primary">
                          № {lesson.orderNumber || '—'}
                        </div>
                        <div className="line-clamp-3 text-sm font-medium text-slate-950">
                          {lesson.title || 'Без названия'}
                        </div>
                      </div>
                      <span className={cn('shrink-0 rounded-full px-2 py-1 text-[11px] font-medium', typeBadgeClass(lesson.type))}>
                        {lessonTypeLabel(lesson.type)}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-600">
                      <span className="inline-flex items-center gap-1">
                        <Clock3 className="h-3.5 w-3.5" />
                        {lesson.durationHours} ч
                      </span>
                      {!compact && (lesson.instructorNames || []).length > 0 ? (
                        <span className="truncate">
                          {(lesson.instructorNames || []).join(', ')}
                        </span>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </button>
        )
      })}
    </>
  )
}
