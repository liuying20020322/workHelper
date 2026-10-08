export interface JobApplication {
  id: number
  companyName: string
  positionName: string
  location: string
  requirements: string
  appliedAt: string
  channel: string
  jobUrl: string
  notes: string
  currentStage: string
  createdAt: string
  updatedAt: string
}
export type ApplicationInput = Omit<JobApplication, 'id' | 'currentStage' | 'createdAt' | 'updatedAt'>

export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 12000)
  try {
    const response = await fetch(url, { ...init, signal: controller.signal, headers: { 'Content-Type': 'application/json', ...init?.headers } })
    if (!response.ok) {
      const error = await response.json().catch(() => ({}))
      throw new Error(error.message || `请求失败（${response.status}），请稍后重试`)
    }
    return response.status === 204 ? undefined as T : await response.json()
  } catch (error) {
    if (error instanceof TypeError || (error instanceof DOMException && error.name === 'AbortError')) {
      throw new Error('连接服务失败或请求超时，请确认服务已启动。保存结果不确定时请刷新核对，避免重复提交。')
    }
    throw error
  } finally { window.clearTimeout(timeout) }
}

export const stages = [
  ['APPLIED', '已投递'], ['ASSESSMENT', '测评'], ['WRITTEN_TEST', '笔试'],
  ['INTERVIEW_1', '一面'], ['INTERVIEW_2', '二面'], ['INTERVIEW_3', '三面'],
  ['OFFER', 'Offer'], ['REJECTED', '未通过'], ['WITHDRAWN', '已撤回'],
]
export const stageName = (stage: string) => stages.find(([key]) => key === stage)?.[1] || stage
export const dateText = (date: string) => date.replace('T', ' ').slice(0, 16)

export type ProcessStage = 'ASSESSMENT' | 'WRITTEN_TEST' | 'INTERVIEW_1' | 'INTERVIEW_2' | 'INTERVIEW_3' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'
export type TimeMode = 'SCHEDULED' | 'DEADLINE' | 'RECORD_ONLY'
export type ProcessStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED'
export interface ProcessInput {
  stage: ProcessStage
  roundName: string
  timeMode: TimeMode
  startAt: string | null
  endAt: string | null
  deadlineAt: string | null
  status: ProcessStatus
  location: string
  notes: string
  occurredAt: string
}
export interface ProcessRecord extends ProcessInput {
  hasInterview: boolean
  id: number
  applicationId: number
  createdAt: string
  updatedAt: string
}
export const statusNames: Record<ProcessStatus, string> = { PENDING: '待完成', COMPLETED: '已完成', CANCELLED: '已取消' }
export function businessNow(timeZone: string) {
  return new Intl.DateTimeFormat('sv-SE', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(new Date()).replace(' ', 'T')
}

export interface Reminder {
  process: ProcessRecord
  companyName: string
  positionName: string
  jobLocation: string
  effectiveAt: string
  overdue: boolean
  inProgress: boolean
  dayLabel: string
}
export interface ReminderPage {
  timeZone: string
  today: string
  throughDate: string
  now: string
  overdue: Reminder[]
  items: Reminder[]
}
