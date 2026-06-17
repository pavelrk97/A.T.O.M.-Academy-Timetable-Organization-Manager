'use client'

import { useEffect, useMemo, useState } from 'react'
import { Plus, RefreshCcw, Save, Trash2, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { usersApi } from '@/lib/api'
import { useI18n } from '@/lib/i18n'
import type { User, UserRole, UserUpsertRequest } from '@/lib/types'

interface UserAdminEditorProps {
  users: User[]
  onChanged: () => Promise<void>
}

const ROLE_OPTIONS: UserRole[] = ['ADMIN', 'EDITOR', 'INSTRUCTOR']

function roleLabel(role: UserRole, t: (key: string) => string) {
  switch (role) {
    case 'ADMIN':
      return t('role.admin')
    case 'EDITOR':
      return t('role.editor')
    case 'INSTRUCTOR':
      return t('role.instructor')
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
  const { t } = useI18n()
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
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    if (!selectedUserId) {
      return
    }

    const selectedUser = users.find((candidate) => candidate.id === selectedUserId)
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

  async function handleDelete() {
    if (!selectedUserId) {
      return
    }
    const target = users.find((candidate) => candidate.id === selectedUserId)
    const targetName = target?.displayName || target?.fullName || target?.username || t('user.fallback')
    if (
      typeof window !== 'undefined' &&
      !window.confirm(`${t('user.confirmDeleteA')}${targetName}${t('user.confirmDeleteB')}`)
    ) {
      return
    }

    setDeleting(true)
    setError('')
    setSuccess('')

    try {
      await usersApi.delete(selectedUserId)
      await onChanged()
      setSelectedUserId(null)
      setForm(createEmptyForm())
      setSuccess(`${t('user.deletedA')} ${targetName} ${t('user.deletedB')}`)
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : t('user.errDelete')
      )
    } finally {
      setDeleting(false)
    }
  }

  async function handleSubmit() {
    if (!form.username.trim()) {
      setError(t('user.errLoginEmpty'))
      return
    }

    if (!form.fullName.trim()) {
      setError(t('user.errNameEmpty'))
      return
    }

    if (!form.password.trim()) {
      setError(t('user.errPasswordRequired'))
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
      setSuccess(selectedUserId ? t('user.updated') : t('user.created'))
    } catch (caught) {
      setError(
        caught instanceof Error && caught.message
          ? caught.message
          : t('user.errSave')
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-2xl border border-border bg-slate-50 p-4">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm font-semibold text-slate-950">{t('admin.tileUsers')}</div>
          <div className="text-xs text-muted-foreground">
            {t('user.descAdminOnly')}
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={startCreate}>
            <Plus className="mr-2 h-4 w-4" />
            {t('user.newUser')}
          </Button>
          <Button variant="outline" size="sm" onClick={onChanged}>
            <RefreshCcw className="mr-2 h-4 w-4" />
            {t('user.refresh')}
          </Button>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[320px_1fr]">
        <div className="max-h-[520px] space-y-2 overflow-auto rounded-xl border border-border bg-white p-3">
          {sortedUsers.map((candidate) => (
            <button
              key={candidate.id}
              type="button"
              onClick={() => {
                setSelectedUserId(candidate.id)
                setError('')
                setSuccess('')
              }}
              className={`w-full rounded-xl border px-3 py-3 text-left transition-colors ${
                selectedUserId === candidate.id
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
                    {candidate.displayName || candidate.fullName || candidate.username}
                  </div>
                  <div className="truncate text-xs text-muted-foreground">
                    {candidate.username}
                  </div>
                  <div className="mt-1 text-xs text-slate-600">
                    {roleLabel(candidate.role, t)}
                    {candidate.editorAccess && candidate.role === 'INSTRUCTOR'
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
            {selectedUserId ? t('user.editUser') : t('user.createUser')}
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                {t('login.username')}
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
                {t('login.password')}
              </span>
              <Input
                type="password"
                value={form.password}
                onChange={(event) =>
                  setForm((current) => ({ ...current, password: event.target.value }))
                }
                placeholder={
                  selectedUserId
                    ? t('user.passwordUpdateHint')
                    : t('user.passwordStartHint')
                }
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                {t('user.fullName')}
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
                {t('user.displayName')}
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
                {t('user.phone')}
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
                {t('user.position')}
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
                {t('user.department')}
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
                {t('user.role')}
              </span>
              <select
                value={form.role}
                onChange={(event) => {
                  const nextRole = event.target.value as UserRole
                  setForm((current) => ({
                    ...current,
                    role: nextRole,
                    // EDITOR/ADMIN семантически имеют права редактирования —
                    // автоматически проставляем editorAccess. Для INSTRUCTOR
                    // флаг оставляем как был, чтобы админ мог управлять им вручную.
                    editorAccess:
                      nextRole === 'EDITOR' || nextRole === 'ADMIN' ? true : current.editorAccess,
                  }))
                }}
                className="h-10 w-full rounded-xl border border-border bg-white px-3 text-sm"
              >
                {ROLE_OPTIONS.map((role) => (
                  <option key={role} value={role}>
                    {roleLabel(role, t)}
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
              {t('user.active')}
            </label>

            <label className="flex items-center gap-3 rounded-xl border border-border px-3 py-3 text-sm">
              <input
                type="checkbox"
                checked={form.canTeach}
                onChange={(event) =>
                  setForm((current) => ({ ...current, canTeach: event.target.checked }))
                }
              />
              {t('user.canTeach')}
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
            <Button onClick={handleSubmit} disabled={saving || deleting}>
              <Save className="mr-2 h-4 w-4" />
              {saving
                ? t('user.saving')
                : selectedUserId
                  ? t('user.saveUser')
                  : t('user.createUserBtn')}
            </Button>
            <Button variant="outline" onClick={startCreate} disabled={saving || deleting}>
              <Plus className="mr-2 h-4 w-4" />
              {t('user.clearForm')}
            </Button>
            {selectedUserId ? (
              <Button
                variant="destructive"
                onClick={() => void handleDelete()}
                disabled={saving || deleting}
                className="ml-auto"
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {deleting ? t('user.deleting') : t('user.deleteUser')}
              </Button>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  )
}
