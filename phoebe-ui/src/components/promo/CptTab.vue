<template>
  <div>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
      title="CPT 包词：按天包下一个词，下单时一次性预付；同一个词同一天只卖给一家。命中该词的检索中，CPT 广告占第一个广告位、优先于 CPC，CPC 顺延；CPT 点击不再扣费。" />
    <div class="pb-toolbar">
      <el-button type="primary" icon="Plus" @click="router.push({ path: '/cpt/new', query: unitId ? { unitId } : {} })">新建 CPT 包词</el-button>
      <el-select v-model="phase" placeholder="全部状态" clearable style="width:120px">
        <el-option v-for="p in ['待开始','投放中','已结束','已取消']" :key="p" :label="p" :value="p" />
      </el-select>
      <span class="spacer" />
      <el-input v-model="kw" placeholder="包断词" prefix-icon="Search" clearable style="width:180px" />
    </div>
    <el-table :data="rows" border v-loading="loading">
      <el-table-column label="包断词" prop="keyword" min-width="120" />
      <el-table-column label="展示问答" prop="question" min-width="200" />
      <el-table-column label="投放日期" min-width="190"><template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template></el-table-column>
      <el-table-column label="天数" width="60"><template #default="{ row }">{{ days(row) }}</template></el-table-column>
      <el-table-column label="日单价（元）" prop="dailyPrice" width="100" />
      <el-table-column label="总价（元）" prop="totalPrice" width="100" />
      <el-table-column label="退款（元）" width="90"><template #default="{ row }">{{ Number(row.refundAmount) ? row.refundAmount : '—' }}</template></el-table-column>
      <el-table-column label="状态" width="80"><template #default="{ row }"><span :class="['st', PHASE[row.phase]]">{{ row.phase }}</span></template></el-table-column>
      <el-table-column label="展现" prop="impressions" width="70" />
      <el-table-column label="点击" prop="clicks" width="70" />
      <el-table-column label="点击率" width="80"><template #default="{ row }">{{ row.impressions ? (row.clicks * 100 / row.impressions).toFixed(2) : '0.00' }}%</template></el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.phase === '待开始' || row.phase === '投放中'" link type="danger" @click="cancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, inject } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api'

const props = defineProps({ planId: Number, unitId: Number, range: Array, plans: Array, units: Array })
const emit = defineEmits(['changed', 'stats'])
const refreshAccount = inject('refreshAccount', () => {})
const router = useRouter()
const PHASE = { 待开始: 'st-blue', 投放中: 'st-green', 已结束: 'st-gray', 已取消: 'st-gray' }
const list = ref([])
const loading = ref(false)
const phase = ref(null)
const kw = ref('')
const rows = computed(() => list.value.filter(o =>
  (!props.planId || o.planId === props.planId) && (!props.unitId || o.unitId === props.unitId) &&
  (!phase.value || o.phase === phase.value) && (!kw.value || o.keyword.includes(kw.value))))
const days = o => Math.round((new Date(o.endDate) - new Date(o.startDate)) / 86400000) + 1

async function load() {
  loading.value = true
  try { list.value = await http.get('/cpt/orders') } finally { loading.value = false }
  emit('stats', null)
}
async function cancel(o) {
  const started = o.phase === '投放中'
  await ElMessageBox.confirm(
    started ? `订单已开始投放：取消后立即停止展现，退还明天起未投放天数的费用（今天的费用不退）。确定取消？` : `订单尚未开始：取消后全额退款 ¥${o.totalPrice}。确定取消？`,
    `取消 CPT「${o.keyword}」`, { type: 'warning' })
  const r = await http.post(`/cpt/orders/${o.id}/cancel`)
  ElMessage.success(`已取消，退款 ¥${r.refundAmount}`)
  refreshAccount()
  load()
}
onMounted(load)
</script>
