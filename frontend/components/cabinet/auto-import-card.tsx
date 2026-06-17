'use client'

import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, CheckCircle2, CloudDownload, Loader2, RefreshCcw, Timer } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { autoImportApi } from '@/lib/api'
import { useI18n } from '@/lib/i18n'
import type { AutoImportSettings } from '@/lib/types'
import { cn } from '@/lib/utils'

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

function formatCountdown(target: string | null, t: (key: string) => string) {
  if (!target) return null
  const targetMs = new Date(target).getTime()
  const diff = targetMs - Date.now()
  if (Number.isNaN(targetMs) || diff <= 0) return `0${t('autoImport.s')}`
  const hours = Math.floor(diff / (60 * 60 * 1000))
  const minutes = Math.floor((diff % (60 * 60 * 1000)) / (60 * 1000))
  const seconds = Math.floor((diff % (60 * 1000)) / 1000)
  if (hours > 0) return `${hours}${t('autoImport.h')} ${minutes}${t('autoImport.m')}`
  if (minutes > 0) return `${minutes}${t('autoImport.m')} ${seconds}${t('autoImport.s')}`
  return `${seconds}${t('autoImport.s')}`
}

interface AutoImportCardProps {
  onImported?: () => void
}

export function AutoImportCard({ onImported }: AutoImportCardProps) {
  const { t } = useI18n()
  const [settings, setSettings] = useState<AutoImportSettings | null>(null)
  const [urlDraft, setUrlDraft] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [nowTick, setNowTick] = useState(0)

  useEffect(() => {
    void loadSettings()
  }, [])

  // Обновляем таймер раз в секунду пока есть nextRunAt
  useEffect(() => {
    if (!settings?.nextRunAt) return
    const id = window.setInterval(() => setNowTick((value) => value + 1), 1000)
    return () => window.clearInterval(id)
  }, [settings?.nextRunAt])

  const countdown = useMemo(() => {
    void nowTick
    return formatCountdown(settings?.nextRunAt || null, t)
  }, [settings?.nextRunAt, nowTick, t])

  async function loadSettings() {
    setLoading(true)
    try {
      const data = await autoImportApi.getSettings()
      setSettings(data)
      setUrlDraft(data.sourceUrl || '')
      setError(null)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('autoImport.errLoad'))
    } finally {
      setLoading(false)
    }
  }

  async function handleToggle(nextEnabled: boolean) {
    if (!settings) return
    await persistSettings({ enabled: nextEnabled, sourceUrl: settings.sourceUrl })
  }

  async function handleSaveUrl() {
    if (!settings) return
    if (!urlDraft.trim()) {
      setError(t('autoImport.errUrlEmpty'))
      return
    }
    await persistSettings({ enabled: settings.enabled, sourceUrl: urlDraft.trim() })
  }

  async function persistSettings(payload: { enabled: boolean; sourceUrl: string }) {
    setSaving(true)
    try {
      const updated = await autoImportApi.updateSettings(payload)
      setSettings(updated)
      setUrlDraft(updated.sourceUrl || '')
      setError(null)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('autoImport.errSave'))
    } finally {
      setSaving(false)
    }
  }

  async function handleRunNow() {
    setRunning(true)
    try {
      const updated = await autoImportApi.runNow()
      setSettings(updated)
      setError(null)
      if (updated.lastStatus === 'OK') {
        onImported?.()
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('autoImport.errRun'))
    } finally {
      setRunning(false)
    }
  }

  if (loading && !settings) {
    return (
      <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          {t('autoImport.loading')}
        </div>
      </section>
    )
  }

  if (!settings) {
    return (
      <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
        <div className="text-sm text-muted-foreground">{t('autoImport.unavailable')}</div>
        {error ? <div className="mt-2 text-sm text-red-600">{error}</div> : null}
      </section>
    )
  }

  const lastStatusIs = (status: AutoImportSettings['lastStatus']) => settings.lastStatus === status
  const urlChanged = urlDraft.trim() !== (settings.sourceUrl || '').trim()

  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="rounded-xl bg-primary/10 p-3">
            <CloudDownload className="h-5 w-5 text-primary" />
          </div>
          <div>
            <div className="text-sm font-semibold text-slate-950">
              {t('autoImport.title')}
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              {t('autoImport.descBefore')} {t('autoImport.runTimes')}.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Label htmlFor="auto-import-toggle" className="text-sm">
            {settings.enabled ? t('autoImport.enabled') : t('autoImport.disabled')}
          </Label>
          <Switch
            id="auto-import-toggle"
            checked={settings.enabled}
            onCheckedChange={handleToggle}
            disabled={saving}
          />
        </div>
      </div>

      {settings.enabled && settings.nextRunAt ? (
        <div className="mt-4 inline-flex items-center gap-2 rounded-full border border-border bg-slate-50 px-3 py-1 text-xs text-slate-600">
          <Timer className="h-3.5 w-3.5 text-primary" />
          <span>
            {t('autoImport.nextRun')} <span className="font-medium text-slate-950">{countdown}</span>
            {' • '}
            <span className="text-slate-500">{formatDateTime(settings.nextRunAt)}</span>
          </span>
        </div>
      ) : null}

      <div className="mt-5 space-y-3">
        <Label htmlFor="auto-import-url" className="text-xs uppercase tracking-[0.14em] text-slate-500">
          {t('autoImport.sourceUrl')}
        </Label>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            id="auto-import-url"
            value={urlDraft}
            onChange={(event) => setUrlDraft(event.target.value)}
            placeholder="https://docs.google.com/spreadsheets/d/.../edit?gid=..."
            className="min-w-[280px] flex-1"
          />
          <Button
            variant="outline"
            size="sm"
            onClick={handleSaveUrl}
            disabled={!urlChanged || saving}
          >
            {t('autoImport.saveUrl')}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          {t('autoImport.shareHint')}
        </p>
      </div>

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <Button onClick={handleRunNow} disabled={running}>
          {running ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCcw className="mr-2 h-4 w-4" />}
          {running ? t('autoImport.updating') : t('autoImport.updateNow')}
        </Button>
        <Button variant="ghost" size="sm" onClick={loadSettings} disabled={loading}>
          {t('autoImport.refreshStatus')}
        </Button>
      </div>

      <div className="mt-5 grid gap-3 rounded-xl border border-border bg-slate-50 p-4 sm:grid-cols-2">
        <StatusLine label={t('autoImport.lastRun')} value={formatDateTime(settings.lastRunAt)} />
        <StatusLine
          label={t('autoImport.status')}
          value={
            <span
              className={cn(
                'inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium',
                lastStatusIs('OK') && 'bg-emerald-100 text-emerald-700',
                lastStatusIs('ERROR') && 'bg-red-100 text-red-700',
                lastStatusIs('RUNNING') && 'bg-amber-100 text-amber-700',
                !settings.lastStatus && 'bg-slate-100 text-slate-600'
              )}
            >
              {lastStatusIs('OK') ? <CheckCircle2 className="h-3.5 w-3.5" /> : null}
              {lastStatusIs('ERROR') ? <AlertTriangle className="h-3.5 w-3.5" /> : null}
              {lastStatusIs('RUNNING') ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
              {settings.lastStatus || t('autoImport.notRun')}
            </span>
          }
        />
        {settings.lastImportedGroups != null ? (
          <StatusLine label={t('autoImport.groupsImported')} value={settings.lastImportedGroups} />
        ) : null}
        {settings.updatedBy ? (
          <StatusLine label={t('autoImport.updatedBy')} value={settings.updatedBy} />
        ) : null}
      </div>

      {settings.lastStatus === 'ERROR' && settings.lastError ? (
        <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <div className="flex items-start gap-2">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <div className="font-medium">{t('autoImport.lastRunError')}</div>
              <div className="mt-1 text-xs">{settings.lastError}</div>
            </div>
          </div>
        </div>
      ) : null}

      {error ? (
        <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-700">
          {error}
        </div>
      ) : null}
    </section>
  )
}

function StatusLine({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500">
        {label}
      </div>
      <div className="mt-1 text-sm text-slate-950">{value}</div>
    </div>
  )
}
