'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Bell, CalendarDays, FileCog, LogOut, Menu, PanelsTopLeft, UserCircle2 } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { useAuth } from '@/lib/auth-context'
import type { User } from '@/lib/types'

const navigation = [
  { href: '/schedule', label: 'Расписание', icon: CalendarDays },
  { href: '/cabinet', label: 'Личный кабинет', icon: PanelsTopLeft },
]

function canUseOperations(user?: User | null) {
  return Boolean(user && (user.role === 'ADMIN' || user.role === 'EDITOR' || user.editorAccess))
}

function roleLabel(user?: User | null) {
  if (!user) {
    return ''
  }

  if (user.role === 'ADMIN') return 'Администратор'
  if (user.role === 'EDITOR') return 'Редактор'
  if (user.editorAccess) return 'Инструктор / Редактор'
  return 'Инструктор'
}

export function Header() {
  const pathname = usePathname()
  const [open, setOpen] = useState(false)
  const { user, isAuthenticated, logout } = useAuth()
  const visibleName = user ? user.displayName || user.fullName || user.username : ''

  return (
    <header className="sticky top-0 z-50 border-b border-border/80 bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/80">
      <div className="mx-auto flex h-16 max-w-[1600px] items-center gap-4 px-4 lg:px-8">
        <Link href="/" className="flex items-center gap-3 font-semibold text-slate-950">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
            <span className="text-sm font-bold">A</span>
          </div>
          <div className="hidden sm:block">
            <div className="text-sm font-semibold">A.T.O.M.</div>
            <div className="text-xs text-muted-foreground">Academy Timetable</div>
          </div>
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          {navigation.map((item) => {
            const active = pathname.startsWith(item.href)
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                  active
                    ? 'bg-primary/10 text-primary'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'
                )}
              >
                <item.icon className="h-4 w-4" />
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          {isAuthenticated ? (
            <Link href="/cabinet?tab=notifications">
              <Button
                variant="ghost"
                size="icon"
                className="relative text-slate-600 hover:text-primary"
              >
                <Bell className="h-5 w-5" />
                <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-primary" />
              </Button>
            </Link>
          ) : null}

          {isAuthenticated && user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" className="h-10 gap-3 rounded-xl px-2 sm:px-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                    {visibleName.charAt(0).toUpperCase()}
                  </div>
                  <div className="hidden text-left sm:block">
                    <div className="max-w-40 truncate text-sm font-medium text-slate-950">
                      {visibleName}
                    </div>
                    <div className="text-xs text-muted-foreground">{roleLabel(user)}</div>
                  </div>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-64">
                <div className="px-3 py-2">
                  <div className="font-medium">{visibleName}</div>
                  <div className="text-xs text-muted-foreground">{user.email || user.username}</div>
                </div>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link href="/cabinet?tab=profile">
                    <UserCircle2 className="mr-2 h-4 w-4" />
                    Профиль
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link href="/cabinet?tab=notifications">
                    <Bell className="mr-2 h-4 w-4" />
                    Уведомления
                  </Link>
                </DropdownMenuItem>
                {canUseOperations(user) ? (
                  <DropdownMenuItem asChild>
                    <Link href="/cabinet?tab=admin">
                      <FileCog className="mr-2 h-4 w-4" />
                      Операции
                    </Link>
                  </DropdownMenuItem>
                ) : null}
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={logout} className="text-destructive focus:text-destructive">
                  <LogOut className="mr-2 h-4 w-4" />
                  Выйти
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <Link href="/login">
              <Button size="sm" className="rounded-xl">
                Войти
              </Button>
            </Link>
          )}

          <Sheet open={open} onOpenChange={setOpen}>
            <SheetTrigger asChild className="md:hidden">
              <Button variant="ghost" size="icon">
                <Menu className="h-5 w-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="w-80">
              <div className="mt-8 space-y-2">
                {navigation.map((item) => (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setOpen(false)}
                    className={cn(
                      'flex items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium transition-colors',
                      pathname.startsWith(item.href)
                        ? 'bg-primary/10 text-primary'
                        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'
                    )}
                  >
                    <item.icon className="h-5 w-5" />
                    {item.label}
                  </Link>
                ))}
                {canUseOperations(user) ? (
                  <Link
                    href="/cabinet?tab=admin"
                    onClick={() => setOpen(false)}
                    className="flex items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950"
                  >
                    <FileCog className="h-5 w-5" />
                    Операции
                  </Link>
                ) : null}
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  )
}
