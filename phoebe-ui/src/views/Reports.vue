<template>
  <div class="pb-page">
    <div class="pb-panel" style="padding:20px 28px">
      <div class="pb-panel-title">数据维度</div>
      <el-form label-width="110px" class="dims">
        <el-form-item label="选择时间范围：">
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="~" :clearable="false" style="width:260px" />
        </el-form-item>
        <el-form-item label="选择报告类型：">
          <el-radio-group v-model="level">
            <el-radio value="ACCOUNT">账户报告</el-radio><el-radio value="PLAN">推广计划报告</el-radio><el-radio value="UNIT">推广单元报告</el-radio>
            <el-radio value="QA">问答报告</el-radio><el-radio value="MIDDLE_PAGE">中间页报告</el-radio><el-radio value="CPT">CPT 包词报告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="选择流量类型：">
          <el-radio-group v-model="flow">
            <el-radio value="ALL">全部</el-radio>
            <el-radio value="ZHIDAO">知道</el-radio>
            <el-radio value="BAOBAO">宝宝知道（不含app）</el-radio>
            <el-radio value="BAOBAO_APP">宝宝知道app</el-radio>
          </el-radio-group>
          <span class="hint">网页搜索中的知道结果计入“知道”</span>
        </el-form-item>
        <el-form-item label="选择推广设备：">
          <el-radio-group v-model="device"><el-radio value="">全部</el-radio><el-radio value="PC">计算机</el-radio><el-radio value="MOBILE">移动设备</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="选择地域类型：">
          <el-radio-group v-model="splitRegion">
            <el-radio :value="false">全地域</el-radio>
            <el-radio :value="true">分地域</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查看报告</el-button>
          <el-button type="primary" plain :disabled="level === 'MIDDLE_PAGE' || level === 'CPT'" @click="exportCsv">下载报告</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="pb-panel" v-if="cptRows">
      <p class="muted" style="margin-top:0">CPT 按天预付、点击不扣费，不计入上面各层级的 CPC 消费，单独统计。消费为订单实付（总价 − 退款）。</p>
      <el-table :data="cptRows" border>
        <el-table-column prop="name" label="包断词" min-width="120" />
        <el-table-column prop="question" label="展示问答" min-width="200" />
        <el-table-column prop="period" label="投放日期" min-width="180" />
        <el-table-column prop="phase" label="状态" width="80" />
        <el-table-column prop="impressions" label="展现" width="80" />
        <el-table-column prop="clicks" label="点击" width="80" />
        <el-table-column label="点击率" width="80"><template #default="{ row }">{{ row.ctr }}%</template></el-table-column>
        <el-table-column prop="dailyPrice" label="日单价" width="90" />
        <el-table-column prop="cost" label="实付（元）" width="100" />
      </el-table>
    </div>
    <div class="pb-panel" v-if="data && level !== 'CPT'">
      <div class="kpis" v-if="data.summary">
        <div><div class="muted">展现</div><b>{{ data.summary.impressions }}</b></div>
        <div><div class="muted">点击</div><b>{{ data.summary.clicks }}</b></div>
        <div><div class="muted">点击率</div><b>{{ data.summary.ctr }}%</b></div>
        <div><div class="muted">消费（元）</div><b>{{ data.summary.cost }}</b></div>
        <div><div class="muted">平均点击价格</div><b>{{ data.summary.cpc }}</b></div>
      </div>
      <div ref="chart" style="height:260px" />
      <el-table :data="data.rows" border style="margin-top:8px">
        <el-table-column prop="name" :label="levelText" min-width="220" />
        <el-table-column v-if="shownSplit" prop="region" label="地域" width="100" sortable />
        <el-table-column prop="impressions" label="展现" width="110" sortable />
        <el-table-column prop="clicks" label="点击" width="100" sortable />
        <el-table-column label="点击率" width="100"><template #default="{ row }">{{ row.ctr }}%</template></el-table-column>
        <template v-if="level !== 'MIDDLE_PAGE'">
          <el-table-column prop="cost" label="消费" width="110" sortable />
          <el-table-column prop="cpc" label="平均点击价格" width="120" />
        </template>
      </el-table>
      <p class="muted">账户/计划/单元/问答提供展现、点击、点击率、消费、平均点击价格；中间页（不区分模块）提供展现、点击、点击率。<span class="evidence">公开资料 A/B</span></p>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import http, { download } from '../api'
import { defaultRange, fmtDate } from '../labels'

const level = ref('PLAN')
const range = ref(defaultRange())
const device = ref('')
const data = ref(null)
const chart = ref()
let ec
const levelText = computed(() => ({ ACCOUNT: '账户', PLAN: '推广计划', UNIT: '推广单元', QA: '问题', MIDDLE_PAGE: '中间页' })[level.value])
const flow = ref('ALL')
const splitRegion = ref(false)
const shownSplit = ref(false)
const params = () => ({ level: level.value, from: range.value[0], to: range.value[1], device: device.value || undefined, flow: flow.value, splitRegion: splitRegion.value })

const cptRows = ref(null)
// CPT 订单多为进行中：切到 CPT 报告时默认包含今天
watch(level, (lv, old) => {
  if (lv === 'CPT') range.value = [fmtDate(new Date(Date.now() - 6 * 86400000)), fmtDate(new Date())]
  else if (old === 'CPT') range.value = defaultRange()
})
async function load() {
  if (level.value === 'CPT') {
    cptRows.value = await http.get('/cpt/report', { params: { from: range.value[0], to: range.value[1] } })
    data.value = null
    return
  }
  cptRows.value = null
  data.value = await http.get('/reports', { params: params() })
  shownSplit.value = splitRegion.value
  await nextTick()
  ec ||= echarts.init(chart.value)
  const t = data.value.trend
  const series = [
    { name: '展现', type: 'bar', barWidth: 16, data: t.map(x => x.impressions), itemStyle: { color: '#c2daff' } },
    { name: '点击', type: 'line', smooth: true, data: t.map(x => x.clicks), itemStyle: { color: '#3385ff' } }
  ]
  if (level.value !== 'MIDDLE_PAGE') series.push({ name: '消费', type: 'line', smooth: true, yAxisIndex: 1, data: t.map(x => x.cost), itemStyle: { color: '#ff9900' } })
  ec.setOption({
    tooltip: { trigger: 'axis' }, legend: { right: 0 }, grid: { left: 50, right: 50, top: 30, bottom: 24 },
    xAxis: { type: 'category', data: t.map(x => x.name.slice(5)) },
    yAxis: [{ type: 'value', splitLine: { lineStyle: { color: '#f0f0f0' } } }, { type: 'value', splitLine: { show: false } }], series
  }, true)
}
function exportCsv() { download('/reports/export', params(), `report-${level.value.toLowerCase()}.csv`) }
onMounted(load)
</script>

<style scoped>
.dims .el-form-item { margin-bottom: 12px; }
.kpis { display: flex; gap: 48px; margin-bottom: 8px; }
.kpis b { font-size: 20px; font-family: Arial; }
</style>
