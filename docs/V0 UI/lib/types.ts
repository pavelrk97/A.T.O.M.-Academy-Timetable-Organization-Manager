export type UserRole = 'ADMIN' | 'EDITOR' | 'INSTRUCTOR'
export type LessonType = 'LECTURE' | 'SELF_STUDY' | 'ASSESSMENT'

export interface User {
  id: string
  username: string
  fullName: string
  displayName?: string | null
  email?: string | null
  phone?: string | null
  position?: string | null
  department?: string | null
  role: UserRole
  active: boolean
  canTeach: boolean
  editorAccess: boolean
}

export interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
}

export interface TokenResponse {
  tokenType: string
  accessToken: string
  expiresAt: string
}

export interface LessonHistoryEntry {
  id: string
  entityType: string
  entityId: string
  action: 'CREATED' | 'UPDATED' | 'DELETED'
  changedBy: string
  changedAt: string
  beforeJson?: string | null
  afterJson?: string | null
  comment?: string | null
}

export interface LessonEditorDto {
  id: string
  version: number
  orderNumber?: number | null
  title: string
  lecturer?: string | null
  lecturers?: string[]
  durationHours: number
  note?: string | null
  type?: LessonType | null
  dayId?: string | null
  groupId?: string | null
  instructorIds?: string[]
  instructorNames?: string[]
}

export interface LessonMutationPayload {
  version?: number | null
  orderNumber?: number | null
  title: string
  lecturer?: string | null
  lecturers?: string[]
  durationHours: number
  note?: string | null
  type?: LessonType | null
  dayId: string
  groupId: string
  instructorIds: string[]
  instructorNames?: string[]
}

export interface DayDto {
  id?: string | null
  date: string
  meta?: Record<string, string>
  lessons: LessonEditorDto[]
}

export interface GroupDto {
  id: string
  code: string
  location?: string | null
  course?: string | null
  days: DayDto[]
}

export interface DayMutationPayload {
  id?: string | null
  date: string
  meta?: Record<string, string>
  lessons: LessonEditorDto[]
}

export interface GroupMutationPayload {
  id?: string | null
  code: string
  location?: string | null
  course?: string | null
  days: DayMutationPayload[]
}

export interface ScheduleEntry {
  lessonId: string
  version: number
  groupId: string
  groupCode: string
  location?: string | null
  date: string
  orderNumber?: number | null
  title: string
  type?: string | null
  durationHours: number
  note?: string | null
  instructorIds?: string[]
  instructorNames?: string[]
}

export interface ScheduleGridLessonCell {
  lessonId: string
  version: number
  orderNumber?: number | null
  title: string
  type?: string | null
  durationHours: number
  note?: string | null
  instructorNames: string[]
}

export interface ScheduleGridDayCell {
  dayId?: string | null
  date: string
  lessons: ScheduleGridLessonCell[]
}

export interface ScheduleGridGroupRow {
  groupId: string
  groupCode: string
  location?: string | null
  course?: string | null
  days: ScheduleGridDayCell[]
}

export interface ScheduleGridData {
  dates: string[]
  groups: ScheduleGridGroupRow[]
}

export interface WorkloadCalendarLesson {
  lessonId: string
  groupCode: string
  title: string
  durationHours: number
}

export interface WorkloadCalendarDay {
  dayId: string
  date: string
  totalHours: number
  lessons: WorkloadCalendarLesson[]
}

export interface WorkloadCalendar {
  instructorId: string
  instructorName: string
  from?: string | null
  to?: string | null
  totalHours: number
  days: WorkloadCalendarDay[]
}

export interface WorkloadSummary {
  instructorId: string
  instructorName: string
  totalHours: number
}

export interface Notification {
  type: string
  dayId?: string | null
  date: string
  message: string
  link: string
}

export interface DashboardData {
  profile: User
  instructorSchedule: ScheduleGridData
  workload: WorkloadCalendar
  notifications: Notification[]
}

export interface ImportResult {
  [key: string]: unknown
}

export interface MyProfileUpdateRequest {
  displayName?: string | null
  email?: string | null
  phone?: string | null
  position?: string | null
  department?: string | null
}

export interface UserUpsertRequest {
  username: string
  password: string
  fullName: string
  displayName?: string | null
  email?: string | null
  phone?: string | null
  position?: string | null
  department?: string | null
  role: UserRole
  active: boolean
  canTeach: boolean
  editorAccess: boolean
}
