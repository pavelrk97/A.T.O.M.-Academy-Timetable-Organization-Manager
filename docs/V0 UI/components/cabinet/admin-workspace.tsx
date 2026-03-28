'use client'

import { useMemo, useState, type ReactNode } from 'react'
import {
  FileSpreadsheet,
  RefreshCcw,
  Rows3,
  ShieldCheck,
  UploadCloud,
  UsersRound,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { LessonAdminEditor } from '@/components/cabinet/lesson-admin-editor'
import type { GroupDto, ImportResult, User } from '@/lib/types'

interface AdminWorkspaceProps {
  users: User[]
  groups: GroupDto[]
  importing: boolean
  importResult: ImportResult | null
  onImport: (file: File) => Promise<void>
  onRefresh: () => Promise<void>
}

export function AdminWorkspace({
  users,
  groups,
  importing,
  importResult,
  onImport,
  onRefresh,
}: AdminWorkspaceProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  const teacherCount = useMemo(
    () => users.filter((user) => user.canTeach).length,
    [users]
  )

  const lessonCount = useMemo(
    () =>
      groups.reduce(
        (sum, group) =>
          sum +
          (group.days || []).reduce(
            (daySum, day) => daySum + (day.lessons?.length || 0),
            0
          ),
        0
      ),
    [groups]
  )

  return (
    <div className="space-y-6">
      <div className="grid gap-4 xl:grid-cols-4">
        <InfoTile
          icon={<UsersRound className="h-5 w-5 text-primary" />}
          label="Пользователи"
          value={users.length}
          helper={`${teacherCount} могут вести занятия`}
        />
        <InfoTile
          icon={<ShieldCheck className="h-5 w-5 text-primary" />}
          label="Группы"
          value={groups.length}
          helper="справочник академии"
        />
        <InfoTile
          icon={<Rows3 className="h-5 w-5 text-primary" />}
          label="Занятия"
          value={lessonCount}
          helper="текущая сетка в базе"
        />
        <InfoTile
          icon={<FileSpreadsheet className="h-5 w-5 text-primary" />}
          label="CSV импорт"
          value={importResult ? 'OK' : '—'}
          helper="ручной перезапуск из кабинета"
        />
      </div>

      <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">Операционный блок</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Импорт, справочники и ручное редактирование расписания без ухода в Postman.
            </p>
          </div>
          <Button variant="outline" onClick={onRefresh}>
            <RefreshCcw className="mr-2 h-4 w-4" />
            Обновить данные
          </Button>
        </div>

        <div className="mt-5 grid gap-6 lg:grid-cols-[420px_1fr]">
          <div className="rounded-2xl border border-border bg-slate-50 p-4">
            <div className="text-sm font-semibold text-slate-950">Импорт CSV</div>
            <p className="mt-1 text-sm text-muted-foreground">
              Работает через `POST /api/import/csv`, backend не меняется.
            </p>
            <div className="mt-4 space-y-3">
              <Input
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
              />
              <Button
                disabled={!selectedFile || importing}
                onClick={() => selectedFile && onImport(selectedFile)}
              >
                <UploadCloud className="mr-2 h-4 w-4" />
                {importing ? 'Импортирую...' : 'Запустить импорт'}
              </Button>
              {importResult ? (
                <pre className="max-h-64 overflow-auto rounded-xl border border-border bg-white p-3 text-xs text-slate-700">
                  {JSON.stringify(importResult, null, 2)}
                </pre>
              ) : null}
            </div>
          </div>

          <div className="grid gap-6 xl:grid-cols-2">
            <div className="rounded-2xl border border-border bg-slate-50 p-4">
              <div className="mb-3 text-sm font-semibold text-slate-950">Пользователи</div>
              <div className="max-h-80 overflow-auto rounded-xl border border-border bg-white">
                <table className="min-w-full text-sm">
                  <thead className="sticky top-0 bg-slate-100">
                    <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                      <th className="px-3 py-2">Логин</th>
                      <th className="px-3 py-2">Роль</th>
                      <th className="px-3 py-2">Email</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users.map((user) => (
                      <tr key={user.id} className="border-t border-border">
                        <td className="px-3 py-2">
                          <div className="font-medium text-slate-950">{user.username}</div>
                          <div className="text-xs text-muted-foreground">{user.fullName}</div>
                        </td>
                        <td className="px-3 py-2">{user.role}</td>
                        <td className="px-3 py-2 text-muted-foreground">{user.email || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="rounded-2xl border border-border bg-slate-50 p-4">
              <div className="mb-3 text-sm font-semibold text-slate-950">Группы</div>
              <div className="max-h-80 overflow-auto rounded-xl border border-border bg-white">
                <table className="min-w-full text-sm">
                  <thead className="sticky top-0 bg-slate-100">
                    <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                      <th className="px-3 py-2">Код</th>
                      <th className="px-3 py-2">Локация</th>
                      <th className="px-3 py-2">Дней</th>
                    </tr>
                  </thead>
                  <tbody>
                    {groups.map((group) => (
                      <tr key={group.id} className="border-t border-border">
                        <td className="px-3 py-2 font-medium text-slate-950">{group.code}</td>
                        <td className="px-3 py-2 text-muted-foreground">{group.location || '—'}</td>
                        <td className="px-3 py-2">{group.days?.length || 0}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </section>

      <LessonAdminEditor groups={groups} users={users} onChanged={onRefresh} />
    </div>
  )
}

function InfoTile({
  icon,
  label,
  value,
  helper,
}: {
  icon: ReactNode
  label: string
  value: ReactNode
  helper: string
}) {
  return (
    <section className="rounded-2xl border border-border bg-white px-5 py-4 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
            {label}
          </div>
          <div className="mt-3 text-3xl font-semibold text-slate-950">{value}</div>
          <div className="mt-1 text-sm text-muted-foreground">{helper}</div>
        </div>
        <div className="rounded-xl bg-primary/10 p-3">{icon}</div>
      </div>
    </section>
  )
}
