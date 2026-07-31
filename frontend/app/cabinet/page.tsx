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
  CalendarRange,
  Download,
  FileCog,
  LayoutDashboard,
  Loader2,
  LockKeyhole,
  Rows3,
  UserRound,
  Users,
  ZoomIn,
  ZoomOut,
} from 'lucide-react'
import { Header } from '@/components/header'
import { DashboardStats } from '@/components/cabinet/dashboard-stats'
import { ProfileSection } from '@/components/cabinet/profile-section'
import { ChangePassword } from '@/components/cabinet/change-password'
import { NotificationsFeed } from '@/components/cabinet/notifications-feed'
import { WorkloadCalendar } from '@/components/cabinet/workload-calendar'
import { WorkloadSummaryTable } from '@/components/cabinet/workload-summary-table'
import { InstructorMultiSelect } from '@/components/cabinet/instructor-multi-select'
import { AdminWorkspace } from '@/components/cabinet/admin-workspace'
import { ScheduleGrid } from '@/components/schedule/schedule-grid'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/lib/auth-context'
import { useI18n } from '@/lib/i18n'
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
  WorkloadSummary,
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

function roleLabel(user: User | null | undefined, t: (key: string) => string) {
  if (!user) {
    return ''
  }
  if (user.role === 'ADMIN') return t('role.admin')
  if (user.role === 'EDITOR') return t('role.editor')
  if (user.editorAccess) return t('role.instructorEditor')
  return t('role.instructor')
}

function createTabItems(user?: User | null) {
  const items: { id: CabinetTab; labelKey: string; icon: ComponentType<{ className?: string }> }[] = [
    { id: 'dashboard', labelKey: 'cabinet.tab.dashboard', icon: LayoutDashboard },
    { id: 'profile', labelKey: 'cabinet.tab.profile', icon: UserRound },
    { id: 'schedule', labelKey: 'cabinet.tab.schedule', icon: Rows3 },
    { id: 'workload', labelKey: 'cabinet.tab.workload', icon: Rows3 },
    { id: 'notifications', labelKey: 'cabinet.tab.notifications', icon: Bell },
    { id: 'security', labelKey: 'cabinet.tab.security', icon: LockKeyhole },
  ]

  if (canUseOperations(user)) {
    items.push({ id: 'admin', labelKey: 'cabinet.tab.operations', icon: FileCog })
  }

  return items
}

function CabinetPageFallback() {
  const { t } = useI18n()
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="inline-flex items-center gap-3 rounded-2xl border border-border bg-white px-5 py-4 text-sm text-muted-foreground shadow-sm">
        <Loader2 className="h-5 w-5 animate-spin text-primary" />
        {t('cabinet.loadingAccount')}
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
  const { t } = useI18n()

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
  const [workloadInstructorIds, setWorkloadInstructorIds] = useState<string[]>([])
  const [workloadSummaries, setWorkloadSummaries] = useState<WorkloadSummary[]>([])
  const [instructorCalendar, setInstructorCalendar] = useState<DashboardData['workload'] | null>(null)
  const [includeBusinessTrips, setIncludeBusinessTrips] = useState(true)
  const [scheduleZoom, setScheduleZoom] = useState(100)
  const [scheduleZoomDraft, setScheduleZoomDraft] = useState(100)
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
    setScheduleZoomDraft(scheduleZoom)
  }, [scheduleZoom])

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
              : t('cabinet.errLoad')
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
  const workloadInstructorOptions = useMemo<User[]>(() => {
    const fromUsers = users.filter((candidate) => candidate.canTeach)
    if (fromUsers.length) {
      return [...fromUsers].sort((left, right) =>
        (left.displayName || left.fullName || left.username).localeCompare(
          right.displayName || right.fullName || right.username,
          'ru'
        )
      )
    }

    return workloadSummaries
      .map((item) => ({
        id: item.instructorId,
        username: item.instructorName,
        fullName: item.instructorName,
        displayName: item.instructorName,
        email: null,
        phone: null,
        position: null,
        department: null,
        role: 'INSTRUCTOR' as const,
        active: true,
        canTeach: true,
        editorAccess: false,
      }))
      .sort((left, right) => left.fullName.localeCompare(right.fullName, 'ru'))
  }, [users, workloadSummaries])

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
          : t('cabinet.errFullGrid')
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
    const canListUsers = user?.role === 'ADMIN' || user?.role === 'EDITOR'
    const [usersResult, groupsResult] = await Promise.allSettled([
      canListUsers ? usersApi.getAll() : Promise.resolve([] as User[]),
      groupsApi.getAll(),
    ])

    if (usersResult.status === 'fulfilled') {
      setUsers(usersResult.value)
    }
    if (groupsResult.status === 'fulfilled') {
      setGroups(groupsResult.value)
    }

    const failed = [usersResult, groupsResult].find(
      (result): result is PromiseRejectedResult => result.status === 'rejected'
    )
    if (failed && groupsResult.status === 'rejected') {
      const reason = failed.reason
      setError(
        reason instanceof Error && reason.message
          ? reason.message
          : t('cabinet.errOps')
      )
    }

    setAdminLoading(false)
  }

  async function loadWorkloadSummaries() {
    try {
      const summaries = await workloadApi.getAll({ from: range.from, to: range.to })
      setWorkloadSummaries(summaries)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : t('cabinet.errWorkloadList')
      )
    }
  }

  useEffect(() => {
    if (activeTab === 'admin' && operationsEnabled && (!users.length || !groups.length)) {
      void loadAdminData()
    }
  }, [activeTab, operationsEnabled, users.length, groups.length])

  useEffect(() => {
    if (activeTab !== 'workload') {
      return
    }

    void loadWorkloadSummaries()
    if ((user?.role === 'ADMIN' || user?.role === 'EDITOR') && !users.length) {
      void loadAdminData()
    }
  }, [activeTab, range.from, range.to, user?.role, users.length])

  // Дневной календарь под таблицей: если выбран ровно один инструктор — показываем его дни,
  // иначе (никто/несколько) остаётся собственная нагрузка текущего пользователя.
  useEffect(() => {
    if (activeTab !== 'workload' || workloadInstructorIds.length !== 1) {
      setInstructorCalendar(null)
      return
    }

    let cancelled = false
    void (async () => {
      try {
        const calendar = await workloadApi.getInstructorCalendar({
          instructorId: workloadInstructorIds[0],
          from: range.from,
          to: range.to,
        })
        if (!cancelled) {
          setInstructorCalendar(calendar)
        }
      } catch (caught) {
        if (!cancelled) {
          setInstructorCalendar(null)
          setError(
            caught instanceof Error && caught.message ? caught.message : t('cabinet.errWorkloadList')
          )
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [activeTab, workloadInstructorIds, range.from, range.to])

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

  async function handleExportAdminWorkload(mode: 'all' | 'selected') {
    setWorkloadExporting(true)
    setError('')
    try {
      const download = await workloadApi.exportCsv({
        instructorIds:
          mode === 'selected' && workloadInstructorIds.length
            ? workloadInstructorIds
            : undefined,
        from: range.from,
        to: range.to,
        includeBusinessTrips,
      })
      saveDownload(download)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : t('cabinet.errExport')
      )
    } finally {
      setWorkloadExporting(false)
    }
  }

  function commitScheduleZoom(value: number) {
    const next = clampZoom(value)
    setScheduleZoom(next)
    setScheduleZoomDraft(next)
  }

  function renderEditorialWorkloadActions() {
    const hasSelection = workloadInstructorIds.length > 0
    return (
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-end gap-2">
          <div className="min-w-[260px] flex-1">
            <InstructorMultiSelect
              instructors={workloadInstructorOptions}
              selectedIds={workloadInstructorIds}
              onChange={setWorkloadInstructorIds}
              placeholder={t('cabinet.allInstructorsSelect')}
              emptyHint={
                adminLoading
                  ? t('cabinet.loadingInstructors')
                  : t('cabinet.noCanTeach')
              }
            />
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleExportAdminWorkload('selected')}
            disabled={workloadExporting || !hasSelection}
          >
            <Download className="mr-2 h-4 w-4" />
            {hasSelection ? `${t('cabinet.selected')} (${workloadInstructorIds.length})` : t('cabinet.selected')}
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleExportAdminWorkload('all')}
            disabled={workloadExporting}
          >
            <Download className="mr-2 h-4 w-4" />
            {t('cabinet.all')}
          </Button>
        </div>
      </div>
    )
  }

  function renderWorkloadActions() {
    return renderEditorialWorkloadActions()
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
          value={scheduleZoomDraft}
          onChange={(event) => setScheduleZoomDraft(clampZoom(Number(event.target.value)))}
          onMouseUp={(event) => commitScheduleZoom(Number(event.currentTarget.value))}
          onTouchEnd={(event) => commitScheduleZoom(Number(event.currentTarget.value))}
          onKeyUp={(event) => commitScheduleZoom(Number(event.currentTarget.value))}
          onBlur={(event) => commitScheduleZoom(Number(event.currentTarget.value))}
        />
        <ZoomIn className="h-4 w-4 text-muted-foreground" />
        <span className="min-w-10 text-right text-xs font-medium text-slate-700">
          {scheduleZoomDraft}%
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
                {t('cabinet.sidebarTitle')}
              </div>
              <div className="mt-2 text-lg font-semibold text-slate-950">
                {user.displayName || user.fullName || user.username}
              </div>
              <div className="text-sm text-muted-foreground">{roleLabel(user, t)}</div>
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
                  {t(tab.labelKey)}
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
                  {t('cabinet.workspaceTitle')}
                </h1>
                <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
                  {t('cabinet.workspaceDesc')}
                </p>
              </div>

              <div className="grid gap-2 sm:grid-cols-2">
                <label className="space-y-1">
                  <span className="text-xs uppercase tracking-wide text-slate-500">{t('cabinet.from')}</span>
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
                  <span className="text-xs uppercase tracking-wide text-slate-500">{t('cabinet.to')}</span>
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
                {t(tab.labelKey)}
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
                {t('cabinet.loadingShort')}
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
                    periodLabel={`${range.from} - ${range.to}`}
                  />

                  <div className="grid gap-6 xl:grid-cols-[1.25fr_0.75fr]">
                    <section className="min-w-0 space-y-4">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <div>
                          <h2 className="text-xl font-semibold text-slate-950">{t('cabinet.myGridTitle')}</h2>
                          <p className="text-sm text-muted-foreground">
                            {t('cabinet.myGridDesc')}
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
                                {t('cabinet.onlyMine')}
                              </Button>
                              <Button
                                variant={scheduleMode === 'all' ? 'default' : 'outline'}
                                size="sm"
                                onClick={loadFullSchedule}
                              >
                                {t('cabinet.wholeAcademy')}
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
                      <h2 className="text-xl font-semibold text-slate-950">{t('cabinet.scheduleGridTitle')}</h2>
                      <p className="text-sm text-muted-foreground">
                        {t('cabinet.scheduleGridDesc')}
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
                            {t('cabinet.myLessons')}
                          </Button>
                          <Button
                            variant={scheduleMode === 'all' ? 'default' : 'outline'}
                            size="sm"
                            onClick={loadFullSchedule}
                          >
                            {t('cabinet.fullGrid')}
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
                <Accordion
                  type="multiple"
                  defaultValue={operationsEnabled ? ['workload-team'] : ['workload-calendar']}
                  className="space-y-3"
                >
                  {operationsEnabled ? (
                    <AccordionItem
                      value="workload-team"
                      className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm"
                    >
                      <AccordionTrigger className="px-5 py-4 hover:no-underline data-[state=open]:bg-slate-50/60">
                        <div className="flex flex-1 items-start gap-3 text-left">
                          <div className="rounded-xl bg-primary/10 p-2.5 shrink-0">
                            <Users className="h-5 w-5 text-primary" />
                          </div>
                          <div className="min-w-0">
                            <div className="text-sm font-semibold text-slate-950">{t('workload.teamTitle')}</div>
                            <div className="mt-0.5 text-xs text-muted-foreground line-clamp-2">
                              {t('workload.teamSubtitle')}
                            </div>
                          </div>
                        </div>
                      </AccordionTrigger>
                      <AccordionContent className="border-t border-border bg-slate-50/40 px-3 py-4 sm:px-5">
                        <WorkloadSummaryTable
                          summaries={workloadSummaries}
                          selectedIds={workloadInstructorIds}
                          includeBusinessTrips={includeBusinessTrips}
                        />
                      </AccordionContent>
                    </AccordionItem>
                  ) : null}

                  <AccordionItem
                    value="workload-calendar"
                    className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm"
                  >
                    <AccordionTrigger className="px-5 py-4 hover:no-underline data-[state=open]:bg-slate-50/60">
                      <div className="flex flex-1 items-start gap-3 text-left">
                        <div className="rounded-xl bg-primary/10 p-2.5 shrink-0">
                          <CalendarRange className="h-5 w-5 text-primary" />
                        </div>
                        <div className="min-w-0">
                          <div className="text-sm font-semibold text-slate-950">{t('home.feature3Title')}</div>
                          <div className="mt-0.5 text-xs text-muted-foreground line-clamp-2">
                            {t('workload.subtitle')}
                          </div>
                        </div>
                      </div>
                    </AccordionTrigger>
                    <AccordionContent className="border-t border-border bg-slate-50/40 px-3 py-4 sm:px-5">
                      <WorkloadCalendar
                        data={instructorCalendar ?? dashboard.workload}
                        onPeriodChange={(from, to) => setRange({ from, to })}
                        onLessonClick={(date) => jumpToScheduleDay(date)}
                        actions={renderWorkloadActions()}
                        includeBusinessTrips={includeBusinessTrips}
                        onIncludeBusinessTripsChange={setIncludeBusinessTrips}
                      />
                    </AccordionContent>
                  </AccordionItem>
                </Accordion>
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
