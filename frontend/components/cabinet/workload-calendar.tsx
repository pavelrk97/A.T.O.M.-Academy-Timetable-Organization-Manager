'use client'

import { useMemo, type ReactNode } from 'react'
import { BriefcaseBusiness, CalendarRange, ChevronLeft, ChevronRight, Clock3, ExternalLink } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { useI18n } from '@/lib/i18n'
import type { WorkloadCalendar } from '@/lib/types'

interface WorkloadCalendarProps {
  data: WorkloadCalendar
  onPeriodChange?: (from: string, to: string) => void
  onLessonClick?: (date: string, lessonId: string) => void
  actions?: ReactNode
  includeBusinessTrips?: boolean
  onIncludeBusinessTripsChange?: (value: boolean) => void
}

function shiftRange(from: string | null | undefined, to: string | null | undefined, days: number) {
  const fromDate = new Date(from || new Date().toISOString().slice(0, 10))
  const toDate = new Date(to || new Date().toISOString().slice(0, 10))
  fromDate.setDate(fromDate.getDate() + days)
  toDate.setDate(toDate.getDate() + days)
  return {
    from: fromDate.toISOString().slice(0, 10),
    to: toDate.toISOString().slice(0, 10),
  }
}

function formatRange(from: string | null | undefined, to: string | null | undefined, locale: string, noPeriod: string) {
  if (!from || !to) return noPeriod
  const left = new Date(from).toLocaleDateString(locale, {
    day: '2-digit',
    month: 'long',
  })
  const right = new Date(to).toLocaleDateString(locale, {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  })
  return `${left} - ${right}`
}

export function WorkloadCalendar({
  data,
  onPeriodChange,
  onLessonClick,
  actions,
  includeBusinessTrips,
  onIncludeBusinessTripsChange,
}: WorkloadCalendarProps) {
  const { t, lang } = useI18n()
  const dateLocale = lang === 'en' ? 'en-US' : 'ru-RU'
  const includeTrips = includeBusinessTrips ?? true

  const tripHours = useMemo(
    () =>
      data.days.reduce(
        (sum, day) =>
          sum +
          day.lessons
            .filter((lesson) => lesson.businessTrip)
            .reduce((daySum, lesson) => daySum + lesson.durationHours, 0),
        0
      ),
    [data]
  )

  const visibleDays = useMemo(() => {
    if (includeTrips) {
      return data.days
    }
    return data.days
      .map((day) => {
        const lessons = day.lessons.filter((lesson) => !lesson.businessTrip)
        return {
          ...day,
          lessons,
          totalHours: lessons.reduce((sum, lesson) => sum + lesson.durationHours, 0),
        }
      })
      .filter((day) => day.lessons.length > 0)
  }, [data, includeTrips])

  const visibleTotalHours = includeTrips ? data.totalHours : data.totalHours - tripHours

  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">{t('home.feature3Title')}</h3>
          <p className="text-sm text-muted-foreground">
            {t('workload.subtitle')}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {onIncludeBusinessTripsChange ? (
            <label className="flex items-center gap-2 rounded-xl border border-border bg-white px-3 py-2 text-sm text-slate-700">
              <Switch
                checked={includeTrips}
                onCheckedChange={onIncludeBusinessTripsChange}
              />
              {t('workload.includeTrips')}
            </label>
          ) : null}
          {actions}
          <Button
            variant="outline"
            size="icon"
            onClick={() => {
              const next = shiftRange(data.from, data.to, -14)
              onPeriodChange?.(next.from, next.to)
            }}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <div className="min-w-52 rounded-xl bg-slate-50 px-3 py-2 text-center text-sm text-slate-700">
            {formatRange(data.from, data.to, dateLocale, t('workload.noPeriod'))}
          </div>
          <Button
            variant="outline"
            size="icon"
            onClick={() => {
              const next = shiftRange(data.from, data.to, 14)
              onPeriodChange?.(next.from, next.to)
            }}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div className="grid gap-4 border-b border-border bg-slate-50 px-5 py-4 md:grid-cols-3">
        <MetricCard
          icon={<Clock3 className="h-5 w-5 text-primary" />}
          label={t('workload.totalHours')}
          value={visibleTotalHours}
        />
        <MetricCard
          icon={<CalendarRange className="h-5 w-5 text-primary" />}
          label={t('stats.teachingDays')}
          value={visibleDays.length}
        />
        <MetricCard
          icon={<BriefcaseBusiness className="h-5 w-5 text-primary" />}
          label={includeTrips ? t('workload.tripHoursIncluded') : t('workload.tripHoursExcluded')}
          value={tripHours}
        />
      </div>

      {visibleDays.length === 0 ? (
        <div className="px-6 py-12 text-center text-sm text-muted-foreground">
          {t('workload.noWorkload')}
        </div>
      ) : (
        <div className="divide-y divide-border">
          {visibleDays.map((day) => (
            <div key={day.dayId} className="px-5 py-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold capitalize text-slate-950">
                    {new Date(day.date).toLocaleDateString(dateLocale, {
                      weekday: 'long',
                      day: '2-digit',
                      month: 'long',
                    })}
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {day.lessons.length} {t('workload.lessonsCount')}
                  </div>
                </div>
                <div className="rounded-full bg-primary/10 px-3 py-1 text-sm font-medium text-primary">
                  {day.totalHours} {t('workload.hoursShort')}
                </div>
              </div>

              <div className="mt-4 space-y-2">
                {day.lessons.map((lesson) => (
                  <button
                    key={lesson.lessonId}
                    type="button"
                    onClick={() => onLessonClick?.(day.date, lesson.lessonId)}
                    className="flex w-full flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-slate-50 px-3 py-3 text-left transition-colors hover:border-primary/40 hover:bg-primary/5"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <div className="text-sm font-medium text-slate-950">{lesson.title}</div>
                        {lesson.businessTrip ? (
                          <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-medium text-amber-700">
                            {t('workload.tripBadge')}
                          </span>
                        ) : null}
                      </div>
                      <div className="text-xs text-muted-foreground">{lesson.groupCode}</div>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="rounded-full bg-white px-3 py-1 text-sm font-medium text-slate-700">
                        {lesson.durationHours} {t('workload.hoursShort')}
                      </div>
                      {onLessonClick ? (
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-primary">
                          {t('lesson.openDay')}
                          <ExternalLink className="h-3.5 w-3.5" />
                        </span>
                      ) : null}
                    </div>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function MetricCard({
  icon,
  label,
  value,
}: {
  icon: ReactNode
  label: string
  value: number
}) {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-border bg-white px-4 py-4">
      <div className="rounded-xl bg-primary/10 p-3">{icon}</div>
      <div>
        <div className="text-2xl font-semibold text-slate-950">{value}</div>
        <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
      </div>
    </div>
  )
}
