<template>
  <div>
    <div class="pb-toolbar">
      <el-button type="primary" icon="Plus" @click="create">新建知识问答</el-button>
      <el-dropdown :disabled="!sel.length" @command="bulk">
        <el-button :disabled="!sel.length">编辑操作<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="ACTIVE">启用</el-dropdown-item>
            <el-dropdown-item command="PAUSED">暂停</el-dropdown-item>
            <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-select v-model="audit" placeholder="全选状态" clearable style="width:110px" @change="load">
        <el-option label="审核中" value="PENDING" /><el-option label="有效" value="APPROVED" /><el-option label="审核拒绝" value="REJECTED" />
      </el-select>
      <el-checkbox v-model="lowOnly">查看所有低质物料</el-checkbox>
      <span class="spacer" />
      <el-input v-model="kw" placeholder="问题/答案关键词" prefix-icon="Search" clearable style="width:200px" @keyup.enter="load" @clear="load" />
    </div>
    <div class="pb-toolbar">
      <el-button icon="Download" @click="exportCsv">问答下载</el-button>
      <el-tooltip content="上传Excel文件，批量创建问答。Excel文件须为文本格式，内容为一列问题，一列答案。最多一次添加100条问答。" placement="top">
        <el-button icon="Upload" @click="openUpload">批量上传问答</el-button>
      </el-tooltip>
      <el-button link type="primary" @click="download('/qas/template', {}, 'qa-template.xlsx')">下载模板</el-button>
      <span class="muted">每个推广单元最多 100 条问答<span class="evidence">公开资料</span></span>
    </div>
    <el-table :data="rows" border v-loading="loading" @selection-change="sel = $event">
      <el-table-column type="selection" width="38" />
      <el-table-column label="问题" min-width="200">
        <template #default="{ row }">
          <span>{{ row.question }}</span><el-icon class="edit-icon" @click="edit(row)"><EditPen /></el-icon>
        </template>
      </el-table-column>
      <el-table-column label="答案" min-width="200">
        <template #default="{ row }">
          <el-tooltip :content="stripHtml(row.answer)" placement="top" :show-after="400" popper-class="ans-tip">
            <span class="ellipsis">{{ stripHtml(row.answer).slice(0, 18) }}…</span>
          </el-tooltip>
          <el-icon class="edit-icon" @click="edit(row)"><EditPen /></el-icon>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tooltip :disabled="!row.rejectReason" :content="row.rejectReason">
            <span :class="['st', row.status === 'PAUSED' ? 'st-gray' : AUDIT[row.auditStatus].cls]">
              {{ row.status === 'PAUSED' ? '暂停推广' : AUDIT[row.auditStatus].text }}<el-icon v-if="row.rejectReason" style="vertical-align:-2px"><QuestionFilled /></el-icon>
            </span>
          </el-tooltip>
          <el-icon class="edit-icon" :title="row.status === 'ACTIVE' ? '暂停' : '启用'" @click="toggle(row)">
            <component :is="row.status === 'ACTIVE' ? 'VideoPause' : 'VideoPlay'" />
          </el-icon>
        </template>
      </el-table-column>
      <el-table-column label="出价（元）" width="110">
        <template #default="{ row }">
          <InlineEdit :value="row.bid" type="number" :min="0.3" :max="999.99" hint="清空则使用单元出价" @save="v => saveBid(row, v)">
            <span v-if="row.bid != null">{{ row.bid }}</span><span v-else class="muted">{{ unitBid(row) }}(单元)</span>
          </InlineEdit>
        </template>
      </el-table-column>
      <el-table-column label="推广单元" min-width="110"><template #default="{ row }">{{ unitName(row.unitId) }}</template></el-table-column>
      <el-table-column label="展现" width="75" sortable :sort-by="r => s(r).impressions"><template #default="{ row }">{{ s(row).impressions }}</template></el-table-column>
      <el-table-column label="点击" width="70"><template #default="{ row }">{{ s(row).clicks }}</template></el-table-column>
      <el-table-column label="点击率" width="75"><template #default="{ row }">{{ s(row).ctr }}%</template></el-table-column>
      <el-table-column label="消费" width="80"><template #default="{ row }">{{ s(row).cost }}</template></el-table-column>
      <el-table-column label="平均点击价格" width="95"><template #default="{ row }">{{ s(row).cpc }}</template></el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="suggest(row)">出价助手</el-button>
          <el-popconfirm title="删除该问答？" @confirm="remove(row)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="lowOnly" class="muted" style="margin-top:8px">低质物料（模拟口径）：所选时间内展现 ≥ 50 且点击率低于 2% 的问答。原产品“低质物料”判定标准未知。</div>

    <!-- 编辑问答 -->
    <el-dialog v-model="editDlg" title="编辑问答" width="1000px" top="4vh">
      <QaEditor v-if="editing" :model-value="editing" title="问答" :knowledge="knowledge" />
      <div class="muted">修改问题或答案后需要重新审核；只修改出价不需要审核。</div>
      <template #footer><el-button @click="editDlg = false">取消</el-button><el-button type="primary" @click="saveEdit">保存并提交</el-button></template>
    </el-dialog>

    <!-- 批量上传 -->
    <el-dialog v-model="upDlg" title="批量上传问答" width="520px">
      <el-form label-width="90px">
        <el-form-item label="推广单元" required>
          <el-select v-model="upUnit" placeholder="选择要上传到的单元" style="width:100%">
            <el-option v-for="u in units" :key="u.unit.id" :label="u.planName + ' / ' + u.unit.name" :value="u.unit.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Excel 文件">
          <el-upload :show-file-list="false" accept=".xlsx,.xls" :http-request="upload" :disabled="!upUnit">
            <el-button type="primary" :disabled="!upUnit">选择文件并上传</el-button>
          </el-upload>
        </el-form-item>
        <div class="muted" style="padding-left:90px">第一列写问题，第二列写答案，以文本格式保存；超过 100 条时超出部分将被忽略。</div>
      </el-form>
      <template v-if="upRes">
        <el-divider />
        <p>共 {{ upRes.totalRows }} 行，成功 <b class="st-green">{{ upRes.success }}</b>，失败 <b class="st-red">{{ upRes.errors.length }}</b>，超出上限忽略 {{ upRes.ignored }}</p>
        <el-table v-if="upRes.errors.length" :data="upRes.errors" size="small" max-height="240" border>
          <el-table-column prop="row" label="行号" width="60" />
          <el-table-column prop="question" label="问题" min-width="140" />
          <el-table-column prop="reason" label="错误信息" min-width="200" />
        </el-table>
      </template>
    </el-dialog>

    <!-- 出价助手 -->
    <el-dialog v-model="bidDlg" title="出价助手" width="560px">
      <template v-if="bid">
        <p><b>{{ bid.question }}</b></p>
        <p class="muted">当前出价 ¥{{ bid.currentBid }} · 质量度 {{ bid.quality }}</p>
        <el-table :data="[{ d: '计算机', ...bid.pc }, { d: '移动设备', ...bid.mobile }]" border>
          <el-table-column prop="d" label="推广设备" width="90" />
          <el-table-column prop="competitors" label="竞争广告主" width="100" />
          <el-table-column label="建议出价"><template #default="{ row }"><b>¥{{ row.suggestedBid }}</b></template></el-table-column>
          <el-table-column label="当前出价能否排第一"><template #default="{ row }"><span :class="row.currentRankFirst ? 'st-green' : 'st-orange'">{{ row.currentRankFirst ? '能' : '不能' }}</span></template></el-table-column>
        </el-table>
        <p class="muted">{{ bid.note }}</p>
      </template>
      <template #footer>
        <el-button @click="bidDlg = false">关闭</el-button>
        <el-button type="primary" @click="applyBid">采用 PC 建议出价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http, { download } from '../../api'
import { AUDIT, stripHtml } from '../../labels'
import { loadStats, batch } from './stats'
import InlineEdit from '../InlineEdit.vue'
import QaEditor from '../QaEditor.vue'

const props = defineProps({ planId: Number, unitId: Number, range: Array, plans: Array, units: Array })
const emit = defineEmits(['changed', 'stats'])
const router = useRouter()
const list = ref([])
const stats = ref(new Map())
const loading = ref(false)
const sel = ref([])
const kw = ref('')
const audit = ref(null)
const lowOnly = ref(false)
const knowledge = ref([])
const editDlg = ref(false)
const editing = ref(null)
const upDlg = ref(false)
const upUnit = ref(null)
const upRes = ref(null)
const bidDlg = ref(false)
const bid = ref(null)
const ZERO = { impressions: 0, clicks: 0, ctr: 0, cost: '0.00', cpc: '0.00' }
const s = row => stats.value.get(row.id) || ZERO
const unitName = id => props.units?.find(u => u.unit.id === id)?.unit.name || '#' + id
const unitBid = row => props.units?.find(u => u.unit.id === row.unitId)?.unit.bid
const rows = computed(() => list.value.filter(r => !lowOnly.value || (s(r).impressions >= 50 && s(r).ctr < 2)))

async function load() {
  loading.value = true
  try {
    const [l, st] = await Promise.all([
      http.get('/qas', { params: { planId: props.planId || undefined, unitId: props.unitId || undefined, auditStatus: audit.value || undefined, keyword: kw.value || undefined } }),
      loadStats('qa', props.range, props.planId, props.unitId)
    ])
    list.value = l
    stats.value = st.map
    emit('stats', st)
  } finally { loading.value = false }
}
function create() { router.push({ path: '/qas/new', query: props.unitId ? { unitId: props.unitId } : {} }) }
async function edit(row) {
  if (!knowledge.value.length) knowledge.value = await http.get('/knowledge')
  editing.value = { id: row.id, question: row.question, answer: row.answer, bid: row.bid, source: row.source }
  editDlg.value = true
}
async function saveEdit() {
  const e = editing.value
  await http.put(`/qas/${e.id}`, { question: e.question, answer: e.answer, bid: e.bid, source: e.source })
  editDlg.value = false
  ElMessage.success('已保存'); load(); emit('changed')
}
async function saveBid(row, v) {
  await http.put(`/qas/${row.id}`, { question: row.question, answer: row.answer, bid: v, source: row.source })
  ElMessage.success('出价已修改'); load()
}
async function toggle(row) { await http.put(`/qas/${row.id}/status`, { status: row.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' }); load() }
async function remove(row) { await http.delete(`/qas/${row.id}`); load(); emit('changed') }
async function bulk(cmd) {
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除选中的 ${sel.value.length} 条问答？`, '删除', { type: 'warning' })
    await batch(sel.value, r => http.delete(`/qas/${r.id}`))
  } else await batch(sel.value, r => http.put(`/qas/${r.id}/status`, { status: cmd }))
  ElMessage.success('操作完成'); load(); emit('changed')
}
function openUpload() { upUnit.value = props.unitId || null; upRes.value = null; upDlg.value = true }
async function upload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  upRes.value = await http.post(`/units/${upUnit.value}/qas/upload`, fd)
  load(); emit('changed')
}
function exportCsv() {
  const esc = v => '"' + String(v ?? '').replace(/"/g, '""') + '"'
  const lines = ['﻿问题,答案,状态,出价,推广单元,展现,点击,消费']
  for (const r of rows.value) lines.push([r.question, stripHtml(r.answer), AUDIT[r.auditStatus].text, r.bid ?? '', unitName(r.unitId), s(r).impressions, s(r).clicks, s(r).cost].map(esc).join(','))
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([lines.join('\n')], { type: 'text/csv' }))
  a.download = 'qa.csv'
  a.click()
}
async function suggest(row) { bid.value = { ...(await http.get('/tools/bid-suggestion', { params: { qaId: row.id } })), row }; bidDlg.value = true }
async function applyBid() { await saveBid(bid.value.row, bid.value.pc.suggestedBid); bidDlg.value = false }
watch(() => [props.range, props.planId, props.unitId], load)
onMounted(load)
</script>

<style scoped>
.ellipsis { color: #666; }
</style>
<style>
.ans-tip { max-width: 480px; line-height: 1.6; }
</style>
