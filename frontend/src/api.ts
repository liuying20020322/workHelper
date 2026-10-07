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
