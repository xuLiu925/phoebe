<template>
  <div class="pb-page">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
      title="模拟业审：提交时保存内容快照，审核结果回写物料。机器预审命中违禁词会直接拒绝。真实产品业审承诺 3 个工作日（公开资料 B）。" />
    <div class="pb-toolbar">
      <el-radio-group v-model="status" @change="load">
        <el-radio-button value="PENDING">待审核</el-radio-button>
        <el-radio-button value="APPROVED">已通过</el-radio-button>
        <el-radio-button value="REJECTED">已拒绝</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <el-select v-model="type" clearable placeholder="物料类型" style="width:130px" @change="load">
        <el-option v-for="(t, k) in TARGET" :key="k" :label="t" :value="k" />
      </el-select>
      <span class="spacer" />
      <template v-if="auth.role==='ADMIN'">
        <span class="muted">自动审核（演示）</span><el-switch v-model="autoApprove" @change="setMode" />
      </template>
    </div>
    <el-table :data="list" border>
      <el-table-column prop="id" label="#" width="60" />
      <el-table-column label="类型" width="90"><template #default="{ row }">{{ TARGET[row.targetType] }}</template></el-table-column>
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="accountId" label="账户" width="60" />
      <el-table-column prop="preCheckResult" label="机器预审" min-width="160" />
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag size="small" :type="AUDIT[row.status].type">{{ AUDIT[row.status].text }}</el-tag></template></el-table-column>
      <el-table-column prop="reason" label="审核意见" min-width="140" />
      <el-table-column prop="createdAt" label="提交时间" width="160" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="open(row)">{{ row.status === 'PENDING' ? '审核' : '查看' }}</el-button></template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:10px" layout="total, prev, pager, next" :total="total" :page-size="20" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="dlg" :title="`审核 #${cur?.id} · ${TARGET[cur?.targetType]}`" width="900px" top="4vh">
      <template v-if="snap">
        <template v-if="cur.targetType === 'QA'">
          <h3>{{ snap.question }}</h3>
          <div class="answer-html" v-html="snap.answer" />
        </template>
        <template v-else-if="cur.targetType === 'CREATIVE'">
          <h3>{{ snap.title }}</h3><p>{{ snap.description }}</p>
          <img v-for="u in snap.imageUrls" :key="u" :src="u" style="width:120px;margin-right:6px" />
          <p class="muted">落地页：{{ snap.targetUrl }}</p>
        </template>
        <template v-else>
          <el-tabs><el-tab-pane label="PC"><PagePreview :content="snap.pc" device="PC" :pc-style="snap.pcStyle" /></el-tab-pane>
            <el-tab-pane label="无线"><PagePreview :content="snap.mobile" device="MOBILE" /></el-tab-pane></el-tabs>
        </template>
        <el-divider />
        <p class="muted">机器预审：{{ cur.preCheckResult }}</p>
        <el-input v-if="cur.status==='PENDING'" v-model="reason" type="textarea" placeholder="拒绝理由（拒绝时必填）" />
      </template>
      <template #footer v-if="cur?.status==='PENDING'">
        <el-button type="danger" @click="decide(false)">拒绝</el-button>
        <el-button type="success" @click="decide(true)">通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'
import { auth } from '../auth'
import { AUDIT, TARGET } from '../labels'
import PagePreview from '../components/PagePreview.vue'

const status = ref('PENDING')
const type = ref(null)
const list = ref([])
const total = ref(0)
const page = ref(1)
const dlg = ref(false)
const cur = ref(null)
const snap = ref(null)
const reason = ref('')
const autoApprove = ref(false)

async function load() {
  const r = await http.get('/audit/tasks', { params: { status: status.value || undefined, type: type.value || undefined, page: page.value, size: 20 } })
  list.value = r.records
  total.value = r.total
}
function open(row) {
  cur.value = row
  try { snap.value = JSON.parse(row.snapshot) } catch { snap.value = {} }
  reason.value = ''
  dlg.value = true
}
async function decide(ok) {
  if (ok) await http.post(`/audit/tasks/${cur.value.id}/approve`, { reason: reason.value || null })
  else await http.post(`/audit/tasks/${cur.value.id}/reject`, { reason: reason.value })
  ElMessage.success(ok ? '已通过' : '已拒绝')
  dlg.value = false
  load()
}
async function setMode(v) { await http.put('/admin/audit-mode', { autoApprove: v }); ElMessage.success(v ? '已开启自动审核' : '已切换为人工审核') }
onMounted(async () => {
  load()
  if (auth.role === 'ADMIN') autoApprove.value = (await http.get('/rules')).audit.autoApprove
})
</script>
