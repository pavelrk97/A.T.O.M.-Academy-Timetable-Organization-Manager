'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { translations, type Language } from './i18n-dictionary'

interface I18nContextType {
  lang: Language
  setLang: (lang: Language) => void
  t: (key: string) => string
}

const I18nContext = createContext<I18nContextType | null>(null)
const LANG_STORAGE_KEY = 'atom_lang'

function readStoredLang(): Language {
  if (typeof window === 'undefined') {
    return 'ru'
  }

  const raw = window.localStorage.getItem(LANG_STORAGE_KEY)
  return raw === 'en' || raw === 'ru' ? raw : 'ru'
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Language>('ru')

  useEffect(() => {
    setLangState(readStoredLang())
  }, [])

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = lang
    }
  }, [lang])

  const setLang = useCallback((next: Language) => {
    setLangState(next)
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(LANG_STORAGE_KEY, next)
    }
  }, [])

  const t = useCallback(
    (key: string) => translations[key]?.[lang] ?? translations[key]?.ru ?? key,
    [lang]
  )

  return (
    <I18nContext.Provider value={{ lang, setLang, t }}>
      {children}
    </I18nContext.Provider>
  )
}

export function useI18n() {
  const context = useContext(I18nContext)
  if (!context) {
    throw new Error('useI18n must be used within LanguageProvider')
  }
  return context
}
