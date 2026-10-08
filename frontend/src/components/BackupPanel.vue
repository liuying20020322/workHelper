<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

interface Counts { applications: number; processes: number; questions: number }
interface Preview { sha256: string; currentRevision: number; timeZone: string; exportedAt: string; incoming: Counts; current: Counts }
interface SafetyFile { name: string; bytes: number; createdAt: string }
const emit = defineEmits<{ restored: []; closed: [] }>()
const open = ref(true)
const file = ref<File>()
const preview = ref<Preview>()
const confirmation = ref('')
const busy = ref('')
const error = ref('')
const success = ref('')
const safetyFiles = ref<SafetyFile[]>([])
const safetyError = ref('')
async function call(url: string, init?: RequestInit) {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), 120000)
  try {
    const response = await fetch(url, { ...init, signal: controller.signal, cache: 'no-store' })
    if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message || `操作失败（${response.status}），请重新检查`) }
    return response
  } catch (e) {
    if (e instanceof TypeError || (e instanceof DOMException && e.name === 'AbortError')) throw new Error('连接失败或请求超时。若刚执行恢复，结果可能尚未收到，请先重新载入页面核对数据，勿直接重复恢复。')
    throw e
  } finally { window.clearTimeout(timer) }
}
async function listSafety() {
  safetyError.value = ''
  try { safetyFiles.value = await (await call('/api/backups/safety')).json() }
  catch (e) { safetyError.value = (e as Error).message }
}
function select(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0]
  preview.value = undefined; confirmation.value = ''; error.value = ''; success.value = ''
  if (file.value && (!file.value.size || file.value.size > 32 * 1024 * 1024)) {
    error.value = '请选择非空且不超过 32 MB 的 JSON 备份文件'; file.value = undefined
  }
}
async function download(url = '/api/backups', name?: string) {
  busy.value = 'download'; error.value = ''
  try {
    const response = await call(url)
    const blob = await response.blob()
    const objectUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = name || response.headers.get('content-disposition')?.match(/filename="([^"]+)"/)?.[1] || 'workHelper-backup.json'
    document.body.append(anchor); anchor.click(); anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60000)
    ElMessage.success('已发起下载，请确认浏览器下载已完成并妥善保存文件')
  } catch (e) { error.value = (e as Error).message }
  finally { busy.value = '' }
}
async function validate() {
  if (!file.value) return
  busy.value = 'preview'; error.value = ''; success.value = ''; preview.value = undefined; confirmation.value = ''
  try { preview.value = await (await call('/api/backups/preview', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: file.value })).json() }
  catch (e) { error.value = (e as Error).message }
  finally { busy.value = '' }
}
async function restore() {
  if (!preview.value || !file.value || confirmation.value !== '替换全部数据') return
  busy.value = 'restore'; error.value = ''
  try {
    const result = await (await call('/api/backups/restore', { method: 'POST', headers: {
      'Content-Type': 'application/json', 'X-Backup-Revision': String(preview.value.currentRevision),
      'X-Backup-SHA256': preview.value.sha256, 'X-Backup-Confirmation': 'REPLACE_ALL',
    }, body: file.value })).json()
    success.value = `恢复成功：${result.restored.applications} 条投递、${result.restored.processes} 条流程、${result.restored.questions} 条问答。恢复前自动备份：${result.safetyBackup}`
    emit('restored')
  } catch (e) { error.value = (e as Error).message }
  finally { preview.value = undefined; confirmation.value = ''; busy.value = ''; await listSafety() }
}
function close(done: () => void) { if (!busy.value) done(); else ElMessage.info('正在处理，请等待操作结束') }
onMounted(listSafety)
</script>

<template>
  <el-dialog v-model="open" title="备份与恢复" width="min(760px, 94vw)" :close-on-click-modal="false" :before-close="close" @closed="emit('closed')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="notice" />
    <el-alert v-if="success" :title="success" type="success" :closable="false" show-icon class="notice" />
    <section class="backup-section"><h3>导出当前数据</h3><p>包含全部投递、流程、安排、面试总结和问答。备份文件含个人求职记录，请保存到可靠的位置。</p><el-button type="primary" :loading="busy === 'download'" :disabled="!!busy" @click="download()">下载 JSON 备份</el-button></section>
    <section class="backup-section"><h3>从备份恢复</h3><p>恢复会替换全部现有业务数据，不是合并。请先保存当前页面和其他标签页中的修改，并关闭其他编辑页面；恢复成功后当前页面会重新载入数据。</p><p>覆盖前会自动备份到本地 backups 目录；自动备份失败则停止，恢复失败则回滚。</p>
      <label class="file-label" for="backup-file">选择 JSON 备份（最多 32 MB）</label><input id="backup-file" type="file" accept=".json,application/json" :disabled="!!busy" @change="select" />
      <el-button :disabled="!file || !!busy" :loading="busy === 'preview'" @click="validate">校验备份</el-button>
      <template v-if="preview">
        <p class="muted">备份导出于 {{ new Date(preview.exportedAt).toLocaleString('zh-CN', { timeZone: preview.timeZone }) }} · {{ preview.timeZone }}</p>
        <table class="counts"><caption>恢复前后数据数量</caption><thead><tr><th>数据</th><th>当前</th><th>恢复后</th></tr></thead><tbody><tr><th>投递</th><td>{{ preview.current.applications }}</td><td>{{ preview.incoming.applications }}</td></tr><tr><th>流程 / 安排</th><td>{{ preview.current.processes }}</td><td>{{ preview.incoming.processes }}</td></tr><tr><th>问答</th><td>{{ preview.current.questions }}</td><td>{{ preview.incoming.questions }}</td></tr></tbody></table>
        <el-alert v-if="preview.incoming.applications === 0" title="这是空备份，恢复后将清空全部业务数据。" type="warning" :closable="false" class="notice" />
        <label class="file-label" for="restore-confirmation">确认替换：输入“替换全部数据”</label><el-input id="restore-confirmation" v-model="confirmation" :disabled="!!busy" placeholder="替换全部数据" autocomplete="off" />
        <el-button class="restore-button" type="danger" :loading="busy === 'restore'" :disabled="!!busy || confirmation !== '替换全部数据'" @click="restore">确认替换并恢复</el-button>
      </template>
    </section>
    <section class="backup-section"><div class="safety-heading"><h3>恢复前自动备份</h3><el-button text :disabled="!!busy" @click="listSafety">刷新列表</el-button></div><p>恢复出错或需要撤回时，下载对应文件，再按上面的步骤恢复。文件不会自动清理，也不会随重新构建删除。</p><el-alert v-if="safetyError" :title="safetyError" type="error" :closable="false" /><p v-else-if="!safetyFiles.length" class="muted">暂无自动备份。首次恢复前会自动创建。</p><ul class="safety-list"><li v-for="item in safetyFiles" :key="item.name"><span>{{ item.name }}<small>{{ (item.bytes / 1024).toFixed(1) }} KB</small></span><el-button :disabled="!!busy" size="small" @click="download(`/api/backups/safety/${encodeURIComponent(item.name)}`, item.name)">下载</el-button></li></ul></section>
    <template #footer><el-button :disabled="!!busy" @click="open = false">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.backup-section{padding:10px 0 20px;border-bottom:1px solid #e5ebe7}.backup-section h3{font-size:16px;color:#294b40}.backup-section p{font-size:13px;line-height:1.8;color:#667d72}.file-label{display:block;margin:16px 0 8px;font-size:13px}input[type=file]{display:block;width:100%;max-width:100%;margin-bottom:14px;font-size:13px}.counts{width:100%;border-collapse:collapse;margin:16px 0;font-size:13px}.counts caption{text-align:left;margin:10px 0}.counts th,.counts td{padding:10px;text-align:left;border-bottom:1px solid #e2e9e5}.counts thead{background:#f1f6f3}.restore-button{margin-top:14px}.notice{margin:12px 0;overflow-wrap:anywhere}.safety-heading{display:flex;align-items:center;justify-content:space-between;gap:10px}.safety-list{list-style:none;padding:0;max-height:240px;overflow:auto}.safety-list li{display:flex;align-items:center;gap:16px;padding:12px 0;border-bottom:1px solid #e5ebe7}.safety-list span{flex:1;min-width:0;overflow-wrap:anywhere;font-size:12px}.safety-list small{display:block;color:#8a9791;margin-top:6px}
</style>
