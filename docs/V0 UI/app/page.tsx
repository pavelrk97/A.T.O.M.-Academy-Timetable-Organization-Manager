'use client'

import type { ReactNode } from 'react'
import Link from 'next/link'
import { ArrowRight, BellRing, CalendarDays, Clock3, ShieldCheck, UserRound } from 'lucide-react'
import { Header } from '@/components/header'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/lib/auth-context'

export default function HomePage() {
  const { isAuthenticated } = useAuth()

  return (
    <div className="min-h-screen bg-transparent">
      <Header />

      <main>
        <section className="border-b border-border/70">
          <div className="mx-auto grid max-w-[1600px] gap-10 px-4 py-14 lg:grid-cols-[1.2fr_0.8fr] lg:px-8 lg:py-20">
            <div>
              <div className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                Rosatom style / operational UI
              </div>
              <h1 className="mt-6 max-w-4xl text-4xl font-semibold tracking-tight text-slate-950 sm:text-5xl lg:text-6xl">
                A.T.O.M. для расписания,
                <br />
                нагрузки и личного кабинета преподавателя
              </h1>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-slate-600">
                Рабочий интерфейс для академии: смотреть расписание как таблицу, быстро
                находить группы, видеть свою нагрузку и держать все личные настройки в одном месте.
              </p>

              <div className="mt-8 flex flex-wrap gap-3">
                <Link href="/schedule">
                  <Button size="lg" className="rounded-xl px-6">
                    <CalendarDays className="mr-2 h-5 w-5" />
                    Открыть расписание
                  </Button>
                </Link>
                <Link href={isAuthenticated ? '/cabinet' : '/login'}>
                  <Button variant="outline" size="lg" className="rounded-xl px-6">
                    {isAuthenticated ? 'Перейти в кабинет' : 'Войти в систему'}
                    <ArrowRight className="ml-2 h-5 w-5" />
                  </Button>
                </Link>
              </div>

              <div className="mt-6 text-sm text-muted-foreground">
                Тестовые учётные данные: `admin / admin123`, `instructor / instructor123`
              </div>
            </div>

            <div className="rounded-[28px] border border-border bg-white p-6 shadow-[0_24px_60px_rgba(23,64,116,0.08)]">
              <div className="grid gap-4">
                <FeatureCard
                  icon={<CalendarDays className="h-5 w-5 text-primary" />}
                  title="Spreadsheet-расписание"
                  description="Липкие заголовки, плотные ячейки, фильтры по периоду и по группе."
                />
                <FeatureCard
                  icon={<UserRound className="h-5 w-5 text-primary" />}
                  title="Личный кабинет"
                  description="Профиль, пароль, мои занятия, мои уведомления и рабочая нагрузка."
                />
                <FeatureCard
                  icon={<Clock3 className="h-5 w-5 text-primary" />}
                  title="Нагрузка по дням"
                  description="Часы, учебные дни и список уроков сразу в одном календарном представлении."
                />
                <FeatureCard
                  icon={<BellRing className="h-5 w-5 text-primary" />}
                  title="Ссылки на занятия"
                  description="Уведомления ведут прямо к дню и диапазону, где у инструктора есть пары."
                />
                <FeatureCard
                  icon={<ShieldCheck className="h-5 w-5 text-primary" />}
                  title="Роли и доступ"
                  description="Один UI для администратора, редактора и инструктора без изменений backend."
                />
              </div>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-[1600px] px-4 py-12 lg:px-8 lg:py-16">
          <div className="grid gap-4 lg:grid-cols-3">
            <HighlightCard
              title="Отдельная страница расписания"
              text="Большая таблица под широкие экраны, отдельная детальная панель и фильтры сверху."
            />
            <HighlightCard
              title="Отдельная страница личного кабинета"
              text="Профиль, смена пароля, моя сетка занятий, уведомления и админ-блок в одном месте."
            />
            <HighlightCard
              title="Живые данные вместо моков"
              text="UI работает через `/api/*` и может использовать текущий gateway без правок backend."
            />
          </div>
        </section>
      </main>
    </div>
  )
}

function FeatureCard({
  icon,
  title,
  description,
}: {
  icon: ReactNode
  title: string
  description: string
}) {
  return (
    <div className="rounded-2xl border border-border bg-slate-50 px-4 py-4">
      <div className="flex items-start gap-3">
        <div className="rounded-xl bg-primary/10 p-2.5">{icon}</div>
        <div>
          <div className="font-medium text-slate-950">{title}</div>
          <div className="mt-1 text-sm text-slate-600">{description}</div>
        </div>
      </div>
    </div>
  )
}

function HighlightCard({ title, text }: { title: string; text: string }) {
  return (
    <section className="rounded-2xl border border-border bg-white px-5 py-5 shadow-sm">
      <div className="text-base font-semibold text-slate-950">{title}</div>
      <div className="mt-2 text-sm leading-6 text-slate-600">{text}</div>
    </section>
  )
}
