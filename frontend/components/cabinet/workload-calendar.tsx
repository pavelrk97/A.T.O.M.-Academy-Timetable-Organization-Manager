'use client'

import type { ReactNode } from 'react'
import { CalendarRange, ChevronLeft, ChevronRight, Clock3, ExternalLink } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { WorkloadCalendar } from '@/lib/types'

interface WorkloadCalendarProps {
  data: WorkloadCalendar
  onPeriodChange?: (from: string, to: string) => void
  onLessonClick?: (date: string, lessonId: string) => void
  actions?: ReactNode
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

function formatRange(from?: string | null, to?: string | null) {
  if (!from || !to) return 'Период не задан'
  const left = new Date(from).toLocaleDateString('ru-RU', {
    day: '2-digit',
    month: 'long',
  })
  const right = new Date(to).toLocaleDateString('ru-RU', {
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
}: WorkloadCalendarProps) {
  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Нагрузка по дням</h3>
          <p className="text-sm text-muted-foreground">
            По клику на занятие можно перейти в расписание на конкретный день.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
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
            {formatRange(data.from, data.to)}
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

      <div className="grid gap-4 border-b border-border bg-slate-50 px-5 py-4 md:grid-cols-2">
        <MetricCard
          icon={<Clock3 className="h-5 w-5 text-primary" />}
          label="Всего часов"
          value={data.totalHours}
        />
        <MetricCard
          icon={<CalendarRange className="h-5 w-5 text-primary" />}
          label="Дней с занятиями"
          value={data.days.length}
        />
      </div>

      {data.days.length === 0 ? (
        <div className="px-6 py-12 text-center text-sm text-muted-foreground">
          В выбранном диапазоне у инструктора нет нагрузки.
        </div>
      ) : (
        <div className="divide-y divide-border">
          {data.days.map((day) => (
            <div key={day.dayId} className="px-5 py-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold capitalize text-slate-950">
                    {new Date(day.date).toLocaleDateString('ru-RU', {
                      weekday: 'long',
                      day: '2-digit',
                      month: 'long',
                    })}
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {day.lessons.length} занятий
                  </div>
                </div>
                <div className="rounded-full bg-primary/10 px-3 py-1 text-sm font-medium text-primary">
                  {day.totalHours} ч.
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
                      <div className="text-sm font-medium text-slate-950">{lesson.title}</div>
                      <div className="text-xs text-muted-foreground">{lesson.groupCode}</div>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="rounded-full bg-white px-3 py-1 text-sm font-medium text-slate-700">
                        {lesson.durationHours} ч.
                      </div>
                      {onLessonClick ? (
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-primary">
                          Открыть день
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
