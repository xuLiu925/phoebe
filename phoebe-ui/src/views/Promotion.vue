<template>
  <div class="promo">
    <!-- 账户结构树 -->
    <aside class="tree" :class="{ folded }">
      <div class="tree-head">
        <span v-if="!folded">账户结构</span>
        <el-icon class="fold" @click="folded = !folded"><component :is="folded ? 'DArrowRight' : 'DArrowLeft'" /></el-icon>
      </div>
      <template v-if="!folded">
        <el-input v-model="treeKw" placeholder="搜索计划/单元" prefix-icon="Search" clearable style="margin:0 10px 8px;width:calc(100% - 20px)" />
        <div :class="['node', 'root', { on: !planId && !unitId }]" @click="select(null, null)">
          <el-icon><Folder /></el-icon>全部计划 <span class="cnt">{{ plans.length }}</span>
        </div>
        <div v-for="p in filteredTree" :key="p.plan.id">
          <div :class="['node', { on: planId == p.plan.id && !unitId }]" @click="select(p.plan.id, null)">
            <el-icon class="caret" @click.stop="toggle(p.plan.id)"><component :is="expanded[p.plan.id] ? 'CaretBottom' : 'CaretRight'" /></el-icon>
            <span :class="['dot', p.blockers.length ? 'warn' : 'ok']" />
            <span class="label" :title="p.plan.name">{{ p.plan.name }}</span>
          </div>
          <template v-if="expanded[p.plan.id]">
            <div v-for="u in unitsOf(p.plan.id)" :key="u.unit.id" :class="['node', 'leaf', { on: unitId == u.unit.id }]" @click="select(p.plan.id, u.unit.id)">
              <span :class="['dot', u.unit.status === 'ACTIVE' ? 'ok' : 'off']" />
              <span class="label" :title="u.unit.name">{{ u.unit.name }}</span>
            </div>
          </template>
        </div>
      </template>
    </aside>

    <section class="work">
      <div class="crumb">
        <a @click="select(null, null)">全部计划</a>
        <template v-if="curPlan"> <span>›</span> <a @click="select(curPlan.plan.id, null)">{{ curPlan.plan.name }}</a></template>
        <template v-if="curUnit"> <span>›</span> <b>{{ curUnit.unit.name }}</b></template>
        <span v-if="curUnit" class="muted" style="margin-left:12px">单元出价 ¥{{ curUnit.unit.bid }} · 订阅词：{{ curUnit.subscribeWords.join('、') || '无' }}</span>
        <span v-else-if="curPlan" class="muted" style="margin-left:12px">
          日预算 {{ curPlan.plan.budget ?? '不限' }} · 移动出价比例 {{ curPlan.plan.mobileBidRatio }} · 地域 {{ curPlan.plan.regions || '不限' }}
        </span>
      </div>

      <div class="levels">
        <router-link v-for="l in LEVELS" :key="l.key" :to="{ path: '/promotion/' + l.key, query: route.query }" :class="{ on: level === l.key }">
          {{ l.text }}
        </router-link>
      </div>

      <div class="pb-panel summary" v-if="summary">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="~" :clearable="false" style="width:230px" />
        <div class="kpis">
          <div class="kpi"><div class="k">展现</div><div class="v">{{ summary.impressions }}</div></div>
          <div class="kpi"><div class="k">点击</div><div class="v">{{ summary.clicks }}</div></div>
          <div class="kpi"><div class="k">点击率</div><div class="v">{{ summary.ctr }}%</div></div>
          <template v-if="summary.cost !== undefined">
            <div class="kpi"><div class="k">消费（元）</div><div class="v">{{ summary.cost }}</div></div>
            <div class="kpi"><div class="k">平均点击价格</div><div class="v">{{ summary.cpc }}</div></div>
          </template>
        </div>
        <div ref="spark" class="spark" />
      </div>

      <div class="pb-panel">
        <component :is="TAB[level]" :plan-id="planId" :unit-id="unitId" :range="range" :plans="plans" :units="units"
          @changed="reloadTree" @stats="onStats" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import http from '../api'
import { defaultRange } from '../labels'
import PlanTab from '../components/promo/PlanTab.vue'
import UnitTab from '../components/promo/UnitTab.vue'
import PageTab from '../components/promo/PageTab.vue'
import QaTab from '../components/promo/QaTab.vue'
import CreativeTab from '../components/promo/CreativeTab.vue'

const LEVELS = [
  { key: 'plan', text: '推广计划' }, { key: 'unit', text: '推广单元' }, { key: 'page', text: '中间页' },
  { key: 'qa', text: '知识问答' }, { key: 'creative', text: '高级创意' }
]
const TAB = { plan: PlanTab, unit: UnitTab, page: PageTab, qa: QaTab, creative: CreativeTab }

const route = useRoute()
const router = useRouter()
const level = computed(() => route.params.level)
const planId = computed(() => (route.query.planId ? Number(route.query.planId) : null))
const unitId = computed(() => (route.query.unitId ? Number(route.query.unitId) : null))
const plans = ref([])
const units = ref([])
const expanded = reactive({})
const folded = ref(false)
const treeKw = ref('')
const range = ref(defaultRange())
const summary = ref(null)
const spark = ref()
let chart

const curPlan = computed(() => plans.value.find(p => p.plan.id === planId.value))
const curUnit = computed(() => units.value.find(u => u.unit.id === unitId.value))
const unitsOf = pid => units.value.filter(u => u.unit.planId === pid && (!treeKw.value || u.unit.name.includes(treeKw.value) || planName(pid).includes(treeKw.value)))
const planName = pid => plans.value.find(p => p.plan.id === pid)?.plan.name || ''
const filteredTree = computed(() => plans.value.filter(p => !treeKw.value || p.plan.name.includes(treeKw.value) || unitsOf(p.plan.id).length))

function select(pid, uid) {
  const q = {}
  if (pid) q.planId = pid
  if (uid) q.unitId = uid
  if (pid) expanded[pid] = true
  // 选中单元时，计划/单元层级 tab 跳到下一层级
  let lv = level.value
  if (uid && (lv === 'plan' || lv === 'unit' || lv === 'page')) lv = 'qa'
  else if (pid && !uid && lv === 'plan') lv = 'unit'
  router.push({ path: '/promotion/' + lv, query: q })
}
function toggle(pid) { expanded[pid] = !expanded[pid] }

async function reloadTree() {
  ;[plans.value, units.value] = await Promise.all([http.get('/plans'), http.get('/units')])
}

async function onStats(s) {
  summary.value = s?.summary || null
  await nextTick()
  if (!spark.value || !s) return
  chart ||= echarts.init(spark.value)
  chart.setOption({
    grid: { left: 0, right: 0, top: 6, bottom: 0 }, tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', show: false, data: s.trend.map(t => t.name) },
    yAxis: { type: 'value', show: false },
    series: [{ type: 'line', smooth: true, symbol: 'none', name: '点击', data: s.trend.map(t => t.clicks), areaStyle: { color: 'rgba(51,133,255,.12)' }, lineStyle: { color: '#3385ff' } }]
  })
}

watch(planId, p => { if (p) expanded[p] = true }, { immediate: true })
onMounted(reloadTree)
</script>

<style scoped>
.promo { display: flex; min-height: calc(100vh - 56px); }
.tree { width: 220px; background: #fff; border-right: 1px solid #e6e8eb; flex-shrink: 0; font-size: 12px; }
.tree.folded { width: 28px; }
.tree-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 10px; color: #666; font-weight: 600; }
.fold { cursor: pointer; color: #999; }
.node { display: flex; align-items: center; gap: 4px; padding: 6px 10px; cursor: pointer; color: #333; }
.node:hover { background: #f5f8ff; }
.node.on { background: #ebf3ff; color: #3385ff; }
.node.leaf { padding-left: 34px; }
.node .label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.caret { color: #999; }
.cnt { margin-left: auto; color: #999; }
.dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.dot.ok { background: #2bb24c; } .dot.warn { background: #ff9900; } .dot.off { background: #c0c4cc; }
.work { flex: 1; padding: 12px 20px 40px; min-width: 0; }
.crumb { font-size: 13px; margin-bottom: 10px; }
.crumb a { cursor: pointer; }
.crumb span { color: #999; margin: 0 2px; }
.levels { display: flex; border-bottom: 1px solid #dcdfe6; margin-bottom: 12px; }
.levels a { padding: 9px 18px; color: #333; font-size: 14px; border-bottom: 2px solid transparent; margin-bottom: -1px; text-decoration: none; }
.levels a.on { color: #3385ff; border-bottom-color: #3385ff; font-weight: 600; }
.summary { display: flex; align-items: center; gap: 24px; padding: 12px 20px; flex-wrap: wrap; }
.summary :deep(.el-date-editor) { flex-shrink: 0; }
.kpis { display: flex; gap: 36px; white-space: nowrap; }
.kpi .k { color: #999; font-size: 12px; }
.kpi .v { font-size: 20px; font-weight: 600; margin-top: 2px; font-family: Arial; }
.spark { flex: 1; height: 44px; min-width: 120px; }
</style>
