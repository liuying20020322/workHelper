<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dateText, request, stageName, type JobApplication, type ProcessRecord } from '../api'

interface Question { id?: number; question: string; answer: string; review: string; key: number }
interface Detail { application: JobApplication; process: ProcessRecord; timeZone: string; summary: string; version: number; questions: Question[] }
const route = useRoute()
const detail = ref<Detail>()
const summary = ref('')
const questions = ref<Question[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const baseline = ref('')
const dialog = ref<'summary' | 'question'>()
const editIndex = ref(-1)
const draftSummary = ref('')
const draft = ref({ question: '', answer: '', review: '' })
const dialogBaseline = ref('')
const dialogDirty = computed(() => !!dialog.value && (dialog.value === 'summary' ? draftSummary.value : JSON.stringify(draft.value)) !== dialogBaseline.value)
let nextKey = 0
const endpoint = computed(() => `/api/applications/${route.params.applicationId}/processes/${route.params.processId}/interview`)
const back = computed(() => `/applications?applicationId=${route.params.applicationId}&processId=${route.params.processId}`)
const payload = computed(() => ({ summary: summary.value, questions: questions.value.map(({ id, question, answer, review }) => ({ id, question, answer, review })) }))
const dirty = computed(() => !!detail.value && JSON.stringify(payload.value) !== baseline.value)
function apply(data: Detail) {
  detail.value = data
  summary.value = data.summary
  questions.value = data.questions.map(q => ({ ...q, key: nextKey++ }))
  baseline.value = JSON.stringify(payload.value)
}
async function confirmLeave() {
  if (saving.value) { ElMessage.info('正在保存，请稍候'); return false }
  if (!dirty.value && !dialogDirty.value) return true
  try { await ElMessageBox.confirm('有尚未保存的面经修改，离开后这些修改会丢失。', '离开面经', { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning' }); return true }
  catch { return false }
}
async function load() {
  loading.value = true
  error.value = ''
  try { apply(await request<Detail>(endpoint.value)) }
  catch (e) { error.value = (e as Error).message }
  finally { loading.value = false }
}
async function reload() { if (await confirmLeave()) await load() }
async function save() {
  const empty = questions.value.findIndex(q => !q.question.trim())
  if (empty !== -1) { ElMessage.warning(`第 ${empty + 1} 条问题不能为空`); return }
  saving.value = true
  error.value = ''
  try {
    apply(await request<Detail>(endpoint.value, { method: 'PUT', body: JSON.stringify({ ...payload.value, version: detail.value!.version }) }))
    ElMessage.success('面经已保存')
  } catch (e) { error.value = (e as Error).message }
  finally { saving.value = false }
}
function editSummary() {
  draftSummary.value = summary.value
  dialogBaseline.value = draftSummary.value
  dialog.value = 'summary'
}
function editQuestion(index = -1) {
  editIndex.value = index
  const q = questions.value[index]
  draft.value = { question: q?.question || '', answer: q?.answer || '', review: q?.review || '' }
  dialogBaseline.value = JSON.stringify(draft.value)
  dialog.value = 'question'
}
function applyDraft() {
  if (dialog.value === 'summary') summary.value = draftSummary.value
  else {
    if (!draft.value.question.trim()) { ElMessage.warning('问题不能为空'); return }
    if (editIndex.value < 0) questions.value.push({ ...draft.value, key: nextKey++ })
    else Object.assign(questions.value[editIndex.value]!, draft.value)
  }
  dialog.value = undefined
}
async function closeDialog(done?: () => void) {
  if (dialogDirty.value) {
    try { await ElMessageBox.confirm('放弃本次弹窗内尚未应用的修改？', '关闭编辑', { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning' }) }
    catch { return }
  }
  dialog.value = undefined
  done?.()
}
function move(index: number, step: number) {
  const target = index + step
  if (target < 0 || target >= questions.value.length) return
  const item = questions.value.splice(index, 1)[0]!
  questions.value.splice(target, 0, item)
}
async function remove(index: number) {
  try { await ElMessageBox.confirm('此条问答将在保存整轮面经后删除。', '删除问答', { confirmButtonText: '删除问答', cancelButtonText: '保留问答', type: 'warning' }); questions.value.splice(index, 1) }
  catch { /* Keep the draft on cancel. */ }
}
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || dialogDirty.value || saving.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(confirmLeave)
onBeforeRouteUpdate(confirmLeave)
watch(endpoint, () => { detail.value = undefined; dialog.value = undefined; void load() })
onMounted(() => { void load(); window.addEventListener('beforeunload', beforeUnload) })
onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">LEARN FROM EVERY CONVERSATION</p><h1>面经复盘<span class="heading-dot">.</span></h1><p class="muted">先记住问题，再慢慢补全答案。</p></div><RouterLink class="primary-link" :to="back">← 返回投递与流程</RouterLink></section>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="error"><p v-if="detail">当前输入已保留；重新载入前请先复制需要保留的内容。</p><el-button text :disabled="saving || loading" @click="reload">重新载入</el-button></el-alert>
  <p v-if="loading" class="muted">正在读取面经…</p>
  <template v-else-if="detail">
    <section class="interview-context"><h2>{{ detail.application.companyName }} · {{ detail.application.positionName }}</h2><p>{{ detail.application.location || '工作地点未填写' }} · {{ detail.process.roundName || stageName(detail.process.stage) }}</p><p class="muted">{{ detail.process.timeMode === 'RECORD_ONLY' ? '阶段发生' : detail.process.timeMode === 'DEADLINE' ? '截止时间' : '开始时间' }}：{{ dateText(detail.process.timeMode === 'RECORD_ONLY' ? detail.process.occurredAt : detail.process.timeMode === 'DEADLINE' ? detail.process.deadlineAt! : detail.process.startAt!) }} · {{ detail.timeZone }}</p></section>
    <el-form label-position="top" :disabled="saving" @submit.prevent="save">
      <section class="note-card"><div class="question-heading"><h2>整体总结</h2><el-button :disabled="saving" @click="editSummary">编辑总结</el-button></div><p class="note-text" :class="{ muted: !summary }">{{ summary || '还没有总结，可以稍后补充面试感受与准备方向。' }}</p></section>
      <div class="question-heading"><h2>面试问答 <span class="muted">{{ questions.length }} 条</span></h2><el-button :disabled="saving || questions.length >= 200" @click="editQuestion()">＋ 添加问答</el-button></div>
      <p v-if="!questions.length" class="note-card muted">还没有问答。可以先记录整体总结，或添加第一个问题。</p>
      <section v-for="(q, index) in questions" :key="q.key" class="note-card" :aria-label="`第 ${index + 1} 条问答`">
        <div class="question-heading"><h3>问题 {{ index + 1 }}</h3><div class="question-actions"><el-button :disabled="saving || index === 0" size="small" @click="move(index, -1)">上移</el-button><el-button :disabled="saving || index === questions.length - 1" size="small" @click="move(index, 1)">下移</el-button><el-button :disabled="saving" size="small" @click="editQuestion(index)">编辑问答</el-button><el-button :disabled="saving" size="small" type="danger" plain @click="remove(index)">删除问答</el-button></div></div>
        <p class="note-text question-text">{{ q.question }}</p><h4>当时回答</h4><p class="note-text" :class="{ muted: !q.answer }">{{ q.answer || '暂未填写' }}</p><h4>复盘补充</h4><p class="note-text" :class="{ muted: !q.review }">{{ q.review || '暂未填写' }}</p>
      </section>
      <div class="save-bar"><span class="muted" aria-live="polite">{{ dirty ? '有未保存的修改' : '内容已同步' }} · 增删与排序统一保存</span><el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存面经</el-button></div>
    </el-form>
    <el-dialog :model-value="!!dialog" :title="dialog === 'summary' ? '编辑整体总结' : editIndex < 0 ? '添加问答' : '编辑问答'" width="min(720px, 94vw)" :before-close="closeDialog" :close-on-click-modal="false" destroy-on-close>
      <el-form label-position="top" @submit.prevent="applyDraft">
        <el-form-item v-if="dialog === 'summary'" label="整体总结"><el-input v-model="draftSummary" type="textarea" :rows="9" maxlength="50000" show-word-limit placeholder="面试感受、薄弱环节、后续准备，可稍后补充" /></el-form-item>
        <template v-else><el-form-item label="问题（必填）"><el-input v-model="draft.question" type="textarea" :rows="3" maxlength="20000" show-word-limit placeholder="面试官问了什么？" /></el-form-item><el-form-item label="当时回答"><el-input v-model="draft.answer" type="textarea" :rows="4" maxlength="50000" show-word-limit placeholder="可留空，之后补充" /></el-form-item><el-form-item label="复盘补充"><el-input v-model="draft.review" type="textarea" :rows="4" maxlength="50000" show-word-limit placeholder="更好的回答、知识点或待查资料" /></el-form-item></template>
      </el-form><template #footer><span class="muted">应用后，请在详情页保存面经。</span><el-button @click="closeDialog()">取消</el-button><el-button type="primary" @click="applyDraft">应用修改</el-button></template>
    </el-dialog>
  </template>
</template>

<style scoped>
.interview-context,.note-card{background:white;border:1px solid #e0e8e3;border-radius:12px;padding:24px;margin:18px 0}.interview-context h2{font-size:20px;margin-top:0;overflow-wrap:anywhere}.interview-context p{line-height:1.8}.question-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.question-heading h2{font-size:18px}.question-heading h3{font-size:15px}.question-heading h2 span{font-size:12px;margin-left:8px}.question-actions{display:flex;gap:8px}.question-actions .el-button{margin:0}.note-card :deep(.el-textarea__inner){line-height:1.8;overflow-wrap:anywhere}.note-card :deep(.el-form-item:last-child){margin-bottom:0}.save-bar{position:sticky;bottom:0;padding:16px 20px;background:#f4f8f5;border:1px solid #dce6df;border-radius:10px;display:flex;justify-content:space-between;align-items:center;gap:12px;z-index:3}.error{margin-bottom:20px}@media(max-width:560px){.note-card,.interview-context{padding:16px}.save-bar{flex-wrap:wrap}.page-heading{gap:16px;flex-wrap:wrap}}
</style>
<style scoped>
.note-text{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;font-size:14px}.question-text{font-weight:600}.note-card h4{font-size:12px;color:#6c8175;margin-bottom:6px}.question-actions{flex-wrap:wrap}
</style>
