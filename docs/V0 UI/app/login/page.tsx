'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { AlertCircle, Loader2, LogIn } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/lib/auth-context'

export default function LoginPage() {
  const router = useRouter()
  const { login, isLoading } = useAuth()
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
      setError('Проверь логин и пароль. Backend не выдал токен доступа.')
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
            <h1 className="mt-6 text-3xl font-semibold text-slate-950">Вход в A.T.O.M.</h1>
            <p className="mt-2 text-sm text-slate-600">
              Используй свою учётную запись, чтобы открыть{' '}
              <span className="font-medium">Academic Timetable Organization Manager</span>. Вход
              выполняется через <code>POST /api/auth/login</code>, а защищённые запросы идут с
              bearer-токеном доступа.
            </p>

            <form onSubmit={submit} className="mt-8 space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">Логин</Label>
                <Input
                  id="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  autoComplete="username"
                  placeholder="Введите логин"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Пароль</Label>
                <Input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  placeholder="Введите пароль"
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
                    Вхожу...
                  </>
                ) : (
                  <>
                    <LogIn className="mr-2 h-4 w-4" />
                    Войти
                  </>
                )}
              </Button>
            </form>

            <div className="mt-6 rounded-2xl border border-border bg-slate-50 p-4 text-sm text-slate-600">
              <div className="font-medium text-slate-950">Управление доступом</div>
              <div className="mt-2 text-xs leading-5 text-muted-foreground">
                Учётные записи, роли и временные пароли управляются через backend и
                административные сценарии. Экран не показывает служебные подсказки и личные
                данные для входа.
              </div>
            </div>

            <div className="mt-6 text-sm text-muted-foreground">
              Или открой{' '}
              <Link href="/schedule" className="font-medium text-primary hover:underline">
                публичное расписание
              </Link>
              .
            </div>
          </section>

          <section className="rounded-[28px] border border-border bg-gradient-to-br from-primary/8 via-white to-sky-50 p-8">
            <div className="max-w-xl">
              <div className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                кабинет + расписание
              </div>
              <h2 className="mt-6 text-4xl font-semibold tracking-tight text-slate-950">
                Один вход,
                <br />
                две рабочие зоны
              </h2>
              <p className="mt-4 text-lg leading-8 text-slate-600">
                После входа открывается личный кабинет с профилем, расписанием, уведомлениями,
                нагрузкой и ролевыми операциями. Публичная страница расписания остаётся отдельной
                поверхностью только для чтения.
              </p>
              <div className="mt-8 grid gap-4 sm:grid-cols-2">
                {[
                  'Личное расписание и нагрузка',
                  'Пароль и безопасность аккаунта',
                  'Уведомления по дням',
                  'Операции по роли пользователя',
                ].map((item) => (
                  <div
                    key={item}
                    className="rounded-2xl border border-border bg-white/80 px-4 py-4 text-sm text-slate-700 shadow-sm"
                  >
                    {item}
                  </div>
                ))}
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
