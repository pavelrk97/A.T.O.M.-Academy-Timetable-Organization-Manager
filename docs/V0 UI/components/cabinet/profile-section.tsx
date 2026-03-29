'use client'

import { useEffect, useState } from 'react'
import { BriefcaseBusiness, Building2, Check, Mail, PencilLine, Phone, UserRound, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { MyProfileUpdateRequest, User } from '@/lib/types'

interface ProfileSectionProps {
  user: User
  onUpdate: (payload: MyProfileUpdateRequest) => Promise<void>
  isEditable?: boolean
}

function roleLabel(role: string) {
  if (role === 'ADMIN') return 'Administrator'
  if (role === 'EDITOR') return 'Editor'
  return 'Instructor'
}

export function ProfileSection({
  user,
  onUpdate,
  isEditable = true,
}: ProfileSectionProps) {
  const visibleName = user.displayName || user.fullName || user.username
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<MyProfileUpdateRequest>({
    displayName: user.displayName || user.fullName || '',
    email: user.email || '',
    phone: user.phone || '',
    position: user.position || '',
    department: user.department || '',
  })

  useEffect(() => {
    setForm({
      displayName: user.displayName || user.fullName || '',
      email: user.email || '',
      phone: user.phone || '',
      position: user.position || '',
      department: user.department || '',
    })
  }, [user])

  const save = async () => {
    setSaving(true)
    try {
      await onUpdate(form)
      setEditing(false)
    } finally {
      setSaving(false)
    }
  }

  const reset = () => {
    setForm({
      displayName: user.displayName || user.fullName || '',
      email: user.email || '',
      phone: user.phone || '',
      position: user.position || '',
      department: user.department || '',
    })
    setEditing(false)
  }

  const fields = [
    { key: 'displayName', label: 'Display name', icon: UserRound, type: 'text' },
    { key: 'email', label: 'Email', icon: Mail, type: 'email' },
    { key: 'phone', label: 'Phone', icon: Phone, type: 'tel' },
    { key: 'position', label: 'Position', icon: BriefcaseBusiness, type: 'text' },
    { key: 'department', label: 'Department', icon: Building2, type: 'text' },
  ] as const

  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Profile</h3>
          <p className="text-sm text-muted-foreground">
            Update the display name and contact data shown in the cabinet. The system name used by
            imports stays managed separately.
          </p>
        </div>
        {isEditable ? (
          editing ? (
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={reset} disabled={saving}>
                <X className="mr-2 h-4 w-4" />
                Cancel
              </Button>
              <Button size="sm" onClick={save} disabled={saving}>
                <Check className="mr-2 h-4 w-4" />
                {saving ? 'Saving...' : 'Save'}
              </Button>
            </div>
          ) : (
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <PencilLine className="mr-2 h-4 w-4" />
              Edit
            </Button>
          )
        ) : null}
      </div>

      <div className="grid gap-6 px-5 py-5 lg:grid-cols-[280px_1fr]">
        <div className="rounded-2xl border border-border bg-slate-50 p-5">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary/10 text-2xl font-semibold text-primary">
            {visibleName.charAt(0).toUpperCase()}
          </div>
          <div className="mt-4">
            <div className="text-lg font-semibold text-slate-950">{visibleName}</div>
            <div className="text-sm text-muted-foreground">@{user.username}</div>
            {user.fullName && user.fullName !== visibleName ? (
              <div className="mt-1 text-xs text-slate-500">System name: {user.fullName}</div>
            ) : null}
          </div>
          <div className="mt-4 inline-flex rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
            {roleLabel(user.role)}
          </div>
          <div className="mt-6 space-y-2 text-sm text-muted-foreground">
            <div>Account: {user.active ? 'active' : 'disabled'}</div>
            <div>Can teach: {user.canTeach ? 'yes' : 'no'}</div>
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          {fields.map((field) => (
            <div key={field.key} className="space-y-2">
              <Label className="inline-flex items-center gap-2 text-xs uppercase tracking-wide text-slate-500">
                <field.icon className="h-4 w-4" />
                {field.label}
              </Label>
              {editing ? (
                <Input
                  type={field.type}
                  value={form[field.key] || ''}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      [field.key]: event.target.value,
                    }))
                  }
                />
              ) : (
                <div className="rounded-xl border border-border bg-white px-3 py-2 text-sm text-slate-900">
                  {form[field.key] || '—'}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
