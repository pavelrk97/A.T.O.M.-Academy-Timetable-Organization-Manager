'use client'

import { useMemo, useState } from 'react'
import { Check, ChevronsUpDown, Search, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useI18n } from '@/lib/i18n'
import type { User } from '@/lib/types'
import { cn } from '@/lib/utils'

interface InstructorMultiSelectProps {
  instructors: User[]
  selectedIds: string[]
  onChange: (next: string[]) => void
  placeholder?: string
  /** Сколько имён показывать на триггере перед сворачиванием в «… и ещё N». */
  maxLabelChips?: number
  disabled?: boolean
  emptyHint?: string
}

function instructorLabel(user: User): string {
  return user.displayName || user.fullName || user.username
}

function normalize(value: string): string {
  return value.toLowerCase().trim()
}

export function InstructorMultiSelect({
  instructors,
  selectedIds,
  onChange,
  placeholder,
  maxLabelChips = 2,
  disabled = false,
  emptyHint,
}: InstructorMultiSelectProps) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')

  const resolvedPlaceholder = placeholder ?? t('multiselect.placeholder')
  const resolvedEmptyHint = emptyHint ?? t('multiselect.emptyHint')

  const sortedInstructors = useMemo(
    () =>
      [...instructors].sort((left, right) =>
        instructorLabel(left).localeCompare(instructorLabel(right), 'ru')
      ),
    [instructors]
  )

  const filteredInstructors = useMemo(() => {
    const query = normalize(search)
    if (!query) return sortedInstructors
    return sortedInstructors.filter((user) =>
      [user.fullName, user.displayName, user.username, user.email]
        .filter((v): v is string => Boolean(v))
        .some((v) => normalize(v).includes(query))
    )
  }, [sortedInstructors, search])

  const selectedSet = useMemo(() => new Set(selectedIds), [selectedIds])
  const selectedInstructors = useMemo(
    () => sortedInstructors.filter((user) => selectedSet.has(user.id)),
    [sortedInstructors, selectedSet]
  )

  function toggle(id: string) {
    if (selectedSet.has(id)) {
      onChange(selectedIds.filter((value) => value !== id))
    } else {
      onChange([...selectedIds, id])
    }
  }

  function selectAllFiltered() {
    const ids = new Set(selectedIds)
    filteredInstructors.forEach((user) => ids.add(user.id))
    onChange([...ids])
  }

  function clearSelection() {
    onChange([])
  }

  const triggerLabel = (() => {
    if (!selectedInstructors.length) return resolvedPlaceholder
    if (selectedInstructors.length <= maxLabelChips) {
      return selectedInstructors.map(instructorLabel).join(', ')
    }
    const head = selectedInstructors
      .slice(0, maxLabelChips)
      .map(instructorLabel)
      .join(', ')
    return `${head} +${selectedInstructors.length - maxLabelChips}`
  })()

  return (
    <div className="space-y-2">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="outline"
            role="combobox"
            aria-expanded={open}
            disabled={disabled}
            className={cn(
              'h-9 w-full justify-between gap-2 truncate text-left text-sm font-normal',
              !selectedInstructors.length && 'text-muted-foreground'
            )}
          >
            <span className="truncate">{triggerLabel}</span>
            <ChevronsUpDown className="h-4 w-4 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent
          className="w-[min(96vw,420px)] p-0"
          align="start"
          sideOffset={4}
        >
          <div className="flex flex-col">
            <div className="relative border-b border-border p-2">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={t('multiselect.search')}
                className="h-9 pl-9"
                autoFocus
              />
            </div>

            <div className="max-h-72 overflow-auto">
              {filteredInstructors.length === 0 ? (
                <div className="px-4 py-6 text-center text-sm text-muted-foreground">
                  {sortedInstructors.length === 0 ? resolvedEmptyHint : t('multiselect.nothingFound')}
                </div>
              ) : (
                <ul className="py-1">
                  {filteredInstructors.map((user) => {
                    const isSelected = selectedSet.has(user.id)
                    return (
                      <li key={user.id}>
                        <button
                          type="button"
                          onClick={() => toggle(user.id)}
                          className={cn(
                            'flex w-full items-center gap-3 px-4 py-2 text-left text-sm transition-colors hover:bg-slate-50',
                            isSelected && 'bg-primary/5'
                          )}
                        >
                          <span
                            className={cn(
                              'flex h-4 w-4 shrink-0 items-center justify-center rounded border',
                              isSelected
                                ? 'border-primary bg-primary text-white'
                                : 'border-slate-300'
                            )}
                          >
                            {isSelected ? <Check className="h-3 w-3" /> : null}
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate font-medium text-slate-950">
                              {instructorLabel(user)}
                            </span>
                            {user.username !== instructorLabel(user) ? (
                              <span className="block truncate text-xs text-muted-foreground">
                                {user.username}
                                {user.email ? ` • ${user.email}` : ''}
                              </span>
                            ) : null}
                          </span>
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>

            <div className="flex items-center justify-between gap-2 border-t border-border bg-slate-50 px-3 py-2 text-xs">
              <span className="text-muted-foreground">
                {t('multiselect.selectedCount')} {selectedInstructors.length}
              </span>
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-7 px-2"
                  onClick={selectAllFiltered}
                  disabled={!filteredInstructors.length}
                >
                  {t('multiselect.selectAll')}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-7 px-2"
                  onClick={clearSelection}
                  disabled={!selectedInstructors.length}
                >
                  <X className="mr-1 h-3.5 w-3.5" /> {t('multiselect.reset')}
                </Button>
              </div>
            </div>
          </div>
        </PopoverContent>
      </Popover>

      {selectedInstructors.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {selectedInstructors.map((user) => (
            <span
              key={user.id}
              className="inline-flex items-center gap-1 rounded-full border border-primary/20 bg-primary/10 px-2 py-0.5 text-xs text-primary"
            >
              {instructorLabel(user)}
              <button
                type="button"
                onClick={() => toggle(user.id)}
                aria-label={`${t('multiselect.remove')} ${instructorLabel(user)}`}
                className="rounded-full p-0.5 hover:bg-primary/20"
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      ) : null}
    </div>
  )
}
