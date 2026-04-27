'use client'

import {
  Suspense,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import {
  AlertCircle,
  CalendarDays,
  Loader2,
  Rows3,
  UsersRound,
  ZoomIn,
  ZoomOut,
} from 'lucide-react'
import { Header } from '@/components/header'
import { LessonDetails } from '@/components/schedule/lesson-details'
import {
  ScheduleFilters,
  createDefaultScheduleFilters,
  type ScheduleFilterValues,
} from '@/components/schedule/schedule-filters'
import { ScheduleGrid } from '@/components/schedule/schedule-grid'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { useAuth } from '@/lib/auth-context'
import { meApi, publicApi } from '@/lib/api'
import { buildGridFromEntries, filterGridByInstructor } from '@/lib/schedule'
import type { ScheduleGridData, ScheduleGridLessonCell } from '@/lib/types'

const MAX_DAYS = 100

function normalizeFiltersFromUrl(searchParams: URLSearchParams): ScheduleFilterValues {
  const defaults = createDefaultScheduleFilters()

  return {
    from: searchParams.get('from') || defaults.from,
    to: searchParams.get('to') || defaults.to,
    groupCode: searchParams.get('groupCode') || '',
    instructorSearch: searchParams.get('instructor') || '',
    onlyMyLessons: searchParams.get('onlyMy') === 'true',
  }
}

function buildScheduleQuery(filters: ScheduleFilterValues) {
  const query = new URLSearchParams()

  query.set('from', filters.from)
  query.set('to', filters.to)

  if (filters.groupCode.trim()) {
    query.set('groupCode', filters.groupCode.trim())
  }

  if (filters.instructorSearch.trim()) {
    query.set('instructor', filters.instructorSearch.trim())
  }

  if (filters.onlyMyLessons) {
    query.set('onlyMy', 'true')
  }

  return query.toString()
}

function daysBetweenInclusive(from: string, to: string) {
  const left = new Date(from)
  const right = new Date(to)
  return Math.floor((right.getTime() - left.getTime()) / (1000 * 60 * 60 * 24)) + 1
}

function clampZoom(value: number) {
  return Math.min(140, Math.max(80, value))
}

function SchedulePageFallback() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="inline-flex items-center gap-3 rounded-2xl border border-border bg-white px-5 py-4 text-sm text-muted-foreground shadow-sm">
        <Loader2 className="h-5 w-5 animate-spin text-primary" />
        Загружаю страницу расписания...
      </div>
    </div>
  )
}

function SchedulePageContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { isAuthenticated } = useAuth()

  const [filters, setFilters] = useState<ScheduleFilterValues>(() =>
    normalizeFiltersFromUrl(new URLSearchParams(searchParams.toString()))
  )
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [grid, setGrid] = useState<ScheduleGridData | null>(null)
  const [zoom, setZoom] = useState(100)
  const [selected, setSelected] = useState<{
    groupCode: string
    date: string
    lessons: ScheduleGridLessonCell[]
    location?: string | null
  } | null>(null)

  useEffect(() => {
    setFilters(normalizeFiltersFromUrl(new URLSearchParams(searchParams.toString())))
  }, [searchParams])

  useEffect(() => {
    const nextQuery = buildScheduleQuery(filters)
    const currentQuery = searchParams.toString()

    if (nextQuery !== currentQuery) {
      router.replace(`/schedule?${nextQuery}`, { scroll: false })
    }
  }, [filters, router, searchParams])

  useEffect(() => {
    let cancelled = false

    async function load() {
      const rangeDays = daysBetweenInclusive(filters.from, filters.to)

      if (rangeDays > MAX_DAYS) {
        setGrid(null)
        setError(`Для стабильной работы держи диапазон до ${MAX_DAYS} дней.`)
        setLoading(false)
        return
      }

      setLoading(true)
      setError('')

      try {
        let nextGrid: ScheduleGridData

        if (filters.onlyMyLessons && isAuthenticated) {
          nextGrid = await meApi.getInstructorGrid({
            from: filters.from,
            to: filters.to,
          })
        } else if (isAuthenticated) {
          nextGrid = await meApi.getScheduleGrid({
            from: filters.from,
            to: filters.to,
          })
        } else {
          const entries = await publicApi.getSchedule({
            groupCode: filters.groupCode.trim() || undefined,
            from: filters.from,
            to: filters.to,
          })
          nextGrid = buildGridFromEntries(entries)
        }

        if (filters.groupCode.trim()) {
          const normalizedGroup = filters.groupCode.trim().toLowerCase()
          nextGrid = {
            ...nextGrid,
            groups: nextGrid.groups.filter((group) =>
              group.groupCode.toLowerCase().includes(normalizedGroup)
            ),
          }
        }

        if (filters.instructorSearch.trim()) {
          nextGrid = filterGridByInstructor(nextGrid, filters.instructorSearch)
        }

        if (!cancelled) {
          setGrid(nextGrid)
          setSelected(null)
        }
      } catch (caught) {
        if (!cancelled) {
          setGrid(null)
          setError(
            caught instanceof Error && caught.message
              ? caught.message
              : 'Не удалось загрузить расписание.'
          )
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [filters, isAuthenticated])

  const groupOptions = useMemo(
    () => Array.from(new Set((grid?.groups || []).map((group) => group.groupCode))).sort(),
    [grid]
  )

  const lessonCount = useMemo(
    () =>
      (grid?.groups || []).reduce(
        (sum, group) =>
          sum + group.days.reduce((daySum, day) => daySum + day.lessons.length, 0),
        0
      ),
    [grid]
  )

  return (
    <div className="min-h-screen bg-transparent">
      <Header />

      <main className="mx-auto flex max-w-[1600px] gap-6 px-4 py-6 lg:px-8">
        <div className="min-w-0 flex-1 space-y-6">
          <section className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
            <div className="rounded-[28px] border border-border bg-white px-6 py-6 shadow-sm">
              <div className="inline-flex rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-primary">
                Schedule workspace
              </div>
              <h1 className="mt-4 text-3xl font-semibold tracking-tight text-slate-950">
                Расписание в формате рабочей таблицы
              </h1>
              <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">
                Группы слева, даты сверху, занятия внутри ячеек. Диапазон можно держать до 100
                дней, а масштаб менять прямо на странице.
              </p>
            </div>

            <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-1">
              <InfoCard
                icon={<CalendarDays className="h-5 w-5 text-primary" />}
                label="Дат в выдаче"
                value={grid?.dates.length || 0}
              />
              <InfoCard
                icon={<UsersRound className="h-5 w-5 text-primary" />}
                label="Групп"
                value={grid?.groups.length || 0}
              />
              <InfoCard
                icon={<Rows3 className="h-5 w-5 text-primary" />}
                label="Занятий"
                value={lessonCount}
              />
            </div>
          </section>

          <ScheduleFilters
            values={filters}
            groupOptions={groupOptions}
            onChange={setFilters}
            showMyLessons
            isAuthenticated={isAuthenticated}
          />

          <div className="flex justify-end">
            <div className="flex items-center gap-2 rounded-2xl border border-border bg-white px-3 py-2 shadow-sm">
              <ZoomOut className="h-4 w-4 text-muted-foreground" />
              <input
                type="range"
                min={80}
                max={140}
                step={5}
                value={zoom}
                onChange={(event) => setZoom(clampZoom(Number(event.target.value)))}
              />
              <ZoomIn className="h-4 w-4 text-muted-foreground" />
              <span className="min-w-10 text-right text-xs font-medium text-slate-700">
                {zoom}%
              </span>
            </div>
          </div>

          {error ? (
            <div className="flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              {error}
            </div>
          ) : null}

          {loading ? (
            <div className="flex min-h-[420px] items-center justify-center rounded-2xl border border-border bg-white shadow-sm">
              <div className="inline-flex items-center gap-3 text-sm text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin text-primary" />
                Загружаю расписание...
              </div>
            </div>
          ) : grid ? (
            <ScheduleGrid
              data={grid}
              compact={false}
              zoom={zoom}
              highlightInstructor={filters.instructorSearch}
              onCellClick={(groupCode, date, lessons, location) =>
                setSelected({ groupCode, date, lessons, location })
              }
            />
          ) : (
            <div className="rounded-2xl border border-dashed border-border bg-white px-5 py-16 text-center text-sm text-muted-foreground">
              Сетка пока пустая.
            </div>
          )}
        </div>

        <aside className="hidden w-[360px] shrink-0 xl:block">
          <div className="sticky top-24 overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
            {selected ? (
              <LessonDetails
                date={selected.date}
                groupCode={selected.groupCode}
                location={selected.location}
                lessons={selected.lessons}
                onClose={() => setSelected(null)}
              />
            ) : (
              <div className="px-5 py-16 text-center">
                <div className="text-base font-semibold text-slate-950">Выбери ячейку</div>
                <p className="mt-2 text-sm text-muted-foreground">
                  Здесь появятся занятия выбранного дня: состав, часы и примечания.
                </p>
              </div>
            )}
          </div>
        </aside>
      </main>

      {/* Mobile / tablet day popup — shown only when sidebar isn't visible (lg-) */}
      <Sheet
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) setSelected(null)
        }}
      >
        <SheetContent
          side="bottom"
          className="h-[90vh] overflow-y-auto p-0 xl:hidden"
        >
          <SheetHeader className="sr-only">
            <SheetTitle>Расписание дня</SheetTitle>
          </SheetHeader>
          {selected ? (
            <LessonDetails
              date={selected.date}
              groupCode={selected.groupCode}
              location={selected.location}
              lessons={selected.lessons}
              onClose={() => setSelected(null)}
            />
          ) : null}
        </SheetContent>
      </Sheet>
    </div>
  )
}

function InfoCard({
  icon,
  label,
  value,
}: {
  icon: ReactNode
  label: string
  value: number
}) {
  return (
    <section className="rounded-2xl border border-border bg-white px-5 py-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
            {label}
          </div>
          <div className="mt-3 text-3xl font-semibold text-slate-950">{value}</div>
        </div>
        <div className="rounded-xl bg-primary/10 p-3">{icon}</div>
      </div>
    </section>
  )
}

export default function SchedulePage() {
  return (
    <Suspense fallback={<SchedulePageFallback />}>
      <SchedulePageContent />
    </Suspense>
  )
}
