<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { businessNow, request, stageName, stages, statusNames, type ProcessInput, type ProcessRecord } from '../api'

const props = defineProps<{ applicationId: number; timeZone: string; record?: ProcessRecord; heading?: string }>()
const emit = defineEmits<{ saved: []; closed: [] }>()
const dialog = ref(false)
const saving = ref(false)
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
    emit('saved')
  } catch (e) { saveError.value = (e as Error).message }
  finally { saving.value = false }
}
onMounted(() => open(props.record))
</script>

<template>
  <el-dialog v-model="dialog" :title="editId === null ? '添加流程 / 安排' : '编辑流程 / 改期'" width="680px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close @closed="emit('closed')">
    <p v-if="heading" class="form-intro">{{ heading }}</p><p class="form-intro">时间使用 {{ timeZone }}。可补录历史阶段，不会使普通阶段倒退。</p>
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
