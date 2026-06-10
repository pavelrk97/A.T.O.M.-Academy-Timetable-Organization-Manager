'use client'

import { BookOpenText, Clock3, MapPin, NotebookText, UserRound, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/lib/i18n'
import type { ScheduleGridLessonCell } from '@/lib/types'

interface LessonDetailsProps {
  date: string
  groupCode: string
  location?: string | null
  lessons: ScheduleGridLessonCell[]
  onClose: () => void
}

function formatDate(date: string, locale: string) {
  return new Date(date).toLocaleDateString(locale, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

function lessonTypeLabel(type: string | null | undefined, t: (key: string) => string) {
  if (type === 'LECTURE') return t('lesson.typeLecture')
  if (type === 'SELF_STUDY') return t('lesson.typeSelfStudy')
  if (type === 'ASSESSMENT') return t('lesson.typeAssessment')
  return t('lesson.typeDefault')
}

export function LessonDetails({
  date,
  groupCode,
  location,
  lessons,
  onClose,
}: LessonDetailsProps) {
  const { t, lang } = useI18n()
  const dateLocale = lang === 'en' ? 'en-US' : 'ru-RU'

  return (
    <div className="flex h-full flex-col bg-white">
      <div className="flex items-start justify-between border-b border-border px-5 py-4">
        <div>
          <div className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
            {t('details.title')}
          </div>
          <h3 className="mt-1 text-lg font-semibold text-slate-950">{groupCode}</h3>
          <p className="mt-1 text-sm text-muted-foreground">{formatDate(date, dateLocale)}</p>
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
            {t('details.noLessons')}
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
                      {t('details.pair')} {lesson.orderNumber || '—'}
                    </div>
                    <h4 className="mt-1 text-base font-semibold text-slate-950">
                      {lesson.title || t('lesson.untitled')}
                    </h4>
                  </div>
                  <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">
                    {lessonTypeLabel(lesson.type, t)}
                  </span>
                </div>

                <div className="mt-4 grid gap-3 text-sm text-slate-700">
                  <div className="flex items-center gap-2">
                    <Clock3 className="h-4 w-4 text-primary" />
                    {lesson.durationHours} {t('workload.hoursShort')}
                  </div>

                  <div className="flex items-start gap-2">
                    <UserRound className="mt-0.5 h-4 w-4 text-primary" />
                    <div className="space-y-1">
                      <div className="font-medium text-slate-900">{t('details.teachers')}</div>
                      <div className="text-sm text-muted-foreground">
                        {lesson.instructorNames.length > 0
                          ? lesson.instructorNames.join(', ')
                          : t('details.notSpecified')}
                      </div>
                    </div>
                  </div>

                  {lesson.note ? (
                    <div className="flex items-start gap-2">
                      <NotebookText className="mt-0.5 h-4 w-4 text-primary" />
                      <div>
                        <div className="font-medium text-slate-900">{t('details.note')}</div>
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
              {lessons.length} {t('workload.lessonsCount')}
            </div>
            <div className="font-medium text-slate-950">
              {lessons.reduce((sum, lesson) => sum + lesson.durationHours, 0)} {t('workload.hoursShort')}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
