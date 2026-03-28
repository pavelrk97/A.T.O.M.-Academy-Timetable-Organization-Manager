'use client'

import { BellRing, CalendarDays, Clock3, Rows3 } from 'lucide-react'

interface DashboardStatsProps {
  totalHours: number
  teachingDays: number
  notificationsCount: number
  upcomingLessonsCount: number
  periodLabel?: string
}

export function DashboardStats({
  totalHours,
  teachingDays,
  notificationsCount,
  upcomingLessonsCount,
  periodLabel = 'текущий диапазон',
}: DashboardStatsProps) {
  const items = [
    {
      label: 'Часы нагрузки',
      value: totalHours,
      icon: <Clock3 className="h-5 w-5 text-primary" />,
      helper: periodLabel,
    },
    {
      label: 'Дней с занятиями',
      value: teachingDays,
      icon: <CalendarDays className="h-5 w-5 text-primary" />,
      helper: periodLabel,
    },
    {
      label: 'Уведомления',
      value: notificationsCount,
      icon: <BellRing className="h-5 w-5 text-primary" />,
      helper: 'лента событий',
    },
    {
      label: 'Ближайшие занятия',
      value: upcomingLessonsCount,
      icon: <Rows3 className="h-5 w-5 text-primary" />,
      helper: 'видно в сетке',
    },
  ]

  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {items.map((item) => (
        <section
          key={item.label}
          className="rounded-2xl border border-border bg-white px-5 py-4 shadow-sm"
        >
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                {item.label}
              </div>
              <div className="mt-3 text-3xl font-semibold text-slate-950">
                {item.value}
              </div>
              <div className="mt-1 text-sm text-muted-foreground">{item.helper}</div>
            </div>
            <div className="rounded-xl bg-primary/10 p-3">{item.icon}</div>
          </div>
        </section>
      ))}
    </div>
  )
}
