'use client'

import { Clock3, UserRound } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useI18n } from '@/lib/i18n'

export type LessonCardType = 'LECTURE' | 'SELF_STUDY' | 'ASSESSMENT' | string | null | undefined

export interface LessonCardData {
  orderNumber?: number | null
  title: string
  type?: LessonCardType
  durationHours: number
  instructorNames?: string[]
}

interface LessonCardProps {
  lesson: LessonCardData
  /** Compact mode hides instructor list and shrinks paddings (used for narrow editor cells). */
  compact?: boolean
  /** Even tighter mode for the admin grid where many rows fit on screen. */
  dense?: boolean
}

export function lessonTypeLabel(type: LessonCardType, t: (key: string) => string): string {
  if (type === 'LECTURE') return t('lesson.typeLecture')
  if (type === 'SELF_STUDY') return t('lesson.typeSelfStudy')
  if (type === 'ASSESSMENT') return t('lesson.typeAssessment')
  return t('lesson.typeDefault')
}

export function lessonTypeBadgeClass(type?: LessonCardType): string {
  if (type === 'LECTURE') return 'bg-primary/10 text-primary'
  if (type === 'SELF_STUDY') return 'bg-slate-200 text-slate-700'
  if (type === 'ASSESSMENT') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-700'
}

export function LessonCard({ lesson, compact = false, dense = false }: LessonCardProps) {
  const { t } = useI18n()
  const instructors = lesson.instructorNames || []
  const padding = dense ? 'px-2 py-1.5' : 'px-2 py-2'

  return (
    <div
      className={cn(
        'rounded-lg border border-slate-200 bg-slate-50 shadow-[inset_0_1px_0_rgba(255,255,255,0.8)] flex flex-col gap-1',
        padding
      )}
    >
      <div className="flex items-center gap-1.5">
        {lesson.orderNumber ? (
          <span className="text-[10px] font-semibold text-primary shrink-0">
            № {lesson.orderNumber}
          </span>
        ) : null}
        <span
          className={cn(
            'rounded-full px-2 py-0.5 text-[10px] font-medium leading-4',
            lessonTypeBadgeClass(lesson.type)
          )}
        >
          {lessonTypeLabel(lesson.type, t)}
        </span>
      </div>

      <div
        className={cn(
          'text-xs font-medium text-slate-950 break-words',
          dense ? 'line-clamp-2' : 'line-clamp-3'
        )}
      >
        {lesson.title || t('lesson.untitled')}
      </div>

      <div className="flex items-center gap-1 text-[11px] text-slate-600">
        <Clock3 className="h-3.5 w-3.5 shrink-0" />
        <span className="tabular-nums">{lesson.durationHours} {t('lesson.hoursH')}</span>
      </div>

      {!compact && instructors.length > 0 ? (
        <div className="flex items-start gap-1 text-[11px] text-slate-600">
          <UserRound className="h-3.5 w-3.5 shrink-0 mt-0.5" />
          <span className="line-clamp-2 break-words">{instructors.join(', ')}</span>
        </div>
      ) : null}
    </div>
  )
}
