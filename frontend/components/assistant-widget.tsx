'use client'

import { useEffect, useRef, useState } from 'react'
import { Send, Sparkles, X } from 'lucide-react'
import { useI18n } from '@/lib/i18n'
import { cn } from '@/lib/utils'

interface Message {
  role: 'user' | 'assistant'
  text: string
}

interface Captcha {
  id: string
  question: string
  pendingQuestion: string
}

export function AssistantWidget() {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])
  const [captcha, setCaptcha] = useState<Captcha | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages, loading])

  async function send() {
    const value = input.trim()
    if (!value || loading) {
      return
    }

    // Обычный вопрос либо ответ на капчу (тогда шлём отложенный вопрос + решение).
    const question = captcha ? captcha.pendingQuestion : value
    const body = captcha
      ? { question, captchaId: captcha.id, captchaAnswer: value }
      : { question }

    setInput('')
    if (!captcha) {
      setMessages((prev) => [...prev, { role: 'user', text: value }])
    }
    setLoading(true)
    try {
      const res = await fetch('/api/assistant', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const data = await res.json()

      if (data.captchaRequired) {
        const wasCaptcha = Boolean(captcha)
        setCaptcha({ id: data.captchaId, question: data.captchaQuestion, pendingQuestion: question })
        setMessages((prev) => [
          ...prev,
          {
            role: 'assistant',
            text: `${wasCaptcha ? t('assistant.captchaWrong') + ' ' : ''}${t('assistant.captchaHint')} ${data.captchaQuestion}`,
          },
        ])
        return
      }

      setCaptcha(null)
      setMessages((prev) => [...prev, { role: 'assistant', text: data.answer || t('assistant.error') }])
    } catch {
      setCaptcha(null)
      setMessages((prev) => [...prev, { role: 'assistant', text: t('assistant.error') }])
    } finally {
      setLoading(false)
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={t('assistant.title')}
        className="fixed bottom-20 right-4 z-50 flex h-12 w-12 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform hover:scale-105"
      >
        <Sparkles className="h-5 w-5" />
      </button>
    )
  }

  return (
    <div className="fixed bottom-20 right-4 z-50 flex h-[70vh] max-h-[560px] w-[min(92vw,380px)] flex-col overflow-hidden rounded-2xl border border-border bg-white shadow-2xl">
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-primary" />
          <span className="text-sm font-semibold text-slate-950">{t('assistant.title')}</span>
        </div>
        <button type="button" onClick={() => setOpen(false)} aria-label="close">
          <X className="h-4 w-4 text-slate-500 transition-colors hover:text-slate-950" />
        </button>
      </div>

      <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto px-4 py-3">
        {messages.length === 0 ? (
          <div className="text-sm text-muted-foreground">{t('assistant.greeting')}</div>
        ) : (
          messages.map((message, index) => (
            <div key={index} className={cn('flex', message.role === 'user' ? 'justify-end' : 'justify-start')}>
              <div
                className={cn(
                  'max-w-[85%] whitespace-pre-wrap rounded-2xl px-3 py-2 text-sm',
                  message.role === 'user'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-slate-100 text-slate-950'
                )}
              >
                {message.text}
              </div>
            </div>
          ))
        )}
        {loading ? <div className="text-sm text-muted-foreground">{t('assistant.thinking')}</div> : null}
      </div>

      <div className="flex items-center gap-2 border-t border-border p-3">
        <input
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              void send()
            }
          }}
          inputMode={captcha ? 'numeric' : 'text'}
          placeholder={captcha ? `${captcha.question} = ?` : t('assistant.placeholder')}
          className="flex-1 rounded-xl border border-border px-3 py-2 text-sm outline-none focus:border-primary"
        />
        <button
          type="button"
          onClick={() => void send()}
          disabled={loading || !input.trim()}
          aria-label={t('assistant.send')}
          className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground transition-opacity disabled:opacity-50"
        >
          <Send className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}
