'use client'

import Link from 'next/link'
import { BellRing, CalendarRange, ChevronRight, Info, NotebookTabs } from 'lucide-react'
import type { Notification } from '@/lib/types'
import { Button } from '@/components/ui/button'

interface NotificationsFeedProps {
  notifications: Notification[]
  maxItems?: number
}

function formatDate(date: string) {
  return new Date(date).toLocaleDateString('ru-RU', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  })
}

function buildNotificationHref(notification: Notification) {
  const params = new URLSearchParams()
  params.set('from', notification.date)
  params.set('to', notification.date)
  params.set('onlyMy', 'true')
  return `/schedule?${params.toString()}`
}

export function NotificationsFeed({
  notifications,
  maxItems = 10,
}: NotificationsFeedProps) {
  const items = notifications.slice(0, maxItems)

  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-border px-5 py-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Уведомления</h3>
          <p className="text-sm text-muted-foreground">
            Пока это коллекция ссылок на дни с занятиями. Позже сюда можно добавить почту и пуши.
          </p>
        </div>
        <div className="rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
          {notifications.length}
        </div>
      </div>

      {items.length === 0 ? (
        <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
          <BellRing className="mb-3 h-8 w-8 text-slate-300" />
          <div className="text-sm font-medium text-slate-950">Уведомлений нет</div>
          <div className="mt-1 text-sm text-muted-foreground">
            Как только у инструктора появятся занятия в выбранном периоде, здесь появятся ссылки.
          </div>
        </div>
      ) : (
        <div className="divide-y divide-border">
          {items.map((notification, index) => (
            <article key={`${notification.dayId || notification.date}-${index}`} className="px-5 py-4">
              <div className="flex items-start gap-3">
                <div className="mt-1 rounded-xl bg-primary/10 p-2 text-primary">
                  <Info className="h-4 w-4" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-medium uppercase tracking-wide text-slate-600">
                      {notification.type}
                    </span>
                    <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                      <CalendarRange className="h-3.5 w-3.5" />
                      {formatDate(notification.date)}
                    </span>
                  </div>

                  <p className="mt-2 text-sm text-slate-800">{notification.message}</p>

                  <div className="mt-3 flex flex-wrap gap-2">
                    <Link href={buildNotificationHref(notification)}>
                      <Button variant="outline" size="sm">
                        Открыть день в расписании
                        <ChevronRight className="ml-2 h-4 w-4" />
                      </Button>
                    </Link>
                    <Link href={`/cabinet?tab=schedule&from=${notification.date}&to=${notification.date}`}>
                      <Button variant="ghost" size="sm">
                        <NotebookTabs className="mr-2 h-4 w-4" />
                        Открыть в ЛК
                      </Button>
                    </Link>
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
