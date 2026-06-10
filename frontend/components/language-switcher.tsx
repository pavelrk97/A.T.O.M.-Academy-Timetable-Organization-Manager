'use client'

import { useI18n } from '@/lib/i18n'
import { cn } from '@/lib/utils'
import type { Language } from '@/lib/i18n-dictionary'

const options: Language[] = ['ru', 'en']

export function LanguageSwitcher() {
  const { lang, setLang } = useI18n()

  return (
    <div className="fixed bottom-4 right-4 z-50 flex items-center rounded-full border border-border/80 bg-white/95 p-1 shadow-sm backdrop-blur supports-[backdrop-filter]:bg-white/80">
      {options.map((code) => (
        <button
          key={code}
          type="button"
          onClick={() => setLang(code)}
          className={cn(
            'rounded-full px-3 py-1 text-xs font-semibold uppercase transition-colors',
            lang === code
              ? 'bg-primary text-primary-foreground'
              : 'text-slate-600 hover:text-slate-950'
          )}
        >
          {code}
        </button>
      ))}
    </div>
  )
}
