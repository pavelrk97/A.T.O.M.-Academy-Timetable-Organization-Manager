import type {
  DashboardData,
  GroupDto,
  ImportResult,
  LessonEditorDto,
  LessonMutationPayload,
  LessonHistoryEntry,
  Notification,
  ScheduleEntry,
  ScheduleGridData,
  User,
  UserUpsertRequest,
  WorkloadCalendar,
  WorkloadSummary,
  MyProfileUpdateRequest,
} from './types'

const API_BASE = '/api'

let authCredentials: string | null = null

export function setAuthCredentials(username: string, password: string) {
  authCredentials = btoa(`${username}:${password}`)
}

export function clearAuthCredentials() {
  authCredentials = null
}

function getAuthHeaders(): HeadersInit {
  if (!authCredentials) {
    return {}
  }

  return {
    Authorization: `Basic ${authCredentials}`,
  }
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
        message = text
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

export const authApi = {
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
}

export const groupsApi = {
  getAll: () => fetchApi<GroupDto[]>('/groups'),
  getById: (id: string) => fetchApi<GroupDto>(`/groups/${id}`),
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

export const workloadApi = {
  getAll: (params?: { instructorId?: string; from?: string; to?: string }) => {
    const searchParams = new URLSearchParams()
    if (params?.instructorId) searchParams.set('instructorId', params.instructorId)
    if (params?.from) searchParams.set('from', params.from)
    if (params?.to) searchParams.set('to', params.to)
    const query = searchParams.toString()
    return fetchApi<WorkloadSummary[]>(`/workload${query ? `?${query}` : ''}`)
  },
}
