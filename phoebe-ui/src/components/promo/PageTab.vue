<template>
  <div>
    <div class="pb-toolbar">
      <el-button type="primary" icon="Plus" @click="router.push({ path: '/middle-pages/new', query: planId ? { planId } : {} })">新建中间页</el-button>
      <el-dropdown :disabled="!sel.length" @command="bulk">
        <el-button :disabled="!sel.length">编辑操作<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
        <template #dropdown><el-dropdown-menu><el-dropdown-item command="delete">删除</el-dropdown-item></el-dropdown-menu></template>
      </el-dropdown>
      <el-select v-model="status" placeholder="全部状态" clearable style="width:120px">
        <el-option v-for="s in ['未完成','待提交','审核中','审核拒绝','有效']" :key="s" :label="s" :value="s" />
      </el-select>
      <span class="muted">一个计划最多 2 套中间页，同一时间只能启用 1 套；审核通过（有效）后才能启用<span class="evidence">公开资料 A/B</span></span>
      <span class="spacer" />
      <el-input v-model="kw" placeholder="物料名称" prefix-icon="Search" clearable style="width:180px" />
    </div>
    <el-table :data="rows" border v-loading="loading" @selection-change="sel = $event">
      <el-table-column type="selection" width="38" />
      <el-table-column label="物料名称" min-width="160">
        <template #default="{ row }">
          <a @click="router.push(`/middle-pages/${row.page.id}`)">{{ row.page.name }}</a>
          <el-tag v-if="row.page.enabled" size="small" type="success" effect="plain" style="margin-left:6px">启用中</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="130">
        <template #default="{ row }">
          <el-tooltip :disabled="!row.latest?.rejectReason && !row.missing?.length"
            :content="row.latest?.rejectReason || ('未完成：' + (row.missing || []).join('、'))">
            <span :class="['st', pageStatus(row).cls]">{{ pageStatus(row).text }}<el-icon v-if="row.latest?.rejectReason || (row.status==='DRAFT' && row.missing?.length)" style="vertical-align:-2px"><QuestionFilled /></el-icon></span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="推广计划" min-width="130"><template #default="{ row }">{{ planName(row.page.planId) }}</template></el-table-column>
      <el-table-column label="线上版本" width="80"><template #default="{ row }">{{ row.online ? 'v' + row.online.versionNo : '—' }}</template></el-table-column>
      <el-table-column label="展现" width="80"><template #default="{ row }">{{ s(row).impressions }}</template></el-table-column>
      <el-table-column label="点击" width="70"><template #default="{ row }">{{ s(row).clicks }}</template></el-table-column>
      <el-table-column label="点击率" width="80"><template #default="{ row }">{{ s(row).ctr }}%</template></el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="preview(row, 'PC')">PC预览</el-button>
          <el-button link type="primary" @click="preview(row, 'MOBILE')">无线预览</el-button>
          <el-button link type="primary" :disabled="row.latest?.auditStatus === 'PENDING'" @click="router.push(`/middle-pages/${row.page.id}`)">编辑</el-button>
          <el-button v-if="!row.page.enabled" link type="primary" :disabled="!row.online" @click="enable(row)">启用</el-button>
          <el-button v-else link @click="disable(row)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="muted" style="margin-top:8px">状态说明：未完成（可预览/编辑）· 审核中（可预览，不可编辑）· 审核拒绝（可预览/编辑）· 有效（可预览/编辑/启用/被导入）<span class="evidence">公开资料 A</span></div>

    <el-dialog v-model="dlg" :title="`${pv?.name} · ${pv?.device === 'MOBILE' ? '无线' : 'PC'}预览（${pv?.which === 'online' ? '线上版本' : '最新版本'} v${pv?.versionNo}）`" :width="pv?.device === 'MOBILE' ? 420 : 960" top="4vh">
      <PagePreview v-if="pv" :content="pv.content" :device="pv.device" :pc-style="pv.pcStyle"
        question="此处为预览，真实展示时以您提交的问题为准" answer="<p>此处为预览，真实展示时以您系统提交的问题为准，请关注中间页物料是否展示正常。</p>" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api'
import { pageStatus } from '../../labels'
import { loadStats, batch } from './stats'
import PagePreview from '../PagePreview.vue'

const props = defineProps({ planId: Number, unitId: Number, range: Array, plans: Array, units: Array })
const emit = defineEmits(['changed', 'stats'])
const router = useRouter()
const list = ref([])
const stats = ref(new Map())
const loading = ref(false)
const sel = ref([])
const kw = ref('')
const status = ref(null)
const dlg = ref(false)
const pv = ref(null)
const ZERO = { impressions: 0, clicks: 0, ctr: 0 }
const s = row => stats.value.get(row.page.id) || ZERO
const planName = id => props.plans?.find(p => p.plan.id === id)?.plan.name || '#' + id
const rows = computed(() => list.value.filter(r => (!kw.value || r.page.name.includes(kw.value)) && (!status.value || pageStatus(r).text.startsWith(status.value))))

async function load() {
  loading.value = true
  try {
    const [l, st] = await Promise.all([http.get('/middle-pages', { params: { planId: props.planId || undefined } }), loadStats('page', props.range, props.planId)])
    list.value = l
    stats.value = st.map
    emit('stats', st)
  } finally { loading.value = false }
}
async function preview(row, device) {
  const which = row.online ? 'online' : 'latest'
  pv.value = { ...(await http.get(`/middle-pages/${row.page.id}/preview`, { params: { device, which } })), which }
  dlg.value = true
}
async function enable(row) { await http.post(`/middle-pages/${row.page.id}/enable`); ElMessage.success('已启用，同计划其他中间页已停用'); load(); emit('changed') }
async function disable(row) { await http.post(`/middle-pages/${row.page.id}/disable`); load(); emit('changed') }
async function bulk() {
  await ElMessageBox.confirm(`确定删除选中的 ${sel.value.length} 套中间页？启用中的不能删除。`, '删除', { type: 'warning' })
  await batch(sel.value, r => http.delete(`/middle-pages/${r.page.id}`))
  load(); emit('changed')
}
watch(() => [props.range, props.planId], load)
onMounted(load)
</script>
