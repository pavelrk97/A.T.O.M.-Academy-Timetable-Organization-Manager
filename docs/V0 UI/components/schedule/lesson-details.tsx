'use client'

import { BookOpenText, Clock3, MapPin, NotebookText, UserRound, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { ScheduleGridLessonCell } from '@/lib/types'

interface LessonDetailsProps {
  date: string
  groupCode: string
  location?: string | null
  lessons: ScheduleGridLessonCell[]
  onClose: () => void
}

function formatDate(date: string) {
  return new Date(date).toLocaleDateString('ru-RU', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

function lessonTypeLabel(type?: string | null) {
  if (type === 'LECTURE') return 'Лекция'
  if (type === 'SELF_STUDY') return 'Самостоятельная работа'
  if (type === 'ASSESSMENT') return 'Контроль'
  return 'Занятие'
}

export function LessonDetails({
  date,
  groupCode,
  location,
  lessons,
  onClose,
}: LessonDetailsProps) {
  return (
    <div className="flex h-full flex-col bg-white">
      <div className="flex items-start justify-between border-b border-border px-5 py-4">
        <div>
          <div className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
            Детали дня
          </div>
          <h3 className="mt-1 text-lg font-semibold text-slate-950">{groupCode}</h3>
          <p className="mt-1 text-sm text-muted-foreground">{formatDate(date)}</p>
          {location ? (
            <p className="mt-1 inline-flex items-center gap-1 text-xs text-muted-foreground">
              <MapPin className="h-3.5 w-3.5" />
              {location}
            </p>
          ) : null}
        </div>
        <Button variant="ghost" size="icon" onClick={onClose}>
          <X className="h-4 w-4" />
        </Button>
      </div>

      <div className="flex-1 overflow-auto px-5 py-4">
        {lessons.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-border bg-slate-50 px-4 py-8 text-center text-sm text-muted-foreground">
            На эту дату занятий нет.
          </div>
        ) : (
          <div className="space-y-4">
            {lessons.map((lesson) => (
              <section
                key={lesson.lessonId}
                className="rounded-2xl border border-border bg-slate-50 p-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="text-xs font-semibold text-primary">
                      Пара {lesson.orderNumber || '—'}
                    </div>
                    <h4 className="mt-1 text-base font-semibold text-slate-950">
                      {lesson.title || 'Без названия'}
                    </h4>
                  </div>
                  <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">
                    {lessonTypeLabel(lesson.type)}
                  </span>
                </div>

                <div className="mt-4 grid gap-3 text-sm text-slate-700">
                  <div className="flex items-center gap-2">
                    <Clock3 className="h-4 w-4 text-primary" />
                    {lesson.durationHours} ч.
                  </div>

                  <div className="flex items-start gap-2">
                    <UserRound className="mt-0.5 h-4 w-4 text-primary" />
                    <div className="space-y-1">
                      <div className="font-medium text-slate-900">Преподаватели</div>
                      <div className="text-sm text-muted-foreground">
                        {lesson.instructorNames.length > 0
                          ? lesson.instructorNames.join(', ')
                          : 'Не указаны'}
                      </div>
                    </div>
                  </div>

                  {lesson.note ? (
                    <div className="flex items-start gap-2">
                      <NotebookText className="mt-0.5 h-4 w-4 text-primary" />
                      <div>
                        <div className="font-medium text-slate-900">Примечание</div>
                        <div className="text-sm text-muted-foreground">{lesson.note}</div>
                      </div>
                    </div>
                  ) : null}
                </div>
              </section>
            ))}
          </div>
        )}
      </div>

      {lessons.length > 0 ? (
        <div className="border-t border-border bg-slate-50 px-5 py-4">
          <div className="flex items-center justify-between text-sm">
            <div className="inline-flex items-center gap-2 text-muted-foreground">
              <BookOpenText className="h-4 w-4 text-primary" />
              {lessons.length} занятий
            </div>
            <div className="font-medium text-slate-950">
              {lessons.reduce((sum, lesson) => sum + lesson.durationHours, 0)} ч.
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
