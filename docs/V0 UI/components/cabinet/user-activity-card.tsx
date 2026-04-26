'use client'

import { useEffect, useMemo, useState } from 'react'
import { Activity, Loader2, RefreshCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { usersApi } from '@/lib/api'
import type { UserActivity } from '@/lib/types'
import { cn } from '@/lib/utils'

const DAY_MS = 24 * 60 * 60 * 1000

function formatDateTime(value: string | null) {
  if (!value) return '—'
  try {
    return new Date(value).toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return value
  }
}

function relativeFromNow(value: string | null): string {
  if (!value) return ''
  const target = new Date(value).getTime()
  if (Number.isNaN(target)) return ''
  const diffDays = Math.floor((Date.now() - target) / DAY_MS)
  if (diffDays <= 0) return 'сегодня'
  if (diffDays === 1) return 'вчера'
  if (diffDays < 7) return `${diffDays}д назад`
  if (diffDays < 30) return `${Math.floor(diffDays / 7)}нед назад`
  if (diffDays < 365) return `${Math.floor(diffDays / 30)}мес назад`
  return `${Math.floor(diffDays / 365)}г назад`
}

function roleLabel(role: UserActivity['role']) {
  switch (role) {
    case 'ADMIN':
      return 'Администратор'
    case 'EDITOR':
      return 'Редактор'
    case 'INSTRUCTOR':
      return 'Инструктор'
    default:
      return role
  }
}

export function UserActivityCard() {
  const [items, setItems] = useState<UserActivity[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  useEffect(() => {
    void load()
  }, [])

  async function load() {
    setLoading(true)
    try {
      const data = await usersApi.getActivity()
      setItems(data)
      setError(null)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Не удалось загрузить активность')
    } finally {
      setLoading(false)
    }
  }

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return items
    return items.filter(
      (item) =>
        item.username.toLowerCase().includes(query) ||
        (item.fullName || '').toLowerCase().includes(query)
    )
  }, [items, search])

  const stats = useMemo(() => {
    const active30d = items.filter((item) => item.loginCount30d > 0).length
    const neverLoggedIn = items.filter((item) => !item.lastLoginAt).length
    const totalLogins30d = items.reduce((sum, item) => sum + item.loginCount30d, 0)
    return { active30d, neverLoggedIn, totalLogins30d }
  }, [items])

  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="rounded-xl bg-primary/10 p-3">
            <Activity className="h-5 w-5 text-primary" />
          </div>
          <div>
            <div className="text-sm font-semibold text-slate-950">Активность пользователей</div>
            <p className="mt-1 text-sm text-muted-foreground">
              Кто и как часто заходит в кабинет. Учитываются только успешные входы.
            </p>
          </div>
        </div>
        <Button variant="ghost" size="sm" onClick={load} disabled={loading}>
          {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCcw className="mr-2 h-4 w-4" />}
          Обновить
        </Button>
      </div>

      <div className="mt-4 grid grid-cols-3 gap-3">
        <Stat label="Активных за 30 дней" value={stats.active30d} />
        <Stat label="Заходов за 30 дней" value={stats.totalLogins30d} />
        <Stat label="Никогда не входили" value={stats.neverLoggedIn} muted />
      </div>

      <div className="mt-4">
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Поиск по логину или имени"
          className="max-w-md"
        />
      </div>

      {error ? (
        <div className="mt-3 rounded-xl border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
          {error}
        </div>
      ) : null}

      <div className="mt-4 overflow-hidden rounded-xl border border-border">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-[0.12em] text-slate-500">
              <tr>
                <th className="px-3 py-2">Пользователь</th>
                <th className="px-3 py-2">Роль</th>
                <th className="px-3 py-2">Последний вход</th>
                <th className="px-3 py-2 text-right">За 30 дней</th>
                <th className="px-3 py-2 text-right">Всего</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 && !loading ? (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-sm text-muted-foreground">
                    {items.length === 0 ? 'Данных пока нет' : 'Ничего не найдено'}
                  </td>
                </tr>
              ) : null}
              {filtered.map((item) => (
                <tr key={item.userId} className="border-t border-border">
                  <td className="px-3 py-2">
                    <div className="font-medium text-slate-950">{item.fullName || item.username}</div>
                    <div className="text-xs text-muted-foreground">{item.username}</div>
                  </td>
                  <td className="px-3 py-2 text-xs text-slate-700">{roleLabel(item.role)}</td>
                  <td className="px-3 py-2">
                    <div className="text-slate-950">{formatDateTime(item.lastLoginAt)}</div>
                    <div className="text-xs text-muted-foreground">{relativeFromNow(item.lastLoginAt)}</div>
                  </td>
                  <td className={cn('px-3 py-2 text-right tabular-nums', item.loginCount30d === 0 && 'text-slate-400')}>
                    {item.loginCount30d}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums text-slate-600">
                    {item.loginCountTotal}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}

function Stat({ label, value, muted = false }: { label: string; value: number; muted?: boolean }) {
  return (
    <div className="rounded-xl border border-border bg-slate-50 px-3 py-2">
      <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500">
        {label}
      </div>
      <div className={cn('mt-1 text-xl font-semibold', muted ? 'text-slate-500' : 'text-slate-950')}>
        {value}
      </div>
    </div>
  )
}
