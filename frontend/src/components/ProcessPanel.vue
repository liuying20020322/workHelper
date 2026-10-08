<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProcessEditor from './ProcessEditor.vue'
import { dateText, request, stageName, statusNames, type ProcessRecord, type ProcessStatus } from '../api'

const props = defineProps<{ applicationId: number; timeZone: string; highlightId?: number }>()
const emit = defineEmits<{ changed: [] }>()
const records = ref<ProcessRecord[]>([])
const loading = ref(false)
const loadError = ref('')
const busyId = ref<number | null>(null)
const editorOpen = ref(false)
const selected = ref<ProcessRecord>()
const endpoint = `/api/applications/${props.applicationId}/processes`
function open(record?: ProcessRecord) { selected.value = record; editorOpen.value = true }
async function saved() { await load(); emit('changed') }
async function focusLinkedProcess() {
  if (!props.highlightId) return
  await nextTick()
  const element = document.getElementById(`process-${props.highlightId}`)
  element?.scrollIntoView({ block: 'center' })
  element?.focus({ preventScroll: true })
}
watch(() => props.highlightId, focusLinkedProcess)
async function load() {
  loading.value = true
  loadError.value = ''
  try { records.value = await request<ProcessRecord[]>(endpoint) }
  catch (e) { loadError.value = (e as Error).message }
  finally { loading.value = false }
  await focusLinkedProcess()
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
      <li v-for="record in records" :key="record.id" :id="`process-${record.id}`" tabindex="-1" :class="{ cancelled: record.status === 'CANCELLED', highlighted: record.id === highlightId }">
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
  <ProcessEditor v-if="editorOpen" :application-id="applicationId" :time-zone="timeZone" :record="selected" @saved="saved" @closed="editorOpen = false" />
</template>
<style scoped>
.process-section{border-top:1px solid #dce6df;padding-top:18px}.process-heading,.process-title{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.process-heading{justify-content:space-between}.process-heading strong,.process-title strong{font-size:14px}.process-timeline{list-style:none;margin:18px 0;padding:0 0 0 16px;border-left:2px solid #dce8e1}.process-timeline li{position:relative;border-bottom:1px solid #e8eee9;padding:0 0 18px;margin-bottom:18px}.process-timeline li::before{content:'';position:absolute;left:-22px;top:5px;width:10px;height:10px;border-radius:50%;background:#167d70;border:2px solid #fbfcfb}.process-timeline li.cancelled::before{background:#9ba7a0}.schedule-text{font-size:13px;line-height:1.7;color:#4b6d60}.process-copy{font-size:13px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}.process-actions{display:flex;flex-wrap:wrap;gap:8px}.process-actions .el-button{margin:0}.process-help{font-size:11px;color:#8a9791;line-height:1.8;margin-bottom:0}
.process-timeline li.highlighted{outline:2px solid #167d70;outline-offset:8px;border-radius:4px;scroll-margin-top:110px}</style>
