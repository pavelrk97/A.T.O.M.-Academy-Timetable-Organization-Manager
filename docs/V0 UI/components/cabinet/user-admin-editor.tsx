'use client'

import { useEffect, useMemo, useState } from 'react'
import { Plus, RefreshCcw, Save, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { usersApi } from '@/lib/api'
import type { User, UserRole, UserUpsertRequest } from '@/lib/types'

interface UserAdminEditorProps {
  users: User[]
  onChanged: () => Promise<void>
}

const ROLE_OPTIONS: UserRole[] = ['ADMIN', 'EDITOR', 'INSTRUCTOR']

function roleLabel(role: UserRole) {
  switch (role) {
    case 'ADMIN':
      return 'Администратор'
    case 'EDITOR':
      return 'Редактор'
    case 'INSTRUCTOR':
      return 'Инструктор'
    default:
      return role
  }
}

function createEmptyForm(): UserUpsertRequest {
  return {
    username: '',
    password: '',
    fullName: '',
    displayName: '',
    email: '',
    phone: '',
    position: '',
    department: '',
    role: 'INSTRUCTOR',
    active: true,
    canTeach: true,
    editorAccess: false,
  }
}

function mapUserToForm(user: User): UserUpsertRequest {
  return {
    username: user.username,
    password: '',
    fullName: user.fullName,
    displayName: user.displayName || '',
    email: user.email || '',
    phone: user.phone || '',
    position: user.position || '',
    department: user.department || '',
    role: user.role,
    active: user.active,
    canTeach: user.canTeach,
    editorAccess: user.editorAccess,
  }
}

function normalizePayload(form: UserUpsertRequest): UserUpsertRequest {
  return {
    ...form,
    username: form.username.trim(),
    password: form.password,
    fullName: form.fullName.trim(),
    displayName: form.displayName?.trim() || null,
    email: form.email?.trim() || null,
    phone: form.phone?.trim() || null,
    position: form.position?.trim() || null,
    department: form.department?.trim() || null,
  }
}

export function UserAdminEditor({ users, onChanged }: UserAdminEditorProps) {
  const sortedUsers = useMemo(
    () =>
      [...users].sort((left, right) =>
        (left.displayName || left.fullName || left.username).localeCompare(
          right.displayName || right.fullName || right.username,
          'ru'
        )
      ),
    [users]
  )

  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [form, setForm] = useState<UserUpsertRequest>(createEmptyForm())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    if (!selectedUserId) {
      return
    }

    const selectedUser = users.find((user) => user.id === selectedUserId)
    if (!selectedUser) {
      setSelectedUserId(null)
      setForm(createEmptyForm())
      return
    }

    setForm(mapUserToForm(selectedUser))
  }, [selectedUserId, users])

  function startCreate() {
    setSelectedUserId(null)
    setForm(createEmptyForm())
    setError('')
    setSuccess('')
  }

  async function handleSubmit() {
    if (!form.username.trim()) {
      setError('Логин не может быть пустым.')
      return
    }

    if (!form.fullName.trim()) {
      setError('ФИО не может быть пустым.')
      return
    }

    if (!form.password.trim()) {
      setError('Пароль обязателен для создания и обновления пользователя.')
      return
    }

    setSaving(true)
    setError('')
    setSuccess('')

    try {
      const payload = normalizePayload(form)
      const savedUser = selectedUserId
        ? await usersApi.update(selectedUserId, payload)
        : await usersApi.create(payload)

      await onChanged()
      setSelectedUserId(savedUser.id)
      setForm((current) => ({
        ...current,
        password: '',
      }))
      setSuccess(
        selectedUserId
          ? 'Пользователь обновлён.'
          : 'Пользователь создан.'
      )
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : 'Не удалось сохранить пользователя.'
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-2xl border border-border bg-slate-50 p-4">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm font-semibold text-slate-950">Пользователи</div>
          <div className="text-xs text-muted-foreground">
            Создание и редактирование учётных записей доступно только администратору.
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={startCreate}>
            <Plus className="mr-2 h-4 w-4" />
            Новый пользователь
          </Button>
          <Button variant="outline" size="sm" onClick={onChanged}>
            <RefreshCcw className="mr-2 h-4 w-4" />
            Обновить
          </Button>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[320px_1fr]">
        <div className="max-h-[520px] space-y-2 overflow-auto rounded-xl border border-border bg-white p-3">
          {sortedUsers.map((user) => (
            <button
              key={user.id}
              type="button"
              onClick={() => {
                setSelectedUserId(user.id)
                setError('')
                setSuccess('')
              }}
              className={`w-full rounded-xl border px-3 py-3 text-left transition-colors ${
                selectedUserId === user.id
                  ? 'border-primary bg-primary/5'
                  : 'border-border hover:bg-slate-50'
              }`}
            >
              <div className="flex items-start gap-3">
                <div className="rounded-full bg-primary/10 p-2 text-primary">
                  <UserRound className="h-4 w-4" />
                </div>
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium text-slate-950">
                    {user.displayName || user.fullName || user.username}
                  </div>
                  <div className="truncate text-xs text-muted-foreground">
                    {user.username}
                  </div>
                  <div className="mt-1 text-xs text-slate-600">
                    {roleLabel(user.role)}
                    {user.editorAccess && user.role === 'INSTRUCTOR'
                      ? ' • editor access'
                      : ''}
                  </div>
                </div>
              </div>
            </button>
          ))}
        </div>

        <div className="rounded-xl border border-border bg-white p-4">
          <div className="mb-4 text-sm font-semibold text-slate-950">
            {selectedUserId ? 'Редактирование пользователя' : 'Создание пользователя'}
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Логин
              </span>
              <Input
                value={form.username}
                onChange={(event) =>
                  setForm((current) => ({ ...current, username: event.target.value }))
                }
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Пароль
              </span>
              <Input
                type="password"
                value={form.password}
                onChange={(event) =>
                  setForm((current) => ({ ...current, password: event.target.value }))
                }
                placeholder={selectedUserId ? 'Укажи новый пароль для обновления' : 'Например, 12345'}
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                ФИО
              </span>
              <Input
                value={form.fullName}
                onChange={(event) =>
                  setForm((current) => ({ ...current, fullName: event.target.value }))
                }
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Отображаемое имя
              </span>
              <Input
                value={form.displayName || ''}
                onChange={(event) =>
                  setForm((current) => ({ ...current, displayName: event.target.value }))
                }
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Email
              </span>
              <Input
                value={form.email || ''}
                onChange={(event) =>
                  setForm((current) => ({ ...current, email: event.target.value }))
                }
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Телефон
              </span>
              <Input
                value={form.phone || ''}
                onChange={(event) =>
                  setForm((current) => ({ ...current, phone: event.target.value }))
                }
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Должность
              </span>
              <Input
                value={form.position || ''}
                onChange={(event) =>
                  setForm((current) => ({ ...current, position: event.target.value }))
                }
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Подразделение
              </span>
              <Input
                value={form.department || ''}
                onChange={(event) =>
                  setForm((current) => ({ ...current, department: event.target.value }))
                }
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Роль
              </span>
              <select
                value={form.role}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    role: event.target.value as UserRole,
                  }))
                }
                className="h-10 w-full rounded-xl border border-border bg-white px-3 text-sm"
              >
                {ROLE_OPTIONS.map((role) => (
                  <option key={role} value={role}>
                    {roleLabel(role)}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="mt-4 grid gap-3 md:grid-cols-3">
            <label className="flex items-center gap-3 rounded-xl border border-border px-3 py-3 text-sm">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(event) =>
                  setForm((current) => ({ ...current, active: event.target.checked }))
                }
              />
              Активен
            </label>

            <label className="flex items-center gap-3 rounded-xl border border-border px-3 py-3 text-sm">
              <input
                type="checkbox"
                checked={form.canTeach}
                onChange={(event) =>
                  setForm((current) => ({ ...current, canTeach: event.target.checked }))
                }
              />
              Может вести занятия
            </label>

            <label className="flex items-center gap-3 rounded-xl border border-border px-3 py-3 text-sm">
              <input
                type="checkbox"
                checked={form.editorAccess}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    editorAccess: event.target.checked,
                  }))
                }
              />
              Editor access
            </label>
          </div>

          {error ? (
            <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-3 text-sm text-red-700">
              {error}
            </div>
          ) : null}

          {success ? (
            <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-3 text-sm text-emerald-700">
              {success}
            </div>
          ) : null}

          <div className="mt-5 flex flex-wrap gap-2">
            <Button onClick={handleSubmit} disabled={saving}>
              <Save className="mr-2 h-4 w-4" />
              {saving
                ? 'Сохраняю...'
                : selectedUserId
                  ? 'Сохранить пользователя'
                  : 'Создать пользователя'}
            </Button>
            <Button variant="outline" onClick={startCreate}>
              <Plus className="mr-2 h-4 w-4" />
              Очистить форму
            </Button>
          </div>
        </div>
      </div>
    </section>
  )
}
