<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ProcessEditor from '../components/ProcessEditor.vue'
import ReminderCard from '../components/ReminderCard.vue'
import { request, type ProcessRecord, type Reminder, type ReminderPage } from '../api'

const view = ref<'RECENT' | 'ALL' | 'COMPLETED'>('RECENT')
const page = ref<ReminderPage>()
const error = ref('')
const loading = ref(false)
const busy = ref(false)
const editing = ref<{ process: ProcessRecord; heading: string }>()
let version = 0
let interval: number | undefined
let midnight: number | undefined
let disposed = false
const views = [{ value: 'RECENT', label: '近期待办' }, { value: 'ALL', label: '全部待办' }, { value: 'COMPLETED', label: '已完成' }]
const sections = computed(() => [
  { key: 'overdue', title: '逾期待处理', description: '这些安排已超过时间，完成或取消后将移出待办。', items: page.value?.overdue || [] },
  { key: 'current', title: view.value === 'COMPLETED' ? '已完成安排' : view.value === 'ALL' ? '全部未逾期安排' : '近三天安排', description: view.value === 'RECENT' ? '今天、明天、后天；跨日进行中的安排也会保留。' : '按安排开始时间或任务截止时间排序。', items: page.value?.items || [] },
].filter(section => section.items.length))
const count = computed(() => (page.value?.items.length || 0) + (page.value?.overdue.length || 0))

async function load() {
  const current = ++version
  loading.value = true
  error.value = ''
  try {
    const data = await request<ReminderPage>(`/api/reminders?view=${view.value}`)
    if (disposed || current !== version) return
    page.value = data
    window.clearTimeout(midnight)
    // Work from server business-local timestamps, independent of the browser's own timezone.
    const delay = Date.parse(`${data.today}T00:00:00Z`) + 86400000 - Date.parse(`${data.now}Z`)
    midnight = window.setTimeout(() => void load(), Math.max(500, delay + 100))
  } catch (e) { if (!disposed && current === version) error.value = (e as Error).message }
  finally { if (!disposed && current === version) loading.value = false }
}
async function complete(item: Reminder) {
  busy.value = true
  try {
    await request<ProcessRecord>(`/api/applications/${item.process.applicationId}/processes/${item.process.id}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'COMPLETED' }) })
    ElMessage.success('已完成，投递中的安排已同步更新')
    await load()
  } catch (e) { ElMessage.error((e as Error).message); void load() }
  finally { busy.value = false }
}
async function edit(item: Reminder) {
  busy.value = true
  try {
    const process = await request<ProcessRecord>(`/api/applications/${item.process.applicationId}/processes/${item.process.id}`)
    if (!disposed) editing.value = { process, heading: `${item.companyName} · ${item.positionName}` }
  } catch (e) { ElMessage.error((e as Error).message); void load() }
  finally { busy.value = false }
}
function refreshWhenVisible() { if (document.visibilityState === 'visible') void load() }
watch(view, () => { page.value = undefined; void load() })
onMounted(() => {
  void load()
  window.addEventListener('focus', refreshWhenVisible)
  document.addEventListener('visibilitychange', refreshWhenVisible)
  interval = window.setInterval(refreshWhenVisible, 30000)
})
onUnmounted(() => {
  disposed = true
  version++
  window.clearInterval(interval)
  window.clearTimeout(midnight)
  window.removeEventListener('focus', refreshWhenVisible)
  document.removeEventListener('visibilitychange', refreshWhenVisible)
})
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">YOUR NEXT STEP</p><h1>首页提醒<span class="heading-dot">.</span></h1><p class="muted">把注意力留给接下来的机会，不错过每一次安排。</p></div><RouterLink class="primary-link" to="/applications">管理投递 →</RouterLink></section>
  <div class="reminder-toolbar"><el-radio-group v-model="view" aria-label="提醒范围"><el-radio-button v-for="option in views" :key="option.value" :value="option.value">{{ option.label }}</el-radio-button></el-radio-group><el-button :loading="loading" @click="load">刷新</el-button></div>
  <p v-if="page" class="reminder-caption">{{ view === 'RECENT' ? `${page.today} 至 ${page.throughDate}` : view === 'ALL' ? '所有未完成的安排' : '已完成的历史安排' }} · {{ page.timeZone }}<span>共 {{ count }} 条</span></p>
  <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="reminder-error"><p v-if="page">当前显示上次读取的内容，请刷新后再操作。</p><el-button text @click="load">重新加载</el-button></el-alert>
  <div v-if="loading && !page" class="empty-card" aria-live="polite">正在读取安排…</div>
  <div v-else-if="page && !count && !error" class="empty-card"><span class="empty-icon">✓</span><h2>{{ view === 'COMPLETED' ? '还没有已完成的安排' : view === 'ALL' ? '暂无待办安排' : '近三天没有待办，也没有逾期事项' }}</h2><p class="muted">{{ view === 'RECENT' ? '可以切换“全部待办”查看更远的安排，或在投递下添加流程。' : '在投递管理中记录流程与安排，提醒会自动出现在这里。' }}</p><RouterLink class="primary-link" to="/applications">前往投递管理 →</RouterLink></div>
  <section v-for="section in sections" :key="section.key" class="reminder-section" :aria-label="section.title"><div class="reminder-section-heading"><h2>{{ section.title }} <span>{{ section.items.length }}</span></h2><p class="muted">{{ section.description }}</p></div><div class="reminder-list"><ReminderCard v-for="item in section.items" :key="item.process.id" :item="item" :disabled="busy || !!error" @complete="complete(item)" @edit="edit(item)" /></div></section>
  <p class="page-note">与投递管理共用流程记录 · 已取消及“仅记录阶段”不进入提醒 · 页面可见时自动刷新</p>
  <ProcessEditor v-if="editing && page" :application-id="editing.process.applicationId" :time-zone="page.timeZone" :record="editing.process" :heading="editing.heading" @saved="load" @closed="editing = undefined" />
</template>

<style scoped>
.reminder-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px}.reminder-caption{font-size:12px;color:#7e9186;margin:20px 0}.reminder-caption span{float:right}.reminder-section{margin-top:26px}.reminder-section-heading{margin-bottom:14px}.reminder-section-heading h2{font-size:17px;margin:0;font-weight:600}.reminder-section-heading h2 span{font-size:12px;background:#e5eee9;border-radius:12px;padding:3px 8px;margin-left:6px}.reminder-section-heading p{margin:8px 0 0}.reminder-list{display:grid;gap:14px}.reminder-error{margin:16px 0}@media(max-width:500px){.reminder-toolbar{flex-wrap:wrap}.reminder-caption{line-height:1.8}.reminder-caption span{float:none;display:block}}
</style>
