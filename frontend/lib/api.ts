import type {
  AutoImportSettings,
  AutoImportSettingsUpdateRequest,
  DashboardData,
  DaySyncPayload,
  GroupDto,
  GroupMutationPayload,
  ImportResult,
  LessonEditorDto,
  LessonMutationPayload,
  LessonHistoryEntry,
  Notification,
  ScheduleEntry,
  ScheduleGridData,
  TokenResponse,
  User,
  UserActivity,
  UserUpsertRequest,
  WorkloadCalendar,
  WorkloadSummary,
  MyProfileUpdateRequest,
} from './types'

const API_BASE = '/api'

export interface DownloadPayload {
  blob: Blob
  fileName: string
}

let accessToken: string | null = null

export function setAccessToken(token: string) {
  accessToken = token
}

export function clearAccessToken() {
  accessToken = null
}

function getAuthHeaders(): HeadersInit {
  if (!accessToken) {
    return {}
  }

  return {
    Authorization: `Bearer ${accessToken}`,
  }
}

function extractApiErrorMessage(text: string) {
  if (!text) {
    return ''
  }

  try {
    const parsed = JSON.parse(text) as { message?: string; error?: string }
    if (parsed.message) {
      return parsed.message
    }
    if (parsed.error) {
      return parsed.error
    }
  } catch {}

  return text
}

async function fetchApi<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {})
  const hasBody = options.body !== undefined && options.body !== null
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData

  for (const [key, value] of Object.entries(getAuthHeaders())) {
    headers.set(key, value)
  }

  if (hasBody && !isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
    cache: 'no-store',
  })

  if (!response.ok) {
    let message = `API error: ${response.status}`
    try {
      const text = await response.text()
      if (text) {
        message = extractApiErrorMessage(text)
      }
    } catch {}
    throw new Error(message)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (!text) {
    return undefined as T
  }

  return JSON.parse(text) as T
}

function resolveDownloadName(contentDisposition: string | null, fallback: string) {
  if (!contentDisposition) {
    return fallback
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }

  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1] || fallback
}

async function fetchDownload(
  endpoint: string,
  options: RequestInit = {},
  fallbackFileName = 'download.bin'
): Promise<DownloadPayload> {
  const headers = new Headers(options.headers || {})
  const hasBody = options.body !== undefined && options.body !== null
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData

  for (const [key, value] of Object.entries(getAuthHeaders())) {
    headers.set(key, value)
  }

  if (hasBody && !isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
    cache: 'no-store',
  })

  if (!response.ok) {
    let message = `API error: ${response.status}`
    try {
      const text = await response.text()
      if (text) {
        message = extractApiErrorMessage(text)
      }
    } catch {}
    throw new Error(message)
  }

  return {
    blob: await response.blob(),
    fileName: resolveDownloadName(response.headers.get('Content-Disposition'), fallbackFileName),
  }
}

export function saveDownload(download: DownloadPayload) {
  const url = window.URL.createObjectURL(download.blob)
  const link = document.createElement('a')
  link.href = url
  link.download = download.fileName
  link.click()
  window.URL.revokeObjectURL(url)
}

export const authApi = {
  login: (payload: { username: string; password: string }) =>
    fetchApi<TokenResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  getMe: () => fetchApi<User>('/auth/me'),
}

export const publicApi = {
  getSchedule: (params?: {
    groupCode?: string
    from?: string
    to?: string
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.groupCode) searchParams.set('groupCode', params.groupCode)
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)

    const query = searchParams.toString()
    return fetchApi<ScheduleEntry[]>(`/public/schedule${query ? `?${query}` : ''}`)
  },
}

export const usersApi = {
  getAll: () => fetchApi<User[]>('/users'),
  getActivity: () => fetchApi<UserActivity[]>('/users/activity'),
  create: (payload: UserUpsertRequest) =>
    fetchApi<User>('/users', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  update: (id: string, payload: UserUpsertRequest) =>
    fetchApi<User>(`/users/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  delete: (id: string) =>
    fetchApi<void>(`/users/${id}`, {
      method: 'DELETE',
    }),
}

export const groupsApi = {
  getAll: () => fetchApi<GroupDto[]>('/groups'),
  getById: (id: string) => fetchApi<GroupDto>(`/groups/${id}`),
  create: (payload: GroupMutationPayload) =>
    fetchApi<GroupDto>('/groups', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  update: (id: string, payload: GroupMutationPayload) =>
    fetchApi<GroupDto>(`/groups/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  delete: (id: string) =>
    fetchApi<void>(`/groups/${id}`, {
      method: 'DELETE',
    }),
}

export const lessonsApi = {
  getAll: (params?: {
    groupCode?: string
    from?: string
    to?: string
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.groupCode) searchParams.set('groupCode', params.groupCode)
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)

    const query = searchParams.toString()
    return fetchApi<ScheduleEntry[]>(`/lessons${query ? `?${query}` : ''}`)
  },
  getById: (id: string) => fetchApi<LessonEditorDto>(`/lessons/${id}`),
  getHistory: (id: string) => fetchApi<LessonHistoryEntry[]>(`/lessons/${id}/history`),
  create: (payload: LessonMutationPayload) =>
    fetchApi<LessonEditorDto>('/lessons', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  syncDay: (payload: DaySyncPayload) =>
    fetchApi<GroupDto>('/lessons/day-sync', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  update: (id: string, payload: LessonMutationPayload) =>
    fetchApi<LessonEditorDto>(`/lessons/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  delete: (id: string, version: number) =>
    fetchApi<void>(`/lessons/${id}?version=${version}`, {
      method: 'DELETE',
    }),
}

export const meApi = {
  getProfile: () => fetchApi<User>('/me/profile'),
  updateProfile: (payload: MyProfileUpdateRequest) =>
    fetchApi<User>('/me/profile', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  changePassword: (payload: { currentPassword: string; newPassword: string }) =>
    fetchApi<void>('/me/password', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  getScheduleGrid: (params?: { from?: string; to?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    const query = searchParams.toString()
    return fetchApi<ScheduleGridData>(`/me/schedule/grid${query ? `?${query}` : ''}`)
  },
  getInstructorGrid: (params?: { from?: string; to?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    const query = searchParams.toString()
    return fetchApi<ScheduleGridData>(
      `/me/schedule/instructor-grid${query ? `?${query}` : ''}`
    )
  },
  getWorkloadCalendar: (params?: { from?: string; to?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    const query = searchParams.toString()
    return fetchApi<WorkloadCalendar>(
      `/me/workload/calendar${query ? `?${query}` : ''}`
    )
  },
  exportWorkloadCalendar: (params?: { from?: string; to?: string; includeBusinessTrips?: boolean }) => {
    const searchParams = new URLSearchParams()
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    if (params?.includeBusinessTrips === false) searchParams.set('includeBusinessTrips', 'false')
    const query = searchParams.toString()
    return fetchDownload(
      `/me/workload/export${query ? `?${query}` : ''}`,
      {},
      'my-workload.xlsx'
    )
  },
  getNotifications: (params?: { from?: string; to?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    const query = searchParams.toString()
    return fetchApi<Notification[]>(`/me/notifications${query ? `?${query}` : ''}`)
  },
  getDashboard: (params?: { from?: string; to?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    const query = searchParams.toString()
    return fetchApi<DashboardData>(`/me/dashboard${query ? `?${query}` : ''}`)
  },
}

export const importApi = {
  uploadCsv: async (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return fetchApi<ImportResult>('/import/csv', {
      method: 'POST',
      body: formData,
    })
  },
}

export const autoImportApi = {
  getSettings: () => fetchApi<AutoImportSettings>('/auto-import/settings'),
  updateSettings: (request: AutoImportSettingsUpdateRequest) =>
    fetchApi<AutoImportSettings>('/auto-import/settings', {
      method: 'PUT',
      body: JSON.stringify(request),
    }),
  runNow: () =>
    fetchApi<AutoImportSettings>('/auto-import/run', {
      method: 'POST',
    }),
}

export const workloadApi = {
  getAll: (params?: {
    instructorId?: string
    instructorIds?: string[]
    from?: string
    to?: string
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.instructorId) searchParams.set('instructorId', params.instructorId)
    if (params?.instructorIds && params.instructorIds.length) {
      for (const id of params.instructorIds) searchParams.append('instructorIds', id)
    }
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    const query = searchParams.toString()
    return fetchApi<WorkloadSummary[]>(`/workload${query ? `?${query}` : ''}`)
  },
  exportCsv: (params?: {
    instructorId?: string
    instructorIds?: string[]
    instructorQuery?: string
    from?: string
    to?: string
    includeBusinessTrips?: boolean
  }) => {
    const searchParams = new URLSearchParams()
    if (params?.instructorId) searchParams.set('instructorId', params.instructorId)
    if (params?.instructorIds && params.instructorIds.length) {
      for (const id of params.instructorIds) searchParams.append('instructorIds', id)
    }
    if (params?.instructorQuery) searchParams.set('instructorQuery', params.instructorQuery)
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    if (params?.includeBusinessTrips === false) searchParams.set('includeBusinessTrips', 'false')
    const query = searchParams.toString()
    return fetchDownload(`/workload/export${query ? `?${query}` : ''}`, {}, 'workload.xlsx')
  },
}
