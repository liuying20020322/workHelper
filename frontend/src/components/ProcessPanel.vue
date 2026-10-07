<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { businessNow, dateText, request, stageName, stages, statusNames, type ProcessInput, type ProcessRecord, type ProcessStatus } from '../api'

const props = defineProps<{ applicationId: number; timeZone: string }>()
const emit = defineEmits<{ changed: [] }>()
const records = ref<ProcessRecord[]>([])
const loading = ref(false)
const loadError = ref('')
const dialog = ref(false)
const saving = ref(false)
const busyId = ref<number | null>(null)
const editId = ref<number | null>(null)
const saveError = ref('')
const formRef = ref<FormInstance>()
const endpoint = `/api/applications/${props.applicationId}/processes`
const empty = (): ProcessInput => ({ stage: 'INTERVIEW_1', roundName: '', timeMode: 'SCHEDULED', startAt: null, endAt: null, deadlineAt: null, status: 'PENDING', location: '', notes: '', occurredAt: businessNow(props.timeZone) })
const form = ref<ProcessInput>(empty())
const isInterview = computed(() => form.value.stage.startsWith('INTERVIEW_'))
const rules: FormRules = {
  stage: [{ required: true, message: '请选择流程阶段' }],
  timeMode: [{ required: true, message: '请选择时间方式' }],
  status: [{ required: true, message: '请选择安排状态' }],
  occurredAt: [{ required: true, message: '请选择阶段发生时间' }],
}
async function load() {
  loading.value = true
  loadError.value = ''
  try { records.value = await request<ProcessRecord[]>(endpoint) }
  catch (e) { loadError.value = (e as Error).message }
  finally { loading.value = false }
}
function open(record?: ProcessRecord) {
  editId.value = record?.id ?? null
  form.value = record ? { stage: record.stage, roundName: record.roundName, timeMode: record.timeMode, startAt: record.startAt, endAt: record.endAt, deadlineAt: record.deadlineAt, status: record.status, location: record.location, notes: record.notes, occurredAt: record.occurredAt } : empty()
  saveError.value = ''
  dialog.value = true
}
function stageChanged() {
  form.value.roundName = ''
  if (['OFFER', 'REJECTED', 'WITHDRAWN'].includes(form.value.stage)) form.value.timeMode = 'RECORD_ONLY'
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  const data = { ...form.value }
  saveError.value = ''
  if (data.timeMode === 'SCHEDULED') {
    if (!data.startAt) { saveError.value = '定时安排必须填写开始时间'; return }
    if (data.endAt && data.endAt < data.startAt) { saveError.value = '结束时间不能早于开始时间'; return }
    data.deadlineAt = null
  } else if (data.timeMode === 'DEADLINE') {
    if (!data.deadlineAt) { saveError.value = '截止任务必须填写截止时间'; return }
    if (data.startAt && data.deadlineAt < data.startAt) { saveError.value = '截止时间不能早于开始时间'; return }
    data.endAt = null
  } else { data.startAt = null; data.endAt = null; data.deadlineAt = null }
  saving.value = true
  try {
    await request<ProcessRecord>(editId.value === null ? endpoint : `${endpoint}/${editId.value}`, { method: editId.value === null ? 'POST' : 'PUT', body: JSON.stringify(data) })
    dialog.value = false
    ElMessage.success('流程已保存，投递阶段已更新')
    await load()
    emit('changed')
  } catch (e) { saveError.value = (e as Error).message }
  finally { saving.value = false }
}
async function changeStatus(record: ProcessRecord, status: ProcessStatus) {
  if (status === 'CANCELLED') {
    try { await ElMessageBox.confirm('取消仅停止这次安排，不撤销招聘阶段。误添加的流程请使用删除。', '取消安排', { confirmButtonText: '取消安排', cancelButtonText: '保留安排', type: 'warning' }) }
    catch { return }
  }
  busyId.value = record.id
  try {
    await request<ProcessRecord>(`${endpoint}/${record.id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })
    ElMessage.success(`已设为${statusNames[status]}`)
    await load()
    emit('changed')
  } catch (e) { ElMessage.error((e as Error).message) }
  finally { busyId.value = null }
}
async function remove(record: ProcessRecord) {
  try { await ElMessageBox.confirm(`删除「${record.roundName || stageName(record.stage)}」后，对应安排一并删除，投递阶段会重新计算。此操作不可撤销。`, '删除流程', { confirmButtonText: '确认删除', cancelButtonText: '保留记录', type: 'warning' }) }
  catch { return }
  busyId.value = record.id
  try {
    await request<void>(`${endpoint}/${record.id}`, { method: 'DELETE' })
    ElMessage.success('流程已删除，投递阶段已重新计算')
    await load()
    emit('changed')
  } catch (e) { ElMessage.error((e as Error).message) }
  finally { busyId.value = null }
}
function schedule(record: ProcessRecord) {
  if (record.timeMode === 'RECORD_ONLY') return '仅记录阶段 · 未设置提醒'
  if (record.timeMode === 'DEADLINE') return `截止任务 · ${dateText(record.deadlineAt!)} 截止${record.startAt ? '（开始 ' + dateText(record.startAt) + '）' : ''}`
  return `定时安排 · ${dateText(record.startAt!)}${record.endAt ? ' — ' + dateText(record.endAt) : ''}`
}
onMounted(load)
</script>

<template>
  <section class="process-section" aria-label="流程记录">
    <div class="process-heading"><div><strong>流程记录</strong><span class="muted"> {{ records.length }} 条</span></div><el-button type="primary" plain size="small" :disabled="busyId !== null" @click="open()">＋ 添加流程 / 安排</el-button></div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false"><el-button text @click="load">重新加载</el-button></el-alert>
    <p v-else-if="loading" class="muted" aria-live="polite">正在读取流程…</p>
    <p v-else-if="!records.length" class="muted">暂无流程记录。收到测评、笔试或面试通知后，在这里记录安排。</p>
    <ol v-else class="process-timeline">
      <li v-for="record in records" :key="record.id" :class="{ cancelled: record.status === 'CANCELLED' }">
        <div class="process-title"><strong>{{ record.roundName || stageName(record.stage) }}</strong><span v-if="record.roundName && record.roundName !== stageName(record.stage)" class="muted">归属{{ stageName(record.stage) }}</span><el-tag size="small" :type="record.status === 'COMPLETED' ? 'success' : record.status === 'CANCELLED' ? 'info' : 'warning'">{{ statusNames[record.status] }}</el-tag></div>
        <p class="schedule-text">{{ schedule(record) }}</p>
        <p v-if="record.location" class="process-copy">地点 / 链接：<a v-if="/^https?:\/\/[^\s]+$/i.test(record.location)" :href="record.location" target="_blank" rel="noopener noreferrer">{{ record.location }}</a><span v-else>{{ record.location }}</span></p>
        <p v-if="record.notes" class="process-copy">{{ record.notes }}</p>
        <p class="muted">阶段发生：{{ dateText(record.occurredAt) }}</p>
        <div class="process-actions"><el-button v-if="record.status === 'PENDING'" size="small" :disabled="busyId !== null" @click="changeStatus(record, 'COMPLETED')">标记完成</el-button><el-button v-else size="small" :disabled="busyId !== null" @click="changeStatus(record, 'PENDING')">恢复待完成</el-button><el-button size="small" :disabled="busyId !== null" @click="open(record)">编辑 / 改期</el-button><el-button v-if="record.status !== 'CANCELLED'" size="small" :disabled="busyId !== null" @click="changeStatus(record, 'CANCELLED')">取消安排</el-button><el-button size="small" type="danger" plain :disabled="busyId !== null" @click="remove(record)">删除</el-button></div>
      </li>
    </ol>
    <p class="process-help">阶段表示已进入该环节；完成安排不会自动进入下一阶段。取消安排保留阶段，删除流程会重新计算。</p>
  </section>
  <el-dialog v-model="dialog" :title="editId === null ? '添加流程 / 安排' : '编辑流程 / 改期'" width="680px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p class="form-intro">时间使用 {{ timeZone }}。可补录历史阶段，不会使普通阶段倒退。</p>
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" show-icon class="save-error" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving">
      <div class="form-grid">
        <el-form-item label="流程阶段" prop="stage"><el-select v-model="form.stage" @change="stageChanged"><el-option v-for="[value,label] in stages.filter(([value]) => value !== 'APPLIED')" :key="value" :value="value" :label="label" /></el-select></el-form-item>
        <el-form-item v-if="isInterview" label="轮次名称（可选）"><el-input v-model="form.roundName" :placeholder="stageName(form.stage)" maxlength="200" /></el-form-item>
        <el-form-item label="阶段发生时间" prop="occurredAt"><el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" :clearable="false" /></el-form-item>
        <el-form-item label="安排状态" prop="status"><el-select v-model="form.status"><el-option v-for="(label,key) in statusNames" :key="key" :label="label" :value="key" /></el-select></el-form-item>
      </div>
      <el-form-item label="时间方式" prop="timeMode"><el-radio-group v-model="form.timeMode"><el-radio-button value="SCHEDULED">定时安排</el-radio-button><el-radio-button value="DEADLINE">截止任务</el-radio-button><el-radio-button value="RECORD_ONLY">仅记录阶段</el-radio-button></el-radio-group></el-form-item>
      <p v-if="form.timeMode === 'RECORD_ONLY'" class="muted">未设置提醒，只记录招聘阶段；已填写的安排时间将在保存时清除。</p>
      <div v-else class="form-grid">
        <el-form-item :label="form.timeMode === 'SCHEDULED' ? '开始时间（必填）' : '开始时间（可选）'"><el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" /></el-form-item>
        <el-form-item v-if="form.timeMode === 'SCHEDULED'" label="结束时间（可选）"><el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" /></el-form-item>
        <el-form-item v-else label="截止时间（必填）"><el-date-picker v-model="form.deadlineAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" /></el-form-item>
      </div>
      <el-form-item label="地点或链接"><el-input v-model="form.location" maxlength="2000" placeholder="现场地址、会议链接或测评入口" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.notes" type="textarea" :rows="3" maxlength="10000" show-word-limit placeholder="准备事项、联系人或其他说明" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存流程</el-button></template>
  </el-dialog>
</template>

<style scoped>
.process-section{border-top:1px solid #dce6df;padding-top:18px}.process-heading,.process-title{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.process-heading{justify-content:space-between}.process-heading strong,.process-title strong{font-size:14px}.process-timeline{list-style:none;margin:18px 0;padding:0 0 0 16px;border-left:2px solid #dce8e1}.process-timeline li{position:relative;border-bottom:1px solid #e8eee9;padding:0 0 18px;margin-bottom:18px}.process-timeline li::before{content:'';position:absolute;left:-22px;top:5px;width:10px;height:10px;border-radius:50%;background:#167d70;border:2px solid #fbfcfb}.process-timeline li.cancelled::before{background:#9ba7a0}.schedule-text{font-size:13px;line-height:1.7;color:#4b6d60}.process-copy{font-size:13px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}.process-actions{display:flex;flex-wrap:wrap;gap:8px}.process-actions .el-button{margin:0}.process-help{font-size:11px;color:#8a9791;line-height:1.8;margin-bottom:0}
</style>
