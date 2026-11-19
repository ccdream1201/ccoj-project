export interface User {
  id: number
  account: string
  nickname: string | null
  userRole: string
}

export interface Problem {
  id: number
  title: string
  difficulty: number
  description: string
  samples: { input: string; output: string }[]
}

export interface Submission {
  id: number
  problemId: number
  status: string
  passedCaseCount?: number | null
  totalCaseCount?: number | null
  compileOutput?: string | null
}

interface Page<T> {
  records: T[]
  total: number
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: number,
  ) {
    super(message)
  }
}

async function request<T>(path: string, body?: object, signal?: AbortSignal): Promise<T> {
  const response = await fetch('/api' + path, {
    credentials: 'same-origin',
    ...(signal ? { signal } : {}),
    ...(body
      ? {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        }
      : {}),
  })
  const result = (await response.json().catch(() => null)) as {
    code?: number
    message?: string
    data: T
  } | null
  if (!response.ok || !result || result.code !== 200) {
    throw new ApiError(
      result?.message || `请求失败（HTTP ${response.status}）`,
      result?.code ?? response.status,
    )
  }
  return result.data
}

export const api = {
  login: (account: string, password: string) =>
    request<number>('/user/login', { account, password }),
  register: (account: string, password: string, confirmPassword: string) =>
    request<number>('/user/register', { account, password, confirmPassword }),
  logout: () => request<void>('/user/logout', {}),
  current: () => request<User>('/user/current'),
  problems: (page: number, signal: AbortSignal) =>
    request<Page<Problem>>(`/problem?page=${page}&size=20`, undefined, signal),
  problem: (id: string, signal: AbortSignal) =>
    request<{ problem: Problem }>(`/problem/${id}`, undefined, signal),
  submit: (problemId: number, sourceCode: string) =>
    request<number>('/submission/judge', { problemId, sourceCode, language: 'JAVA' }),
  submission: (id: string, signal: AbortSignal) =>
    request<Submission>(`/submission/${id}`, undefined, signal),
  submissions: (page: number, signal: AbortSignal) =>
    request<Page<Submission>>(`/submission?page=${page}&size=20`, undefined, signal),
}

export const difficulty = (value: number) =>
  ({ 1: '简单', 2: '中等', 3: '困难' })[value] ?? '未设置'

const statusLabels: Record<string, string> = {
  PENDING: '等待判题',
  RUNNING: '判题中',
  ACCEPTED: '通过',
  WRONG_ANSWER: '答案错误',
  COMPILE_ERROR: '编译错误',
  RUNTIME_ERROR: '运行错误',
  TIME_LIMIT: '时间超限',
  MEMORY_LIMIT: '内存超限',
  OUTPUT_LIMIT: '输出超限',
  SYSTEM_FAILED: '判题服务异常',
}
export const statusLabel = (status: string) => statusLabels[status] ?? status
export const isJudging = (status: string) => status === 'PENDING' || status === 'RUNNING'
export const messageOf = (error: unknown) =>
  error instanceof Error ? error.message : '请求失败，请稍后重试'
