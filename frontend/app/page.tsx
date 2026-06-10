'use client'

import type { ReactNode } from 'react'
import Link from 'next/link'
import { ArrowRight, BellRing, CalendarDays, Clock3, ShieldCheck, UserRound } from 'lucide-react'
import { Header } from '@/components/header'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/lib/auth-context'
import { useI18n } from '@/lib/i18n'

export default function HomePage() {
  const { isAuthenticated } = useAuth()
  const { t } = useI18n()

  return (
    <div className="min-h-screen bg-transparent">
      <Header />

      <main>
        <section className="border-b border-border/70">
          <div className="mx-auto grid max-w-[1600px] gap-10 px-4 py-14 lg:grid-cols-[1.2fr_0.8fr] lg:px-8 lg:py-20">
            <div>
              <div className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                {t('home.badge')}
              </div>
              <h1 className="mt-6 max-w-4xl text-4xl font-semibold tracking-tight text-slate-950 sm:text-5xl lg:text-6xl">
                {t('home.heroTitle1')}
                <br />
                {t('home.heroTitle2')}
              </h1>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-slate-600">
                <span className="font-medium">Academic Timetable Organization Manager</span>{' '}
                {t('home.heroText')}
              </p>

              <div className="mt-8 flex flex-wrap gap-3">
                <Link href="/schedule">
                  <Button size="lg" className="rounded-xl px-6">
                    <CalendarDays className="mr-2 h-5 w-5" />
                    {t('home.openSchedule')}
                  </Button>
                </Link>
                <Link href={isAuthenticated ? '/cabinet' : '/login'}>
                  <Button variant="outline" size="lg" className="rounded-xl px-6">
                    {isAuthenticated ? t('home.openCabinet') : t('header.login')}
                    <ArrowRight className="ml-2 h-5 w-5" />
                  </Button>
                </Link>
              </div>

              <div className="mt-6 text-sm text-muted-foreground">
                {t('home.accessNote')}
              </div>
            </div>

            <div className="rounded-[28px] border border-border bg-white p-6 shadow-[0_24px_60px_rgba(23,64,116,0.08)]">
              <div className="grid gap-4">
                <FeatureCard
                  icon={<CalendarDays className="h-5 w-5 text-primary" />}
                  title={t('home.feature1Title')}
                  description={t('home.feature1Desc')}
                />
                <FeatureCard
                  icon={<UserRound className="h-5 w-5 text-primary" />}
                  title={t('home.feature2Title')}
                  description={t('home.feature2Desc')}
                />
                <FeatureCard
                  icon={<Clock3 className="h-5 w-5 text-primary" />}
                  title={t('home.feature3Title')}
                  description={t('home.feature3Desc')}
                />
                <FeatureCard
                  icon={<BellRing className="h-5 w-5 text-primary" />}
                  title={t('home.feature4Title')}
                  description={t('home.feature4Desc')}
                />
                <FeatureCard
                  icon={<ShieldCheck className="h-5 w-5 text-primary" />}
                  title={t('home.feature5Title')}
                  description={t('home.feature5Desc')}
                />
              </div>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-[1600px] px-4 py-12 lg:px-8 lg:py-16">
          <div className="grid gap-4 lg:grid-cols-3">
            <HighlightCard
              title={t('home.highlight1Title')}
              text={t('home.highlight1Text')}
            />
            <HighlightCard
              title={t('home.highlight2Title')}
              text={t('home.highlight2Text')}
            />
            <HighlightCard
              title={t('home.highlight3Title')}
              text={t('home.highlight3Text')}
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
