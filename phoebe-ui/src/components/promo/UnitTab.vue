<template>
  <div>
    <div class="pb-toolbar">
      <el-button type="primary" icon="Plus" @click="router.push({ path: '/units/new', query: planId ? { planId } : {} })">新建推广单元</el-button>
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
      <span class="muted">每个推广计划最多 100 个推广单元<span class="evidence">公开资料</span></span>
      <span class="spacer" />
      <el-input v-model="kw" placeholder="推广单元名称" prefix-icon="Search" clearable style="width:200px" />
    </div>
    <el-table :data="rows" border v-loading="loading" @selection-change="sel = $event">
      <el-table-column type="selection" width="38" />
      <el-table-column label="推广单元" min-width="150">
        <template #default="{ row }">
          <a @click="drill(row)">{{ row.unit.name }}</a>
          <InlineEdit :value="row.unit.name" :maxlength="30" @save="v => patch(row.unit, { name: v })"><span /></InlineEdit>
        </template>
      </el-table-column>
      <el-table-column label="推广计划" prop="planName" min-width="130" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span :class="['st', row.unit.status === 'ACTIVE' ? 'st-green' : 'st-gray']">{{ row.unit.status === 'ACTIVE' ? '有效' : '暂停推广' }}</span>
          <el-icon class="edit-icon" @click="toggle(row.unit)"><component :is="row.unit.status === 'ACTIVE' ? 'VideoPause' : 'VideoPlay'" /></el-icon>
        </template>
      </el-table-column>
      <el-table-column label="单元出价（元）" width="120">
        <template #default="{ row }">
          <InlineEdit :value="row.unit.bid" type="number" :min="0.3" :max="999.99" @save="v => patch(row.unit, { bid: v })" />
        </template>
      </el-table-column>
      <el-table-column label="订阅词" min-width="150"><template #default="{ row }">{{ row.subscribeWords.join('、') || '—' }}</template></el-table-column>
      <el-table-column label="问答（有效/全部）" width="120">
        <template #default="{ row }"><a @click="toQa(row)">{{ row.approvedQaCount }} / {{ row.qaCount }}</a></template>
      </el-table-column>
      <el-table-column label="展现" width="80"><template #default="{ row }">{{ s(row).impressions }}</template></el-table-column>
      <el-table-column label="点击" width="70"><template #default="{ row }">{{ s(row).clicks }}</template></el-table-column>
      <el-table-column label="点击率" width="80"><template #default="{ row }">{{ s(row).ctr }}%</template></el-table-column>
      <el-table-column label="消费" width="90"><template #default="{ row }">{{ s(row).cost }}</template></el-table-column>
      <el-table-column label="平均点击价格" width="100"><template #default="{ row }">{{ s(row).cpc }}</template></el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/units/${row.unit.id}/edit`)">编辑</el-button>
          <el-popconfirm title="删除单元及其问答、创意？" @confirm="remove(row.unit)">
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
const ZERO = { impressions: 0, clicks: 0, ctr: 0, cost: '0.00', cpc: '0.00' }
const s = row => stats.value.get(row.unit.id) || ZERO
const rows = computed(() => list.value.filter(r =>
  (!props.planId || r.unit.planId === props.planId) && (!props.unitId || r.unit.id === props.unitId) && (!kw.value || r.unit.name.includes(kw.value))))

async function load() {
  loading.value = true
  try {
    const [l, st] = await Promise.all([http.get('/units'), loadStats('unit', props.range, props.planId)])
    list.value = l
    stats.value = st.map
    emit('stats', st)
  } finally { loading.value = false }
}
const drill = row => router.push({ path: '/promotion/qa', query: { planId: row.unit.planId, unitId: row.unit.id } })
const toQa = drill
async function patch(u, change) {
  await http.put(`/units/${u.id}`, { name: u.name, bid: u.bid, subscribeWords: null, ...change })
  ElMessage.success('已修改'); load(); emit('changed')
}
async function toggle(u) { await http.put(`/units/${u.id}/status`, { status: u.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' }); load(); emit('changed') }
async function remove(u) { await http.delete(`/units/${u.id}`); load(); emit('changed') }
async function bulk(cmd) {
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除选中的 ${sel.value.length} 个单元？`, '删除', { type: 'warning' })
    await batch(sel.value, r => http.delete(`/units/${r.unit.id}`))
  } else await batch(sel.value, r => http.put(`/units/${r.unit.id}/status`, { status: cmd }))
  ElMessage.success('操作完成'); load(); emit('changed')
}
watch(() => [props.range, props.planId], load)
onMounted(load)
</script>
