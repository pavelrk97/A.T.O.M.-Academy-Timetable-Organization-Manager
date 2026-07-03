'use client'

import { useMemo } from 'react'
import { useI18n } from '@/lib/i18n'
import type { WorkloadSummary } from '@/lib/types'

interface WorkloadSummaryTableProps {
  summaries: WorkloadSummary[]
  selectedIds: string[]
  includeBusinessTrips: boolean
}

export function WorkloadSummaryTable({
  summaries,
  selectedIds,
  includeBusinessTrips,
}: WorkloadSummaryTableProps) {
  const { t } = useI18n()

  const rows = useMemo(() => {
    const filtered = selectedIds.length
      ? summaries.filter((item) => selectedIds.includes(item.instructorId))
      : summaries
    return [...filtered].sort((left, right) =>
      left.instructorName.localeCompare(right.instructorName, 'ru')
    )
  }, [summaries, selectedIds])

  const totalHours = rows.reduce(
    (sum, row) =>
      sum + (includeBusinessTrips ? row.totalHours : row.totalHours - (row.businessTripHours || 0)),
    0
  )

  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
      <div className="flex items-center justify-end border-b border-border px-5 py-3">
        <div className="rounded-full bg-primary/10 px-3 py-1 text-sm font-medium text-primary">
          {totalHours} {t('workload.hoursShort')}
        </div>
      </div>

      {rows.length === 0 ? (
        <div className="px-6 py-10 text-center text-sm text-muted-foreground">
          {t('workload.teamEmpty')}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <th className="px-5 py-3 font-semibold">{t('workload.tableInstructor')}</th>
                <th className="px-5 py-3 text-right font-semibold">{t('workload.tableHours')}</th>
                <th className="px-5 py-3 text-right font-semibold">{t('workload.tableTripHours')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {rows.map((row) => {
                const tripHours = row.businessTripHours || 0
                const shownHours = includeBusinessTrips ? row.totalHours : row.totalHours - tripHours
                return (
                  <tr key={row.instructorId}>
                    <td className="px-5 py-3 text-slate-950">{row.instructorName}</td>
                    <td className="px-5 py-3 text-right font-medium text-slate-950">
                      {shownHours} {t('workload.hoursShort')}
                    </td>
                    <td className="px-5 py-3 text-right text-muted-foreground">
                      {tripHours} {t('workload.hoursShort')}
                      {!includeBusinessTrips && tripHours > 0 ? ` (${t('workload.tableExcluded')})` : ''}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
