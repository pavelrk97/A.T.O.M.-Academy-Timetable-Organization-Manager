'use client'

import { useMemo, useState, type ReactNode } from 'react'
import {
  CalendarPlus2,
  FileSpreadsheet,
  RefreshCcw,
  ShieldCheck,
  UploadCloud,
  UsersRound,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { LessonAdminEditor } from '@/components/cabinet/lesson-admin-editor'
import { UserAdminEditor } from '@/components/cabinet/user-admin-editor'
import type { GroupDto, ImportResult, User } from '@/lib/types'

interface AdminWorkspaceProps {
  currentUser: User
  users: User[]
  groups: GroupDto[]
  canImport: boolean
  canManageUsers: boolean
  canManageGroups: boolean
  importing: boolean
  importResult: ImportResult | null
  onImport: (file: File) => Promise<void>
  onRefresh: () => Promise<void>
  range: {
    from: string
    to: string
  }
}

function workspaceTitle(user: User) {
  if (user.role === 'ADMIN') {
    return 'Административный контур'
  }
  if (user.role === 'EDITOR') {
    return 'Редактор расписания'
  }
  if (user.editorAccess) {
    return 'Инструктор с правом редактирования'
  }
  return 'Операции'
}

function workspaceDescription(user: User) {
  if (user.role === 'ADMIN') {
    return 'Администратор управляет импортом CSV, пользователями, группами, пустыми днями и занятиями.'
  }
  if (user.role === 'EDITOR' || user.editorAccess) {
    return 'Редакторский контур работает с сеткой расписания: создание групп, дней, занятий и назначение преподавателей.'
  }
  return 'Доступ к операциям ограничен.'
}

export function AdminWorkspace({
  currentUser,
  users,
  groups,
  canImport,
  canManageUsers,
  canManageGroups,
  importing,
  importResult,
  onImport,
  onRefresh,
  range,
}: AdminWorkspaceProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  const teacherCount = useMemo(
    () => users.filter((user) => user.canTeach).length,
    [users]
  )

  const editorCapableCount = useMemo(
    () =>
      users.filter(
        (user) => user.role === 'ADMIN' || user.role === 'EDITOR' || user.editorAccess
      ).length,
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
          label="Доступ к операциям"
          value={editorCapableCount}
          helper="админы, редакторы и инструкторы с editor access"
        />
        <InfoTile
          icon={<CalendarPlus2 className="h-5 w-5 text-primary" />}
          label="Занятия"
          value={lessonCount}
          helper="текущая сетка в базе"
        />
        <InfoTile
          icon={<FileSpreadsheet className="h-5 w-5 text-primary" />}
          label="Импорт CSV"
          value={canImport ? (importResult ? 'OK' : 'ADMIN') : 'ADMIN'}
          helper={
            canImport
              ? 'ручной импорт доступен в этом кабинете'
              : 'скрыт для редакторов и инструкторов'
          }
        />
      </div>

      <section className="rounded-2xl border border-border bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">{workspaceTitle(currentUser)}</h3>
            <p className="mt-1 max-w-3xl text-sm text-muted-foreground">
              {workspaceDescription(currentUser)}
            </p>
          </div>
          <Button variant="outline" onClick={onRefresh}>
            <RefreshCcw className="mr-2 h-4 w-4" />
            Обновить данные
          </Button>
        </div>

        {canImport ? (
          <div className="mt-5 rounded-2xl border border-border bg-slate-50 p-4">
            <div className="text-sm font-semibold text-slate-950">Импорт CSV</div>
            <p className="mt-1 text-sm text-muted-foreground">
              Вызывает `POST /api/import/csv` через gateway. Большие файлы могут обрабатываться несколько минут.
            </p>
            <div className="mt-4 flex flex-wrap items-center gap-3">
              <Input
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
                className="max-w-md"
              />
              <Button
                disabled={!selectedFile || importing}
                onClick={() => selectedFile && onImport(selectedFile)}
              >
                <UploadCloud className="mr-2 h-4 w-4" />
                {importing ? 'Импортирую...' : 'Запустить импорт'}
              </Button>
            </div>
            {importResult ? (
              <pre className="mt-4 max-h-64 overflow-auto rounded-xl border border-border bg-white p-3 text-xs text-slate-700">
                {JSON.stringify(importResult, null, 2)}
              </pre>
            ) : null}
          </div>
        ) : null}
      </section>

      {canManageUsers ? <UserAdminEditor users={users} onChanged={onRefresh} /> : null}

      <LessonAdminEditor
        groups={groups}
        users={users}
        canManageGroups={canManageGroups}
        range={range}
      />
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
