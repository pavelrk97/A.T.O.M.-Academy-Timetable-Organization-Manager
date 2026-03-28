'use client'

import { useState } from 'react'
import { AlertCircle, CheckCircle2, Eye, EyeOff, LockKeyhole } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface ChangePasswordProps {
  onSubmit: (payload: {
    currentPassword: string
    newPassword: string
  }) => Promise<void>
}

export function ChangePassword({ onSubmit }: ChangePasswordProps) {
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState('')
  const [error, setError] = useState('')
  const [visible, setVisible] = useState({
    current: false,
    next: false,
    confirm: false,
  })
  const [form, setForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  })

  const toggle = (key: keyof typeof visible) => {
    setVisible((current) => ({ ...current, [key]: !current[key] }))
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSuccess('')
    setError('')

    if (form.newPassword.length < 6) {
      setError('Новый пароль должен быть не короче 6 символов.')
      return
    }

    if (form.newPassword !== form.confirmPassword) {
      setError('Подтверждение пароля не совпадает.')
      return
    }

    setSubmitting(true)
    try {
      await onSubmit({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      setForm({
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
      })
      setSuccess('Пароль обновлён. Новый пароль уже используется в текущей сессии.')
    } catch (caught) {
      const message =
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось сменить пароль.'
      setError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="rounded-2xl border border-border bg-white shadow-sm">
      <div className="border-b border-border px-5 py-4">
        <h3 className="text-lg font-semibold text-slate-950">Смена пароля</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Пароль меняется сразу в identity-service и сохраняется для этой фронтовой сессии.
        </p>
      </div>

      <form onSubmit={submit} className="space-y-4 px-5 py-5">
        {[
          { key: 'currentPassword', label: 'Текущий пароль', visibleKey: 'current' },
          { key: 'newPassword', label: 'Новый пароль', visibleKey: 'next' },
          { key: 'confirmPassword', label: 'Повторите новый пароль', visibleKey: 'confirm' },
        ].map((field) => (
          <div key={field.key} className="space-y-2">
            <Label htmlFor={field.key}>{field.label}</Label>
            <div className="relative">
              <LockKeyhole className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id={field.key}
                type={visible[field.visibleKey as keyof typeof visible] ? 'text' : 'password'}
                value={form[field.key as keyof typeof form]}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    [field.key]: event.target.value,
                  }))
                }
                className="pl-9 pr-11"
                autoComplete={field.key === 'currentPassword' ? 'current-password' : 'new-password'}
              />
              <button
                type="button"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-slate-950"
                onClick={() => toggle(field.visibleKey as keyof typeof visible)}
              >
                {visible[field.visibleKey as keyof typeof visible] ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <Eye className="h-4 w-4" />
                )}
              </button>
            </div>
          </div>
        ))}

        {error ? (
          <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 px-3 py-3 text-sm text-red-700">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            {error}
          </div>
        ) : null}

        {success ? (
          <div className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-3 text-sm text-emerald-700">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
            {success}
          </div>
        ) : null}

        <div className="flex items-center justify-between gap-3">
          <div className="text-xs text-muted-foreground">Минимум 6 символов.</div>
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Меняю пароль...' : 'Обновить пароль'}
          </Button>
        </div>
      </form>
    </section>
  )
}
