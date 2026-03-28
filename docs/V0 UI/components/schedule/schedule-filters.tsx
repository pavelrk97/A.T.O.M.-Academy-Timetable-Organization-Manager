'use client'

import { CalendarDays, Search, SlidersHorizontal, UserRound, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Switch } from '@/components/ui/switch'

export interface ScheduleFilterValues {
  from: string
  to: string
  groupCode: string
  instructorSearch: string
  onlyMyLessons: boolean
}

interface ScheduleFiltersProps {
  values: ScheduleFilterValues
  groupOptions: string[]
  onChange: (values: ScheduleFilterValues) => void
  showMyLessons?: boolean
  isAuthenticated?: boolean
}

function buildDefaultRange(days = 14) {
  const today = new Date()
  const to = new Date(today)
  to.setDate(today.getDate() + days)

  return {
    from: today.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10),
  }
}

export function ScheduleFilters({
  values,
  groupOptions,
  onChange,
  showMyLessons = false,
  isAuthenticated = false,
}: ScheduleFiltersProps) {
  const setValue = <K extends keyof ScheduleFilterValues>(
    key: K,
    value: ScheduleFilterValues[K]
  ) => {
    onChange({ ...values, [key]: value })
  }

  const resetFilters = () => {
    onChange({
      ...buildDefaultRange(),
      groupCode: '',
      instructorSearch: '',
      onlyMyLessons: false,
    })
  }

  const applyPreset = (preset: 'today' | 'week' | 'month') => {
    const today = new Date()
    const end = new Date(today)

    if (preset === 'week') end.setDate(today.getDate() + 7)
    if (preset === 'month') end.setDate(today.getDate() + 30)

    onChange({
      ...values,
      from: today.toISOString().slice(0, 10),
      to: end.toISOString().slice(0, 10),
    })
  }

  const hasExtras =
    Boolean(values.groupCode.trim()) ||
    Boolean(values.instructorSearch.trim()) ||
    values.onlyMyLessons

  return (
    <div className="rounded-2xl border border-border bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-center">
        <div className="flex flex-wrap items-center gap-2">
          <CalendarDays className="h-4 w-4 text-primary" />
          <Input
            type="date"
            value={values.from}
            onChange={(event) => setValue('from', event.target.value)}
            className="h-10 w-[160px]"
          />
          <span className="text-sm text-muted-foreground">—</span>
          <Input
            type="date"
            value={values.to}
            onChange={(event) => setValue('to', event.target.value)}
            className="h-10 w-[160px]"
          />
        </div>

        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={() => applyPreset('today')}>
            Сегодня
          </Button>
          <Button variant="outline" size="sm" onClick={() => applyPreset('week')}>
            7 дней
          </Button>
          <Button variant="outline" size="sm" onClick={() => applyPreset('month')}>
            30 дней
          </Button>
        </div>

        <div className="grid flex-1 gap-3 md:grid-cols-[minmax(220px,320px)_minmax(220px,320px)] xl:max-w-3xl">
          <div className="space-y-1">
            <Label htmlFor="group-code" className="text-xs text-muted-foreground">
              Группа
            </Label>
            <Input
              id="group-code"
              list="group-code-options"
              value={values.groupCode}
              onChange={(event) => setValue('groupCode', event.target.value)}
              placeholder="Например, гр.6 ()"
              className="h-10"
            />
            <datalist id="group-code-options">
              {groupOptions.map((groupCode) => (
                <option key={groupCode} value={groupCode} />
              ))}
            </datalist>
          </div>

          <div className="space-y-1">
            <Label htmlFor="instructor-search" className="text-xs text-muted-foreground">
              Преподаватель
            </Label>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="instructor-search"
                value={values.instructorSearch}
                onChange={(event) => setValue('instructorSearch', event.target.value)}
                placeholder="Поиск по имени преподавателя"
                className="h-10 pl-9"
              />
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 xl:ml-auto">
          {showMyLessons && isAuthenticated && (
            <label className="flex items-center gap-2 rounded-xl border border-border bg-slate-50 px-3 py-2 text-sm">
              <Switch
                checked={values.onlyMyLessons}
                onCheckedChange={(checked) => setValue('onlyMyLessons', checked)}
              />
              <span className="inline-flex items-center gap-2">
                <UserRound className="h-4 w-4 text-primary" />
                Только мои занятия
              </span>
            </label>
          )}

          {hasExtras && (
            <Button variant="ghost" size="sm" onClick={resetFilters}>
              <X className="mr-1 h-4 w-4" />
              Сбросить
            </Button>
          )}

          <Popover>
            <PopoverTrigger asChild>
              <Button variant="outline" size="sm">
                <SlidersHorizontal className="mr-2 h-4 w-4" />
                Фильтры
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-80 space-y-4">
              <div>
                <h4 className="font-medium text-slate-950">Панель фильтров</h4>
                <p className="mt-1 text-sm text-muted-foreground">
                  Держи диапазон в пределах месяца, чтобы таблица оставалась быстрой.
                </p>
              </div>
              <div className="space-y-2">
                <Label className="text-xs text-muted-foreground">Код группы</Label>
                <Input
                  value={values.groupCode}
                  onChange={(event) => setValue('groupCode', event.target.value)}
                  placeholder="гр.6 ()"
                />
              </div>
              <div className="space-y-2">
                <Label className="text-xs text-muted-foreground">Поиск преподавателя</Label>
                <Input
                  value={values.instructorSearch}
                  onChange={(event) => setValue('instructorSearch', event.target.value)}
                  placeholder="Меняйло"
                />
              </div>
              <div className="flex justify-between">
                <Button variant="ghost" size="sm" onClick={resetFilters}>
                  Сбросить
                </Button>
                <Button size="sm">Готово</Button>
              </div>
            </PopoverContent>
          </Popover>
        </div>
      </div>
    </div>
  )
}

export function createDefaultScheduleFilters(): ScheduleFilterValues {
  return {
    ...buildDefaultRange(),
    groupCode: '',
    instructorSearch: '',
    onlyMyLessons: false,
  }
}
