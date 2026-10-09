<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { request, businessNow, unappliedReasons, reasonText, type UnappliedCompany, type UnappliedInput } from '../api'
const items = ref<UnappliedCompany[]>([])
const search = ref(''), month = ref(''), error = ref(''), saveError = ref('')
const loading = ref(false), saving = ref(false), dialog = ref(false)
const editing = ref<number | null>(null), deleting = ref<number | null>(null)
const formRef = ref<FormInstance>()
let zone = 'Asia/Shanghai', version = 0
const empty = (): UnappliedInput => ({ companyName: '', reason: '', otherReason: '', viewedDate: businessNow(zone).slice(0, 10) })
const form = ref(empty())
const rules: FormRules = {
  companyName: [{ required: true, whitespace: true, message: '请填写公司名称', trigger: 'blur' }],
  reason: [{ required: true, message: '请选择未投原因', trigger: 'change' }],
  otherReason: [{ required: true, whitespace: true, message: '请填写具体原因', trigger: 'blur' }],
  viewedDate: [{ required: true, message: '请选择看过日期', trigger: 'change' }],
}
async function load() {
  const current = ++version
  loading.value = true; error.value = ''
  try {
    const result = await request<UnappliedCompany[]>(`/api/unapplied-companies?${new URLSearchParams({ search: search.value, month: month.value || '' })}`)
    if (current === version) items.value = result
  } catch (e) { if (current === version) error.value = (e as Error).message }
  finally { if (current === version) loading.value = false }
}
function open(item?: UnappliedCompany) {
  editing.value = item?.id ?? null
  form.value = item ? { companyName: item.companyName, reason: item.reason, otherReason: item.otherReason, viewedDate: item.viewedDate } : empty()
  saveError.value = ''; dialog.value = true
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true; saveError.value = ''
  try {
    await request(`/api/unapplied-companies${editing.value === null ? '' : '/' + editing.value}`, { method: editing.value === null ? 'POST' : 'PUT', body: JSON.stringify({ ...form.value, otherReason: form.value.reason === 'OTHER' ? form.value.otherReason : '' }) })
    dialog.value = false; ElMessage.success('记录已保存'); await load()
  } catch (e) { saveError.value = (e as Error).message }
  finally { saving.value = false }
}
async function remove(item: UnappliedCompany) {
  try { await ElMessageBox.confirm(`确定删除「${item.companyName}」的未投记录吗？`, '删除记录', { confirmButtonText: '确认删除', cancelButtonText: '保留记录', type: 'warning' }) } catch { return }
  deleting.value = item.id
  try { await request(`/api/unapplied-companies/${item.id}`, { method: 'DELETE' }); ElMessage.success('记录已删除'); await load() }
  catch (e) { ElMessage.error((e as Error).message) }
  finally { deleting.value = null }
}
function reset() { search.value = ''; month.value = ''; void load() }
onMounted(async () => { await load(); try { zone = (await request<{ timeZone: string }>('/api/info')).timeZone } catch { /* List displays connection errors. */ } })
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">KEEP TRACK OF WHAT YOU FOUND</p><h1>看过未投<span class="heading-dot">.</span></h1><p class="muted">记下看过的公司，以及当时没有投递的原因。</p></div><el-button type="primary" size="large" @click="open()">＋ 新增记录</el-button></section>
  <section class="workspace">
    <div class="filter-bar">
      <el-input v-model="search" class="search" maxlength="200" placeholder="搜索公司名称" aria-label="搜索公司名称" clearable @keyup.enter="load" @clear="load" />
      <el-date-picker v-model="month" type="month" value-format="YYYY-MM" placeholder="全部月份" aria-label="看过月份" clearable @change="load" />
      <el-button :loading="loading" @click="load">搜索</el-button><el-button v-if="search || month" text @click="reset">重置</el-button>
    </div>
    <div class="list-caption"><span>看过未投 <strong>{{ items.length }}</strong> 家公司</span><span class="muted">按看过日期从新到旧</span></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button text @click="load">重新加载</el-button></el-alert>
    <div v-else-if="loading" class="loading-state">正在读取记录…</div>
    <div v-else-if="!items.length" class="empty-card"><h2>{{ search || month ? '没有找到匹配的记录' : '记录一家看过但还没投的公司' }}</h2><p class="muted">{{ search || month ? '试试其他公司名称或月份。' : '填上公司、原因和看过日期，下次查看时不再重复查找。' }}</p><el-button v-if="search || month" @click="reset">清除筛选</el-button><el-button v-else type="primary" @click="open()">＋ 新增记录</el-button></div>
    <el-table v-else :data="items" class="unapplied-table"><el-table-column prop="companyName" label="公司名称" min-width="180" /><el-table-column label="未投原因" min-width="240"><template #default="{ row }"><span class="reason-text">{{ reasonText(row) }}</span></template></el-table-column><el-table-column prop="viewedDate" label="看过日期" width="140" /><el-table-column label="操作" width="150"><template #default="{ row }"><el-button text @click="open(row)">编辑</el-button><el-button text type="danger" :disabled="deleting !== null" :loading="deleting === row.id" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
  </section>
  <el-dialog v-model="dialog" :title="editing === null ? '新增记录' : '编辑记录'" width="min(520px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" show-icon />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <el-form-item label="公司名称" prop="companyName"><el-input v-model="form.companyName" maxlength="200" placeholder="填写公司名称" /></el-form-item>
      <el-form-item label="未投原因" prop="reason"><el-select v-model="form.reason" placeholder="请选择原因"><el-option v-for="[value, label] in unappliedReasons" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item v-if="form.reason === 'OTHER'" label="具体原因" prop="otherReason"><el-input v-model="form.otherReason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="填写其他原因" /></el-form-item>
      <el-form-item label="看过日期" prop="viewedDate"><el-date-picker v-model="form.viewedDate" type="date" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存记录</el-button></template>
  </el-dialog>
</template>
<style scoped>.unapplied-table{margin-top:12px}.reason-text{white-space:pre-wrap;overflow-wrap:anywhere}</style>
