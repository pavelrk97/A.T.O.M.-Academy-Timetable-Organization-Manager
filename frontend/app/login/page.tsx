'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { AlertCircle, Loader2, LogIn } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/lib/auth-context'
import { useI18n } from '@/lib/i18n'

export default function LoginPage() {
  const router = useRouter()
  const { login, isLoading } = useAuth()
  const { t } = useI18n()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')

    const success = await login(username, password)
    if (success) {
      router.push('/cabinet')
    } else {
      setError(t('login.error'))
    }

    setSubmitting(false)
  }

  return (
    <div className="min-h-screen bg-transparent">
      <div className="mx-auto flex min-h-screen max-w-[1600px] items-center px-4 py-10 lg:px-8">
        <div className="grid w-full gap-8 lg:grid-cols-[0.95fr_1.05fr]">
          <section className="rounded-[28px] border border-border bg-white p-8 shadow-[0_30px_70px_rgba(23,64,116,0.08)]">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
              <span className="text-lg font-bold">A</span>
            </div>
            <h1 className="mt-6 text-3xl font-semibold text-slate-950">{t('login.title')}</h1>
            <p className="mt-2 text-sm text-slate-600">
              {t('login.introBefore')}{' '}
              <span className="font-medium">Academic Timetable Organization Manager</span>
              {t('login.introMid')} <code>POST /api/auth/login</code>
              {t('login.introAfter')}
            </p>

            <form onSubmit={submit} className="mt-8 space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">{t('login.username')}</Label>
                <Input
                  id="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  autoComplete="username"
                  placeholder={t('login.usernamePlaceholder')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">{t('login.password')}</Label>
                <Input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  placeholder={t('login.passwordPlaceholder')}
                />
              </div>

              {error ? (
                <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 px-3 py-3 text-sm text-red-700">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                  {error}
                </div>
              ) : null}

              <Button type="submit" className="w-full rounded-xl" disabled={isLoading || submitting}>
                {submitting ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('login.signingIn')}
                  </>
                ) : (
                  <>
                    <LogIn className="mr-2 h-4 w-4" />
                    {t('header.login')}
                  </>
                )}
              </Button>
            </form>

            <div className="mt-6 rounded-2xl border border-border bg-slate-50 p-4 text-sm text-slate-600">
              <div className="font-medium text-slate-950">{t('login.accessTitle')}</div>
              <div className="mt-2 text-xs leading-5 text-muted-foreground">
                {t('login.accessBody')}
              </div>
            </div>

            <div className="mt-6 text-sm text-muted-foreground">
              {t('login.orOpen')}{' '}
              <Link href="/schedule" className="font-medium text-primary hover:underline">
                {t('login.publicSchedule')}
              </Link>
              .
            </div>
          </section>

          <section className="rounded-[28px] border border-border bg-gradient-to-br from-primary/8 via-white to-sky-50 p-8">
            <div className="max-w-xl">
              <div className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                {t('login.badge')}
              </div>
              <h2 className="mt-6 text-4xl font-semibold tracking-tight text-slate-950">
                {t('login.heroTitle1')}
                <br />
                {t('login.heroTitle2')}
              </h2>
              <p className="mt-4 text-lg leading-8 text-slate-600">
                {t('login.heroText')}
              </p>
              <div className="mt-8 grid gap-4 sm:grid-cols-2">
                {['login.feature1', 'login.feature2', 'login.feature3', 'login.feature4'].map(
                  (key) => (
                    <div
                      key={key}
                      className="rounded-2xl border border-border bg-white/80 px-4 py-4 text-sm text-slate-700 shadow-sm"
                    >
                      {t(key)}
                    </div>
                  )
                )}
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
