'use client'

import {
  Suspense,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ComponentType,
} from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import {
  Bell,
  Download,
  FileCog,
  LayoutDashboard,
  Loader2,
  LockKeyhole,
  Rows3,
  UserRound,
  ZoomIn,
  ZoomOut,
} from 'lucide-react'
import { Header } from '@/components/header'
import { DashboardStats } from '@/components/cabinet/dashboard-stats'
import { ProfileSection } from '@/components/cabinet/profile-section'
import { ChangePassword } from '@/components/cabinet/change-password'
import { NotificationsFeed } from '@/components/cabinet/notifications-feed'
import { WorkloadCalendar } from '@/components/cabinet/workload-calendar'
import { AdminWorkspace } from '@/components/cabinet/admin-workspace'
import { ScheduleGrid } from '@/components/schedule/schedule-grid'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/lib/auth-context'
import {
  groupsApi,
  importApi,
  meApi,
  saveDownload,
  usersApi,
  workloadApi,
} from '@/lib/api'
import type {
  DashboardData,
  GroupDto,
  ImportResult,
  ScheduleGridData,
  User,
} from '@/lib/types'
import { cn } from '@/lib/utils'

type CabinetTab =
  | 'dashboard'
  | 'profile'
  | 'schedule'
  | 'workload'
  | 'notifications'
  | 'security'
  | 'admin'

function createDefaultRange() {
  const today = new Date()
  const next = new Date(today)
  next.setDate(today.getDate() + 14)

  return {
    from: today.toISOString().slice(0, 10),
    to: next.toISOString().slice(0, 10),
  }
}

function buildCabinetQuery(tab: CabinetTab, from: string, to: string) {
  const query = new URLSearchParams()
  query.set('tab', tab)
  query.set('from', from)
  query.set('to', to)
  return query.toString()
}

function clampZoom(value: number) {
  return Math.min(140, Math.max(80, value))
}

function canUseOperations(user?: User | null) {
  return Boolean(user && (user.role === 'ADMIN' || user.role === 'EDITOR' || user.editorAccess))
}

function roleLabel(user?: User | null) {
  if (!user) {
    return ''
  }
  if (user.role === 'ADMIN') return 'Администратор'
  if (user.role === 'EDITOR') return 'Редактор'
  if (user.editorAccess) return 'Инструктор / Редактор'
  return 'Инструктор'
}

function createTabItems(user?: User | null) {
  const items: { id: CabinetTab; label: string; icon: ComponentType<{ className?: string }> }[] = [
    { id: 'dashboard', label: 'Обзор', icon: LayoutDashboard },
    { id: 'profile', label: 'Профиль', icon: UserRound },
    { id: 'schedule', label: 'Моё расписание', icon: Rows3 },
    { id: 'workload', label: 'Нагрузка', icon: Rows3 },
    { id: 'notifications', label: 'Уведомления', icon: Bell },
    { id: 'security', label: 'Безопасность', icon: LockKeyhole },
  ]

  if (canUseOperations(user)) {
    items.push({ id: 'admin', label: 'Операции', icon: FileCog })
  }

  return items
}

function CabinetPageFallback() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="inline-flex items-center gap-3 rounded-2xl border border-border bg-white px-5 py-4 text-sm text-muted-foreground shadow-sm">
        <Loader2 className="h-5 w-5 animate-spin text-primary" />
        Загружаю личный кабинет...
      </div>
    </div>
  )
}

function CabinetPageContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const {
    user,
    isAuthenticated,
    isLoading: authLoading,
    refreshUser,
    updateStoredPassword,
  } = useAuth()

  const defaultRange = useMemo(() => createDefaultRange(), [])
  const urlTab = (searchParams.get('tab') as CabinetTab) || 'dashboard'
  const urlFrom = searchParams.get('from') || defaultRange.from
  const urlTo = searchParams.get('to') || defaultRange.to
  const urlQuery = useMemo(
    () => buildCabinetQuery(urlTab, urlFrom, urlTo),
    [urlTab, urlFrom, urlTo]
  )

  const [activeTab, setActiveTab] = useState<CabinetTab>(urlTab)
  const [range, setRange] = useState({ from: urlFrom, to: urlTo })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [dashboard, setDashboard] = useState<DashboardData | null>(null)
  const [scheduleMode, setScheduleMode] = useState<'my' | 'all'>('my')
  const [fullSchedule, setFullSchedule] = useState<ScheduleGridData | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [groups, setGroups] = useState<GroupDto[]>([])
  const [adminLoading, setAdminLoading] = useState(false)
  const [importing, setImporting] = useState(false)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [workloadExporting, setWorkloadExporting] = useState(false)
  const [workloadFilter, setWorkloadFilter] = useState('')
  const [scheduleZoom, setScheduleZoom] = useState(100)
  const syncingFromUrlRef = useRef(false)

  useEffect(() => {
    if (activeTab === urlTab && range.from === urlFrom && range.to === urlTo) {
      return
    }

    syncingFromUrlRef.current = true
    setActiveTab(urlTab)
    setRange({ from: urlFrom, to: urlTo })
  }, [urlFrom, urlTab, urlTo])

  useEffect(() => {
    if (syncingFromUrlRef.current) {
      syncingFromUrlRef.current = false
      return
    }

    const nextQuery = buildCabinetQuery(activeTab, range.from, range.to)
    if (nextQuery !== urlQuery) {
      router.replace(`/cabinet?${nextQuery}`, { scroll: false })
    }
  }, [activeTab, range.from, range.to, router, urlQuery])

  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.replace('/login')
    }
  }, [authLoading, isAuthenticated, router])

  useEffect(() => {
    if (!isAuthenticated) {
      return
    }

    let cancelled = false

    async function loadDashboard() {
      setLoading(true)
      setError('')

      try {
        const nextDashboard = await meApi.getDashboard(range)
        if (!cancelled) {
          setDashboard(nextDashboard)
        }
      } catch (caught) {
        if (!cancelled) {
          setDashboard(null)
          setError(
            caught instanceof Error && caught.message
              ? caught.message
              : 'Не удалось загрузить личный кабинет.'
          )
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadDashboard()

    return () => {
      cancelled = true
    }
  }, [isAuthenticated, range])

  const operationsEnabled = canUseOperations(user)
  const canManageUsers = user?.role === 'ADMIN'
  const canManageGroups = operationsEnabled
  const tabs = createTabItems(user)
  const currentSchedule = scheduleMode === 'my' ? dashboard?.instructorSchedule : fullSchedule
  const adminWorkloadMatches = useMemo(() => {
    if (user?.role !== 'ADMIN') {
      return []
    }

    const query = workloadFilter.trim().toLowerCase()
    if (!query) {
      return []
    }

    return users
      .filter((candidate) => candidate.canTeach)
      .filter((candidate) =>
        [candidate.fullName, candidate.displayName, candidate.username]
          .filter((value): value is string => Boolean(value))
          .some((value) => value.toLowerCase().includes(query))
      )
      .sort((left, right) =>
        (left.displayName || left.fullName || left.username).localeCompare(
          right.displayName || right.fullName || right.username,
          'ru'
        )
      )
  }, [user?.role, users, workloadFilter])

  async function loadFullSchedule() {
    if (!operationsEnabled) {
      return
    }

    setLoading(true)
    try {
      const grid = await meApi.getScheduleGrid(range)
      setFullSchedule(grid)
      setScheduleMode('all')
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось загрузить полную сетку академии.'
      )
    } finally {
      setLoading(false)
    }
  }

  async function loadAdminData() {
    if (!operationsEnabled) {
      return
    }

    setAdminLoading(true)
    try {
      const [nextUsers, nextGroups] = await Promise.all([usersApi.getAll(), groupsApi.getAll()])
      setUsers(nextUsers)
      setGroups(nextGroups)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось загрузить данные операционного блока.'
      )
    } finally {
      setAdminLoading(false)
    }
  }

  useEffect(() => {
    if (activeTab === 'admin' && operationsEnabled && (!users.length || !groups.length)) {
      void loadAdminData()
    }
  }, [activeTab, operationsEnabled, users.length, groups.length])

  useEffect(() => {
    if (activeTab === 'workload' && user?.role === 'ADMIN' && !users.length) {
      void loadAdminData()
    }
  }, [activeTab, user?.role, users.length])

  async function handleProfileUpdate(payload: {
    displayName?: string | null
    email?: string | null
    phone?: string | null
    position?: string | null
    department?: string | null
  }) {
    await meApi.updateProfile(payload)
    await refreshUser()
    const nextDashboard = await meApi.getDashboard(range)
    setDashboard(nextDashboard)
  }

  async function handlePasswordChange(payload: {
    currentPassword: string
    newPassword: string
  }) {
    await meApi.changePassword(payload)
    updateStoredPassword(payload.newPassword)
  }

  async function handleImport(file: File) {
    setImporting(true)
    try {
      const result = await importApi.uploadCsv(file)
      setImportResult(result)
      await loadAdminData()
    } finally {
      setImporting(false)
    }
  }

  function jumpToScheduleDay(date: string) {
    setScheduleMode('my')
    setRange({ from: date, to: date })
    setActiveTab('schedule')
  }

  async function handleExportMyWorkload() {
    setWorkloadExporting(true)
    setError('')
    try {
      const download = await meApi.exportWorkloadCalendar(range)
      saveDownload(download)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось выгрузить нагрузку.'
      )
    } finally {
      setWorkloadExporting(false)
    }
  }

  async function handleExportAdminWorkload(mode: 'all' | 'filtered') {
    setWorkloadExporting(true)
    setError('')
    try {
      const download = await workloadApi.exportCsv({
        instructorQuery: mode === 'filtered' ? workloadFilter.trim() || undefined : undefined,
        from: range.from,
        to: range.to,
      })
      saveDownload(download)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось выгрузить сводную нагрузку.'
      )
    } finally {
      setWorkloadExporting(false)
    }
  }

  function renderAdminWorkloadActions() {
    return (
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Input
            value={workloadFilter}
            onChange={(event) => setWorkloadFilter(event.target.value)}
            placeholder="Р¤Р°РјРёР»РёСЏ РґР»СЏ С„РёР»СЊС‚СЂР°"
            className="h-9 w-48"
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleExportAdminWorkload('filtered')}
            disabled={
              workloadExporting ||
              !workloadFilter.trim() ||
              (users.length > 0 && adminWorkloadMatches.length === 0)
            }
          >
            <Download className="mr-2 h-4 w-4" />
            РџРѕ С„Р°РјРёР»РёРё
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleExportAdminWorkload('all')}
            disabled={workloadExporting}
          >
            <Download className="mr-2 h-4 w-4" />
            Р’СЃРµ
          </Button>
        </div>
        {workloadFilter.trim() ? (
          <div className="text-xs text-muted-foreground">
            {adminWorkloadMatches.length
              ? `РЎРѕРІРїР°РґРµРЅРёСЏ: ${adminWorkloadMatches
                  .slice(0, 5)
                  .map((candidate) => candidate.displayName || candidate.fullName || candidate.username)
                  .join(', ')}${adminWorkloadMatches.length > 5 ? ` (+${adminWorkloadMatches.length - 5})` : ''}`
              : 'РџРѕ Р·Р°РїСЂРѕСЃСѓ РЅРµС‚ РёРЅСЃС‚СЂСѓРєС‚РѕСЂРѕРІ СЃ canTeach=true.'}
          </div>
        ) : null}
      </div>
    )
  }

  function renderWorkloadActions() {
    if (user?.role === 'ADMIN') {
      return renderAdminWorkloadActions()
    }
    if (false) {
      return (
        <div className="flex flex-wrap items-center gap-2">
          <Input
            value={workloadFilter}
            onChange={(event) => setWorkloadFilter(event.target.value)}
            placeholder="Фамилия для фильтра"
            className="h-9 w-48"
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleExportAdminWorkload('filtered')}
            disabled={workloadExporting || !workloadFilter.trim()}
          >
            <Download className="mr-2 h-4 w-4" />
            По фамилии
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleExportAdminWorkload('all')}
            disabled={workloadExporting}
          >
            <Download className="mr-2 h-4 w-4" />
            Все
          </Button>
        </div>
      )
    }

    return (
      <Button
        variant="outline"
        size="sm"
        onClick={() => void handleExportMyWorkload()}
        disabled={workloadExporting}
      >
        <Download className="mr-2 h-4 w-4" />
        {workloadExporting ? 'Готовлю Excel...' : 'Экспорт Excel'}
      </Button>
    )
  }

  function renderZoomControls() {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-border bg-white px-3 py-2">
        <ZoomOut className="h-4 w-4 text-muted-foreground" />
        <input
          type="range"
          min={80}
          max={140}
          step={5}
          value={scheduleZoom}
          onChange={(event) => setScheduleZoom(clampZoom(Number(event.target.value)))}
        />
        <ZoomIn className="h-4 w-4 text-muted-foreground" />
        <span className="min-w-10 text-right text-xs font-medium text-slate-700">
          {scheduleZoom}%
        </span>
      </div>
    )
  }

  if (authLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!isAuthenticated || !user) {
    return null
  }

  return (
    <div className="min-h-screen bg-transparent">
      <Header />

      <div className="mx-auto flex max-w-[1600px] gap-6 px-4 py-6 lg:px-8">
        <aside className="hidden w-72 shrink-0 xl:block">
          <div className="sticky top-24 space-y-4 rounded-[28px] border border-border bg-white p-5 shadow-sm">
            <div className="rounded-2xl bg-primary/8 p-4">
              <div className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                Личный кабинет
              </div>
              <div className="mt-2 text-lg font-semibold text-slate-950">
                {user.displayName || user.fullName || user.username}
              </div>
              <div className="text-sm text-muted-foreground">{roleLabel(user)}</div>
            </div>

            <nav className="space-y-1">
              {tabs.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  className={cn(
                    'flex w-full items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium transition-colors',
                    activeTab === tab.id
                      ? 'bg-primary/10 text-primary'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'
                  )}
                >
                  <tab.icon className="h-4 w-4" />
                  {tab.label}
                </button>
              ))}
            </nav>
          </div>
        </aside>

        <main className="min-w-0 flex-1 space-y-6">
          <section className="rounded-[28px] border border-border bg-white px-6 py-6 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <div className="inline-flex rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-primary">
                  Cabinet / {activeTab}
                </div>
                <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-950">
                  Рабочее пространство пользователя
                </h1>
                <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
                  Профиль, расписание, уведомления, безопасность и нагрузка собраны в одном месте.
                  Администратор дополнительно получает импорт, управление пользователями и полную
                  операционную сетку. Инструктор с editor access может создавать группы и занятия.
                </p>
              </div>

              <div className="grid gap-2 sm:grid-cols-2">
                <label className="space-y-1">
                  <span className="text-xs uppercase tracking-wide text-slate-500">От</span>
                  <input
                    type="date"
                    value={range.from}
                    onChange={(event) =>
                      setRange((current) => ({ ...current, from: event.target.value }))
                    }
                    className="h-10 rounded-xl border border-border px-3 text-sm"
                  />
                </label>
                <label className="space-y-1">
                  <span className="text-xs uppercase tracking-wide text-slate-500">До</span>
                  <input
                    type="date"
                    value={range.to}
                    onChange={(event) =>
                      setRange((current) => ({ ...current, to: event.target.value }))
                    }
                    className="h-10 rounded-xl border border-border px-3 text-sm"
                  />
                </label>
              </div>
            </div>
          </section>

          <div className="flex gap-2 overflow-x-auto xl:hidden">
            {tabs.map((tab) => (
              <Button
                key={tab.id}
                type="button"
                variant={activeTab === tab.id ? 'default' : 'outline'}
                onClick={() => setActiveTab(tab.id)}
                className="rounded-xl"
              >
                <tab.icon className="mr-2 h-4 w-4" />
                {tab.label}
              </Button>
            ))}
          </div>

          {error ? (
            <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
              {error}
            </div>
          ) : null}

          {loading && !dashboard ? (
            <div className="flex min-h-[320px] items-center justify-center rounded-2xl border border-border bg-white shadow-sm">
              <div className="inline-flex items-center gap-3 text-sm text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin text-primary" />
                Загружаю кабинет...
              </div>
            </div>
          ) : null}

          {dashboard ? (
            <>
              {activeTab === 'dashboard' ? (
                <div className="space-y-6">
                  <DashboardStats
                    totalHours={dashboard.workload.totalHours}
                    teachingDays={dashboard.workload.days.length}
                    notificationsCount={dashboard.notifications.length}
                    upcomingLessonsCount={dashboard.instructorSchedule.groups.reduce(
                      (sum, group) =>
                        sum +
                        group.days.reduce((daySum, day) => daySum + day.lessons.length, 0),
                      0
                    )}
                    periodLabel={`${range.from} — ${range.to}`}
                  />

                  <div className="grid gap-6 xl:grid-cols-[1.25fr_0.75fr]">
                    <section className="space-y-4">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <div>
                          <h2 className="text-xl font-semibold text-slate-950">Моя сетка занятий</h2>
                          <p className="text-sm text-muted-foreground">
                            Масштаб можно менять без выхода за пределы таблицы.
                          </p>
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                          {renderZoomControls()}
                          {operationsEnabled ? (
                            <div className="flex gap-2">
                              <Button
                                variant={scheduleMode === 'my' ? 'default' : 'outline'}
                                size="sm"
                                onClick={() => setScheduleMode('my')}
                              >
                                Только мои
                              </Button>
                              <Button
                                variant={scheduleMode === 'all' ? 'default' : 'outline'}
                                size="sm"
                                onClick={loadFullSchedule}
                              >
                                Вся академия
                              </Button>
                            </div>
                          ) : null}
                        </div>
                      </div>
                      <ScheduleGrid
                        data={currentSchedule || dashboard.instructorSchedule}
                        compact
                        zoom={scheduleZoom}
                      />
                    </section>

                    <div className="space-y-6">
                      <NotificationsFeed notifications={dashboard.notifications} maxItems={6} />
                      <WorkloadCalendar
                        data={dashboard.workload}
                        onPeriodChange={(from, to) => setRange({ from, to })}
                        onLessonClick={(date) => jumpToScheduleDay(date)}
                        actions={renderWorkloadActions()}
                      />
                    </div>
                  </div>
                </div>
              ) : null}

              {activeTab === 'profile' ? (
                <ProfileSection user={dashboard.profile} onUpdate={handleProfileUpdate} />
              ) : null}

              {activeTab === 'schedule' ? (
                <div className="space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <h2 className="text-xl font-semibold text-slate-950">Сетка расписания</h2>
                      <p className="text-sm text-muted-foreground">
                        Табличный вид с управляемым масштабом и диапазоном до 100 дней.
                      </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      {renderZoomControls()}
                      {operationsEnabled ? (
                        <div className="flex gap-2">
                          <Button
                            variant={scheduleMode === 'my' ? 'default' : 'outline'}
                            size="sm"
                            onClick={() => setScheduleMode('my')}
                          >
                            Мои занятия
                          </Button>
                          <Button
                            variant={scheduleMode === 'all' ? 'default' : 'outline'}
                            size="sm"
                            onClick={loadFullSchedule}
                          >
                            Полная сетка
                          </Button>
                        </div>
                      ) : null}
                    </div>
                  </div>
                  <ScheduleGrid
                    data={currentSchedule || dashboard.instructorSchedule}
                    zoom={scheduleZoom}
                  />
                </div>
              ) : null}

              {activeTab === 'workload' ? (
                <WorkloadCalendar
                  data={dashboard.workload}
                  onPeriodChange={(from, to) => setRange({ from, to })}
                  onLessonClick={(date) => jumpToScheduleDay(date)}
                  actions={renderWorkloadActions()}
                />
              ) : null}

              {activeTab === 'notifications' ? (
                <NotificationsFeed notifications={dashboard.notifications} maxItems={50} />
              ) : null}

              {activeTab === 'security' ? (
                <ChangePassword onSubmit={handlePasswordChange} />
              ) : null}

              {activeTab === 'admin' && operationsEnabled ? (
                adminLoading && !users.length && !groups.length ? (
                  <div className="flex min-h-[240px] items-center justify-center rounded-2xl border border-border bg-white shadow-sm">
                    <Loader2 className="h-5 w-5 animate-spin text-primary" />
                  </div>
                ) : (
                  <AdminWorkspace
                    currentUser={user}
                    users={users}
                    groups={groups}
                    canImport={user.role === 'ADMIN'}
                    canManageUsers={canManageUsers}
                    canManageGroups={canManageGroups}
                    importing={importing}
                    importResult={importResult}
                    onImport={handleImport}
                    onRefresh={loadAdminData}
                    range={range}
                  />
                )
              ) : null}
            </>
          ) : null}
        </main>
      </div>
    </div>
  )
}

export default function CabinetPage() {
  return (
    <Suspense fallback={<CabinetPageFallback />}>
      <CabinetPageContent />
    </Suspense>
  )
}
