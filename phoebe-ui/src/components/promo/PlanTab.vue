<template>
  <div>
    <div class="pb-toolbar">
      <el-button type="primary" icon="Plus" @click="router.push('/plans/new')">新建推广计划</el-button>
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
      <el-select v-model="status" placeholder="全部状态" clearable style="width:120px">
        <el-option v-for="s in ['有效','暂停推广','暂停时段','预算不足','余额不足','中间页无效']" :key="s" :label="s" :value="s" />
      </el-select>
      <span class="muted">每个账户最多 100 个推广计划<span class="evidence">公开资料</span></span>
      <span class="spacer" />
      <el-input v-model="kw" placeholder="推广计划名称" prefix-icon="Search" clearable style="width:200px" />
    </div>
    <el-table :data="rows" border v-loading="loading" @selection-change="sel = $event" row-key="plan.id">
      <el-table-column type="selection" width="38" />
      <el-table-column label="推广计划" min-width="160">
        <template #default="{ row }">
          <a @click="drill(row)">{{ row.plan.name }}</a>
          <InlineEdit :value="row.plan.name" :maxlength="30" @save="v => patch(row.plan, { name: v })"><span /></InlineEdit>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tooltip :disabled="!planStatus(row).tip" :content="planStatus(row).tip">
            <span :class="['st', planStatus(row).cls]">{{ planStatus(row).text }}</span>
          </el-tooltip>
          <el-icon class="edit-icon" :title="row.plan.status === 'ACTIVE' ? '暂停' : '启用'" @click="toggle(row.plan)">
            <component :is="row.plan.status === 'ACTIVE' ? 'VideoPause' : 'VideoPlay'" />
          </el-icon>
        </template>
      </el-table-column>
      <el-table-column label="计划预算（元）" width="120">
        <template #default="{ row }">
          <InlineEdit :value="row.plan.budget" type="number" :min="50" :step="50" hint="清空表示不限预算" @save="v => patch(row.plan, { budget: v })">
            {{ row.plan.budget ?? '不限' }}
          </InlineEdit>
        </template>
      </el-table-column>
      <el-table-column label="移动出价比例" width="110">
        <template #default="{ row }">
          <InlineEdit :value="row.plan.mobileBidRatio" type="number" :min="0.1" :max="10" hint="0.10~10.00" @save="v => patch(row.plan, { mobileBidRatio: v })" />
        </template>
      </el-table-column>
      <el-table-column label="推广地域" min-width="100"><template #default="{ row }">{{ row.plan.regions || '不限' }}</template></el-table-column>
      <el-table-column label="投放时段" width="90">
        <template #default="{ row }">
          <el-tooltip v-if="row.plan.schedule" :content="`每周 ${[...row.plan.schedule].filter(c => c === '1').length} 小时`"><span>自选时段</span></el-tooltip>
          <span v-else>不限</span>
        </template>
      </el-table-column>
      <el-table-column label="IP排除" width="80"><template #default="{ row }">{{ row.plan.excludedIps ? row.plan.excludedIps.split(',').length + ' 条' : '不限' }}</template></el-table-column>
      <el-table-column label="单元数" prop="unitCount" width="70" />
      <el-table-column label="启用中间页" min-width="120"><template #default="{ row }">{{ row.enabledMiddlePageName || '—' }}</template></el-table-column>
      <el-table-column label="展现" width="80" sortable :sort-by="r => s(r).impressions"><template #default="{ row }">{{ s(row).impressions }}</template></el-table-column>
      <el-table-column label="点击" width="70" sortable :sort-by="r => s(r).clicks"><template #default="{ row }">{{ s(row).clicks }}</template></el-table-column>
      <el-table-column label="点击率" width="80"><template #default="{ row }">{{ s(row).ctr }}%</template></el-table-column>
      <el-table-column label="消费" width="90" sortable :sort-by="r => Number(s(r).cost)"><template #default="{ row }">{{ s(row).cost }}</template></el-table-column>
      <el-table-column label="平均点击价格" width="100"><template #default="{ row }">{{ s(row).cpc }}</template></el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/plans/${row.plan.id}/edit`)">编辑</el-button>
          <el-popconfirm title="删除计划将同时删除其下单元、问答、创意与中间页，确定？" width="240" @confirm="remove(row.plan)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api'
import { planStatus } from '../../labels'
import { loadStats, batch } from './stats'
import InlineEdit from '../InlineEdit.vue'

const props = defineProps({ planId: Number, unitId: Number, range: Array, plans: Array, units: Array })
const emit = defineEmits(['changed', 'stats'])
const router = useRouter()
const list = ref([])
const stats = ref(new Map())
const loading = ref(false)
const sel = ref([])
const kw = ref('')
const status = ref(null)
const ZERO = { impressions: 0, clicks: 0, ctr: 0, cost: '0.00', cpc: '0.00' }
const s = row => stats.value.get(row.plan.id) || ZERO

const rows = computed(() => list.value.filter(r =>
  (!props.planId || r.plan.id === props.planId) && (!kw.value || r.plan.name.includes(kw.value)) && (!status.value || planStatus(r).text === status.value)))

async function load() {
  loading.value = true
  try {
    const [l, st] = await Promise.all([http.get('/plans'), loadStats('plan', props.range)])
    list.value = l
    stats.value = st.map
    emit('stats', st)
  } finally { loading.value = false }
}
function drill(row) { router.push({ path: '/promotion/unit', query: { planId: row.plan.id } }) }
function req(p, patch) {
  return { name: p.name, budget: p.budget, mobileBidRatio: p.mobileBidRatio, regions: p.regions ? p.regions.split(',') : [], mobilePhone: p.mobilePhone,
    schedule: p.schedule, excludedIps: p.excludedIps ? p.excludedIps.split(',') : [], ...patch }
}
async function patch(p, change) { await http.put(`/plans/${p.id}`, req(p, change)); ElMessage.success('已修改'); load(); emit('changed') }
async function toggle(p) { await http.put(`/plans/${p.id}/status`, { status: p.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' }); load(); emit('changed') }
async function remove(p) { await http.delete(`/plans/${p.id}`); ElMessage.success('已删除'); load(); emit('changed') }
async function bulk(cmd) {
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除选中的 ${sel.value.length} 个计划及其下级物料？`, '删除', { type: 'warning' })
    await batch(sel.value, r => http.delete(`/plans/${r.plan.id}`))
  } else {
    await batch(sel.value, r => http.put(`/plans/${r.plan.id}/status`, { status: cmd }))
  }
  ElMessage.success('操作完成')
  load(); emit('changed')
}
watch(() => props.range, load)
onMounted(load)
</script>
