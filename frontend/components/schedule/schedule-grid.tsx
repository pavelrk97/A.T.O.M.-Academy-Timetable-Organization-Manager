'use client'

import { useEffect, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, GraduationCap, MapPin } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useI18n } from '@/lib/i18n'
import type { ScheduleGridData, ScheduleGridLessonCell } from '@/lib/types'
import { limitGrid } from '@/lib/schedule'
import { LessonCard } from '@/components/schedule/lesson-card'

function useIsMobile(maxWidth = 639) {
  const [isMobile, setIsMobile] = useState(false)
  useEffect(() => {
    if (typeof window === 'undefined') return
    const query = window.matchMedia(`(max-width: ${maxWidth}px)`)
    const update = () => setIsMobile(query.matches)
    update()
    query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [maxWidth])
  return isMobile
}

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
  zoom?: number
}

function formatDateLabel(date: string, locale: string) {
  const value = new Date(date)
  return {
    day: value.toLocaleDateString(locale, { day: '2-digit' }),
    weekday: value.toLocaleDateString(locale, { weekday: 'short' }),
    month: value.toLocaleDateString(locale, { month: 'short' }),
  }
}

export function ScheduleGrid({
  data,
  onCellClick,
  highlightInstructor,
  compact = false,
  zoom = 100,
}: ScheduleGridProps) {
  const { t, lang } = useI18n()
  const dateLocale = lang === 'en' ? 'en-US' : 'ru-RU'
  const viewportRef = useRef<HTMLDivElement>(null)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const { data: limitedData, meta } = limitGrid(data)
  const normalizedInstructor = (highlightInstructor || '').trim().toLowerCase()
  const isMobile = useIsMobile()
  // Group column has a fixed minimum width (only enough to fit "гр. NNN") and does NOT scale with zoom.
  // Only date columns scale, so the user keeps maximum schedule area on small screens.
  const groupColumnWidth = isMobile ? 72 : 140
  const columnBaseWidth = isMobile ? 124 : compact ? 118 : 136
  const columnWidth = Math.round(columnBaseWidth * (zoom / 100))

  const scrollBy = (offset: number) => {
    viewportRef.current?.scrollBy({ left: offset, behavior: 'smooth' })
  }

  if (!limitedData.groups.length || !limitedData.dates.length) {
    return (
      <div className="flex min-h-[320px] items-center justify-center rounded-2xl border border-dashed border-border bg-white text-sm text-muted-foreground">
        {t('grid.noLessonsFilters')}
      </div>
    )
  }

  return (
    <div className="max-w-full overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
      <div className="flex items-center justify-between gap-4 border-b border-border bg-slate-50/80 px-4 py-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-950">{t('grid.tableTitle')}</h3>
          <p className="text-xs text-muted-foreground">
            {t('grid.showing')} {meta.shownGroups} {t('grid.of')} {meta.totalGroups} {t('grid.groupsWord')} {t('grid.and')} {meta.shownDates} {t('grid.of')}{' '}
            {meta.totalDates} {t('grid.datesWord')}
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

      <div ref={viewportRef} className="max-w-full overflow-auto">
        <div
          className="grid w-max min-w-full"
          style={{
            gridTemplateColumns: `${groupColumnWidth}px repeat(${limitedData.dates.length}, ${columnWidth}px)`,
          }}
        >
          <div className="sticky left-0 top-0 z-20 border-b border-r border-border bg-slate-100 px-2 py-3">
            <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500">
              {t('lesson.group')}
            </div>
            <div className="mt-1 text-xs text-slate-700">{t('grid.dates')}</div>
          </div>

          {limitedData.dates.map((date) => {
            const label = formatDateLabel(date, dateLocale)
            return (
              <div
                key={date}
                className="sticky top-0 z-10 border-b border-r border-border bg-slate-50 px-3 py-3 text-center"
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
              isMobile={isMobile}
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
  isMobile,
  selectedKey,
  onSelect,
  highlightInstructor,
}: {
  group: ScheduleGridData['groups'][number]
  compact: boolean
  isMobile: boolean
  selectedKey: string | null
  onSelect: (date: string, lessons: ScheduleGridLessonCell[]) => void
  highlightInstructor: string
}) {
  const { t } = useI18n()
  return (
    <>
      <div
        className={cn(
          'sticky left-0 z-10 border-b border-r border-border bg-white py-3',
          isMobile ? 'px-2' : 'px-3'
        )}
      >
        <div className="min-w-0">
          <div className="text-sm font-semibold text-slate-950 truncate">{group.groupCode}</div>
          <div className="mt-1 space-y-1 text-[11px] text-muted-foreground">
            {group.location ? (
              <div className="inline-flex items-center gap-1">
                <MapPin className="h-3 w-3 shrink-0" />
                <span className="truncate">{group.location}</span>
              </div>
            ) : null}
            {group.course ? (
              <div className="inline-flex items-center gap-1">
                <GraduationCap className="h-3 w-3 shrink-0" />
                <span className="truncate">{t('lesson.course')} {group.course}</span>
              </div>
            ) : null}
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
              'border-b border-r border-border px-2 py-2 text-left align-top transition-colors',
              selectedKey === key
                ? 'bg-primary/10'
                : cellHighlighted
                  ? 'bg-sky-50'
                  : 'bg-white hover:bg-slate-50'
            )}
          >
            {!hasLessons ? (
              <div
                className={cn(
                  'flex items-center justify-center rounded-lg border border-dashed border-slate-200 px-2 text-center text-[11px] text-slate-400',
                  isMobile ? 'min-h-[80px]' : 'min-h-[56px]'
                )}
              >
                {t('lesson.empty')}
              </div>
            ) : (
              <div className="space-y-1.5">
                {day.lessons.map((lesson) => (
                  <LessonCard
                    key={lesson.lessonId}
                    lesson={{
                      orderNumber: lesson.orderNumber,
                      title: lesson.title,
                      type: lesson.type,
                      durationHours: lesson.durationHours,
                      instructorNames: lesson.instructorNames,
                    }}
                    compact={compact}
                  />
                ))}
              </div>
            )}
          </button>
        )
      })}
    </>
  )
}
