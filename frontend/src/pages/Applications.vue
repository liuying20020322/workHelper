<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ProcessPanel from '../components/ProcessPanel.vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { dateText, request, stageName, stages, type ApplicationInput, type JobApplication } from '../api'

const items = ref<JobApplication[]>([])
const search = ref('')
const stage = ref('')
const loading = ref(false)
const loadError = ref('')
const closedCompanies = ref(new Set<string>())
const openedJobs = ref(new Set<number>())
const dialog = ref(false)
const saving = ref(false)
const deleting = ref<number | null>(null)
const editingId = ref<number | null>(null)
const saveError = ref('')
const formRef = ref<FormInstance>()
const timeZone = ref('Asia/Shanghai')
let loadVersion = 0

function localNow() {
  const parts = new Intl.DateTimeFormat('sv-SE', { timeZone: timeZone.value, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(new Date())
  return parts.replace(' ', 'T')
}
const emptyForm = (): ApplicationInput => ({ companyName: '', positionName: '', location: '', requirements: '', appliedAt: localNow(), channel: '', jobUrl: '', notes: '' })
const form = ref<ApplicationInput>(emptyForm())
const rules: FormRules = {
  companyName: [{ required: true, whitespace: true, message: '请填写公司名称', trigger: 'blur' }],
  positionName: [{ required: true, whitespace: true, message: '请填写岗位名称', trigger: 'blur' }],
  appliedAt: [{ required: true, message: '请选择投递时间', trigger: 'change' }],
  jobUrl: [{ pattern: /^(https?:\/\/[^\s]+)?$/i, message: '请输入完整的 http:// 或 https:// 地址', trigger: 'blur' }],
}
const groups = computed(() => {
  const map = new Map<string, JobApplication[]>()
  for (const item of items.value) {
    const jobs = map.get(item.companyName) || []
    jobs.push(item)
    map.set(item.companyName, jobs)
  }
  return [...map].map(([company, jobs]) => ({ company, jobs }))
})
const filtered = computed(() => !!search.value.trim() || !!stage.value)
function toggleCompany(company: string) {
  if (closedCompanies.value.has(company)) closedCompanies.value.delete(company)
  else closedCompanies.value.add(company)
}
function toggleJob(id: number) {
  if (openedJobs.value.has(id)) openedJobs.value.delete(id)
  else openedJobs.value.add(id)
}
async function load() {
  const version = ++loadVersion
  loading.value = true
  loadError.value = ''
  try {
    const result = await request<JobApplication[]>(`/api/applications?${new URLSearchParams({ search: search.value, stage: stage.value })}`)
    if (version === loadVersion) items.value = result
  } catch (error) {
    if (version === loadVersion) loadError.value = (error as Error).message
  } finally { if (version === loadVersion) loading.value = false }
}
async function refreshApplication(item: JobApplication) {
  try {
    const latest = await request<JobApplication>(`/api/applications/${item.id}`)
    Object.assign(item, latest)
    if (stage.value && latest.currentStage !== stage.value) {
      items.value = items.value.filter(row => row.id !== item.id)
      ElMessage.info('阶段已更新，该投递不再符合当前筛选条件')
    }
  } catch (error) { loadError.value = (error as Error).message }
}
function resetFilters() { search.value = ''; stage.value = ''; void load() }
async function openForm(item?: JobApplication) {
  editingId.value = item?.id ?? null
  form.value = item ? { companyName: item.companyName, positionName: item.positionName, location: item.location, requirements: item.requirements, appliedAt: item.appliedAt, channel: item.channel, jobUrl: item.jobUrl, notes: item.notes } : emptyForm()
  saveError.value = ''
  dialog.value = true
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  saveError.value = ''
  try {
    const saved = await request<JobApplication>(editingId.value === null ? '/api/applications' : `/api/applications/${editingId.value}`, {
      method: editingId.value === null ? 'POST' : 'PUT', body: JSON.stringify(form.value),
    })
    dialog.value = false
    closedCompanies.value.delete(saved.companyName)
    openedJobs.value.add(saved.id)
    ElMessage.success(editingId.value === null ? '投递已添加' : '投递已更新')
    await load()
  } catch (error) { saveError.value = (error as Error).message }
  finally { saving.value = false }
}
async function remove(item: JobApplication) {
  try {
    await ElMessageBox.confirm(`确定删除「${item.companyName} · ${item.positionName}」吗？此操作不可撤销，关联的流程、安排和面试问答也将一并删除。`, '删除投递', { confirmButtonText: '确认删除', cancelButtonText: '保留记录', type: 'warning' })
  } catch { return }
  deleting.value = item.id
  try {
    await request<void>(`/api/applications/${item.id}`, { method: 'DELETE' })
    openedJobs.value.delete(item.id)
    ElMessage.success('投递已删除')
    await load()
  } catch (error) { ElMessage.error((error as Error).message) }
  finally { deleting.value = null }
}
onMounted(async () => {
  void load()
  try { timeZone.value = (await request<{ timeZone: string }>('/api/info')).timeZone }
  catch { /* The list request displays connection errors. */ }
})
</script>

<template>
  <section class="page-heading">
    <div><p class="eyebrow">ONE OPPORTUNITY AT A TIME</p><h1>投递管理<span class="heading-dot">.</span></h1><p class="muted">记录每一次投递，让求职进展清晰有序。</p></div>
    <el-button type="primary" size="large" @click="openForm()"><span class="plus">＋</span>新增投递</el-button>
  </section>
  <section class="workspace">
    <div class="filter-bar">
      <el-input v-model="search" maxlength="200" class="search" clearable placeholder="搜索公司、岗位或地点" aria-label="搜索公司、岗位或地点" @keyup.enter="load" @clear="load"><template #prefix>⌕</template></el-input>
      <el-select v-model="stage" placeholder="全部阶段" clearable aria-label="当前阶段" @change="load"><el-option label="全部阶段" value="" /><el-option v-for="[value, label] in stages" :key="value" :label="label" :value="value" /></el-select>
      <el-button @click="load" :loading="loading">搜索</el-button>
      <el-button v-if="filtered" text @click="resetFilters">重置</el-button>
    </div>
    <div class="list-caption"><span>{{ filtered ? '筛选结果' : '全部投递' }} <strong>{{ items.length }}</strong><span class="muted"> 条 · {{ groups.length }} 家公司</span></span><span class="muted">按投递时间从新到旧</span></div>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false"><el-button text @click="load">重新加载</el-button></el-alert>
    <div v-else-if="loading" class="loading-state" aria-live="polite">正在读取投递记录…</div>
    <div v-else-if="!items.length" class="empty-card">
      <span class="empty-icon">▤</span><h2>{{ filtered ? '没有找到匹配的投递' : '从记录第一份投递开始' }}</h2>
      <p class="muted">{{ filtered ? '试试其他关键词，或清除筛选条件。' : '只需填写公司和岗位，其他信息可以随时补充。' }}</p>
      <el-button v-if="filtered" @click="resetFilters">清除筛选</el-button><el-button v-else type="primary" @click="openForm()">＋ 新增投递</el-button>
    </div>
    <div v-else class="company-list">
      <section v-for="group in groups" :key="group.company" class="company-card">
        <button class="company-heading" :aria-expanded="!closedCompanies.has(group.company)" @click="toggleCompany(group.company)">
          <span class="company-avatar">{{ group.company.slice(0, 1) }}</span><span class="company-name">{{ group.company }}</span><span class="count-tag">{{ group.jobs.length }} 个投递</span><span class="chevron">{{ closedCompanies.has(group.company) ? '＋' : '−' }}</span>
        </button>
        <div v-if="!closedCompanies.has(group.company)" class="jobs">
          <article v-for="item in group.jobs" :key="item.id" class="job">
            <div class="job-row">
              <button class="job-toggle" :aria-expanded="openedJobs.has(item.id)" @click="toggleJob(item.id)"><span class="arrow">{{ openedJobs.has(item.id) ? '⌄' : '›' }}</span><span><strong>{{ item.positionName }}</strong><span class="job-meta">{{ item.location || '地点待补充' }}<span>·</span>{{ dateText(item.appliedAt) }} 投递</span></span></button>
              <el-tag effect="plain" round>{{ stageName(item.currentStage) }}</el-tag>
              <div class="job-actions"><el-button text @click="openForm(item)">编辑</el-button><el-button text class="delete-button" :loading="deleting === item.id" :disabled="deleting !== null" @click="remove(item)">删除</el-button></div>
            </div>
            <div v-if="openedJobs.has(item.id)" class="job-detail">
              <div class="detail-title">职位信息<span>编号 #{{ item.id }}</span></div>
              <dl class="detail-grid"><div><dt>投递渠道</dt><dd>{{ item.channel || '未填写' }}</dd></div><div><dt>职位链接</dt><dd><a v-if="/^https?:\/\//i.test(item.jobUrl)" :href="item.jobUrl" target="_blank" rel="noopener noreferrer">查看原始职位 ↗</a><span v-else>未填写</span></dd></div><div class="full"><dt>职位要求</dt><dd>{{ item.requirements || '暂无职位要求，可在编辑中补充。' }}</dd></div><div class="full"><dt>备注</dt><dd>{{ item.notes || '暂无备注' }}</dd></div></dl>
              <ProcessPanel :application-id="item.id" :time-zone="timeZone" @changed="refreshApplication(item)" />
              <div class="updated-at">最近更新 {{ dateText(item.updatedAt) }}</div>
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
  <p class="page-note">公司按名称自动分组 · 同公司不同岗位、地点独立记录 · 当前阶段由流程自动产生</p>

  <el-dialog v-model="dialog" :title="editingId === null ? '新增投递' : '编辑投递'" width="680px" class="application-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p class="form-intro">先记下这次机会，更多信息可以慢慢补充。<span>时间使用 {{ timeZone }}</span></p>
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" show-icon class="save-error" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <div class="form-grid"><el-form-item label="公司名称" prop="companyName"><el-input v-model="form.companyName" maxlength="200" placeholder="例如：腾讯" /></el-form-item><el-form-item label="岗位名称" prop="positionName"><el-input v-model="form.positionName" maxlength="200" placeholder="例如：Java 后端开发" /></el-form-item>
      <el-form-item label="工作地点"><el-input v-model="form.location" maxlength="200" placeholder="例如：深圳 / 远程" /></el-form-item><el-form-item label="投递时间" prop="appliedAt"><el-date-picker v-model="form.appliedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" :clearable="false" /></el-form-item>
      <el-form-item label="投递渠道"><el-input v-model="form.channel" maxlength="200" placeholder="官网、招聘平台、内推…" /></el-form-item><el-form-item label="职位链接" prop="jobUrl"><el-input v-model="form.jobUrl" maxlength="2000" placeholder="https://" /></el-form-item></div>
      <el-form-item label="职位要求"><el-input v-model="form.requirements" type="textarea" :rows="4" maxlength="10000" show-word-limit placeholder="记录岗位职责、技能要求，方便后续准备" /></el-form-item><el-form-item label="备注"><el-input v-model="form.notes" type="textarea" :rows="3" maxlength="10000" show-word-limit placeholder="其他想记下的信息" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ editingId === null ? '保存投递' : '保存修改' }}</el-button></template>
  </el-dialog>
</template>
